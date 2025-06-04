package com.o11y;

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
    private long lastInsertTime = System.currentTimeMillis(); // Initialize last insert time
    public static Boolean tableStructureChanged = true;

    /**
     * Constructor for TransformerService using batchConfig map.
     * 
     * @param databaseService The database service instance.
     * @param kafkaService    The Kafka service instance.
     * @param batchConfig     The batch configuration map.
     */
    public TransformerService(DatabaseService databaseService, KafkaService kafkaService,
            Map<String, Integer> batchConfig) {
        this(databaseService, kafkaService, batchConfig.get("size"), batchConfig.get("interval"));
    }

    /**
     * Constructor for TransformerService using batchConfig and
     * segmentOnEventMappings.
     * 
     * @param databaseService        The database service instance.
     * @param kafkaService           The Kafka service instance.
     * @param batchConfig            The batch configuration map.
     * @param segmentOnEventMappings The segment on event mappings.
     */
    public TransformerService(DatabaseService databaseService, KafkaService kafkaService,
            Map<String, Integer> batchConfig, Map<String, Object> segmentOnEventMappings) {
        this(databaseService, kafkaService, batchConfig.get("size"), batchConfig.get("interval"));
    }

    /**
     * Constructor for TransformerService using explicit batch size and interval.
     * 
     * @param databaseService The database service instance.
     * @param kafkaService    The Kafka service instance.
     * @param batchSize       The batch size.
     * @param batchInterval   The batch interval in milliseconds.
     */
    public TransformerService(DatabaseService databaseService, KafkaService kafkaService,
            int batchSize, int batchInterval) {
        this.databaseService = databaseService;
        this.kafkaService = kafkaService;
        this.batchSize = batchSize;
        this.batchInterval = batchInterval;
    }

    /**
     * Main entry point for the TransformerService application.
     * 
     * @param args Command line arguments.
     * @throws Exception if an error occurs during execution.
     */
    public static void main(String[] args) throws Exception {
        // Load configuration file
        Map<String, Object> config = ConfigLoader.loadConfig("application.yaml");

        // Initialize service
        DatabaseService databaseService = new DatabaseService((Map<String, String>) config.get("clickhouse"))
                .initConnection();
        KafkaService kafkaService = new KafkaService((Map<String, Object>) config.get("kafka"));

        TransformerService transformerService = new TransformerService(databaseService, kafkaService,
                (Map<String, Integer>) config.get("batch"));

        // Start a new thread to execute addColumns method every addColumnsInterval
        // milliseconds
        BackgroundTaskManager.startAddColumnsTask(databaseService, missingFields,
                (int) config.get("add_columns_interval"));

        // Call run method to process Kafka messages
        transformerService.run();
    }

    /**
     * Main loop for processing Kafka messages.
     * 
     * @throws Exception if an error occurs while processing messages.
     */
    public void run() throws Exception {
        while (true) {
            try {
                if (tableStructureChanged) {
                    databaseService.buildInsertSQL();
                    tableStructureChanged = false; // Reset the flag
                }
                ConsumerRecords<String, byte[]> records = kafkaService.consumeMessages();
                for (ConsumerRecord<String, byte[]> record : records) {
                    insertToDb(record.value()); // Process each message
                }
            } catch (java.sql.SQLException e) {
                databaseService.buildInsertSQL();
            }
        }
    }

    public void insertToDb(byte[] data) throws Exception {
        // Parse SegmentObject and insert into database
        SegmentObject segment = SegmentObject.parseFrom(data);
        TransformerUtils.insertSegmentObjectToEvents(databaseService, segment, invalidFields, missingFields); // Insert
                                                                                                              // data
        spanCounter += segment.getSpansCount(); // Increase the counter

        // Check if batch insert is needed
        long currentTime = System.currentTimeMillis();
        if (spanCounter >= batchSize || (currentTime - lastInsertTime >= batchInterval)) {
            databaseService.getStatement().executeBatch();
            logger.debug("Inserted {} spans into events table.", spanCounter);
            spanCounter = 0; // Reset the counter
            lastInsertTime = currentTime; // Update last insert time
        }
    }

}