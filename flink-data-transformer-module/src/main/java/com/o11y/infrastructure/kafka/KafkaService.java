package com.o11y.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class KafkaService {
    private final KafkaConsumer<String, byte[]> consumer;
    private final long pollIntervalMs;

    /**
     * Constructs a KafkaService with the given configuration.
     * 
     * @param kafkaConfig The Kafka configuration map.
     */
    public KafkaService(Map<String, Object> kafkaConfig) {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaConfig.get("bootstrap_servers"));
        props.put("group.id", kafkaConfig.get("group_id"));
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put("auto.offset.reset", kafkaConfig.get("auto_offset_reset"));
        this.pollIntervalMs = (int) kafkaConfig.get("poll_interval_ms");

        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(kafkaConfig.get("topic").toString()));
    }

    /**
     * Consumes messages from the Kafka topic.
     * 
     * @return ConsumerRecords containing the polled messages.
     * @throws Exception if polling fails.
     */
    public ConsumerRecords<String, byte[]> consumeMessages() throws Exception {
        return consumer.poll(Duration.ofMillis(pollIntervalMs));
    }

    /**
     * Closes the Kafka consumer.
     */
    public void close() {
        consumer.close();
    }
}