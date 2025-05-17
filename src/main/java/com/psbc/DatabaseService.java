package com.psbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.psbc.utilities.Tools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private final String clickhouseUrl;
    private final String username;
    private final String password;
    private final String schemaName; // schemaName 成员变量
    private final String tableName; // tableName 成员变量
    private Connection connection; // 管理数据库连接
    private PreparedStatement statement; // 新增 PreparedStatement 属性
    private final List<String> columns = new ArrayList<>(); // 管理表的列信息

    /**
     * 构造函数，接收 ClickHouse 配置
     * 
     * @throws Exception
     */
    public DatabaseService(Map<String, String> clickhouseConfig) throws Exception {
        this.clickhouseUrl = clickhouseConfig.get("url");
        this.username = clickhouseConfig.get("username");
        this.password = clickhouseConfig.get("password");
        this.schemaName = clickhouseConfig.get("schema_name");
        this.tableName = clickhouseConfig.get("table_name");
        initConnection();
    }

    public DatabaseService(String dbUrl, String schemaName, String tableName, String username, String password)
            throws Exception {
        this.clickhouseUrl = dbUrl;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.username = username;
        this.password = password;
        initConnection();
    }

    /**
     * 初始化数据库连接
     */
    public void initConnection() {
        while (true) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close(); // 关闭旧连接
                    logger.info("Closed existing database connection.");
                }
                connection = DriverManager.getConnection(clickhouseUrl, username, password);
                closeStatement(); // 关闭旧的 PreparedStatement
                logger.info("Database connection initialized successfully.");
                break; // 连接成功后退出循环
            } catch (SQLException e) {
                logger.error("Failed to connect to ClickHouse: {}. Retrying in 1000ms...", e.getMessage(), e);
                Tools.sleep(3000);
            }
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws Exception {
        return connection;
    }

    /**
     * 初始化 PreparedStatement
     */
    public void initStatement(String sql) throws Exception {
        if (statement != null) {
            statement.close();
        }
        statement = connection.prepareStatement(sql);
        logger.info("PreparedStatement initialized with SQL: {}", sql);
    }

    /**
     * 获取 PreparedStatement
     * 
     * @throws Exception
     */
    public PreparedStatement getStatement() throws Exception {
        if (statement == null) {
            initStatement(buildInsertSQL());
        }
        return statement;
    }

    /**
     * 关闭 PreparedStatement
     */
    public void closeStatement() {
        if (statement != null) {
            try {
                statement.close();
                statement = null; // 清空引用以便下次重新创建
                logger.info("PreparedStatement closed.");
            } catch (Exception e) {
                logger.error("Failed to close PreparedStatement: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 构建 INSERT SQL 语句
     */
    public String buildInsertSQL() throws Exception {
        ResultSet columnsResultSet = connection.getMetaData().getColumns(null, schemaName, tableName, null);

        StringBuilder fields = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        this.columns.clear();

        while (columnsResultSet.next()) {
            String columnName = columnsResultSet.getString("COLUMN_NAME");
            this.columns.add(columnName);

            if (fields.length() > 0) {
                fields.append(", ");
                placeholders.append(", ");
            }
            fields.append(columnName);
            placeholders.append("?");
        }

        columnsResultSet.close();
        String sql = String.format("INSERT INTO %s.%s (%s) VALUES (%s)", schemaName, tableName, fields, placeholders);
        initStatement(sql);
        return sql;
    }

    /**
     * 动态添加缺失字段，并确保所有字段类型为 Nullable
     */
    public void addColumns(ConcurrentSkipListSet<String> missingFields) throws Exception {
        synchronized (missingFields) {
            for (String field : new HashSet<>(missingFields)) { // 避免 ConcurrentModificationException
                if (columns.contains(field)) {
                    logger.info("Column {} already exists, skipping.", field);
                    missingFields.remove(field);
                    continue;
                }

                // 默认字段类型为 Nullable(String)
                String columnType = "Nullable(String)";

                // 检查字段是否以 _type.xxx 结尾
                if (field.contains("_type_")) {
                    String[] parts = field.split("_type_");
                    if (parts.length == 2) {
                        String type = parts[1];
                        // 首字母大写
                        String typeCap = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
                        if (!TransformerUtils.isClickhouseNumericType(typeCap)) {
                            continue; // Skip this field
                        }
                        columnType = "Nullable(" + typeCap + ")"; // 包装字段类型为 Nullable
                    }
                }

                String alterSQL = String.format("ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s %s",
                        schemaName, tableName, field, columnType);

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(alterSQL);
                    logger.info("Added column: {} with type: {}", field, columnType);
                    columns.add(field); // 更新 columns 管理
                    missingFields.remove(field);
                    TransformerService.tableStructureChanged = true; // 标记表结构已变化
                } catch (Exception e) {
                    logger.error("Failed to add column {}: {}", field, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 获取当前表的列信息
     */
    public List<String> getColumns() {
        return columns;
    }

}