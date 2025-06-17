package com.o11y.flink.hotupdate;

import com.o11y.flink.rule.AlarmRule;
import com.o11y.flink.serde.AlarmRuleDeserializationSchema;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import java.util.Map;

public class RuleBroadcastStreamFactory {
    /**
     * 构建规则流广播流，支持批量规则（Map<String, AlarmRule>）。
     */
    public static BroadcastStream<Map<String, AlarmRule>> buildRuleBroadcastStream(
            StreamExecutionEnvironment env,
            Map<String, String> kafkaConfig,
            MapStateDescriptor<String, Map<String, AlarmRule>> ruleStateDescriptor) {
        KafkaSource<Map<String, AlarmRule>> alarmRuleKafkaSource = KafkaSource.<Map<String, AlarmRule>>builder()
                .setBootstrapServers(kafkaConfig.get("bootstrap_servers"))
                .setTopics(kafkaConfig.get("alarm_rule_topic"))
                .setGroupId(kafkaConfig.get("alarm_rule_group_id"))
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new AlarmRuleDeserializationSchema())
                .build();
        DataStream<Map<String, AlarmRule>> ruleStream = env.fromSource(
                alarmRuleKafkaSource,
                WatermarkStrategy.noWatermarks(),
                "KafkaSource-AlarmRule");
        // 增加 null 过滤，跳过反序列化失败的规则
        ruleStream = ruleStream.filter(ruleMap -> ruleMap != null);
        return ruleStream.broadcast(ruleStateDescriptor);
    }
}
