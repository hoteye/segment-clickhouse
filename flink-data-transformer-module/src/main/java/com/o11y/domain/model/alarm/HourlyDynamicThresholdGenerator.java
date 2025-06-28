package com.o11y.domain.model.alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.shared.util.ConfigurationUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 默认分析前7天的数据
    private static final int DEFAULT_ANALYSIS_DAYS = 7;

    public static void main(String[] args) throws Exception {
        HourlyDynamicThresholdGenerator generator = new HourlyDynamicThresholdGenerator();

        int analysisDays = DEFAULT_ANALYSIS_DAYS;

        if (args.length > 0 && "generate-all".equals(args[0])) {
            // 一次性生成所有24小时的规则
            if (args.length > 1) {
                try {
                    analysisDays = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    LOG.warn("无效的天数参数: {}，使用默认值: {}", args[1], DEFAULT_ANALYSIS_DAYS);
                }
            }
            generator.generateAllHourlyRulesOnce(analysisDays);
        } else if (args.length > 0 && "publish-hour".equals(args[0])) {
            // 下发指定小时的规则（主要用于测试，生产环境建议使用Flink算子自动下发）
            if (args.length < 2) {
                LOG.error("请指定小时数，例如: publish-hour 9");
                System.exit(1);
            }
            try {
                int hour = Integer.parseInt(args[1]);
                LOG.info("注意：这是测试功能，生产环境建议使用HourlyRulePublishProcessFunction自动下发规则");
                generator.publishHourlyRules(hour);
            } catch (NumberFormatException e) {
                LOG.error("无效的参数: {}", args[1]);
                System.exit(1);
            }
        } else {
            // 默认：一次性生成所有规则
            generator.generateAllHourlyRulesOnce(analysisDays);
        }
    }

    /**
     * 一次性生成所有24小时的动态阈值规则
     * 核心方法：分析前N天数据，生成所有24小时的规则
     * 
     * @param analysisDays 分析的历史天数
     */
    public void generateAllHourlyRulesOnce(int analysisDays) throws Exception {
        LOG.info("开始一次性生成所有24小时的动态阈值规则，基于前 {} 天的历史数据", analysisDays);

        // 1. 初始化 ClickHouse 连接（参考FlinkService的方式读取配置）
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        // 2. 一次性查询前N天所有数据，按小时分组
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

        // 3. 按小时组织数据：Map<hour, Map<ruleKey, AlarmRule>>
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

            // 4. 生成该小时的动态阈值规则
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

            LOG.debug("生成规则: {}时 {} -> 平均延迟: {:.2f}ms, 最大延迟: {:.2f}ms, 成功率: {:.2f}%, 样本数: {}",
                    hourOfDay, ruleKey, avgDuration, maxDuration, successRate * 100, sampleCount);
        }

        rs.close();
        ps.close();

        // 5. 批量保存所有24小时的规则到数据库
        if (totalRules > 0) {
            LOG.info("开始批量保存所有24小时的规则到数据库，总规则数: {}", totalRules);

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

                    LOG.info("已保存 {} 时规则，规则数量: {}, 服务数: {}, 操作员数: {}",
                            hour, hourRules.size(), stats.services.size(), stats.operators.size());
                } else {
                    LOG.warn("{} 时没有生成任何规则，可能前 {} 天该时段数据不足", hour, analysisDays);
                }
            }

            LOG.info("一次性生成完成！总共生成 {} 条规则，覆盖 {} 个小时", totalRules,
                    allHourlyRules.entrySet().stream().mapToInt(e -> e.getValue().isEmpty() ? 0 : 1).sum());
        } else {
            LOG.warn("没有生成任何规则，可能前 {} 天数据不足或无交易数据", analysisDays);
        }

        db.close();
    }

    /**
     * 下发指定小时的规则到Kafka
     * 注意：这个方法主要用于测试和手动触发，正常情况下规则下发由Flink算子自动处理
     * 
     * @param hourOfDay 小时（0-23）
     */
    public void publishHourlyRules(int hourOfDay) throws Exception {
        LOG.info("开始下发 {}时 的规则到Kafka", hourOfDay);

        // 初始化数据库连接
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        // 查询指定小时的规则
        String sql = "SELECT rule_map_json FROM hourly_alarm_rules WHERE hour_of_day = ? LIMIT 1";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, hourOfDay);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            String ruleMapJson = rs.getString("rule_map_json");

            // 反序列化JSON为Map<String, AlarmRule>
            @SuppressWarnings("unchecked")
            Map<String, Object> rawMap = objectMapper.readValue(ruleMapJson, Map.class);
            Map<String, AlarmRule> ruleMap = new HashMap<>();

            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                String key = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> ruleData = (Map<String, Object>) entry.getValue();

                // 将Map转换为AlarmRule对象
                AlarmRule rule = objectMapper.convertValue(ruleData, AlarmRule.class);
                ruleMap.put(key, rule);
            }

            if (!ruleMap.isEmpty()) {
                pushRulesToKafka(ruleMap, hourOfDay);
                LOG.info("成功下发 {}时 的规则到Kafka，规则数量: {}", hourOfDay, ruleMap.size());
            } else {
                LOG.warn("{}时 的规则为空，跳过下发", hourOfDay);
            }
        } else {
            LOG.warn("未找到 {}时 的规则数据", hourOfDay);
        }

        rs.close();
        ps.close();
        conn.close();
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
     * 保存小时级规则到数据库（每个规则一条记录，不使用JSON）
     */
    private void saveHourlyRuleToDatabase(Connection conn, int hourOfDay,
            Map<String, AlarmRule> ruleMap,
            int totalServices, int totalOperators, int ruleCount,
            double avgAvgDuration, double avgMaxDuration,
            double avgSuccessRate, double avgTotalCount,
            int analysisDays, int totalSampleCount) throws Exception {

        // 先删除该小时的所有规则
        String deleteSql = "DELETE FROM hourly_alarm_rules WHERE hour_of_day = ?";
        PreparedStatement deletePs = conn.prepareStatement(deleteSql);
        deletePs.setInt(1, hourOfDay);
        deletePs.executeUpdate();
        deletePs.close();

        // 逐条插入规则（参考DynamicThresholdGenerator的方式）
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
        int insertCount = 0;

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
            insertCount++;
        }

        ps.executeBatch();
        ps.close();

        LOG.info("已保存 {} 时规则到数据库，规则数量: {}, 基于前{}天数据",
                hourOfDay, insertCount, analysisDays);
    }

    /**
     * 推送规则到Kafka
     */
    private void pushRulesToKafka(Map<String, AlarmRule> ruleMap, int hourOfDay) throws Exception {
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");

        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaConfig.get("bootstrap.servers"));
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            String topicName = kafkaConfig.getOrDefault("alarm.rule.topic", "alarm_rule");
            String ruleMapJson = objectMapper.writeValueAsString(ruleMap);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topicName,
                    "hourly_rules_" + hourOfDay,
                    ruleMapJson);

            producer.send(record).get(); // 同步发送
            LOG.info("规则已推送到Kafka topic: {}, key: hourly_rules_{}", topicName, hourOfDay);
        }
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
        LOG.info("开始清空 hourly_alarm_rules 表...");

        // 1. 初始化 ClickHouse 连接
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        // 2. 清空表
        String sql = "TRUNCATE TABLE hourly_alarm_rules";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.executeUpdate();
        ps.close();

        LOG.info("已清空 hourly_alarm_rules 表，准备生成新的规则数据");

        db.close();
    }
}