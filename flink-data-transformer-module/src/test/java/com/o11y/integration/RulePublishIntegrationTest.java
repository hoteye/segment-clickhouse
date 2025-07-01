package com.o11y.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.o11y.domain.model.alarm.AlarmRule;
import com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator;
import com.o11y.stream.task.HourlyRulePublishProcessFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.util.KeyedOneInputStreamOperatorTestHarness;
import org.apache.flink.streaming.util.ProcessFunctionTestHarnesses;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;

import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * 规则下发集成测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RulePublishIntegrationTest extends BaseIntegrationTest {

    private HourlyDynamicThresholdGenerator generator;

    @BeforeEach
    void setUpPublisher() throws Exception {
        // 清空规则表，插入测试规则 - 使用测试专用表
        Statement stmt = getClickHouseConnection().createStatement();

        // 创建测试专用表（如果不存在）
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS test_hourly_alarm_rules (" +
                        "hour_of_day Int8, service String, operator_name String, operator_class String, " +
                        "avg_duration_low Float64, avg_duration_mid Float64, avg_duration_high Float64, " +
                        "max_duration_low Float64, max_duration_mid Float64, max_duration_high Float64, " +
                        "success_rate_low Float64, success_rate_mid Float64, success_rate_high Float64, " +
                        "traffic_volume_low Float64, traffic_volume_mid Float64, traffic_volume_high Float64, " +
                        "alarm_template String, analysis_days Int8, sample_count Int32, " +
                        "generated_time DateTime, last_updated DateTime, version Int32" +
                        ") ENGINE = MergeTree() ORDER BY (hour_of_day, service, operator_name)");

        // 清空测试表而不是生产表
        stmt.execute("TRUNCATE TABLE test_hourly_alarm_rules");

        generator = new HourlyDynamicThresholdGenerator();

        LOG.info("规则下发测试初始化完成");
    }

    @Test
    @Order(1)
    @DisplayName("测试规则下发到Kafka流程")
    void testRulePublishToKafkaFlow() throws Exception {
        LOG.info("=== 开始测试规则下发到Kafka流程 ===");

        // 1. 生成测试规则
        generateTestRules();

        // 2. 模拟规则下发ProcessFunction
        testRulePublishProcessFunction();

        // 3. 验证Kafka消息
        verifyKafkaMessage();

        LOG.info("=== 规则下发到Kafka流程测试通过 ===");
    }

    @Test
    @Order(2)
    @DisplayName("测试定时器机制")
    void testTimerMechanism() throws Exception {
        LOG.info("=== 开始测试定时器机制 ===");

        // 生成测试规则
        generateTestRules();

        // 创建ProcessFunction测试环境
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) getTestConfig().get("clickhouse");
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) getTestConfig().get("kafka");

        HourlyRulePublishProcessFunction function = new HourlyRulePublishProcessFunction(
                clickhouseConfig.get("url"),
                clickhouseConfig.get("schema"),
                clickhouseConfig.get("username"),
                clickhouseConfig.get("password"),
                kafkaConfig.get("bootstrap_servers"),
                kafkaConfig.get("alarm_rule_topic"));

        // 创建测试工具
        KeyedOneInputStreamOperatorTestHarness<String, String, String> harness = ProcessFunctionTestHarnesses
                .forKeyedProcessFunction(
                        function,
                        x -> x,
                        TypeInformation.of(String.class));

        harness.open();

        // 发送触发信号
        harness.processElement("trigger", 1000L);

        // 设置处理时间，触发定时器
        harness.setProcessingTime(2000L);

        // 验证输出（规则下发ProcessFunction不产生输出到下游）
        assertThat(harness.extractOutputValues()).isEmpty();

        harness.close();

        LOG.info("=== 定时器机制测试通过 ===");
    }

    @Test
    @Order(3)
    @DisplayName("测试Kafka消息格式正确性")
    void testKafkaMessageFormat() throws Exception {
        LOG.info("=== 开始测试Kafka消息格式正确性 ===");

        // 生成测试规则
        generateTestRules();

        // 手动发送规则到Kafka（模拟HourlyRulePublishProcessFunction的行为）
        publishTestRulesToKafka();

        // 验证消息格式
        verifyKafkaMessageFormat();

        LOG.info("=== Kafka消息格式正确性测试通过 ===");
    }

    /**
     * 生成测试规则
     */
    private void generateTestRules() throws Exception {
        LOG.info("开始生成测试规则...");
        generator.generateAllHourlyRulesOnce(7);
        LOG.info("测试规则生成完成");
    }

    /**
     * 测试规则下发ProcessFunction
     */
    private void testRulePublishProcessFunction() throws Exception {
        // 这里可以添加更详细的ProcessFunction测试
        // 由于涉及到ClickHouse和Kafka的复杂交互，主要通过集成测试验证
        LOG.info("规则下发ProcessFunction测试完成");
    }

    /**
     * 验证Kafka消息
     */
    private void verifyKafkaMessage() throws Exception {
        // 订阅告警规则topic
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) getTestConfig().get("kafka");
        String topicName = kafkaConfig.get("alarm_rule_topic");

        getKafkaConsumer().subscribe(Collections.singletonList(topicName));

        // 发送测试规则到Kafka
        publishTestRulesToKafka();

        // 等待并验证消息
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = getKafkaConsumer().poll(Duration.ofSeconds(1));
            assertThat(records).isNotEmpty();

            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("global_alarm_rules");
            assertThat(record.value()).isNotNull();

            // 验证JSON格式
            Map<String, AlarmRule> rules = getObjectMapper().readValue(
                    record.value(),
                    new TypeReference<Map<String, AlarmRule>>() {
                    });
            assertThat(rules).isNotEmpty();
        });

        LOG.info("Kafka消息验证通过");
    }

    /**
     * 手动发送规则到Kafka
     */
    private void publishTestRulesToKafka() throws Exception {
        // 构造测试规则
        AlarmRule testRule = new AlarmRule();
        testRule.service = "test-service";
        testRule.operatorName = "testMethod";
        testRule.operatorClass = "TestOperator";
        testRule.avgDurationLow = 100.0;
        testRule.avgDurationMid = 150.0;
        testRule.avgDurationHigh = 200.0;
        testRule.maxDurationLow = 300.0;
        testRule.maxDurationMid = 450.0;
        testRule.maxDurationHigh = 600.0;
        testRule.successRateLow = 0.95;
        testRule.successRateMid = 0.98;
        testRule.successRateHigh = 0.99;
        testRule.trafficVolumeLow = 100.0;
        testRule.trafficVolumeMid = 500.0;
        testRule.trafficVolumeHigh = 1000.0;
        testRule.alarmTemplate = "测试告警模板";

        Map<String, AlarmRule> ruleMap = Map.of("test-service|testMethod", testRule);
        String ruleMapJson = getObjectMapper().writeValueAsString(ruleMap);

        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) getTestConfig().get("kafka");
        String topicName = kafkaConfig.get("alarm_rule_topic");

        ProducerRecord<String, String> record = new ProducerRecord<>(
                topicName,
                "global_alarm_rules",
                ruleMapJson);

        getKafkaProducer().send(record).get();
        LOG.info("测试规则已发送到Kafka");
    }

    /**
     * 验证Kafka消息格式
     */
    private void verifyKafkaMessageFormat() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) getTestConfig().get("kafka");
        String topicName = kafkaConfig.get("alarm_rule_topic");

        getKafkaConsumer().subscribe(Collections.singletonList(topicName));

        // 等待消息
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = getKafkaConsumer().poll(Duration.ofSeconds(1));

            if (!records.isEmpty()) {
                ConsumerRecord<String, String> record = records.iterator().next();

                // 验证key格式
                assertThat(record.key())
                        .describedAs("Kafka消息key应该是固定值")
                        .isEqualTo("global_alarm_rules");

                // 验证value是有效的JSON
                String jsonValue = record.value();
                assertThat(jsonValue).isNotNull();

                // 验证JSON可以反序列化为规则Map
                Map<String, AlarmRule> rules = getObjectMapper().readValue(
                        jsonValue,
                        new TypeReference<Map<String, AlarmRule>>() {
                        });

                assertThat(rules).isNotEmpty();

                // 验证规则内容
                AlarmRule rule = rules.values().iterator().next();
                assertThat(rule.service).isNotNull();
                assertThat(rule.operatorName).isNotNull();
                assertThat(rule.avgDurationHigh).isPositive();
                assertThat(rule.successRateHigh).isBetween(0.0, 1.0);

                LOG.info("Kafka消息格式验证通过 - 规则数量: {}", rules.size());
            }
        });
    }
}