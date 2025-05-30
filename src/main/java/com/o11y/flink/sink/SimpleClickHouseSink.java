package com.o11y.flink.sink;

import com.o11y.DatabaseService;
import com.o11y.TransformerUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import segment.v3.Segment.SegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import segment.v3.Segment.KeyStringValuePair;

/**
 * 支持批量写入 ClickHouse 的 Sink
 */
public class SimpleClickHouseSink extends RichSinkFunction<SegmentObject> {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleClickHouseSink.class);
    private transient DatabaseService databaseService;
    private final Map<String, String> clickhouseConfig;
    private final Map<String, Integer> batchConfig;
    static ConcurrentSkipListSet<String> invalidFields = new ConcurrentSkipListSet<>();
    static ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
    private int spanCounter = 0;
    private long lastInsertTime = System.currentTimeMillis();
    private Integer batchSize;
    private Integer batchInterval;
    private final static String DEFAULT_KEY_TYPE = "String"; // 可根据实际类型推断

    public SimpleClickHouseSink(Map<String, String> clickhouseConfig, Map<String, Integer> batchConfig) {
        this.batchConfig = batchConfig;
        this.clickhouseConfig = clickhouseConfig;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        if (databaseService == null) {
            databaseService = new DatabaseService(
                    clickhouseConfig.get("url"),
                    clickhouseConfig.get("schema_name"),
                    clickhouseConfig.get("table_name"),
                    clickhouseConfig.get("username"),
                    clickhouseConfig.get("password")).initConnection();
        }
        this.batchSize = batchConfig.get("size");
        this.batchInterval = batchConfig.get("interval");
    }

    @Override
    public void invoke(SegmentObject segment, Context context) throws Exception {
        // 1. 检查新 tag key
        HashSet<String> newTagKeys = new HashSet<>();
        for (var span : segment.getSpansList()) {
            for (KeyStringValuePair tag : span.getTagsList()) {
                String key = tag.getKey();
                // 这里假设 events 表字段已全部缓存到 invalidFields + missingFields
                if (!invalidFields.contains(key) && !missingFields.contains(key)) {
                    newTagKeys.add(key);
                }
            }
        }
        // 2. 新 key 写入 new_key 表
        if (!newTagKeys.isEmpty()) {
            for (String key : newTagKeys) {
                insertNewKeyToClickHouse(key, DEFAULT_KEY_TYPE);
            }
            LOG.info("Cached new tag keys to new_key table: {}", newTagKeys);
        }
        // 3. 正常数据写入
        TransformerUtils.insertSegmentObjectToEvents(
                databaseService, segment,
                invalidFields,
                missingFields);
        LOG.debug("Successfully inserted data into ClickHouse: {}", segment.getTraceId());
        LOG.debug("Invalid fields: {}", invalidFields);
        LOG.debug("Missing fields: {}", missingFields);
        spanCounter += segment.getSpansCount();
        long currentTime = System.currentTimeMillis();
        if (spanCounter >= batchSize || (currentTime - lastInsertTime >= batchInterval)) {
            databaseService.getStatement().executeBatch();
            LOG.debug("Inserted {} spans into events table.", spanCounter);
            spanCounter = 0;
            lastInsertTime = currentTime;
        }
    }

    /**
     * 写入 new_key 表（keyName, keyType, isCreated, createTime）
     */
    private void insertNewKeyToClickHouse(String keyName, String keyType) {
        try {
            String sql = "INSERT INTO new_key (keyName, keyType, isCreated, createTime) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = databaseService.getConnection().prepareStatement(sql);
            ps.setString(1, keyName);
            ps.setString(2, keyType);
            ps.setBoolean(3, false);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            LOG.error("Failed to insert new key to new_key table: {}", keyName, e);
        }
    }

    @Override
    public void close() throws Exception {
        if (spanCounter > 0) {
            databaseService.getStatement().executeBatch();
            LOG.debug("Batch inserted {} records into ClickHouse on close.", spanCounter);
        }
        super.close();
    }

    /**
     * 仅用于测试注入 mock DatabaseService
     */
    void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }
}
