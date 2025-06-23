package com.o11y.flink.task;

import com.o11y.stream.task.NewKeyTableSyncTask;
import com.o11y.shared.util.ConfigurationUtils;
import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.shared.util.SegmentObjectMapper;
import org.junit.jupiter.api.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 集成测试：验证 NewKeyTableSyncTask 能正确将各种类型字段同步到 ClickHouse 中
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NewKeyTableSyncTaskIT {
    private static DatabaseService dbService;
    private static Connection conn;
    private static final String TABLE = "events_test";

    @BeforeAll
    static void setup() throws Exception {
        Map<String, Object> config = ConfigurationUtils.loadConfig("application.yaml");
        @SuppressWarnings("unchecked")
        Map<String, String> clickhouseConfig = (Map<String, String>) config.get("clickhouse");
        String url = clickhouseConfig.get("url");
        String user = clickhouseConfig.get("username");
        String password = clickhouseConfig.get("password");
        conn = DriverManager.getConnection(url, user, password);
        dbService = new DatabaseService(url, "default", TABLE, user, password).initConnection();
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + TABLE);
            st.execute("CREATE TABLE " + TABLE + " (id UInt32) ENGINE = MergeTree() ORDER BY id");
            st.execute("DROP TABLE IF EXISTS new_key");
            // 修正：new_key 表使用 MergeTree 引擎，isCreated 字段类型改为 UInt8，兼容 ClickHouse
            st.execute(
                    "CREATE TABLE new_key (keyName String, keyType String, isCreated UInt8, createTime DateTime) ENGINE = MergeTree() ORDER BY keyName");
        }
    }

    @Test
    @Order(1)
    void testSyncAllSupportedTypes() throws Exception {
        // 插入所有支持类型的 key（包括 decimal 类型），统一字段名风格 col_xxx
        for (String type : SegmentObjectMapper.getClickhouseSupportedTypes()) {
            String key = "col_"
                    + type.toLowerCase().replaceAll("[()]+", "_").replaceAll("_+", "_").replaceAll("_$", "");
            try (Statement st = conn.createStatement()) {
                st.execute(String.format("INSERT INTO new_key (keyName, keyType, isCreated) VALUES ('%s', '%s', 0)",
                        key, type));
            }
        }
        // 额外插入小写/别名类型，测试类型映射
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO new_key (keyName, keyType, isCreated) VALUES ('col_uint16_alias', 'uInt16', 0)");
            st.execute("INSERT INTO new_key (keyName, keyType, isCreated) VALUES ('col_int64_alias', 'int_64', 0)");
        }
        // 执行同步
        NewKeyTableSyncTask task = new NewKeyTableSyncTask(dbService, 10000L);
        task.getClass().getDeclaredMethod("syncNewKeys").setAccessible(true);
        task.getClass().getDeclaredMethod("syncNewKeys").invoke(task);
        // 校验表结构，只校验非 decimal 类型
        Set<String> columns = new HashSet<>();
        try (ResultSet rs = conn.createStatement().executeQuery("DESCRIBE TABLE " + TABLE)) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        // 校验表结构，统一字段名风格 col_xxx
        for (String type : SegmentObjectMapper.getClickhouseSupportedTypes()) {
            String key = "col_"
                    + type.toLowerCase().replaceAll("[()]+", "_").replaceAll("_+", "_").replaceAll("_$", "");
            Assertions.assertTrue(columns.contains(key), "缺少字段: " + key);
        }
        Assertions.assertTrue(columns.contains("col_uint16_alias"), "别名类型未被正确映射");
        Assertions.assertTrue(columns.contains("col_int64_alias"), "别名类型未被正确映射");
    }

    @AfterAll
    static void cleanup() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("TRUNCATE TABLE " + TABLE);
            st.execute("TRUNCATE TABLE new_key");
        }
        conn.close();
    }
}
