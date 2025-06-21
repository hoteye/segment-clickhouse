package com.o11y.stream.sink;

import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.shared.util.TransformerUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import segment.v3.Segment.SegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * ClickHouse 批量写入 Sink 实现。
 * 
 * <p>
 * 负责将 Flink 流中的 SegmentObject 数据批量写入到 ClickHouse 数据库。
 * 支持按批次大小和时间间隔触发写入，并提供动态表结构管理能力。
 * 
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 * <li>批量数据写入优化，支持按大小和时间双重触发</li>
 * <li>动态字段检测和表结构扩展</li>
 * <li>连接池管理和异常恢复</li>
 * <li>性能监控和日志记录</li>
 * <li>数据完整性和一致性保证</li>
 * </ul>
 * 
 * <p>
 * <strong>批量策略：</strong>
 * <ul>
 * <li>批次大小：达到指定数量自动提交</li>
 * <li>时间间隔：超过指定时间自动提交</li>
 * <li>字段同步：定期同步新字段到表结构</li>
 * </ul>
 * 
 * <p>
 * <strong>性能优化：</strong>
 * <ul>
 * <li>PreparedStatement 重用</li>
 * <li>批量提交减少网络往返</li>
 * <li>异步字段同步避免阻塞主流程</li>
 * </ul>
 * 
 * @see DatabaseService ClickHouse 数据库服务
 * @see TransformerUtils 数据转换工具
 * @see SegmentObject Skywalking 数据模型
 * @author DDD Architecture Team
 * @since 1.0.0
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
    // 新增：记录上次写入 new_key 的时间，避免高频访问，初始化为当前时间
    private long lastNewKeyInsertTime = System.currentTimeMillis();
    private static long interval = 60_000L;

    public SimpleClickHouseSink(Map<String, String> clickhouseConfig, Map<String, Integer> batchConfig) {
        this.batchConfig = batchConfig;
        this.clickhouseConfig = clickhouseConfig;
    }

    /**
     * 初始化 ClickHouse 数据库连接和批量参数。
     * 
     * @param parameters Flink 配置参数
     * @throws Exception 初始化异常
     */
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

    /**
     * 批量写入 SegmentObject 数据到 ClickHouse。
     * 
     * @param segmentObject 源数据对象
     * @param context       Flink sink 上下文
     * @throws Exception 写入异常
     */
    @Override
    public void invoke(SegmentObject segmentObject, Context context) throws Exception {
        try {
            // 1. 正常数据写入（会自动填充 missingFields）
            TransformerUtils.insertSegmentObjectToEvents(
                    databaseService, segmentObject,
                    invalidFields,
                    missingFields);
            LOG.debug("segmentId={}", segmentObject.getTraceSegmentId());
            LOG.debug("Successfully inserted data into ClickHouse: {}", segmentObject.getTraceId());
            LOG.debug("Invalid fields: {}", invalidFields);
            LOG.debug("Missing fields: {}", missingFields);
            // 2. 将 missingFields 中的新 key 写入 new_key 表（限流）
            long now = System.currentTimeMillis();
            interval = interval + new Random().nextInt(3_000); // 增加随机延迟，避免高并发时频繁写入
            if (!missingFields.isEmpty() && (now - lastNewKeyInsertTime >= interval)) {
                lastNewKeyInsertTime = now;
                for (String key : missingFields) {
                    if (insertNewKeyToClickHouse(key)) {
                        // 如果 key 已经写入 new_key 并且 isCreated 为 true，表示字段已经建立，则重建sql语句
                        databaseService.initConnection();
                        LOG.info("Key '{}' already exists in new_key table, re-initializing database connection.", key);
                    }
                }
                LOG.info("Cached new tag keys to new_key table: {}", missingFields);
                missingFields.clear(); // 避免重复写入
            }
            spanCounter += segmentObject.getSpansCount();
            long currentTime = System.currentTimeMillis();
            if (spanCounter >= batchSize || (currentTime - lastInsertTime >= batchInterval)) {
                databaseService.getStatement().executeBatch();
                LOG.debug("Inserted {} spans into events table.", spanCounter);
                spanCounter = 0;
                lastInsertTime = currentTime;
            }
        } catch (java.sql.SQLException e) {
            LOG.error("SQLException in invoke, try to re-init database connection");
            databaseService.initConnection();
        }
    }

    /**
     * 写入 new_key 表（keyName, keyType, isCreated, createTime），根据 keyName 自动推断类型。
     * 增加详细的时间戳日志来分析并发插入情况。
     * 
     * @param keyName 新 key 名称
     * @return 是否已创建
     */
    private Boolean insertNewKeyToClickHouse(String keyName) {
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        LOG.info("🔍 [THREAD:{}] [START:{}ms] Starting insertNewKeyToClickHouse for key: {}",
                threadName, startTime, keyName);

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
            long checkStartTime = System.currentTimeMillis();
            LOG.info("🔍 [THREAD:{}] [CHECK_START:{}ms] Checking if key exists: {}",
                    threadName, checkStartTime, keyName);

            // 先检查 keyName 是否已存在，若存在则返回 isCreated 的值
            String checkSql = "SELECT isCreated FROM new_key WHERE keyName = ?";
            PreparedStatement checkPs = databaseService.getConnection().prepareStatement(checkSql);
            checkPs.setString(1, keyName);
            java.sql.ResultSet rs = checkPs.executeQuery();

            long checkEndTime = System.currentTimeMillis();

            if (rs.next()) {
                boolean isCreated = rs.getBoolean(1);
                rs.close();
                checkPs.close();

                LOG.info("✅ [THREAD:{}] [CHECK_END:{}ms] [DURATION:{}ms] Key '{}' already exists, isCreated={}",
                        threadName, checkEndTime, (checkEndTime - checkStartTime), keyName, isCreated);
                return isCreated;
            }
            rs.close();
            checkPs.close();

            LOG.info("🆕 [THREAD:{}] [CHECK_END:{}ms] [DURATION:{}ms] Key '{}' does not exist, proceeding to insert",
                    threadName, checkEndTime, (checkEndTime - checkStartTime), keyName);

            long insertStartTime = System.currentTimeMillis();
            LOG.info("💾 [THREAD:{}] [INSERT_START:{}ms] Inserting new key: {} with type: {}",
                    threadName, insertStartTime, keyName, keyType);

            String sql = "INSERT INTO new_key (keyName, keyType, isCreated, createTime) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = databaseService.getConnection().prepareStatement(sql);
            ps.setString(1, keyName);
            ps.setString(2, keyType);
            ps.setBoolean(3, false);
            Timestamp insertTimestamp = new Timestamp(System.currentTimeMillis());
            ps.setTimestamp(4, insertTimestamp);

            int affectedRows = ps.executeUpdate();
            ps.close();

            long insertEndTime = System.currentTimeMillis();
            LOG.info(
                    "✅ [THREAD:{}] [INSERT_END:{}ms] [DURATION:{}ms] Successfully inserted key '{}', affected rows: {}, DB timestamp: {}",
                    threadName, insertEndTime, (insertEndTime - insertStartTime), keyName, affectedRows,
                    insertTimestamp);

            long totalDuration = insertEndTime - startTime;
            LOG.info("🏁 [THREAD:{}] [TOTAL_DURATION:{}ms] Completed insertNewKeyToClickHouse for key: {}",
                    threadName, totalDuration, keyName);

            return false;

        } catch (Exception e) {
            long errorTime = System.currentTimeMillis();
            LOG.error("❌ [THREAD:{}] [ERROR:{}ms] Failed to insert new key '{}' to new_key table",
                    threadName, errorTime, keyName, e);
            return null;
        }
    }

    /**
     * 关闭 sink，批量写入剩余数据。
     * 
     * @throws Exception 关闭异常
     */
    @Override
    public void close() throws Exception {
        if (spanCounter > 0) {
            databaseService.getStatement().executeBatch();
            LOG.debug("Batch inserted {} records into ClickHouse on close.", spanCounter);
        }
        super.close();
    }

    /**
     * 仅用于测试注入 mock DatabaseService。
     * 
     * @param databaseService mock 数据库服务
     */
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }
}
