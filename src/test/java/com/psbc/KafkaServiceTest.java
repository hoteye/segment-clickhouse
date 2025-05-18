package com.psbc;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class KafkaServiceTest {
    private KafkaService kafkaService;

    @BeforeEach
    public void setUp() {
        Map<String, String> kafkaConfig = Map.of(
                "bootstrap_servers", "localhost:9092",
                "group_id", "test-group",
                "topic", "test-topic",
                "auto_offset_reset", "earliest");
        kafkaService = new KafkaService(kafkaConfig);
    }

    @Test
    public void testConsumeMessages() throws Exception {
        ConsumerRecords<String, byte[]> records = kafkaService.consumeMessages();

        assert records != null;

    }
}