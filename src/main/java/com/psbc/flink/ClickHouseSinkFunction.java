package com.psbc.flink;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import com.psbc.DatabaseService;
import com.psbc.ConfigLoader;
import com.psbc.TransformerUtils;
import segment.v3.Segment.SegmentObject;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.Properties;
import java.time.Duration;

public class ClickHouseSinkFunction implements SinkFunction<String> {
    private transient DatabaseService databaseService;
    private transient ConcurrentSkipListSet<String> invalidFields;
    private transient ConcurrentSkipListSet<String> missingFields;

    private static final String STRUCTURE_TOPIC = "table_structure_changed";
    private transient Producer<String, String> kafkaProducer;
    private transient KafkaConsumer<String, String> kafkaConsumer;
    private transient Thread structureListenerThread;

    // Static flag to indicate table structure change (shared across all sink
    // instances)
    private static volatile boolean tableStructureChanged = true;

    private void initKafkaStructureSync(Map<String, String> kafkaConfig) {
        // Producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.get("bootstrap_servers"));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        kafkaProducer = new KafkaProducer<>(producerProps);
        // Consumer
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", kafkaConfig.get("bootstrap_servers"));
        consumerProps.put("group.id", kafkaConfig.get("group_id") + "-structure-sync");
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("auto.offset.reset", "earliest");
        kafkaConsumer = new KafkaConsumer<>(consumerProps);
        kafkaConsumer.subscribe(java.util.Collections.singletonList(STRUCTURE_TOPIC));
        // Listener thread
        structureListenerThread = new Thread(() -> {
            while (true) {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    if ("events".equals(record.value())) {
                        tableStructureChanged = true;
                    }
                }
            }
        });
        structureListenerThread.setDaemon(true);
        structureListenerThread.start();
    }

    private void notifyStructureChanged() {
        if (kafkaProducer != null) {
            kafkaProducer.send(new ProducerRecord<>(STRUCTURE_TOPIC, "events"));
        }
    }

    @Override
    public void invoke(String value, Context context) throws Exception {
        if (databaseService == null) {
            Map<String, Object> config = ConfigLoader.loadConfig("application.yaml");
            @SuppressWarnings("unchecked")
            Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
            @SuppressWarnings("unchecked")
            Map<String, String> kafkaConfig = (Map<String, String>) config.get("kafka");
            databaseService = new DatabaseService(
                    clickhouseConfig.get("url"),
                    clickhouseConfig.get("schema_name"),
                    clickhouseConfig.get("table_name"),
                    clickhouseConfig.get("username"),
                    clickhouseConfig.get("password")).initConnection();
            invalidFields = new ConcurrentSkipListSet<>();
            missingFields = new ConcurrentSkipListSet<>();
            initKafkaStructureSync(kafkaConfig);
        }
        // Check if table structure changed, rebuild SQL if needed
        if (tableStructureChanged) {
            databaseService.buildInsertSQL();
            tableStructureChanged = false;
        }
        // Parse SegmentObject and write to ClickHouse
        SegmentObject segment = SegmentObject.parseFrom(value.getBytes());
        TransformerUtils.insertSegmentObjectToEvents(databaseService, segment, invalidFields, missingFields);
        // 动态扩展表结构并通知其他进程
        boolean changed = false;
        if (!missingFields.isEmpty()) {
            databaseService.addColumns(missingFields);
            changed = true;
        }
        if (changed) {
            notifyStructureChanged();
            tableStructureChanged = true;
        }
    }

}
