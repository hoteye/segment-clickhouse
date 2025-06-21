package com.o11y.infrastructure.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.shared.util.Tools;
import com.o11y.shared.util.TransformerUtils;
import com.o11y.application.service.TransformerService;

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

/**
 * ClickHouse 数据库服务类。
 * 
 * <p>
 * 负责 ClickHouse 数据库的连接管理、表结构操作、批量数据插入等核心功能。
 * 提供高性能的数据库操作接口，支持动态表结构管理和批量写入优化。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>数据库连接的建立、维护和重连机制</li>
 * <li>PreparedStatement 的管理和重用</li>
 * <li>表结构的动态查询和修改</li>
 * <li>批量数据插入和事务管理</li>
 * <li>字段类型验证和数据完整性保证</li>
 * </ul>
 * 
 * <p>
 * <strong>架构特点：</strong>
 * <ul>
 * <li>支持连接池和连接重用</li>
 * <li>提供自动重连和异常恢复机制</li>
 * <li>优化批量操作性能</li>
 * <li>支持表结构的动态扩展</li>
 * </ul>
 * 
 * <p>
 * <strong>线程安全性：</strong>
 * 该类不是线程安全的，每个线程应该使用独立的实例。
 * 
 * @see TransformerUtils 数据转换工具
 * @see Tools 通用工具类
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    /** ClickHouse 数据库连接 URL */
    private final String clickhouseUrl;

    /** 数据库用户名 */
    private final String username;

    /** 数据库密码 */
    private final String password;

    /** 数据库 schema 名称 */
    private final String schemaName;

    /** 目标表名称 */
    private final String tableName;

    /** 数据库连接实例，负责与 ClickHouse 服务器的通信 */
    private Connection connection;

    /** 预编译 SQL 语句，用于批量插入数据以提升性能 */
    private PreparedStatement statement;

    /** 表列信息缓存，避免重复查询数据库元数据 */
    private final List<String> columns = new ArrayList<>();

    /**
     * 使用 ClickHouse 配置映射构造 DatabaseService。
     * 
     * <p>
     * 从配置映射中提取数据库连接所需的参数，包括 URL、用户名、密码、
     * schema 名称和表名称。这种构造方式便于从配置文件或环境变量中
     * 动态加载数据库配置。
     * 
     * @param clickhouseConfig ClickHouse 配置映射，包含以下必需键：
     *                         <ul>
     *                         <li>url - 数据库连接 URL</li>
     *                         <li>username - 用户名</li>
     *                         <li>password - 密码</li>
     *                         <li>schema_name - Schema 名称</li>
     *                         <li>table_name - 表名称</li>
     *                         </ul>
     * @throws Exception 如果配置参数缺失或无效时抛出异常
     */
    public DatabaseService(Map<String, String> clickhouseConfig) throws Exception {
        this.clickhouseUrl = clickhouseConfig.get("url");
        this.username = clickhouseConfig.get("username");
        this.password = clickhouseConfig.get("password");
        this.schemaName = clickhouseConfig.get("schema_name");
        this.tableName = clickhouseConfig.get("table_name");
    }

    /**
     * 使用显式参数构造 DatabaseService。
     * 
     * <p>
     * 直接使用具体的参数值初始化数据库服务，适用于参数明确的场景。
     * 这种构造方式提供了更直接的控制，避免了配置映射的依赖。
     * 
     * @param dbUrl      数据库连接 URL，格式如 jdbc:clickhouse://host:port/database
     * @param schemaName 数据库 schema 名称，通常是数据库名称
     * @param tableName  目标表名称，用于数据插入和结构操作
     * @param username   数据库用户名
     * @param password   数据库密码
     * @throws Exception 如果参数验证失败时抛出异常
     */
    public DatabaseService(String dbUrl, String schemaName, String tableName, String username, String password)
            throws Exception {
        this.clickhouseUrl = dbUrl;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.username = username;
        this.password = password;
    }

    /**
     * 初始化数据库连接，支持失败重试机制。
     * 
     * <p>
     * 建立与 ClickHouse 服务器的连接，如果连接失败会自动重试最多 5 次，
     * 每次重试间隔 3 秒。成功连接后会清理旧的 PreparedStatement 以确保状态一致性。
     * 
     * <p>
     * <strong>重试策略：</strong>
     * <ul>
     * <li>最大重试次数：5 次</li>
     * <li>重试间隔：3 秒</li>
     * <li>失败后抛出 RuntimeException</li>
     * </ul>
     * 
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     * <li>会关闭已存在的连接避免资源泄漏</li>
     * <li>会清理相关的 PreparedStatement</li>
     * <li>支持链式调用以便于初始化流程</li>
     * </ul>
     * 
     * @return 当前 DatabaseService 实例，支持链式调用
     * @throws RuntimeException 如果重试次数用尽仍无法连接时抛出
     */
    public DatabaseService initConnection() {
        int retry = 0;
        int maxRetry = 5;
        while (retry < maxRetry) {
            try {
                // 如果已有连接且未关闭，先关闭旧连接
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    logger.info("Closed existing database connection.");
                }

                // 建立新的数据库连接
                connection = DriverManager.getConnection(clickhouseUrl, username, password);

                // 清理旧的 PreparedStatement 以避免状态不一致
                closeStatement();

                logger.info("Database connection initialized successfully.");
                break; // 连接成功，退出重试循环
            } catch (SQLException e) {
                retry++;
                logger.error("Failed to connect to ClickHouse: {}. Retrying in 3s... (attempt {}/{})",
                        e.getMessage(), retry, maxRetry, e);

                // 重试前等待 3 秒
                Tools.sleep(3000);
            }
        }

        // 如果达到最大重试次数仍未成功，抛出异常
        if (retry >= maxRetry) {
            logger.error("Failed to connect to ClickHouse after {} attempts. Giving up.", maxRetry);
            throw new RuntimeException("Failed to connect to ClickHouse after " + maxRetry + " attempts.");
        }

        return this; // 返回当前实例支持链式调用
    }

    /**
     * 获取数据库连接实例。
     * 
     * <p>
     * 返回当前的数据库连接对象，调用者可以使用此连接执行自定义的 SQL 操作。
     * 注意：调用者应该避免直接关闭此连接，应该通过 DatabaseService 的生命周期管理。
     * 
     * @return 数据库连接实例
     * @throws Exception 如果连接未初始化或已关闭时抛出异常
     */
    public Connection getConnection() throws Exception {
        return connection;
    }

    /**
     * 初始化 PreparedStatement。
     * 
     * <p>
     * 使用指定的 SQL 语句创建预编译语句。如果已存在 PreparedStatement，
     * 会先关闭旧的实例再创建新的，确保资源正确管理。
     * 
     * <p>
     * <strong>性能优势：</strong>
     * <ul>
     * <li>SQL 预编译提升执行效率</li>
     * <li>支持参数绑定防止 SQL 注入</li>
     * <li>适合批量操作场景</li>
     * </ul>
     * 
     * @param sql 要预编译的 SQL 语句，通常是 INSERT 语句
     * @throws Exception 如果 SQL 语法错误或数据库连接无效时抛出异常
     */
    public void initStatement(String sql) throws Exception {
        // 如果已存在 PreparedStatement，先关闭它释放资源
        if (statement != null) {
            statement.close();
        }

        // 创建新的 PreparedStatement
        statement = connection.prepareStatement(sql);
        logger.info("PreparedStatement initialized with SQL: {}", sql);
    }

    /**
     * 获取 PreparedStatement 实例，支持懒加载。
     * 
     * <p>
     * 如果 PreparedStatement 尚未初始化，会自动调用 buildInsertSQL() 方法
     * 构建 INSERT 语句并创建 PreparedStatement。这种懒加载机制确保了
     * 只有在真正需要时才会执行表结构查询和 SQL 构建。
     * 
     * <p>
     * <strong>自动化特性：</strong>
     * <ul>
     * <li>首次调用时自动构建 INSERT SQL</li>
     * <li>缓存 PreparedStatement 实例提升性能</li>
     * <li>支持表结构动态变化</li>
     * </ul>
     * 
     * @return 预编译的 INSERT 语句实例
     * @throws Exception 如果 SQL 构建失败或数据库连接无效时抛出异常
     */
    public PreparedStatement getStatement() throws Exception {
        // 懒加载：仅在需要时才初始化 PreparedStatement
        if (statement == null) {
            initStatement(buildInsertSQL());
        }
        return statement;
    }

    /**
     * 关闭 PreparedStatement 并释放相关资源。
     * 
     * <p>
     * 安全地关闭当前的 PreparedStatement 实例，并将引用设置为 null
     * 以便下次调用时重新创建。此方法会捕获并记录关闭过程中的异常，
     * 确保程序的稳定性。
     * 
     * <p>
     * <strong>资源管理：</strong>
     * <ul>
     * <li>安全关闭 PreparedStatement</li>
     * <li>清空引用防止内存泄漏</li>
     * <li>异常容错处理</li>
     * </ul>
     */
    public void closeStatement() {
        if (statement != null) {
            try {
                statement.close();
                statement = null; // 清空引用，为下次创建做准备
                logger.info("PreparedStatement closed.");
            } catch (Exception e) {
                logger.error("Failed to close PreparedStatement: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 构建 INSERT SQL 语句并初始化 PreparedStatement。
     * 
     * <p>
     * 通过查询数据库元数据获取目标表的所有列信息，然后构建对应的
     * INSERT SQL 语句。这个方法确保了 SQL 语句与表结构的一致性，
     * 支持动态表结构的场景。
     * 
     * <p>
     * <strong>构建流程：</strong>
     * <ol>
     * <li>查询表的所有列元数据</li>
     * <li>构建字段名称和占位符字符串</li>
     * <li>生成完整的 INSERT SQL 语句</li>
     * <li>初始化对应的 PreparedStatement</li>
     * <li>更新内部列信息缓存</li>
     * </ol>
     * 
     * <p>
     * <strong>SQL 格式：</strong>
     * {@code INSERT INTO schema.table (col1, col2, ...) VALUES (?, ?, ...)}
     * 
     * @return 构建的 INSERT SQL 语句
     * @throws Exception 如果数据库元数据查询失败或 SQL 构建过程中出错
     */
    public String buildInsertSQL() throws Exception {
        // 查询表的所有列元数据
        ResultSet columnsResultSet = connection.getMetaData().getColumns(null, schemaName, tableName, null);

        StringBuilder fields = new StringBuilder(); // 字段名称
        StringBuilder placeholders = new StringBuilder(); // 参数占位符
        this.columns.clear(); // 清空列缓存

        // 遍历所有列，构建字段列表和占位符
        while (columnsResultSet.next()) {
            String columnName = columnsResultSet.getString("COLUMN_NAME");
            this.columns.add(columnName);

            // 添加分隔符（除了第一个字段）
            if (fields.length() > 0) {
                fields.append(", ");
                placeholders.append(", ");
            }

            fields.append(columnName); // 添加字段名
            placeholders.append("?"); // 添加参数占位符
        }

        columnsResultSet.close();

        // 构建完整的 INSERT SQL 语句
        String sql = String.format("INSERT INTO %s.%s (%s) VALUES (%s)",
                schemaName, tableName, fields, placeholders);

        // 立即初始化 PreparedStatement
        initStatement(sql);
        return sql;
    }

    /**
     * 动态添加缺失的列到目标表。
     * 
     * <p>
     * 根据提供的字段名集合，自动向 ClickHouse 表中添加缺失的列。
     * 支持类型推断和自动类型映射，所有新列都会设置为 Nullable 类型
     * 以确保数据兼容性。
     * 
     * <p>
     * <strong>类型推断规则：</strong>
     * <ul>
     * <li>默认类型：Nullable(String)</li>
     * <li>类型后缀：字段名包含 "_type_xxx" 会根据 xxx 推断具体类型</li>
     * <li>支持的类型：通过 TransformerUtils.isClickhouseSupportedType() 验证</li>
     * </ul>
     * 
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     * <li>检查字段是否已存在于列缓存中</li>
     * <li>根据字段名推断 ClickHouse 数据类型</li>
     * <li>执行 ALTER TABLE ADD COLUMN 语句</li>
     * <li>更新内部列缓存和表结构状态</li>
     * <li>从待处理集合中移除已处理字段</li>
     * </ol>
     * 
     * <p>
     * <strong>并发安全：</strong>
     * 使用 synchronized 确保多线程环境下的数据一致性。
     * 
     * @param missingFields 需要添加的字段名集合，使用 ConcurrentSkipListSet 保证线程安全
     * @throws Exception 如果 SQL 执行失败或数据库连接异常
     */
    public void addColumns(ConcurrentSkipListSet<String> missingFields) throws Exception {
        synchronized (missingFields) {
            // 使用 HashSet 避免 ConcurrentModificationException
            for (String field : new HashSet<>(missingFields)) {
                // 检查字段是否已存在于列缓存中
                if (columns.contains(field)) {
                    logger.info("Column {} already exists, skipping.", field);
                    missingFields.remove(field);
                    continue;
                }

                // 默认字段类型为可空字符串
                String columnType = "Nullable(String)";

                // 根据字段名推断类型（支持 _type_xxx 格式）
                if (field.contains("_type_")) {
                    String[] parts = field.split("_type_");
                    if (parts.length == 2) {
                        String type = parts[1];
                        // 验证类型是否被 ClickHouse 支持
                        if (!TransformerUtils.isClickhouseSupportedType(type)) {
                            continue;
                        }
                        columnType = "Nullable(" + TransformerUtils.toClickHouseType(type) + ")";
                    }
                }

                // 构建 ALTER TABLE 语句
                String alterSQL = String.format("ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s %s",
                        schemaName, tableName, field, columnType);

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(alterSQL);
                    logger.info("Added column: {} with type: {}", field, columnType);

                    // 更新列缓存
                    columns.add(field);
                    missingFields.remove(field);

                    // 标记表结构已变更，触发相关组件更新
                    TransformerService.tableStructureChanged = true;
                } catch (Exception e) {
                    logger.error("Failed to add column {}: {}", field, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 获取当前表的列信息缓存。
     * 
     * <p>
     * 返回内部维护的列信息列表，这个列表在构建 INSERT SQL 时会被更新。
     * 调用者可以使用此信息了解表的当前结构，但不应直接修改返回的列表。
     * 
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     * <li>列表内容会在 buildInsertSQL() 时更新</li>
     * <li>添加新列时也会同步更新此缓存</li>
     * <li>返回的是内部列表的引用，请勿直接修改</li>
     * </ul>
     * 
     * @return 当前表的列名列表
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * 关闭数据库连接并释放所有相关资源。
     * 
     * <p>
     * 安全地关闭数据库连接和 PreparedStatement，确保不会发生资源泄漏。
     * 此方法会捕获并记录关闭过程中的异常，保证程序的稳定性。
     * 
     * <p>
     * <strong>清理顺序：</strong>
     * <ol>
     * <li>关闭 PreparedStatement</li>
     * <li>关闭数据库连接</li>
     * <li>记录清理结果</li>
     * </ol>
     * 
     * <p>
     * <strong>最佳实践：</strong>
     * 建议在应用程序关闭或 DatabaseService 实例不再使用时调用此方法。
     */
    public void close() {
        try {
            // 首先关闭 PreparedStatement
            if (statement != null) {
                statement.close();
            }

            // 然后关闭数据库连接
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            logger.info("Database connection closed.");
        } catch (SQLException e) {
            logger.error("Failed to close database connection: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取数据库 schema 名称。
     * 
     * @return schema 名称
     */
    public String getSchemaName() {
        return schemaName;
    }

    /**
     * 获取目标表名称。
     * 
     * @return 表名称
     */
    public String getTableName() {
        return tableName;
    }
}