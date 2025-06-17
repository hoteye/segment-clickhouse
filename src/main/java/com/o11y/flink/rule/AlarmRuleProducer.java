package com.o11y.flink.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;

/**
 * 告警规则写入 Kafka 的工具类。
 */
public class AlarmRuleProducer {
    public static void main(String[] args) throws Exception {
        // 构造多个规则对象
        AlarmRule rule1 = new AlarmRule();
        rule1.service = "dubbo-provider-b";
        rule1.operatorName = "com.example.provider.service.HaiService.sayHai(String)";
        rule1.operatorClass = "AggregateOperator";
        rule1.successRateHigh = 0.99;
        rule1.successRateMid = 0.95;
        rule1.successRateLow = 0.90;
        rule1.trafficVolumeHigh = 10000.0;
        rule1.trafficVolumeMid = 5000.0;
        rule1.trafficVolumeLow = 1000.0;
        rule1.maxDurationHigh = 1000.0;
        rule1.maxDurationMid = 800.0;
        rule1.maxDurationLow = 500.0;
        rule1.avgDurationHigh = 600.0;
        rule1.avgDurationMid = 400.0;
        rule1.avgDurationLow = 200.0;
        rule1.alarmTemplate = "服务${service}算子${operatorName}延迟超阈值";

        AlarmRule rule2 = new AlarmRule();
        rule2.service = "dubbo-consumer";
        rule2.operatorName = "{GET}/sayGoodMorning";
        rule2.operatorClass = "AggregateOperator";
        rule2.successRateHigh = 0.98;
        rule2.successRateMid = 0.95;
        rule2.successRateLow = 0.90;
        rule2.trafficVolumeHigh = 8000.0;
        rule2.trafficVolumeMid = 4000.0;
        rule2.trafficVolumeLow = 1000.0;
        rule2.maxDurationHigh = 1200.0;
        rule2.maxDurationMid = 900.0;
        rule2.maxDurationLow = 600.0;
        rule2.avgDurationHigh = 700.0;
        rule2.avgDurationMid = 500.0;
        rule2.avgDurationLow = 300.0;
        rule2.alarmTemplate = "服务${service}算子${operatorName}延迟超阈值";

        AlarmRule rule3 = new AlarmRule();
        rule3.service = "dubbo-consumer";
        rule3.operatorName = "{GET}/sayHai";
        rule3.operatorClass = "AggregateOperator";
        rule3.successRateHigh = 0.98;
        rule3.successRateMid = 0.95;
        rule3.successRateLow = 0.90;
        rule3.trafficVolumeHigh = 8000.0;
        rule3.trafficVolumeMid = 4000.0;
        rule3.trafficVolumeLow = 1000.0;
        rule3.maxDurationHigh = 1200.0;
        rule3.maxDurationMid = 900.0;
        rule3.maxDurationLow = 600.0;
        rule3.avgDurationHigh = 700.0;
        rule3.avgDurationMid = 500.0;
        rule3.avgDurationLow = 300.0;
        rule3.alarmTemplate = "服务${service}算子${operatorName}延迟超阈值";

        // 组装 map
        Map<String, AlarmRule> ruleMap = new HashMap<>();
        ruleMap.put(rule1.combine(), rule1);
        ruleMap.put(rule2.combine(), rule2);
        ruleMap.put(rule3.combine(), rule3);

        // 序列化为 JSON
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ruleMap);
        System.out.println("发送规则Map: " + json);

        // 发送到 Kafka，key 可用 "all_rules" 或自定义
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        try (Producer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("alarm_rule_topic", "all_rules", json));
            System.out.println("已写入 alarm_rule_topic, key=all_rules");
        }
    }
}
