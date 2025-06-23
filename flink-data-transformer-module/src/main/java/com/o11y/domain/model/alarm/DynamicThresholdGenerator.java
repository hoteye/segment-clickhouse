package com.o11y.domain.model.alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.infrastructure.database.DatabaseService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * 动态阈值生成器：从 ClickHouse 聚合表统计 service/operatorName 的平均值，自动推送动态告警规则。
 */
public class DynamicThresholdGenerator {
    public static void main(String[] args) throws Exception {
        // 1. 初始化 ClickHouse 连接
        Map<String, String> clickhouseConfig = new HashMap<>();
        clickhouseConfig.put("url", "jdbc:clickhouse://localhost:8123/default");
        clickhouseConfig.put("username", "default");
        clickhouseConfig.put("password", "");
        clickhouseConfig.put("schema_name", "default");
        clickhouseConfig.put("table_name", "flink_operator_agg_result");
        DatabaseService db = new DatabaseService(clickhouseConfig).initConnection();
        Connection conn = db.getConnection();

        // 2. 查询近1天每组的平均值
        String sql = "SELECT service, operator_name, operator_class, " +
                "avg(avg_duration) as avg_avg_duration, " +
                "avg(max_duration) as avg_max_duration, " +
                "avg(success_rate) as avg_success_rate, " +
                "avg(total_count) as avg_total_count " +
                "FROM flink_operator_agg_result " +
                "WHERE window_start > now() - 86400 " +
                "GROUP BY service, operator_name, operator_class";
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        Map<String, AlarmRule> ruleMap = new HashMap<>();
        while (rs.next()) {
            String service = rs.getString("service");
            String operatorName = rs.getString("operator_name");
            String operatorClass = rs.getString("operator_class");
            double avgDuration = rs.getDouble("avg_avg_duration");
            double maxDuration = rs.getDouble("avg_max_duration");
            double successRate = rs.getDouble("avg_success_rate");
            double totalCount = rs.getDouble("avg_total_count");

            AlarmRule rule = new AlarmRule();
            rule.service = service;
            rule.operatorName = operatorName;
            rule.operatorClass = operatorClass;
            // 平均延迟阈值
            rule.avgDurationLow = avgDuration * 1.2;
            rule.avgDurationMid = avgDuration * 1.5;
            rule.avgDurationHigh = avgDuration * 2.0;
            // 最大延迟阈值
            rule.maxDurationLow = maxDuration * 1.2;
            rule.maxDurationMid = maxDuration * 1.5;
            rule.maxDurationHigh = maxDuration * 2.0;
            // 成功率阈值（低于平均值告警）
            rule.successRateLow = successRate * 0.98;
            rule.successRateMid = successRate * 0.95;
            rule.successRateHigh = successRate * 0.90;
            // 交易量阈值
            rule.trafficVolumeLow = totalCount * 1.2;
            rule.trafficVolumeMid = totalCount * 1.5;
            rule.trafficVolumeHigh = totalCount * 2.0;
            rule.alarmTemplate = "服务${service}算子${operatorName}动态阈值告警";
            ruleMap.put(rule.combine(), rule);
        }
        rs.close();
        ps.close();
        db.close();

        // 3. 推送到 Kafka
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ruleMap);
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("alarm_rule_topic", "all_rules", json));
            System.out.println("已写入 alarm_rule_topic, key=all_rules, 动态阈值规则条数: " + ruleMap.size());
        }
    }
}
