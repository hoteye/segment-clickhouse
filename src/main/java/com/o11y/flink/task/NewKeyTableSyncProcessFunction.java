package com.o11y.flink.task;

import com.o11y.DatabaseService;
import com.o11y.TransformerUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Flink 原生定时器实现的 new_key 表同步算子。
 * 并发度建议为 1，全局唯一周期性同步。
 */
public class NewKeyTableSyncProcessFunction extends KeyedProcessFunction<String, String, Object> {
    private static final Logger LOG = LoggerFactory.getLogger(NewKeyTableSyncProcessFunction.class);
    private transient DatabaseService databaseService;
    private final String url, schema, table, username, password;
    private final long intervalMs;
    private boolean timerRegistered = false;

    public NewKeyTableSyncProcessFunction(String url, String schema, String table, String username, String password,
            long intervalMs) {
        this.url = url;
        this.schema = schema;
        this.table = table;
        this.username = username;
        this.password = password;
        this.intervalMs = intervalMs;
    }

    @Override
    public void open(Configuration parameters) {
        try {
            this.databaseService = new DatabaseService(url, schema, table, username, password).initConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DatabaseService in open()", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    @Override
    public void processElement(String value, Context ctx, Collector<Object> out) throws Exception {
        LOG.info("processElement called, registering timer");
        if (!timerRegistered) {
            long now = ctx.timerService().currentProcessingTime();
            ctx.timerService().registerProcessingTimeTimer(now);
            timerRegistered = true;
            LOG.info("NewKeyTableSyncProcessFunction timer registered, interval: {} ms", intervalMs);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Object> out) throws Exception {
        try {
            syncNewKeys();
            LOG.info("Synced new keys to events table at {}", timestamp);
        } catch (Exception e) {
            LOG.error("Failed to sync new keys to events table", e);
        }
        // 注册下一个定时器
        ctx.timerService().registerProcessingTimeTimer(timestamp + intervalMs);
    }

    void syncNewKeys() throws Exception {
        Connection conn = databaseService.getConnection();
        String selectSql = "SELECT keyName, keyType FROM new_key WHERE isCreated = 0";
        Set<String> newKeys = new HashSet<>();
        PreparedStatement ps = conn.prepareStatement(selectSql);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String keyName = rs.getString("keyName");
            String keyType = rs.getString("keyType");
            String chType = TransformerUtils.toClickHouseType(keyType);
            String alterSql = String.format(
                    "ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s Nullable(%s)",
                    databaseService.getSchemaName(),
                    databaseService.getTableName(),
                    keyName,
                    chType);
            try (PreparedStatement alterPs = conn.prepareStatement(alterSql)) {
                alterPs.execute();
                LOG.info("Added column '{}' to events table.", keyName);
            } catch (Exception e) {
                LOG.error("Failed to add column '{}' to events table: {}", keyName, e.getMessage());
            }
            newKeys.add(keyName);
        }
        if (!newKeys.isEmpty()) {
            String updateSql = "UPDATE new_key SET isCreated = 1 WHERE keyName = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                for (String key : newKeys) {
                    updatePs.setString(1, key);
                    updatePs.executeUpdate();
                }
            }
            LOG.info("Updated isCreated=true for keys: {}", newKeys);
        }
    }
}
