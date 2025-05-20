package com.psbc.flink;

import com.psbc.ConfigLoader;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;

import java.util.Map;

public class FlinkKafkaToClickHouseJob {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Load Kafka config from application.yaml
        Map<String, Object> config = ConfigLoader.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");

        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaConfig.get("bootstrap_servers"))
                .setTopics(kafkaConfig.get("topic"))
                .setGroupId(kafkaConfig.get("group_id"))
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> stream = env.fromSource(kafkaSource,
                org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "Kafka Source");

        // Add sink to ClickHouse
        stream.addSink(new ClickHouseSinkFunction());

        env.execute("Flink Kafka to ClickHouse Job");
    }
}
