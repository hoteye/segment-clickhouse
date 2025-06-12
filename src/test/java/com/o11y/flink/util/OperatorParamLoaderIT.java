package com.o11y.flink.util;

import com.o11y.DatabaseService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试：真实 ClickHouse + 真实 Kafka
 * 需保证 application.yaml 配置正确，ClickHouse/Kafka 服务可用
 */
public class OperatorParamLoaderIT {
    private static DatabaseService dbService;
    private static KafkaProducer<String, String> producer;
    private static org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer;
    private static String operatorClass = "ServiceAvgDurationAggregateFunctionOperator";
    private static String paramUpdateTopic;

    @BeforeAll
    public static void setup() throws Exception {
        // 读取 application.yaml
        Map<String, Object> config = com.o11y.ConfigLoader.loadConfig("application.yaml");
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");
        paramUpdateTopic = kafkaConfig.getOrDefault("param_update_topic", "flink-operator-param-update");
        dbService = new DatabaseService(
                clickhouseConfig.get("url"),
                clickhouseConfig.get("schema_name"),
                clickhouseConfig.get("table_name"),
                clickhouseConfig.get("username"),
                clickhouseConfig.get("password")).initConnection();
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.get("bootstrap_servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
        // 增加 KafkaConsumer 用于校验消息
        Properties consProps = new Properties();
        consProps.put("bootstrap.servers", kafkaConfig.get("bootstrap_servers"));
        consProps.put("group.id", "OperatorParamLoaderIT-" + UUID.randomUUID());
        consProps.put("key.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        consProps.put("value.deserializer", org.apache.kafka.common.serialization.StringDeserializer.class.getName());
        consProps.put("auto.offset.reset", "latest");
        consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consProps);
        consumer.subscribe(Collections.singletonList(paramUpdateTopic));
    }

    @Test
    public void testNewAndUpdateParamsWithRealClickHouseAndKafka() throws Exception {
        // 新增参数前，先 poll 一次，确保分区分配完成
        consumer.poll(java.time.Duration.ofSeconds(1));
        // 新增参数
        Map<String, List<String>> params = new HashMap<>();
        params.put("k1", Arrays.asList("v1", "v2"));
        params.put("k2", Collections.singletonList("v3"));
        OperatorParamLoader.newParams(dbService, operatorClass, params, producer);
        producer.flush(); // 等待消息写入 broker
        // 校验 Kafka 是否收到 :new 消息
        boolean foundNew = false;
        for (int i = 0; i < 5 && !foundNew; i++) {
            var records = consumer.poll(java.time.Duration.ofSeconds(1));
            for (var record : records) {
                if (record.key().equals(operatorClass) && record.value().contains(":new")) {
                    foundNew = true;
                    break;
                }
            }
        }
        assertTrue(foundNew, "Kafka 队列中应有参数新增的通知消息");
        // 校验 ClickHouse
        Map<String, List<String>> loaded = OperatorParamLoader.loadParamList(dbService, operatorClass);
        assertTrue(loaded.get("k1").contains("v1"));
        assertTrue(loaded.get("k1").contains("v2"));
        assertTrue(loaded.get("k2").contains("v3"));

        // 更新参数前，先 poll 一次，确保分区分配完成
        consumer.poll(java.time.Duration.ofSeconds(1));
        // 更新参数
        Map<String, List<String>> updateParams = new HashMap<>();
        updateParams.put("k1", Collections.singletonList("v4"));
        OperatorParamLoader.updateParams(dbService, operatorClass, updateParams, producer);
        producer.flush();
        // 校验 Kafka 是否收到 :update 消息
        boolean foundUpdate = false;
        for (int i = 0; i < 5 && !foundUpdate; i++) {
            var records = consumer.poll(java.time.Duration.ofSeconds(1));
            for (var record : records) {
                if (record.key().equals(operatorClass) && record.value().contains(":update")) {
                    foundUpdate = true;
                    break;
                }
            }
        }
        assertTrue(foundUpdate, "Kafka 队列中应有参数更新的通知消息");
        Map<String, List<String>> loaded2 = OperatorParamLoader.loadParamList(dbService, operatorClass);
        assertEquals(Collections.singletonList("v4"), loaded2.get("k1"));
    }
}
