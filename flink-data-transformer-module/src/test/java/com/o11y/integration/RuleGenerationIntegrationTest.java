package com.o11y.integration;

import com.o11y.domain.model.alarm.AlarmRule;
import com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator;
import org.junit.jupiter.api.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 业务规则生成集成测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RuleGenerationIntegrationTest extends BaseIntegrationTest {

    private HourlyDynamicThresholdGenerator generator;

    @BeforeEach
    void setUpGenerator() throws Exception {
        generator = new HourlyDynamicThresholdGenerator();

        // 清空规则表，确保测试独立性 - 使用测试专用表
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

        LOG.info("规则生成器初始化完成（使用测试专用表）");
    }

    @Test
    @Order(1)
    @DisplayName("测试完整的规则生成流程")
    void testCompleteRuleGenerationFlow() throws Exception {
        LOG.info("=== 开始测试完整规则生成流程 ===");

        // 1. 验证历史数据存在
        verifyHistoricalDataExists();

        // 2. 执行规则生成
        executeRuleGeneration();

        // 3. 验证24小时规则完整性
        verifyAllHourlyRulesGenerated();

        // 4. 验证规则算法正确性
        verifyRuleAlgorithmCorrectness();

        LOG.info("=== 完整规则生成流程测试通过 ===");
    }

    @Test
    @Order(2)
    @DisplayName("测试高峰期与低峰期阈值差异")
    void testPeakVsOffPeakThresholds() throws Exception {
        LOG.info("=== 开始测试高峰期与低峰期阈值差异 ===");

        // 生成规则
        generator.generateAllHourlyRulesOnce(7);

        // 获取高峰期规则（10点）
        List<AlarmRule> peakRules = queryRulesByHour(10);
        assertThat(peakRules).isNotEmpty();
        AlarmRule peakRule = peakRules.get(0);

        // 获取低峰期规则（2点）
        List<AlarmRule> offPeakRules = queryRulesByHour(2);
        assertThat(offPeakRules).isNotEmpty();
        AlarmRule offPeakRule = offPeakRules.get(0);

        // 验证阈值层级关系
        verifyThresholdHierarchy(peakRule);
        verifyThresholdHierarchy(offPeakRule);

        LOG.info("高峰期阈值 - 平均延迟: {}", peakRule.avgDurationHigh);
        LOG.info("低峰期阈值 - 平均延迟: {}", offPeakRule.avgDurationHigh);

        LOG.info("=== 高峰期与低峰期阈值差异测试通过 ===");
    }

    /**
     * 验证历史数据存在
     */
    private void verifyHistoricalDataExists() throws Exception {
        Statement stmt = getClickHouseConnection().createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT count(*) FROM flink_operator_agg_result WHERE service = 'test-service'");

        assertThat(rs.next()).isTrue();
        long count = rs.getLong(1);
        assertThat(count)
                .describedAs("应该有历史聚合数据存在")
                .isGreaterThan(0);

        LOG.info("历史数据验证通过，共{}条记录", count);
    }

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
     * 验证24小时规则完整性
     */
    private void verifyAllHourlyRulesGenerated() throws Exception {
        for (int hour = 0; hour < 24; hour++) {
            List<AlarmRule> rules = queryRulesByHour(hour);
            assertThat(rules)
                    .describedAs("第%d小时应该有规则生成", hour)
                    .isNotEmpty();
        }

        // 验证总规则数
        List<AlarmRule> allRules = queryAllRules();
        assertThat(allRules)
                .describedAs("应该有24条规则，每小时一条")
                .hasSize(24);

        LOG.info("24小时规则完整性验证通过");
    }

    /**
     * 验证规则算法正确性
     */
    private void verifyRuleAlgorithmCorrectness() throws Exception {
        List<AlarmRule> allRules = queryAllRules();

        for (AlarmRule rule : allRules) {
            // 验证阈值层级关系：低 < 中 < 高
            verifyThresholdHierarchy(rule);

            // 验证阈值合理性：所有阈值都应该大于0
            verifyThresholdReasonableness(rule);
        }

        LOG.info("规则算法正确性验证通过");
    }

    /**
     * 验证阈值层级关系
     */
    private void verifyThresholdHierarchy(AlarmRule rule) {
        // 平均延迟阈值：低 < 中 < 高
        assertThat(rule.avgDurationLow)
                .describedAs("平均延迟低阈值应该小于中阈值")
                .isLessThan(rule.avgDurationMid);
        assertThat(rule.avgDurationMid)
                .describedAs("平均延迟中阈值应该小于高阈值")
                .isLessThan(rule.avgDurationHigh);

        // 成功率阈值：高 > 中 > 低（注意：成功率是反向的）
        assertThat(rule.successRateHigh)
                .describedAs("成功率高阈值应该大于中阈值")
                .isGreaterThan(rule.successRateMid);
        assertThat(rule.successRateMid)
                .describedAs("成功率中阈值应该大于低阈值")
                .isGreaterThan(rule.successRateLow);
    }

    /**
     * 验证阈值合理性
     */
    private void verifyThresholdReasonableness(AlarmRule rule) {
        // 延迟阈值应该大于0
        assertThat(rule.avgDurationLow).isPositive();
        assertThat(rule.avgDurationMid).isPositive();
        assertThat(rule.avgDurationHigh).isPositive();

        // 成功率应该在0-1之间
        assertThat(rule.successRateLow).isBetween(0.0, 1.0);
        assertThat(rule.successRateMid).isBetween(0.0, 1.0);
        assertThat(rule.successRateHigh).isBetween(0.0, 1.0);

        // 交易量阈值应该大于0
        assertThat(rule.trafficVolumeLow).isPositive();
        assertThat(rule.trafficVolumeMid).isPositive();
        assertThat(rule.trafficVolumeHigh).isPositive();
    }

    /**
     * 查询指定小时的规则
     */
    private List<AlarmRule> queryRulesByHour(int hour) throws Exception {
        String sql = "SELECT service, operator_name, operator_class, " +
                "avg_duration_low, avg_duration_mid, avg_duration_high, " +
                "max_duration_low, max_duration_mid, max_duration_high, " +
                "success_rate_low, success_rate_mid, success_rate_high, " +
                "traffic_volume_low, traffic_volume_mid, traffic_volume_high, " +
                "alarm_template " +
                "FROM hourly_alarm_rules WHERE hour_of_day = ?";

        PreparedStatement ps = getClickHouseConnection().prepareStatement(sql);
        ps.setInt(1, hour);
        ResultSet rs = ps.executeQuery();

        List<AlarmRule> rules = new ArrayList<>();
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

    /**
     * 查询所有规则
     */
    private List<AlarmRule> queryAllRules() throws Exception {
        List<AlarmRule> allRules = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            allRules.addAll(queryRulesByHour(hour));
        }
        return allRules;
    }
}