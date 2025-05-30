package com.o11y;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class KafkaServiceTest {
    private KafkaService kafkaService;

    @BeforeEach
    public void setUp() {
        Map<String, Object> kafkaConfig = Map.of(
                "bootstrap_servers", "localhost:9092",
                "group_id", "test-group",
                "topic", "test-topic",
                "auto_offset_reset", "earliest",
                "poll_interval_ms", 1000);
        kafkaService = new KafkaService(kafkaConfig);
    }

    @Test
    public void testConsumeMessages() throws Exception {
        ConsumerRecords<String, byte[]> records = kafkaService.consumeMessages();

        assert records != null;

    }
}