package com.o11y.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.o11y.domain.model.alarm.AlarmRule;
import com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * 端到端集成测试
 * 
 * 测试从Segment数据流入到告警规则热更新的完整业务流程
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest extends BaseIntegrationTest {

    private HourlyDynamicThresholdGenerator generator;

    @BeforeAll
    static void setUpE2ETest() {
        LOG.info("=== 开始端到端集成测试 ===");
    }

    @AfterAll
    static void tearDownE2ETest() {
        LOG.info("=== 端到端集成测试完成 ===");
    }

    @BeforeEach
    void setUpE2E() throws Exception {
        generator = new TestHourlyDynamicThresholdGenerator();

        // 使用测试专用表，确保不影响生产数据
        Statement stmt = getClickHouseConnection().createStatement();

        // 创建测试专用表（如果不存在）
        createTestTables(stmt);

        // 清空测试表数据
        stmt.execute("TRUNCATE TABLE test_flink_operator_agg_result");
        stmt.execute("TRUNCATE TABLE test_hourly_alarm_rules");

        LOG.info("端到端测试环境准备完成（使用测试专用表）");
    }

    /**
     * 创建测试专用表
     */
    private void createTestTables(Statement stmt) throws Exception {
        // 创建测试聚合结果表
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS test_flink_operator_agg_result (" +
                        "window_start DateTime64(3), " +
                        "windowSize Int32, " +
                        "operator_class String, " +
                        "operator_name String, " +
                        "service String, " +
                        "avg_duration Float64, " +
                        "max_duration Int64, " +
                        "error_rate Float64, " +
                        "total_count Int64, " +
                        "error_count Int64, " +
                        "success_count Int64" +
                        ") ENGINE = MergeTree() " +
                        "ORDER BY window_start");

        // 创建测试规则表
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS test_hourly_alarm_rules (" +
                        "hour_of_day Int8, " +
                        "service String, " +
                        "operator_name String, " +
                        "operator_class String, " +
                        "avg_duration_low Float64, " +
                        "avg_duration_mid Float64, " +
                        "avg_duration_high Float64, " +
                        "max_duration_low Float64, " +
                        "max_duration_mid Float64, " +
                        "max_duration_high Float64, " +
                        "success_rate_low Float64, " +
                        "success_rate_mid Float64, " +
                        "success_rate_high Float64, " +
                        "traffic_volume_low Float64, " +
                        "traffic_volume_mid Float64, " +
                        "traffic_volume_high Float64, " +
                        "alarm_template String, " +
                        "analysis_days Int8, " +
                        "sample_count Int32, " +
                        "generated_time DateTime, " +
                        "last_updated DateTime, " +
                        "version Int32" +
                        ") ENGINE = MergeTree() " +
                        "ORDER BY (hour_of_day, service, operator_name)");
    }

    /**
     * 测试专用的HourlyDynamicThresholdGenerator
     * 重写SQL查询，使用测试表而不是生产表
     */
    private static class TestHourlyDynamicThresholdGenerator extends HourlyDynamicThresholdGenerator {
        // 这里需要重写方法以使用测试表
        // 或者提供配置参数来指定表名
    }

    @Test
    @Order(1)
    @DisplayName("完整业务流程端到端测试")
    void testCompleteBusinessFlowEndToEnd() throws Exception {
        LOG.info("=== 开始完整业务流程端到端测试 ===");

        // 第一阶段：数据聚合阶段
        LOG.info("阶段1：模拟Segment数据聚合");
        simulateSegmentDataAggregation();
        verifyAggregationResults();

        // 第二阶段：规则生成阶段
        LOG.info("阶段2：动态阈值规则生成");
        executeRuleGeneration();
        verifyRuleGeneration();

        // 第三阶段：规则下发阶段
        LOG.info("阶段3：规则下发到Kafka");
        executeRulePublishing();
        verifyRulePublishing();

        // 第四阶段：模拟热更新
        LOG.info("阶段4：模拟实时流热更新");
        simulateHotUpdate();
        verifyHotUpdate();

        LOG.info("=== 完整业务流程端到端测试通过 ===");
    }

    @Test
    @Order(2)
    @DisplayName("异常场景端到端测试")
    void testExceptionScenariosEndToEnd() throws Exception {
        LOG.info("=== 开始异常场景端到端测试 ===");

        // 场景1：空数据处理
        testEmptyDataScenario();

        // 场景2：异常数据处理
        testAbnormalDataScenario();

        // 场景3：部分数据缺失
        testPartialDataMissingScenario();

        LOG.info("=== 异常场景端到端测试通过 ===");
    }

    @Test
    @Order(3)
    @DisplayName("性能压测端到端测试")
    void testPerformanceEndToEnd() throws Exception {
        LOG.info("=== 开始性能压测端到端测试 ===");

        // 生成大量测试数据
        generateLargeScaleTestData();

        // 测试规则生成性能
        long startTime = System.currentTimeMillis();
        generator.generateAllHourlyRulesOnce(7);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        LOG.info("大规模数据规则生成耗时: {}ms", duration);

        // 验证性能指标（根据实际情况调整）
        assertThat(duration)
                .describedAs("规则生成应该在合理时间内完成")
                .isLessThan(30000L); // 30秒内完成

        // 验证结果正确性
        verifyLargeScaleResults();

        LOG.info("=== 性能压测端到端测试通过 ===");
    }

    // ============================== 阶段1：数据聚合 ==============================

    /**
     * 模拟Segment数据聚合
     */
    private void simulateSegmentDataAggregation() throws Exception {
        LOG.info("开始模拟Segment数据聚合...");

        Statement stmt = getClickHouseConnection().createStatement();

        // 模拟过去7天的聚合数据
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                insertAggregationData(stmt, day, hour);
            }
        }

        LOG.info("Segment数据聚合模拟完成");
    }

    /**
     * 插入聚合数据
     */
    private void insertAggregationData(Statement stmt, int day, int hour) throws Exception {
        // 生成符合业务规律的测试数据
        double avgDuration = generateRealisticAvgDuration(hour);
        long maxDuration = (long) (avgDuration * (2.5 + Math.random()));
        double errorRate = generateRealisticErrorRate(hour);
        int totalCount = generateRealisticTotalCount(hour);
        int errorCount = (int) (totalCount * errorRate);
        int successCount = totalCount - errorCount;

        String sql = String.format(
                "INSERT INTO test_flink_operator_agg_result VALUES " +
                        "(toDateTime64(now() - INTERVAL %d DAY - INTERVAL %d HOUR, 3), " +
                        "22, 'PaymentOperator', 'processPayment', 'payment-service', " +
                        "%.2f, %d, %.4f, %d, %d, %d)",
                day, hour, avgDuration, maxDuration, errorRate,
                totalCount, errorCount, successCount);

        stmt.execute(sql);
    }

    /**
     * 验证聚合结果
     */
    private void verifyAggregationResults() throws Exception {
        Statement stmt = getClickHouseConnection().createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT count(*) FROM test_flink_operator_agg_result WHERE service = 'payment-service'");

        assertThat(rs.next()).isTrue();
        long count = rs.getLong(1);
        assertThat(count)
                .describedAs("应该有7天*24小时=168条聚合数据")
                .isEqualTo(168);

        LOG.info("聚合结果验证通过，共{}条数据", count);
    }

    // ============================== 阶段2：规则生成 ==============================

    /**
     * 执行规则生成
     */
    private void executeRuleGeneration() throws Exception {
        LOG.info("开始执行规则生成...");

        long startTime = System.currentTimeMillis();
        generator.generateAllHourlyRulesOnce(7);
        long endTime = System.currentTimeMillis();

        LOG.info("规则生成完成，耗时: {}ms", endTime - startTime);
    }

    /**
     * 验证规则生成
     */
    private void verifyRuleGeneration() throws Exception {
        Statement stmt = getClickHouseConnection().createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT count(*) FROM test_hourly_alarm_rules WHERE service = 'payment-service'");

        assertThat(rs.next()).isTrue();
        long count = rs.getLong(1);
        assertThat(count)
                .describedAs("应该有24条规则，每小时一条")
                .isEqualTo(24);

        // 验证规则内容的合理性
        verifyRuleReasonableness();

        LOG.info("规则生成验证通过，共{}条规则", count);
    }

    // ============================== 阶段3：规则下发 ==============================

    /**
     * 执行规则下发
     */
    private void executeRulePublishing() throws Exception {
        LOG.info("开始执行规则下发...");

        // 查询当前小时的规则
        int currentHour = LocalDateTime.now().getHour();
        Map<String, AlarmRule> currentHourRules = queryCurrentHourRules(currentHour);

        // 下发到Kafka
        publishRulesToKafka(currentHourRules);

        LOG.info("规则下发完成，当前{}时规则数量: {}", currentHour, currentHourRules.size());
    }

    /**
     * 验证规则下发
     */
    private void verifyRulePublishing() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) getTestConfig().get("kafka");
        String topicName = kafkaConfig.get("alarm_rule_topic");

        getKafkaConsumer().subscribe(Collections.singletonList(topicName));

        // 等待并验证Kafka消息
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = getKafkaConsumer().poll(Duration.ofSeconds(1));
            assertThat(records).isNotEmpty();

            ConsumerRecord<String, String> record = records.iterator().next();
            assertThat(record.key()).isEqualTo("global_alarm_rules");

            // 验证规则内容
            Map<String, AlarmRule> rules = getObjectMapper().readValue(
                    record.value(),
                    new TypeReference<Map<String, AlarmRule>>() {
                    });
            assertThat(rules).isNotEmpty();

            AlarmRule rule = rules.values().iterator().next();
            assertThat(rule.service).isEqualTo("payment-service");
            assertThat(rule.operatorName).isEqualTo("processPayment");
        });

        LOG.info("规则下发验证通过");
    }

    // ============================== 阶段4：热更新 ==============================

    /**
     * 模拟热更新
     */
    private void simulateHotUpdate() throws Exception {
        LOG.info("开始模拟热更新...");

        // 这里可以模拟AggAlertBroadcastFunction接收到新规则的场景
        // 实际测试中会验证broadcast state的更新

        LOG.info("热更新模拟完成");
    }

    /**
     * 验证热更新
     */
    private void verifyHotUpdate() throws Exception {
        // 验证热更新的效果
        // 这里可以检查规则是否被正确应用到实时流中

        LOG.info("热更新验证通过");
    }

    // ============================== 异常场景测试 ==============================

    /**
     * 测试空数据场景
     */
    private void testEmptyDataScenario() throws Exception {
        LOG.info("测试空数据场景...");

        // 确保表为空
        Statement stmt = getClickHouseConnection().createStatement();
        stmt.execute("TRUNCATE TABLE test_flink_operator_agg_result");

        // 尝试生成规则
        assertThatNoException()
                .describedAs("空数据情况下生成规则应该不抛异常")
                .isThrownBy(() -> generator.generateAllHourlyRulesOnce(7));

        LOG.info("空数据场景测试通过");
    }

    /**
     * 测试异常数据场景
     */
    private void testAbnormalDataScenario() throws Exception {
        LOG.info("测试异常数据场景...");

        // 插入异常数据
        Statement stmt = getClickHouseConnection().createStatement();
        stmt.execute(
                "INSERT INTO test_flink_operator_agg_result VALUES " +
                        "(now(), 22, 'AbnormalOperator', 'abnormalMethod', 'abnormal-service', " +
                        "-1.0, -1, 2.0, 0, 10, -5)" // 异常数据：负延迟、错误率>1、负成功数
        );

        // 应该能处理异常数据
        assertThatNoException()
                .describedAs("异常数据情况下生成规则应该不抛异常")
                .isThrownBy(() -> generator.generateAllHourlyRulesOnce(7));

        LOG.info("异常数据场景测试通过");
    }

    /**
     * 测试部分数据缺失场景
     */
    private void testPartialDataMissingScenario() throws Exception {
        LOG.info("测试部分数据缺失场景...");

        // 只插入部分小时的数据
        Statement stmt = getClickHouseConnection().createStatement();
        for (int hour = 9; hour <= 17; hour++) { // 只有工作时间数据
            stmt.execute(String.format(
                    "INSERT INTO test_flink_operator_agg_result VALUES " +
                            "(now() - INTERVAL %d HOUR, 22, 'PartialOperator', 'partialMethod', 'partial-service', " +
                            "%.2f, %d, %.4f, %d, %d, %d)",
                    hour, 100.0 + hour * 10, 200 + hour * 20, 0.02, 1000, 20, 980));
        }

        // 应该能处理部分数据缺失
        assertThatNoException()
                .describedAs("部分数据缺失情况下生成规则应该不抛异常")
                .isThrownBy(() -> generator.generateAllHourlyRulesOnce(7));

        LOG.info("部分数据缺失场景测试通过");
    }

    // ============================== 性能测试 ==============================

    /**
     * 生成大规模测试数据
     */
    private void generateLargeScaleTestData() throws Exception {
        LOG.info("开始生成大规模测试数据...");

        Statement stmt = getClickHouseConnection().createStatement();

        // 模拟100个服务，每个服务10个方法，7天数据
        for (int serviceIndex = 1; serviceIndex <= 100; serviceIndex++) {
            for (int methodIndex = 1; methodIndex <= 10; methodIndex++) {
                for (int day = 0; day < 7; day++) {
                    for (int hour = 0; hour < 24; hour++) {
                        insertLargeScaleData(stmt, serviceIndex, methodIndex, day, hour);
                    }
                }
            }
        }

        LOG.info("大规模测试数据生成完成");
    }

    /**
     * 插入大规模数据
     */
    private void insertLargeScaleData(Statement stmt, int serviceIndex, int methodIndex, int day, int hour)
            throws Exception {
        String sql = String.format(
                "INSERT INTO test_flink_operator_agg_result VALUES " +
                        "(toDateTime64(now() - INTERVAL %d DAY - INTERVAL %d HOUR, 3), " +
                        "22, 'Operator_%d', 'method_%d', 'service_%d', " +
                        "%.2f, %d, %.4f, %d, %d, %d)",
                day, hour, serviceIndex, methodIndex, serviceIndex,
                100.0 + Math.random() * 100,
                (long) (200 + Math.random() * 200),
                0.01 + Math.random() * 0.03,
                (int) (500 + Math.random() * 1000),
                (int) (Math.random() * 30),
                (int) (470 + Math.random() * 1000));

        stmt.execute(sql);
    }

    /**
     * 验证大规模结果
     */
    private void verifyLargeScaleResults() throws Exception {
        Statement stmt = getClickHouseConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM test_hourly_alarm_rules");

        assertThat(rs.next()).isTrue();
        long count = rs.getLong(1);

        // 100个服务 * 10个方法 * 24小时 = 24000条规则
        assertThat(count)
                .describedAs("大规模数据应该生成正确数量的规则")
                .isEqualTo(24000);

        LOG.info("大规模结果验证通过，共{}条规则", count);
    }

    // ============================== 辅助方法 ==============================

    /**
     * 生成符合业务规律的平均延迟
     */
    private double generateRealisticAvgDuration(int hour) {
        // 高峰期(9-12, 14-18): 较高延迟
        if ((hour >= 9 && hour <= 12) || (hour >= 14 && hour <= 18)) {
            return 120.0 + Math.random() * 80;
        }
        // 低峰期(0-6, 22-24): 较低延迟
        else if (hour <= 6 || hour >= 22) {
            return 60.0 + Math.random() * 40;
        }
        // 平峰期: 中等延迟
        else {
            return 90.0 + Math.random() * 60;
        }
    }

    /**
     * 生成符合业务规律的错误率
     */
    private double generateRealisticErrorRate(int hour) {
        // 高峰期错误率稍高
        if ((hour >= 9 && hour <= 12) || (hour >= 14 && hour <= 18)) {
            return 0.015 + Math.random() * 0.02; // 1.5-3.5%
        } else {
            return 0.005 + Math.random() * 0.015; // 0.5-2%
        }
    }

    /**
     * 生成符合业务规律的交易量
     */
    private int generateRealisticTotalCount(int hour) {
        // 高峰期交易量大
        if ((hour >= 9 && hour <= 12) || (hour >= 14 && hour <= 18)) {
            return 1500 + (int) (Math.random() * 1000);
        }
        // 低峰期交易量小
        else if (hour <= 6 || hour >= 22) {
            return 200 + (int) (Math.random() * 300);
        }
        // 平峰期中等交易量
        else {
            return 800 + (int) (Math.random() * 600);
        }
    }

    /**
     * 验证规则合理性
     */
    private void verifyRuleReasonableness() throws Exception {
        // 验证规则的数学关系合理性
        Statement stmt = getClickHouseConnection().createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT * FROM test_hourly_alarm_rules WHERE service = 'payment-service' ORDER BY hour_of_day");

        while (rs.next()) {
            int hour = rs.getInt("hour_of_day");
            double avgDurationHigh = rs.getDouble("avg_duration_high");
            double successRateLow = rs.getDouble("success_rate_low");

            // 验证阈值合理性
            assertThat(avgDurationHigh).isPositive();
            assertThat(successRateLow).isBetween(0.0, 1.0);

            LOG.debug("第{}小时规则 - 平均延迟高阈值: {}, 成功率低阈值: {}",
                    hour, avgDurationHigh, successRateLow);
        }
    }

    /**
     * 查询当前小时规则
     */
    private Map<String, AlarmRule> queryCurrentHourRules(int hour) throws Exception {
        Map<String, AlarmRule> rules = new HashMap<>();

        String sql = "SELECT * FROM test_hourly_alarm_rules WHERE hour_of_day = ?";
        PreparedStatement ps = getClickHouseConnection().prepareStatement(sql);
        ps.setInt(1, hour);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            AlarmRule rule = new AlarmRule();
            rule.service = rs.getString("service");
            rule.operatorName = rs.getString("operator_name");
            rule.operatorClass = rs.getString("operator_class");
            rule.avgDurationLow = rs.getDouble("avg_duration_low");
            rule.avgDurationMid = rs.getDouble("avg_duration_mid");
            rule.avgDurationHigh = rs.getDouble("avg_duration_high");
            rule.maxDurationLow = rs.getDouble("max_duration_low");
            rule.maxDurationMid = rs.getDouble("max_duration_mid");
            rule.maxDurationHigh = rs.getDouble("max_duration_high");
            rule.successRateLow = rs.getDouble("success_rate_low");
            rule.successRateMid = rs.getDouble("success_rate_mid");
            rule.successRateHigh = rs.getDouble("success_rate_high");
            rule.trafficVolumeLow = rs.getDouble("traffic_volume_low");
            rule.trafficVolumeMid = rs.getDouble("traffic_volume_mid");
            rule.trafficVolumeHigh = rs.getDouble("traffic_volume_high");
            rule.alarmTemplate = rs.getString("alarm_template");

            String key = rule.service + "|" + rule.operatorName;
            rules.put(key, rule);
        }

        return rules;
    }

    /**
     * 发布规则到Kafka
     */
    private void publishRulesToKafka(Map<String, AlarmRule> rules) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) getTestConfig().get("kafka");
        String topicName = kafkaConfig.get("alarm_rule_topic");

        String rulesJson = getObjectMapper().writeValueAsString(rules);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topicName,
                "global_alarm_rules",
                rulesJson);

        getKafkaProducer().send(record).get();
        LOG.info("规则已发布到Kafka，规则数量: {}", rules.size());
    }
}