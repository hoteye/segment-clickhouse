package com.o11y.integration;

import com.o11y.application.launcher.EventsBasedThresholdGenerator;
import com.o11y.domain.model.alarm.AlarmRule;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * EventsBasedThresholdGenerator 集成测试
 * 
 * 验证基于events表的动态阈值生成功能
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventsBasedThresholdGeneratorTest extends BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EventsBasedThresholdGeneratorTest.class);
    private EventsBasedThresholdGenerator generator;

    @BeforeEach
    void setUp() {
        super.setUp();
        generator = new EventsBasedThresholdGenerator();

        // 清空规则表
        try {
            Statement stmt = clickhouseConnection.createStatement();
            stmt.execute("TRUNCATE TABLE hourly_alarm_rules");
            LOG.info("测试用例准备完成");
        } catch (Exception e) {
            LOG.error("清空规则表失败", e);
        }
    }

    @Test
    @Order(1)
    @DisplayName("测试基于events表的规则生成")
    void testEventsBasedRuleGeneration() throws Exception {
        LOG.info("=== 开始测试基于events表的规则生成 ===");

        // 验证events表中有数据
        verifyEventsDataExists();

        // 执行规则生成
        long startTime = System.currentTimeMillis();
        generator.generateAllHourlyRulesFromEvents(7);
        long endTime = System.currentTimeMillis();

        LOG.info("规则生成完成，耗时: {}ms", endTime - startTime);

        // 验证规则生成结果
        List<AlarmRule> rules = queryAllRules();
        assertThat(rules).isNotEmpty();

        LOG.info("生成规则数量: {}", rules.size());

        // 验证规则内容
        for (AlarmRule rule : rules) {
            verifyRuleContent(rule);
        }

        LOG.info("=== 基于events表的规则生成测试通过 ===");
    }

    @Test
    @Order(2)
    @DisplayName("测试规则生成的幂等性")
    void testRuleGenerationIdempotency() throws Exception {
        LOG.info("=== 开始测试规则生成幂等性 ===");

        // 第一次生成
        generator.generateAllHourlyRulesFromEvents(7);
        List<AlarmRule> firstGenerationRules = queryAllRules();

        // 第二次生成（应该覆盖之前的规则）
        generator.generateAllHourlyRulesFromEvents(7);
        List<AlarmRule> secondGenerationRules = queryAllRules();

        // 验证规则数量一致
        assertThat(secondGenerationRules)
                .describedAs("规则生成应该是幂等的，规则数量应该一致")
                .hasSameSizeAs(firstGenerationRules);

        LOG.info("=== 规则生成幂等性测试通过 ===");
    }

    /**
     * 验证events表中存在数据
     */
    private void verifyEventsDataExists() throws Exception {
        Statement stmt = clickhouseConnection.createStatement();
        var rs = stmt.executeQuery("SELECT count(*) FROM events");
        assertThat(rs.next()).isTrue();
        long count = rs.getLong(1);
        assertThat(count).isGreaterThan(0);
        LOG.info("events表中有{}条数据", count);
    }

    /**
     * 验证规则内容
     */
    private void verifyRuleContent(AlarmRule rule) {
        assertThat(rule.service).isNotNull().isNotEmpty();
        assertThat(rule.operatorName).isNotNull().isNotEmpty();
        assertThat(rule.avgDurationLow).isPositive();
        assertThat(rule.avgDurationMid).isPositive();
        assertThat(rule.avgDurationHigh).isPositive();
        assertThat(rule.maxDurationLow).isPositive();
        assertThat(rule.maxDurationMid).isPositive();
        assertThat(rule.maxDurationHigh).isPositive();
        assertThat(rule.successRateLow).isBetween(0.0, 1.0);
        assertThat(rule.successRateMid).isBetween(0.0, 1.0);
        assertThat(rule.successRateHigh).isBetween(0.0, 1.0);
        assertThat(rule.trafficVolumeLow).isPositive();
        assertThat(rule.trafficVolumeMid).isPositive();
        assertThat(rule.trafficVolumeHigh).isPositive();

        // 验证阈值层级关系
        assertThat(rule.avgDurationLow).isLessThan(rule.avgDurationMid);
        assertThat(rule.avgDurationMid).isLessThan(rule.avgDurationHigh);
        assertThat(rule.maxDurationLow).isLessThan(rule.maxDurationMid);
        assertThat(rule.maxDurationMid).isLessThan(rule.maxDurationHigh);
        assertThat(rule.successRateLow).isGreaterThan(rule.successRateMid);
        assertThat(rule.successRateMid).isGreaterThan(rule.successRateHigh);
        assertThat(rule.trafficVolumeLow).isLessThan(rule.trafficVolumeMid);
        assertThat(rule.trafficVolumeMid).isLessThan(rule.trafficVolumeHigh);
    }

    /**
     * 查询所有规则
     */
    private List<AlarmRule> queryAllRules() throws Exception {
        Statement stmt = clickhouseConnection.createStatement();
        var rs = stmt.executeQuery(
                "SELECT service, operator_name, operator_class, " +
                        "avg_duration_low, avg_duration_mid, avg_duration_high, " +
                        "max_duration_low, max_duration_mid, max_duration_high, " +
                        "success_rate_low, success_rate_mid, success_rate_high, " +
                        "traffic_volume_low, traffic_volume_mid, traffic_volume_high, " +
                        "alarm_template FROM hourly_alarm_rules");

        List<AlarmRule> rules = new java.util.ArrayList<>();
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
            rules.add(rule);
        }
        return rules;
    }
}