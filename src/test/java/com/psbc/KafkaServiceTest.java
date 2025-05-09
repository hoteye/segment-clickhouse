package com.psbc;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class KafkaServiceTest {
    private KafkaService kafkaService;

    @BeforeEach
    void setUp() {
        Map<String, String> kafkaConfig = Map.of(
                "bootstrap_servers", "localhost:9092",
                "group_id", "test-group",
                "topic", "test-topic",
                "auto_offset_reset", "earliest");
        kafkaService = new KafkaService(kafkaConfig);
    }

    @Test
    void testConsumeMessages() throws Exception {
        ConsumerRecords<String, byte[]> records = kafkaService.consumeMessages();

        // 验证是否正确消费消息
        assert records != null;
    }
}