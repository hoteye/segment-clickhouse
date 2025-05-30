package com.o11y;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.utilities.Tools;

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
    private final String schemaName; // schemaName field
    private final String tableName; // tableName field
    private Connection connection; // Manage database connection
    private PreparedStatement statement; // New PreparedStatement property
    private final List<String> columns = new ArrayList<>(); // Manage table column info

    /**
     * Constructor for DatabaseService using ClickHouse configuration map.
     * 
     * @param clickhouseConfig The ClickHouse configuration map.
     * @throws Exception if initialization fails.
     */
    public DatabaseService(Map<String, String> clickhouseConfig) throws Exception {
        this.clickhouseUrl = clickhouseConfig.get("url");
        this.username = clickhouseConfig.get("username");
        this.password = clickhouseConfig.get("password");
        this.schemaName = clickhouseConfig.get("schema_name");
        this.tableName = clickhouseConfig.get("table_name");
    }

    /**
     * Constructor for DatabaseService using explicit parameters.
     * 
     * @param dbUrl      The database URL.
     * @param schemaName The schema name.
     * @param tableName  The table name.
     * @param username   The username.
     * @param password   The password.
     * @throws Exception if initialization fails.
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
     * Initialize database connection, retry several times then exit if failed
     */
    public DatabaseService initConnection() {
        int retry = 0;
        int maxRetry = 5;
        while (retry < maxRetry) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close(); // Close old connection
                    logger.info("Closed existing database connection.");
                }
                connection = DriverManager.getConnection(clickhouseUrl, username, password);
                closeStatement(); // Close old PreparedStatement
                logger.info("Database connection initialized successfully.");
                break; // Exit loop after successful connection
            } catch (SQLException e) {
                retry++;
                logger.error("Failed to connect to ClickHouse: {}. Retrying in 3s... (attempt {}/{})", e.getMessage(),
                        retry, maxRetry, e);
                Tools.sleep(3000);
            }
        }
        if (retry >= maxRetry) {
            logger.error("Failed to connect to ClickHouse after {} attempts. Giving up.", maxRetry);
            throw new RuntimeException("Failed to connect to ClickHouse after " + maxRetry + " attempts.");
        }
        return this; // Return current instance for chain call
    }

    /**
     * Get database connection
     */
    public Connection getConnection() throws Exception {
        return connection;
    }

    /**
     * Initialize PreparedStatement
     */
    public void initStatement(String sql) throws Exception {
        if (statement != null) {
            statement.close();
        }
        statement = connection.prepareStatement(sql);
        logger.info("PreparedStatement initialized with SQL: {}", sql);
    }

    /**
     * Get PreparedStatement
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
     * Close PreparedStatement
     */
    public void closeStatement() {
        if (statement != null) {
            try {
                statement.close();
                statement = null; // Clear reference for next creation
                logger.info("PreparedStatement closed.");
            } catch (Exception e) {
                logger.error("Failed to close PreparedStatement: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Build INSERT SQL statement
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
     * Dynamically add missing columns, ensure all columns are Nullable
     */
    public void addColumns(ConcurrentSkipListSet<String> missingFields) throws Exception {
        synchronized (missingFields) {
            for (String field : new HashSet<>(missingFields)) { // Avoid ConcurrentModificationException
                if (columns.contains(field)) {
                    logger.info("Column {} already exists, skipping.", field);
                    missingFields.remove(field);
                    continue;
                }

                // Default field type is Nullable(String)
                String columnType = "Nullable(String)";

                // Check if the field ends with _type.xxx
                if (field.contains("_type_")) {
                    String[] parts = field.split("_type_");
                    if (parts.length == 2) {
                        String type = parts[1];
                        if (!TransformerUtils.isClickhouseSupportedType(type)) {
                            continue;
                        }
                        columnType = "Nullable(" + TransformerUtils.toClickHouseType(type) + ")";
                    }
                }

                String alterSQL = String.format("ALTER TABLE %s.%s ADD COLUMN IF NOT EXISTS %s %s",
                        schemaName, tableName, field, columnType);

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(alterSQL);
                    logger.info("Added column: {} with type: {}", field, columnType);
                    columns.add(field); // Update columns management
                    missingFields.remove(field);
                    TransformerService.tableStructureChanged = true; // Mark table structure as changed
                } catch (Exception e) {
                    logger.error("Failed to add column {}: {}", field, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Get the column information of the current table
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * Close database connection
     */
    public void close() {
        try {
            if (statement != null) {
                statement.close();
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            logger.info("Database connection closed.");
        } catch (SQLException e) {
            logger.error("Failed to close database connection: {}", e.getMessage(), e);
        }
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }
}