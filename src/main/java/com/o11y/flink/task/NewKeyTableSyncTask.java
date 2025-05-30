package com.o11y.flink.task;

import com.o11y.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 定时任务：定期扫描 new_key 表，将 isCreated=false 的 key 批量添加到 events 表，并将 isCreated 更新为
 * true。
 */
public class NewKeyTableSyncTask {
    private static final Logger LOG = LoggerFactory.getLogger(NewKeyTableSyncTask.class);
    private final DatabaseService databaseService;
    private final long intervalMs;

    public NewKeyTableSyncTask(DatabaseService databaseService, long intervalMs) {
        this.databaseService = databaseService;
        this.intervalMs = intervalMs;
    }

    public void start() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    syncNewKeys();
                } catch (Exception e) {
                    LOG.error("Failed to sync new keys to events table", e);
                }
            }
        }, 0, intervalMs);
        LOG.info("NewKeyTableSyncTask started, interval: {} ms", intervalMs);
    }

    private void syncNewKeys() throws Exception {
        Connection conn = databaseService.getConnection();
        // 1. 查询所有 isCreated=false 的新 key
        String selectSql = "SELECT keyName, keyType FROM new_key WHERE isCreated = 0";
        Set<String> newKeys = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(selectSql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String keyName = rs.getString("keyName");
                String keyType = rs.getString("keyType");
                // 2. 执行 ALTER TABLE 新增字段
                String alterSql = String.format(
                        "ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s Nullable(%s)",
                        databaseService.getSchemaName(),
                        databaseService.getTableName(),
                        keyName,
                        keyType);
                try (PreparedStatement alterPs = conn.prepareStatement(alterSql)) {
                    alterPs.execute();
                    LOG.info("Added column '{}' to events table.", keyName);
                } catch (Exception e) {
                    LOG.error("Failed to add column '{}' to events table: {}", keyName, e.getMessage());
                }
                newKeys.add(keyName);
            }
        }
        // 3. 更新 isCreated=true
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
