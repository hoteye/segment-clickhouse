package com.psbc;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import segment.v3.Segment.SegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

public class TransformerService {
    static final Logger logger = LoggerFactory.getLogger(TransformerService.class);

    private final DatabaseService databaseService;
    private final KafkaService kafkaService;
    private final int batchSize;
    private final int batchInterval;

    static ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
    static ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
    private int spanCounter = 0;
    private long lastInsertTime = System.currentTimeMillis(); // 初始化上次插入时间
    public static Boolean tableStructureChanged = true;

    public TransformerService(DatabaseService databaseService, KafkaService kafkaService,
            Map<String, Integer> batchConfig) {
        this(databaseService, kafkaService, batchConfig.get("size"), batchConfig.get("interval"));
    }

    public TransformerService(DatabaseService databaseService, KafkaService kafkaService,
            Map<String, Integer> batchConfig, Map<String, Object> segmentOnEventMappings) {
        this(databaseService, kafkaService, batchConfig.get("size"), batchConfig.get("interval"));
    }

    public TransformerService(DatabaseService databaseService, KafkaService kafkaService,
            int batchSize, int batchInterval) {
        this.databaseService = databaseService;
        this.kafkaService = kafkaService;
        this.batchSize = batchSize;
        this.batchInterval = batchInterval;
    }

    public static void main(String[] args) throws Exception {
        // 加载配置文件
        Map<String, Object> config = ConfigLoader.loadConfig("application.yaml");

        // 初始化服务
        DatabaseService databaseService = new DatabaseService((Map<String, String>) config.get("clickhouse"))
                .initConnection();
        KafkaService kafkaService = new KafkaService((Map<String, String>) config.get("kafka"));

        TransformerService transformerService = new TransformerService(databaseService, kafkaService,
                (Map<String, Integer>) config.get("batch"));

        // 启动新线程，每 addColumnsInterval 毫秒执行一次 addColumns 方法
        BackgroundTaskManager.startAddColumnsTask(databaseService, missingFields,
                (int) config.get("add_columns_interval"));

        // 调用 run 方法处理 Kafka 消息
        transformerService.run();
    }

    /**
     * 处理 Kafka 消息的主循环
     * 
     * @param databaseService DatabaseService 实例
     * @param kafkaService    KafkaService 实例
     * @param batchSize       批量插入的大小
     * @param batchInterval   批量插入的时间间隔（毫秒）
     * @throws Exception 如果处理消息时发生错误
     */
    public void run() throws Exception {
        while (true) {
            try {
                if (tableStructureChanged) {
                    databaseService.buildInsertSQL();
                    tableStructureChanged = false; // 重置标志
                }
                ConsumerRecords<String, byte[]> records = kafkaService.consumeMessages();
                for (ConsumerRecord<String, byte[]> record : records) {
                    insertToDb(record.value()); // 处理每条消息
                }
            } catch (java.sql.SQLException e) {
                databaseService.initConnection();
            }
        }
    }

    public void insertToDb(byte[] data) throws Exception {
        // 解析 SegmentObject 并插入到数据库
        SegmentObject segment = SegmentObject.parseFrom(data);
        TransformerUtils.insertSegmentObjectToEvents(databaseService, segment, invalidFields, missingFields); // 插入数据
        spanCounter += segment.getSpansCount(); // 增加计数器

        // 检查是否需要执行批量插入
        long currentTime = System.currentTimeMillis();
        if (spanCounter >= batchSize || (currentTime - lastInsertTime >= batchInterval)) {
            databaseService.getStatement().executeBatch();
            logger.info("Inserted {} spans into events table.", spanCounter);
            spanCounter = 0; // 重置计数器
            lastInsertTime = currentTime; // 更新上次插入时间
        }
    }

}