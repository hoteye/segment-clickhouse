package com.o11y.application.launcher;

import com.o11y.domain.model.alarm.AlarmRule;
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
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;

/**
 * 基于 events 原始表的动态阈值生成器：
 * 1. 直接从 events 表分析前N天的原始数据
 * 2. 按小时汇总，生成所有24小时的动态阈值规则
 * 3. 存储到hourly_alarm_rules表中
 * 4. 支持过滤异常数据，只统计成功的交易
 * 5. 支持从配置文件读取阈值参数
 * 
 * 优势：
 * - 可以精确过滤失败/异常交易
 * - 统计更准确，基于原始数据
 * - 可以灵活添加过滤条件
 * - 支持配置文件管理阈值参数
 */
public class EventsBasedThresholdGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(EventsBasedThresholdGenerator.class);

    // 默认分析前7天的数据
    private static final int DEFAULT_ANALYSIS_DAYS = 5;
    // 最小样本数要求
    private static final int DEFAULT_MIN_SAMPLE_COUNT = 10;
    // 异常响应时间阈值（毫秒），超过此值的数据将被过滤
    private static final int DEFAULT_MAX_REASONABLE_DURATION_MS = 60000; // 60秒

    // 配置对象
    private ThresholdConfig thresholdConfig;

    public static void main(String[] args) throws Exception {
        EventsBasedThresholdGenerator generator = new EventsBasedThresholdGenerator();

        LOG.info("开始基于events表生成小时级动态阈值规则，使用前{}天历史数据", DEFAULT_ANALYSIS_DAYS);
        generator.generateAllHourlyRulesFromEvents(DEFAULT_ANALYSIS_DAYS);
    }

    /**
     * 从 events 表生成所有24小时的动态阈值规则
     * 
     * @param analysisDays 分析的历史天数
     */
    public void generateAllHourlyRulesFromEvents(int analysisDays) throws Exception {
        LOG.info("开始基于events表生成24小时动态阈值规则，基于前{}天数据", analysisDays);

        // 1. 加载配置文件
        loadThresholdConfig();

        // 2. 初始化 ClickHouse 连接
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        // 3. 清空整个hourly_alarm_rules表
        LOG.info("清空hourly_alarm_rules表，确保数据一致性");
        String clearSql = "TRUNCATE TABLE hourly_alarm_rules";
        PreparedStatement clearPs = conn.prepareStatement(clearSql);
        clearPs.executeUpdate();
        clearPs.close();
        LOG.info("hourly_alarm_rules表已清空");

        // 4. 直接从 events 表查询并聚合数据
        String sql = "SELECT " +
                "toHour(start_time) as hour_of_day, " +
                "service, " +
                "operation_name as operator_name, " +
                // 计算响应时间（毫秒）- 使用正确的时间差计算
                "avg((end_time - start_time) * 1000) as avg_duration, " +
                "max((end_time - start_time) * 1000) as max_duration, " +
                "quantile(0.75)((end_time - start_time) * 1000) as duration_p75, " +
                "quantile(0.90)((end_time - start_time) * 1000) as duration_p90, " +
                "quantile(0.95)((end_time - start_time) * 1000) as duration_p95, " +
                "quantile(0.99)((end_time - start_time) * 1000) as duration_p99, " +
                // 计算成功率和错误率
                "sum(is_error) / count(*) as error_rate, " +
                "1 - (sum(is_error) / count(*)) as success_rate, " +
                // 统计信息
                "count(*) as total_count, " +
                "sum(is_error) as error_count, " +
                "count(*) - sum(is_error) as success_count, " +
                // 标准差
                "stddevSamp((end_time - start_time) * 1000) as duration_std " +
                "FROM events " +
                "WHERE start_time >= now() - INTERVAL ? DAY " +
                "AND start_time < now() " +
                "AND service IS NOT NULL " +
                "AND operation_name IS NOT NULL " +
                // "AND span_type = 'Entry' " + // 只统计入口类型的span
                "AND is_error = 0 " + // 只统计成功的请求
                "AND (end_time - start_time) * 1000 < ? " + // 过滤异常长的响应时间
                "AND (end_time - start_time) > 0 " + // 过滤负数响应时间
                "GROUP BY hour_of_day, service, operator_name " +
                "HAVING total_count >= ? " +
                "ORDER BY hour_of_day";

        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, analysisDays);
        ps.setInt(2, getMaxReasonableDurationMs());
        ps.setInt(3, getMinSampleCount());
        ResultSet rs = ps.executeQuery();

        // 5. 按小时组织数据
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
            String operatorClass = EventsBasedThresholdGenerator.class.getSimpleName();

            double avgDuration = rs.getDouble("avg_duration");
            double maxDuration = rs.getDouble("max_duration");
            double durationP75 = rs.getDouble("duration_p75");
            double durationP90 = rs.getDouble("duration_p90");
            double durationP95 = rs.getDouble("duration_p95");
            double durationP99 = rs.getDouble("duration_p99");

            double errorRate = rs.getDouble("error_rate");
            double successRate = rs.getDouble("success_rate");
            long totalCount = rs.getLong("total_count");
            long errorCount = rs.getLong("error_count");
            long successCount = rs.getLong("success_count");
            double durationStd = rs.getDouble("duration_std");

            // 打印统计信息
            LOG.info("hour:{}, service:{}, operator:{}, samples:{}, avgDuration:{}, p95:{}",
                    hourOfDay, service, operatorName, totalCount,
                    String.format("%.2f", avgDuration), String.format("%.2f", durationP95));

            // 6. 生成该小时的动态阈值规则
            AlarmRule rule = generateRuleFromEventsData(
                    service, operatorName, operatorClass, hourOfDay,
                    avgDuration, maxDuration, successRate, totalCount,
                    durationStd, durationP75, durationP90, durationP95, durationP99,
                    analysisDays);

            String ruleKey = rule.combine();
            allHourlyRules.get(hourOfDay).put(ruleKey, rule);

            // 累计该小时的统计信息
            HourlyStatistics stats = hourlyStats.get(hourOfDay);
            stats.services.add(service);
            stats.operators.add(operatorName);
            stats.totalAvgDuration += avgDuration;
            stats.totalMaxDuration += maxDuration;
            stats.totalSuccessRate += successRate;
            stats.totalCount += totalCount;
            stats.totalSampleCount += (int) totalCount;
            stats.ruleCount++;
            totalRules++;
        }

        rs.close();
        ps.close();

        // 7. 批量保存所有24小时的规则到数据库
        if (totalRules > 0) {
            LOG.info("从events表生成{}条规则，开始保存到数据库", totalRules);

            for (int hour = 0; hour < 24; hour++) {
                Map<String, AlarmRule> hourRules = allHourlyRules.get(hour);
                HourlyStatistics stats = hourlyStats.get(hour);

                if (!hourRules.isEmpty()) {
                    // 调用数据库保存方法，传入该小时的统计汇总信息
                    // 这些统计信息用于监控和调试，帮助了解规则生成的整体情况
                    saveHourlyRuleToDatabase(conn, hour, hourRules,
                            stats.services.size(), // 该小时涉及的服务数量
                            stats.operators.size(), // 该小时涉及的操作数量
                            stats.ruleCount, // 该小时生成的规则总数
                            stats.totalAvgDuration / stats.ruleCount, // 该小时所有规则的平均延迟均值
                            stats.totalMaxDuration / stats.ruleCount, // 该小时所有规则的最大延迟均值
                            stats.totalSuccessRate / stats.ruleCount, // 该小时所有规则的成功率均值
                            stats.totalCount / stats.ruleCount, // 该小时所有规则的平均交易量
                            analysisDays, // 分析的历史天数
                            stats.totalSampleCount); // 该小时的总样本数
                    LOG.info("保存第{}小时规则到数据库，共{}条，总样本数{}，平均样本数{}",
                            hour, hourRules.size(), stats.totalSampleCount,
                            stats.totalSampleCount / stats.ruleCount);
                }
            }

            LOG.info("完成基于events表的24小时规则生成，总规则数: {}", totalRules);
        } else {
            LOG.warn("未从events表生成任何规则，请检查数据");
        }

        conn.close();
    }

    /**
     * 基于events数据生成告警规则
     */
    private AlarmRule generateRuleFromEventsData(
            String service, String operatorName, String operatorClass, int hourOfDay,
            double avgDuration, double maxDuration, double successRate, double totalCount,
            double durationStd, double durationP75, double durationP90,
            double durationP95, double durationP99, int analysisDays) {

        // 根据小时特征调整阈值倍数
        HourlyAdjustmentFactor factor = getHourlyAdjustmentFactor(hourOfDay);

        AlarmRule rule = new AlarmRule();
        rule.service = service;
        rule.operatorName = operatorName;
        rule.operatorClass = operatorClass;

        // 基于分位数设置阈值，更加科学合理
        // LOW: 基于P75，轻微异常
        // MID: 基于P90，中度异常
        // HIGH: 基于P95或P99，严重异常
        rule.avgDurationLow = Math.max(
                durationP75 * factor.avgDurationFactor,
                avgDuration * getAvgDurationMultiplier("low"));
        rule.avgDurationMid = Math.max(
                durationP90 * factor.avgDurationFactor,
                avgDuration * getAvgDurationMultiplier("mid"));
        rule.avgDurationHigh = Math.max(
                durationP95 * factor.avgDurationFactor,
                avgDuration * getAvgDurationMultiplier("high"));

        // 最大延迟阈值（基于P99和标准差）
        rule.maxDurationLow = Math.max(
                durationP90 * factor.maxDurationFactor,
                maxDuration * getMaxDurationMultiplier("low"));
        rule.maxDurationMid = Math.max(
                durationP95 * factor.maxDurationFactor,
                maxDuration * getMaxDurationMultiplier("mid"));
        rule.maxDurationHigh = Math.max(
                durationP99 * factor.maxDurationFactor,
                maxDuration * getMaxDurationMultiplier("high"));

        // 成功率阈值（使用配置文件中的固定值）
        rule.successRateLow = getSuccessRateThreshold("low");
        rule.successRateMid = getSuccessRateThreshold("mid");
        rule.successRateHigh = getSuccessRateThreshold("high");

        // 交易量阈值（基于历史均值）
        rule.trafficVolumeLow = totalCount * getVolumeMultiplier("low") * factor.trafficVolumeFactor;
        rule.trafficVolumeMid = totalCount * getVolumeMultiplier("mid") * factor.trafficVolumeFactor;
        rule.trafficVolumeHigh = totalCount * getVolumeMultiplier("high") * factor.trafficVolumeFactor;

        rule.alarmTemplate = String.format("服务%s操作%s在%d时动态阈值告警(基于前%d天events数据)",
                service, operatorName, hourOfDay, analysisDays);
        rule.sampleCount = (int) totalCount;

        return rule;
    }

    /**
     * 加载阈值配置文件
     */
    private void loadThresholdConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("threshold-config.yaml");
            if (inputStream != null) {
                Map<String, Object> config = yaml.load(inputStream);
                thresholdConfig = new ThresholdConfig();

                @SuppressWarnings("unchecked")
                Map<String, Object> threshold = (Map<String, Object>) config.get("threshold");

                // 加载成功率阈值
                @SuppressWarnings("unchecked")
                Map<String, Object> successRate = (Map<String, Object>) threshold.get("success_rate");
                thresholdConfig.successRateLow = (Double) successRate.get("low");
                thresholdConfig.successRateMid = (Double) successRate.get("mid");
                thresholdConfig.successRateHigh = (Double) successRate.get("high");

                // 加载响应时间倍数
                @SuppressWarnings("unchecked")
                Map<String, Object> responseTime = (Map<String, Object>) threshold.get("response_time");
                @SuppressWarnings("unchecked")
                Map<String, Object> avgDurationMultiplier = (Map<String, Object>) responseTime
                        .get("avg_duration_multiplier");
                thresholdConfig.avgDurationLowMultiplier = (Double) avgDurationMultiplier.get("low");
                thresholdConfig.avgDurationMidMultiplier = (Double) avgDurationMultiplier.get("mid");
                thresholdConfig.avgDurationHighMultiplier = (Double) avgDurationMultiplier.get("high");

                @SuppressWarnings("unchecked")
                Map<String, Object> maxDurationMultiplier = (Map<String, Object>) responseTime
                        .get("max_duration_multiplier");
                thresholdConfig.maxDurationLowMultiplier = (Double) maxDurationMultiplier.get("low");
                thresholdConfig.maxDurationMidMultiplier = (Double) maxDurationMultiplier.get("mid");
                thresholdConfig.maxDurationHighMultiplier = (Double) maxDurationMultiplier.get("high");

                // 加载交易量倍数
                @SuppressWarnings("unchecked")
                Map<String, Object> trafficVolume = (Map<String, Object>) threshold.get("traffic_volume");
                @SuppressWarnings("unchecked")
                Map<String, Object> volumeMultiplier = (Map<String, Object>) trafficVolume.get("volume_multiplier");
                thresholdConfig.volumeLowMultiplier = (Double) volumeMultiplier.get("low");
                thresholdConfig.volumeMidMultiplier = (Double) volumeMultiplier.get("mid");
                thresholdConfig.volumeHighMultiplier = (Double) volumeMultiplier.get("high");

                // 加载数据过滤配置
                @SuppressWarnings("unchecked")
                Map<String, Object> dataFilter = (Map<String, Object>) threshold.get("data_filter");
                thresholdConfig.minSampleCount = (Integer) dataFilter.get("min_sample_count");
                thresholdConfig.maxReasonableDurationMs = (Integer) dataFilter.get("max_reasonable_duration_ms");
                thresholdConfig.analysisDays = (Integer) dataFilter.get("analysis_days");

                LOG.info("成功加载阈值配置文件，成功率阈值: {}/{}/{}, 分析天数: {}, 最小样本数: {}",
                        thresholdConfig.successRateLow, thresholdConfig.successRateMid, thresholdConfig.successRateHigh,
                        thresholdConfig.analysisDays, thresholdConfig.minSampleCount);
            } else {
                LOG.warn("未找到threshold-config.yaml配置文件，使用默认值");
                thresholdConfig = new ThresholdConfig();
            }
        } catch (Exception e) {
            LOG.error("加载阈值配置文件失败，使用默认值: {}", e.getMessage());
            thresholdConfig = new ThresholdConfig();
        }
    }

    /**
     * 获取成功率阈值
     */
    private double getSuccessRateThreshold(String level) {
        if (thresholdConfig == null) {
            // 默认值
            switch (level) {
                case "low":
                    return 0.995;
                case "mid":
                    return 0.99;
                case "high":
                    return 0.985;
                default:
                    return 0.99;
            }
        }

        switch (level) {
            case "low":
                return thresholdConfig.successRateLow;
            case "mid":
                return thresholdConfig.successRateMid;
            case "high":
                return thresholdConfig.successRateHigh;
            default:
                return 0.99;
        }
    }

    /**
     * 获取平均响应时间倍数
     */
    private double getAvgDurationMultiplier(String level) {
        if (thresholdConfig == null) {
            // 默认值
            switch (level) {
                case "low":
                    return 1.2;
                case "mid":
                    return 1.5;
                case "high":
                    return 2.0;
                default:
                    return 1.5;
            }
        }

        switch (level) {
            case "low":
                return thresholdConfig.avgDurationLowMultiplier;
            case "mid":
                return thresholdConfig.avgDurationMidMultiplier;
            case "high":
                return thresholdConfig.avgDurationHighMultiplier;
            default:
                return 1.5;
        }
    }

    /**
     * 获取最大响应时间倍数
     */
    private double getMaxDurationMultiplier(String level) {
        if (thresholdConfig == null) {
            // 默认值
            switch (level) {
                case "low":
                    return 1.1;
                case "mid":
                    return 1.3;
                case "high":
                    return 1.5;
                default:
                    return 1.3;
            }
        }

        switch (level) {
            case "low":
                return thresholdConfig.maxDurationLowMultiplier;
            case "mid":
                return thresholdConfig.maxDurationMidMultiplier;
            case "high":
                return thresholdConfig.maxDurationHighMultiplier;
            default:
                return 1.3;
        }
    }

    /**
     * 获取交易量倍数
     */
    private double getVolumeMultiplier(String level) {
        if (thresholdConfig == null) {
            // 默认值
            switch (level) {
                case "low":
                    return 1.5;
                case "mid":
                    return 2.0;
                case "high":
                    return 3.0;
                default:
                    return 2.0;
            }
        }

        switch (level) {
            case "low":
                return thresholdConfig.volumeLowMultiplier;
            case "mid":
                return thresholdConfig.volumeMidMultiplier;
            case "high":
                return thresholdConfig.volumeHighMultiplier;
            default:
                return 2.0;
        }
    }

    /**
     * 获取最小样本数
     */
    private int getMinSampleCount() {
        return thresholdConfig != null ? thresholdConfig.minSampleCount : DEFAULT_MIN_SAMPLE_COUNT;
    }

    /**
     * 获取最大合理响应时间
     */
    private int getMaxReasonableDurationMs() {
        return thresholdConfig != null ? thresholdConfig.maxReasonableDurationMs : DEFAULT_MAX_REASONABLE_DURATION_MS;
    }

    /**
     * 保存小时级规则到数据库
     * 
     * 将指定小时的所有告警规则批量插入到 hourly_alarm_rules 表中
     * 
     * @param conn             数据库连接
     * @param hourOfDay        小时序号 (0-23)
     * @param ruleMap          该小时的所有规则映射，key为规则标识，value为AlarmRule对象
     * @param totalServices    该小时涉及的服务数量（用于统计监控）
     * @param totalOperators   该小时涉及的操作数量（用于统计监控）
     * @param ruleCount        该小时生成的规则总数（用于统计监控）
     * @param avgAvgDuration   该小时所有规则的平均延迟均值（用于统计监控）
     * @param avgMaxDuration   该小时所有规则的最大延迟均值（用于统计监控）
     * @param avgSuccessRate   该小时所有规则的成功率均值（用于统计监控）
     * @param avgTotalCount    该小时所有规则的平均交易量（用于统计监控）
     * @param analysisDays     分析的历史天数
     * @param totalSampleCount 该小时的总样本数（用于统计监控）
     */
    private void saveHourlyRuleToDatabase(Connection conn, int hourOfDay,
            Map<String, AlarmRule> ruleMap,
            int totalServices, int totalOperators, int ruleCount,
            double avgAvgDuration, double avgMaxDuration,
            double avgSuccessRate, double avgTotalCount,
            int analysisDays, int totalSampleCount) throws Exception {

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
        int ruleIndex = 0;
        for (AlarmRule rule : ruleMap.values()) {
            ruleIndex++;

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
            ps.setInt(19, rule.sampleCount); // 使用真实的样本数

            ps.addBatch();

            // 记录每个规则的详细信息
            LOG.debug("保存规则[{}/{}]: 服务[{}] 操作[{}] 小时[{}] 样本数[{}]",
                    ruleIndex, ruleCount, rule.service, rule.operatorName, hourOfDay, rule.sampleCount);
        }

        ps.executeBatch();
        ps.close();
    }

    /**
     * 根据小时获取调整因子
     */
    private HourlyAdjustmentFactor getHourlyAdjustmentFactor(int hourOfDay) {
        HourlyAdjustmentFactor factor = new HourlyAdjustmentFactor();

        // 根据业务特征调整阈值严格程度
        if (hourOfDay >= 9 && hourOfDay <= 11) {
            // 上午高峰期：相对宽松
            factor.avgDurationFactor = 1.2;
            factor.maxDurationFactor = 1.3;
            factor.successRateFactor = 0.98;
            factor.trafficVolumeFactor = 1.5;
        } else if (hourOfDay >= 14 && hourOfDay <= 16) {
            // 下午高峰期：相对宽松
            factor.avgDurationFactor = 1.2;
            factor.maxDurationFactor = 1.3;
            factor.successRateFactor = 0.98;
            factor.trafficVolumeFactor = 1.5;
        } else if (hourOfDay >= 20 && hourOfDay <= 22) {
            // 晚间高峰期：相对宽松
            factor.avgDurationFactor = 1.15;
            factor.maxDurationFactor = 1.2;
            factor.successRateFactor = 0.99;
            factor.trafficVolumeFactor = 1.3;
        } else if (hourOfDay >= 0 && hourOfDay <= 6) {
            // 凌晨低峰期：相对严格
            factor.avgDurationFactor = 0.9;
            factor.maxDurationFactor = 0.95;
            factor.successRateFactor = 1.0;
            factor.trafficVolumeFactor = 0.8;
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
     * 阈值配置类
     * 用于存储从YAML配置文件读取的阈值参数
     */
    private static class ThresholdConfig {
        // 成功率阈值（固定值）
        double successRateLow = 0.995;
        double successRateMid = 0.99;
        double successRateHigh = 0.985;

        // 平均响应时间倍数
        double avgDurationLowMultiplier = 1.2;
        double avgDurationMidMultiplier = 1.5;
        double avgDurationHighMultiplier = 2.0;

        // 最大响应时间倍数
        double maxDurationLowMultiplier = 1.1;
        double maxDurationMidMultiplier = 1.3;
        double maxDurationHighMultiplier = 1.5;

        // 交易量倍数
        double volumeLowMultiplier = 1.5;
        double volumeMidMultiplier = 2.0;
        double volumeHighMultiplier = 3.0;

        // 数据过滤配置
        int minSampleCount = 10;
        int maxReasonableDurationMs = 60000;
        int analysisDays = 7;
    }

    /**
     * 小时调整因子
     * 用于根据不同的业务时段调整阈值严格程度
     */
    private static class HourlyAdjustmentFactor {
        double avgDurationFactor = 1.0;
        double maxDurationFactor = 1.0;
        double successRateFactor = 1.0;
        double trafficVolumeFactor = 1.0;
    }

    /**
     * 小时统计信息
     * 
     * 用于收集和汇总指定小时内所有规则的统计信息，
     * 这些信息用于监控规则生成的质量和覆盖范围
     */
    private static class HourlyStatistics {
        /** 该小时涉及的所有服务名称集合 */
        Set<String> services = new HashSet<>();
        /** 该小时涉及的所有操作名称集合 */
        Set<String> operators = new HashSet<>();
        /** 该小时所有规则的平均延迟总和（用于计算均值） */
        double totalAvgDuration = 0.0;
        /** 该小时所有规则的最大延迟总和（用于计算均值） */
        double totalMaxDuration = 0.0;
        /** 该小时所有规则的成功率总和（用于计算均值） */
        double totalSuccessRate = 0.0;
        /** 该小时所有规则的交易量总和（用于计算均值） */
        double totalCount = 0.0;
        /** 该小时所有规则的总样本数 */
        int totalSampleCount = 0;
        /** 该小时生成的规则总数 */
        int ruleCount = 0;
    }
}