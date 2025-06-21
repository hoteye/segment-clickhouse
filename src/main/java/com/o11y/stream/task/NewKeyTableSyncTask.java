package com.o11y.stream.task;

import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.shared.util.TransformerUtils;
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
 * 动态字段同步任务类。
 * 
 * <p>
 * 负责定时扫描 new_key 表，将标记为 isCreated=false 的新字段批量添加到 events 表结构中，
 * 确保 ClickHouse 表能够动态扩展以适应新的数据字段。这是实现表结构动态管理的核心组件。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>定时扫描 new_key 表中未处理的新字段</li>
 * <li>批量执行 ALTER TABLE 语句添加新列</li>
 * <li>更新字段状态为已创建（isCreated=true）</li>
 * <li>异常处理和日志记录</li>
 * </ul>
 * 
 * <p>
 * <strong>使用场景：</strong>
 * 当 Segment 数据中包含新的 tag 或 log 字段时，会先记录到 new_key 表，
 * 此任务负责将这些新字段同步到主表结构中，实现无停机的表结构扩展。
 * 
 * @see DatabaseService 数据库操作服务
 * @see TransformerUtils 数据转换工具
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class NewKeyTableSyncTask {
    private static final Logger LOG = LoggerFactory.getLogger(NewKeyTableSyncTask.class);
    private final DatabaseService databaseService;
    private final long intervalMs;

    /**
     * 构造函数，初始化数据库服务和同步间隔。
     * 
     * @param databaseService ClickHouse 数据库服务实例，用于执行表结构修改操作
     * @param intervalMs      同步任务执行间隔（毫秒），建议设置为 30-60 秒以平衡性能和实时性
     */
    public NewKeyTableSyncTask(DatabaseService databaseService, long intervalMs) {
        this.databaseService = databaseService;
        this.intervalMs = intervalMs;
    }

    /**
     * 启动定时同步任务。
     * 
     * <p>
     * 创建一个守护线程定时器，按指定间隔执行新字段同步操作。
     * 任务会持续运行直到应用程序关闭。
     * 
     * <p>
     * <strong>注意：</strong>使用守护线程确保不会阻止 JVM 正常退出。
     * 
     * @throws RuntimeException 如果定时器创建失败
     */
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

    /**
     * 执行新 key 同步逻辑，将未创建的新 key 字段同步到 ClickHouse 主表。
     * 
     * @throws Exception SQL 执行异常
     */
    // 将 syncNewKeys 方法从 private 改为 package-private，便于集成测试反射调用
    void syncNewKeys() throws Exception {
        Connection conn = databaseService.getConnection();
        // 1. 查询所有 isCreated=false 的新 key
        String selectSql = "SELECT keyName, keyType FROM new_key WHERE isCreated = 0";
        Set<String> newKeys = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(selectSql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String keyName = rs.getString("keyName");
                String keyType = rs.getString("keyType");
                // 类型映射
                String chType = TransformerUtils.toClickHouseType(keyType);
                // 2. 执行 ALTER TABLE 新增字段
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
        }
        // 3. 更新 isCreated=true 后续主业务流 SimpleClickHouseSink invoke
        // insertSegmentObjectToEvents insertNewKeyToClickHouse() ，
        // 根据 insertNewKeyToClickHouse() 返回值判断是否已创建。如果没有已经创建则重新初始化sql语句
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
