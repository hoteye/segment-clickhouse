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
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

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
    private final static String DEFAULT_KEY_TYPE = "String";
    // 新增：记录上次写入 new_key 的时间，避免高频访问
    private long lastNewKeyInsertTime = 0L;
    private static final long NEW_KEY_INSERT_INTERVAL_MS = 10_000L;

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
        try {
            // 1. 正常数据写入（会自动填充 missingFields）
            TransformerUtils.insertSegmentObjectToEvents(
                    databaseService, segment,
                    invalidFields,
                    missingFields);
            LOG.debug("Successfully inserted data into ClickHouse: {}", segment.getTraceId());
            LOG.debug("Invalid fields: {}", invalidFields);
            LOG.debug("Missing fields: {}", missingFields);
            // 2. 将 missingFields 中的新 key 写入 new_key 表（限流）
            long now = System.currentTimeMillis();
            if (!missingFields.isEmpty() && (now - lastNewKeyInsertTime >= NEW_KEY_INSERT_INTERVAL_MS)) {
                for (String key : missingFields) {
                    if (insertNewKeyToClickHouse(key)) {
                        // 如果 key 已经写入 new_key 并且 isCreated 为 true，表示字段已经建立，则重建sql语句
                        databaseService.initConnection();
                    }
                }
                LOG.info("Cached new tag keys to new_key table: {}", missingFields);
                missingFields.clear(); // 避免重复写入
                lastNewKeyInsertTime = now;
            }
            spanCounter += segment.getSpansCount();
            long currentTime = System.currentTimeMillis();
            if (spanCounter >= batchSize || (currentTime - lastInsertTime >= batchInterval)) {
                databaseService.getStatement().executeBatch();
                LOG.info("Inserted {} spans into events table.", spanCounter);
                spanCounter = 0;
                lastInsertTime = currentTime;
            }
        } catch (java.sql.SQLException e) {
            LOG.error("SQLException in invoke, try to re-init database connection");
            databaseService.initConnection();
        }
    }

    /**
     * 写入 new_key 表（keyName, keyType, isCreated, createTime），根据 keyName 自动推断类型
     */
    private Boolean insertNewKeyToClickHouse(String keyName) {
        String keyType = DEFAULT_KEY_TYPE;
        if (keyName.contains("_type_")) {
            String[] parts = keyName.split("_type_");
            if (parts.length == 2) {
                String type = parts[1];
                if (TransformerUtils.isClickhouseSupportedType(type)) {
                    keyType = type;
                }
            }
        }
        try {
            // 先检查 keyName 是否已存在，若存在则返回 isCreated 的值
            String checkSql = "SELECT isCreated FROM new_key WHERE keyName = ?";
            PreparedStatement checkPs = databaseService.getConnection().prepareStatement(checkSql);
            checkPs.setString(1, keyName);
            java.sql.ResultSet rs = checkPs.executeQuery();
            if (rs.next()) {
                boolean isCreated = rs.getBoolean(1);
                rs.close();
                checkPs.close();
                LOG.debug("Key '{}' already exists in new_key table, isCreated={}", keyName, isCreated);
                return isCreated;
            }
            rs.close();
            checkPs.close();
            String sql = "INSERT INTO new_key (keyName, keyType, isCreated, createTime) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = databaseService.getConnection().prepareStatement(sql);
            ps.setString(1, keyName);
            ps.setString(2, keyType);
            ps.setBoolean(3, false);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            ps.close();
            return false;
        } catch (Exception e) {
            LOG.error("Failed to insert new key to new_key table: {}", keyName, e);
            return null;
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
