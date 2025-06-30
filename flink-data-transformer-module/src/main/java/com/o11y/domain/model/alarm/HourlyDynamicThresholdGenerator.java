package com.o11y.domain.model.alarm;

import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.shared.util.ConfigurationUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 小时级动态阈值生成器：
 * 1. 一次性分析前N天的 flink_operator_agg_result 数据
 * 2. 按小时汇总，生成所有24小时的动态阈值规则
 * 3. 存储到hourly_alarm_rules表中（24条记录）
 * 4. 每小时整点下发对应小时的规则到Kafka
 * 
 * 设计思路：批量生成 + 定时下发
 */
public class HourlyDynamicThresholdGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(HourlyDynamicThresholdGenerator.class);
    // 默认分析前7天的数据
    private static final int DEFAULT_ANALYSIS_DAYS = 7;

    public static void main(String[] args) throws Exception {
        HourlyDynamicThresholdGenerator generator = new HourlyDynamicThresholdGenerator();

        LOG.info("开始生成小时级动态阈值规则，固定使用前{}天历史数据", DEFAULT_ANALYSIS_DAYS);
        generator.generateAllHourlyRulesOnce(DEFAULT_ANALYSIS_DAYS);
    }

    /**
     * 一次性生成所有24小时的动态阈值规则
     * 核心方法：分析前N天数据，生成所有24小时的规则
     * 
     * @param analysisDays 分析的历史天数
     */
    public void generateAllHourlyRulesOnce(int analysisDays) throws Exception {
        LOG.info("开始生成24小时动态阈值规则，基于前{}天数据", analysisDays);

        // 1. 初始化 ClickHouse 连接（参考FlinkService的方式读取配置）
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        // 2. 清空整个hourly_alarm_rules表，确保没有重复阈值规则消息
        LOG.info("清空hourly_alarm_rules表，确保数据一致性");
        String clearSql = "TRUNCATE TABLE hourly_alarm_rules";
        PreparedStatement clearPs = conn.prepareStatement(clearSql);
        clearPs.executeUpdate();
        clearPs.close();
        LOG.info("hourly_alarm_rules表已清空");

        // 3. 一次性查询前N天所有数据，按小时分组
        String sql = "SELECT " +
                "toHour(window_start) as hour_of_day, " +
                "service, " +
                "operator_name, " +
                "operator_class, " +
                "avg(avg_duration) as avg_avg_duration, " +
                "avg(max_duration) as avg_max_duration, " +
                "avg(error_rate) as avg_error_rate, " +
                "avg(total_count) as avg_total_count, " +
                "count(*) as sample_count, " +
                "stddevSamp(avg_duration) as avg_duration_std, " +
                "stddevSamp(max_duration) as max_duration_std, " +
                "stddevSamp(error_rate) as error_rate_std, " +
                "stddevSamp(total_count) as total_count_std, " +
                "quantile(0.75)(avg_duration) as avg_duration_p75, " +
                "quantile(0.90)(avg_duration) as avg_duration_p90, " +
                "quantile(0.95)(avg_duration) as avg_duration_p95, " +
                "quantile(0.75)(max_duration) as max_duration_p75, " +
                "quantile(0.90)(max_duration) as max_duration_p90, " +
                "quantile(0.95)(max_duration) as max_duration_p95 " +
                "FROM flink_operator_agg_result " +
                "WHERE window_start >= now() - INTERVAL ? DAY " +
                "AND window_start < now() " +
                "AND total_count > 0 " +
                "AND service IS NOT NULL " +
                "AND operator_name IS NOT NULL " +
                "GROUP BY hour_of_day, service, operator_name, operator_class " +
                "HAVING sample_count >= ? " +
                "ORDER BY hour_of_day, service, operator_name";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, analysisDays);
        ps.setInt(2, Math.max(3, analysisDays)); // 至少需要分析天数的样本
        ResultSet rs = ps.executeQuery();

        // 4. 按小时组织数据：Map<hour, Map<ruleKey, AlarmRule>>
        Map<Integer, Map<String, AlarmRule>> allHourlyRules = new HashMap<>();
        Map<Integer, HourlyStatistics> hourlyStats = new HashMap<>();

        // 初始化24小时的容器
        for (int hour = 0; hour < 24; hour++) {
            allHourlyRules.put(hour, new HashMap<>());
            hourlyStats.put(hour, new HourlyStatistics());
        }

        int totalRules = 0;
        while (rs.next()) {
            int hourOfDay = rs.getInt("hour_of_day");
            String service = rs.getString("service");
            String operatorName = rs.getString("operator_name");
            String operatorClass = rs.getString("operator_class");
            double avgDuration = rs.getDouble("avg_avg_duration");
            double maxDuration = rs.getDouble("avg_max_duration");
            double errorRate = rs.getDouble("avg_error_rate");
            double successRate = Math.max(0.0, 1.0 - errorRate); // 转换为成功率
            double totalCountValue = rs.getDouble("avg_total_count");
            int sampleCount = rs.getInt("sample_count");

            // 获取标准差
            double avgDurationStd = rs.getDouble("avg_duration_std");
            double maxDurationStd = rs.getDouble("max_duration_std");
            double errorRateStd = rs.getDouble("error_rate_std");
            double totalCountStd = rs.getDouble("total_count_std");

            // 获取分位数
            double avgDurationP75 = rs.getDouble("avg_duration_p75");
            double avgDurationP90 = rs.getDouble("avg_duration_p90");
            double avgDurationP95 = rs.getDouble("avg_duration_p95");
            double maxDurationP75 = rs.getDouble("max_duration_p75");
            double maxDurationP90 = rs.getDouble("max_duration_p90");
            double maxDurationP95 = rs.getDouble("max_duration_p95");

            // 5. 生成该小时的动态阈值规则
            AlarmRule rule = generateHourlyRule(
                    service, operatorName, operatorClass, hourOfDay,
                    avgDuration, maxDuration, successRate, totalCountValue,
                    avgDurationStd, maxDurationStd, errorRateStd, totalCountStd,
                    avgDurationP75, avgDurationP90, avgDurationP95,
                    maxDurationP75, maxDurationP90, maxDurationP95,
                    sampleCount, analysisDays);

            String ruleKey = rule.combine();
            allHourlyRules.get(hourOfDay).put(ruleKey, rule);

            // 累计该小时的统计信息
            HourlyStatistics stats = hourlyStats.get(hourOfDay);
            stats.services.add(service);
            stats.operators.add(operatorName);
            stats.totalAvgDuration += avgDuration;
            stats.totalMaxDuration += maxDuration;
            stats.totalSuccessRate += successRate;
            stats.totalCount += totalCountValue;
            stats.totalSampleCount += sampleCount;
            stats.ruleCount++;
            totalRules++;
        }

        rs.close();
        ps.close();

        // 6. 批量保存所有24小时的规则到数据库
        if (totalRules > 0) {
            LOG.info("生成{}条规则，开始保存到数据库", totalRules);

            for (int hour = 0; hour < 24; hour++) {
                Map<String, AlarmRule> hourRules = allHourlyRules.get(hour);
                HourlyStatistics stats = hourlyStats.get(hour);

                if (!hourRules.isEmpty()) {
                    saveHourlyRuleToDatabase(conn, hour, hourRules,
                            stats.services.size(), stats.operators.size(), stats.ruleCount,
                            stats.totalAvgDuration / stats.ruleCount,
                            stats.totalMaxDuration / stats.ruleCount,
                            stats.totalSuccessRate / stats.ruleCount,
                            stats.totalCount / stats.ruleCount,
                            analysisDays, stats.totalSampleCount);
                }
            }

            LOG.info("完成24小时规则生成，总规则数: {}", totalRules);
        } else {
            LOG.warn("未生成任何规则，请检查数据");
        }

        conn.close();
    }

    /**
     * 保存小时级规则到数据库（每个规则一条记录，不使用JSON）
     */
    private void saveHourlyRuleToDatabase(Connection conn, int hourOfDay,
            Map<String, AlarmRule> ruleMap,
            int totalServices, int totalOperators, int ruleCount,
            double avgAvgDuration, double avgMaxDuration,
            double avgSuccessRate, double avgTotalCount,
            int analysisDays, int totalSampleCount) throws Exception {

        // 逐条插入规则到hourly_alarm_rules表（表已在主流程开始时清空）
        String sql = "INSERT INTO hourly_alarm_rules (" +
                "hour_of_day, service, operator_name, operator_class, " +
                "avg_duration_low, avg_duration_mid, avg_duration_high, " +
                "max_duration_low, max_duration_mid, max_duration_high, " +
                "success_rate_low, success_rate_mid, success_rate_high, " +
                "traffic_volume_low, traffic_volume_mid, traffic_volume_high, " +
                "alarm_template, analysis_days, sample_count, " +
                "generated_time, last_updated, version " +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), 1)";

        PreparedStatement ps = conn.prepareStatement(sql);
        for (AlarmRule rule : ruleMap.values()) {
            ps.setInt(1, hourOfDay);
            ps.setString(2, rule.service);
            ps.setString(3, rule.operatorName);
            ps.setString(4, rule.operatorClass);
            ps.setDouble(5, rule.avgDurationLow);
            ps.setDouble(6, rule.avgDurationMid);
            ps.setDouble(7, rule.avgDurationHigh);
            ps.setDouble(8, rule.maxDurationLow);
            ps.setDouble(9, rule.maxDurationMid);
            ps.setDouble(10, rule.maxDurationHigh);
            ps.setDouble(11, rule.successRateLow);
            ps.setDouble(12, rule.successRateMid);
            ps.setDouble(13, rule.successRateHigh);
            ps.setDouble(14, rule.trafficVolumeLow);
            ps.setDouble(15, rule.trafficVolumeMid);
            ps.setDouble(16, rule.trafficVolumeHigh);
            ps.setString(17, rule.alarmTemplate);
            ps.setInt(18, analysisDays);
            ps.setInt(19, totalSampleCount / ruleCount); // 平均样本数

            ps.addBatch();
        }

        ps.executeBatch();
        ps.close();
    }

    /**
     * 生成小时级告警规则
     */
    private AlarmRule generateHourlyRule(
            String service, String operatorName, String operatorClass, int hourOfDay,
            double avgDuration, double maxDuration, double successRate, double totalCount,
            double avgDurationStd, double maxDurationStd, double errorRateStd, double totalCountStd,
            double avgDurationP75, double avgDurationP90, double avgDurationP95,
            double maxDurationP75, double maxDurationP90, double maxDurationP95,
            int sampleCount, int analysisDays) {

        // 根据小时特征调整阈值倍数
        HourlyAdjustmentFactor factor = getHourlyAdjustmentFactor(hourOfDay);

        AlarmRule rule = new AlarmRule();
        rule.service = service;
        rule.operatorName = operatorName;
        rule.operatorClass = operatorClass;

        // 平均延迟阈值（基于分位数和标准差的组合策略）
        // LOW: P75 + 0.5 * std * factor
        // MID: P90 + 1.0 * std * factor
        // HIGH: P95 + 2.0 * std * factor
        rule.avgDurationLow = Math.max(
                avgDurationP75 + (avgDurationStd * 0.5) * factor.avgDurationFactor,
                avgDuration * 1.1);
        rule.avgDurationMid = Math.max(
                avgDurationP90 + (avgDurationStd * 1.0) * factor.avgDurationFactor,
                avgDuration * 1.3);
        rule.avgDurationHigh = Math.max(
                avgDurationP95 + (avgDurationStd * 2.0) * factor.avgDurationFactor,
                avgDuration * 1.8);

        // 最大延迟阈值
        rule.maxDurationLow = Math.max(
                maxDurationP75 + (maxDurationStd * 0.5) * factor.maxDurationFactor,
                maxDuration * 1.2);
        rule.maxDurationMid = Math.max(
                maxDurationP90 + (maxDurationStd * 1.0) * factor.maxDurationFactor,
                maxDuration * 1.5);
        rule.maxDurationHigh = Math.max(
                maxDurationP95 + (maxDurationStd * 2.0) * factor.maxDurationFactor,
                maxDuration * 2.0);

        // 成功率阈值（低于基准值告警）
        double successRateLowThreshold = successRate - (errorRateStd * 0.5) * factor.successRateFactor;
        double successRateMidThreshold = successRate - (errorRateStd * 1.0) * factor.successRateFactor;
        double successRateHighThreshold = successRate - (errorRateStd * 2.0) * factor.successRateFactor;

        rule.successRateLow = Math.max(successRateLowThreshold, successRate * 0.98);
        rule.successRateMid = Math.max(successRateMidThreshold, successRate * 0.95);
        rule.successRateHigh = Math.max(successRateHighThreshold, successRate * 0.90);

        // 交易量阈值（高于基准值告警）
        double totalCountLowThreshold = totalCount + (totalCountStd * 0.5) * factor.trafficVolumeFactor;
        double totalCountMidThreshold = totalCount + (totalCountStd * 1.0) * factor.trafficVolumeFactor;
        double totalCountHighThreshold = totalCount + (totalCountStd * 2.0) * factor.trafficVolumeFactor;

        rule.trafficVolumeLow = Math.max(totalCountLowThreshold, totalCount * 1.2);
        rule.trafficVolumeMid = Math.max(totalCountMidThreshold, totalCount * 1.5);
        rule.trafficVolumeHigh = Math.max(totalCountHighThreshold, totalCount * 2.0);

        rule.alarmTemplate = String.format("服务%s算子%s在%d时动态阈值告警(基于前%d天数据)",
                service, operatorName, hourOfDay, analysisDays);

        return rule;
    }

    /**
     * 根据小时获取调整因子（体现不同小时的交易特征）
     */
    private HourlyAdjustmentFactor getHourlyAdjustmentFactor(int hourOfDay) {
        HourlyAdjustmentFactor factor = new HourlyAdjustmentFactor();

        // 根据业务特征调整阈值严格程度
        if (hourOfDay >= 9 && hourOfDay <= 11) {
            // 上午高峰期：相对宽松，允许更高的延迟和交易量
            factor.avgDurationFactor = 1.3;
            factor.maxDurationFactor = 1.4;
            factor.successRateFactor = 1.2;
            factor.trafficVolumeFactor = 1.5;
        } else if (hourOfDay >= 14 && hourOfDay <= 16) {
            // 下午高峰期：相对宽松
            factor.avgDurationFactor = 1.3;
            factor.maxDurationFactor = 1.4;
            factor.successRateFactor = 1.2;
            factor.trafficVolumeFactor = 1.5;
        } else if (hourOfDay >= 20 && hourOfDay <= 22) {
            // 晚间高峰期：相对宽松
            factor.avgDurationFactor = 1.2;
            factor.maxDurationFactor = 1.3;
            factor.successRateFactor = 1.1;
            factor.trafficVolumeFactor = 1.4;
        } else if (hourOfDay >= 0 && hourOfDay <= 6) {
            // 凌晨低峰期：相对严格，期望更好的性能
            factor.avgDurationFactor = 0.7;
            factor.maxDurationFactor = 0.8;
            factor.successRateFactor = 0.8;
            factor.trafficVolumeFactor = 0.6;
        } else {
            // 其他时段：标准
            factor.avgDurationFactor = 1.0;
            factor.maxDurationFactor = 1.0;
            factor.successRateFactor = 1.0;
            factor.trafficVolumeFactor = 1.0;
        }

        return factor;
    }

    /**
     * 小时统计信息
     */
    private static class HourlyStatistics {
        Set<String> services = new HashSet<>();
        Set<String> operators = new HashSet<>();
        double totalAvgDuration = 0.0;
        double totalMaxDuration = 0.0;
        double totalSuccessRate = 0.0;
        double totalCount = 0.0;
        int totalSampleCount = 0;
        int ruleCount = 0;
    }

    /**
     * 小时调整因子
     */
    private static class HourlyAdjustmentFactor {
        double avgDurationFactor = 1.0;
        double maxDurationFactor = 1.0;
        double successRateFactor = 1.0;
        double trafficVolumeFactor = 1.0;
    }

    /**
     * 清空整个 hourly_alarm_rules 表
     * 用于全量更新前确保数据一致性
     */
    public void clearAllHourlyRules() throws Exception {
        LOG.info("清空hourly_alarm_rules表");

        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        String sql = "TRUNCATE TABLE hourly_alarm_rules";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.executeUpdate();
        ps.close();
        conn.close();

        LOG.info("已清空hourly_alarm_rules表");
    }
}