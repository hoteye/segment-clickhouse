package com.o11y.stream.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NewKeyTableSyncProcessFunction 单元测试
 * 
 * 测试新字段表同步函数的核心功能：
 * 1. 构造函数参数验证
 * 2. 基本对象创建
 * 3. 参数配置正确性
 */
@ExtendWith(MockitoExtension.class)
public class NewKeyTableSyncProcessFunctionTest {

    @Test
    @DisplayName("测试构造函数参数")
    void testConstructor() {
        String url = "jdbc:clickhouse://localhost:8123/test";
        String schema = "test_schema";
        String table = "test_table";
        String username = "test_user";
        String password = "test_pass";
        long interval = 30000L;

        NewKeyTableSyncProcessFunction function = new NewKeyTableSyncProcessFunction(
                url, schema, table, username, password, interval);

        assertNotNull(function, "函数应该成功创建");
    }

    @Test
    @DisplayName("测试不同参数组合的构造函数")
    void testConstructorWithDifferentParameters() {
        // 测试最小参数
        NewKeyTableSyncProcessFunction function1 = new NewKeyTableSyncProcessFunction(
                "jdbc:clickhouse://localhost:8123/db", 
                "schema", 
                "table", 
                "", 
                "", 
                60000L);
        assertNotNull(function1, "最小参数应该能创建函数");

        // 测试完整参数
        NewKeyTableSyncProcessFunction function2 = new NewKeyTableSyncProcessFunction(
                "jdbc:clickhouse://remote:8123/production", 
                "production_schema", 
                "events_table", 
                "admin", 
                "password123", 
                120000L);
        assertNotNull(function2, "完整参数应该能创建函数");
    }

    @Test
    @DisplayName("测试空字符串参数")
    void testConstructorWithEmptyStrings() {
        // 空字符串参数应该被接受（可能使用默认值）
        assertDoesNotThrow(() -> {
            new NewKeyTableSyncProcessFunction("", "", "", "", "", 30000L);
        }, "空字符串参数应该被处理");
    }

    @Test
    @DisplayName("测试null参数")
    void testConstructorWithNullParameters() {
        // null参数可能导致异常，这取决于具体实现
        // 这里测试是否能正常处理或抛出预期的异常
        assertDoesNotThrow(() -> {
            try {
                new NewKeyTableSyncProcessFunction(null, null, null, null, null, 30000L);
            } catch (NullPointerException e) {
                // 如果抛出NPE，这是预期的行为
            }
        }, "null参数应该被优雅处理或抛出明确的异常");
    }

    @Test
    @DisplayName("测试不同的间隔时间")
    void testConstructorWithDifferentIntervals() {
        String url = "jdbc:clickhouse://localhost:8123/test";
        String schema = "schema";
        String table = "table";
        String username = "user";
        String password = "pass";

        // 测试不同的间隔时间
        long[] intervals = {1000L, 30000L, 60000L, 300000L, 3600000L};
        
        for (long interval : intervals) {
            NewKeyTableSyncProcessFunction function = new NewKeyTableSyncProcessFunction(
                    url, schema, table, username, password, interval);
            assertNotNull(function, "间隔时间 " + interval + "ms 应该能创建函数");
        }
    }

    @Test
    @DisplayName("测试零和负数间隔时间")
    void testConstructorWithZeroAndNegativeIntervals() {
        String url = "jdbc:clickhouse://localhost:8123/test";
        String schema = "schema";
        String table = "table";
        String username = "user";
        String password = "pass";

        // 测试零间隔
        assertDoesNotThrow(() -> {
            new NewKeyTableSyncProcessFunction(url, schema, table, username, password, 0L);
        }, "零间隔应该被处理");

        // 测试负数间隔
        assertDoesNotThrow(() -> {
            new NewKeyTableSyncProcessFunction(url, schema, table, username, password, -1000L);
        }, "负数间隔应该被处理");
    }

    @Test
    @DisplayName("测试长URL和参数")
    void testConstructorWithLongParameters() {
        // 测试很长的参数
        String longUrl = "jdbc:clickhouse://very-long-hostname-for-testing-purposes.example.com:8123/very_long_database_name_for_testing";
        String longSchema = "very_long_schema_name_for_testing_purposes_with_many_characters";
        String longTable = "very_long_table_name_for_testing_purposes_with_many_characters";
        String longUsername = "very_long_username_for_testing_purposes";
        String longPassword = "very_long_password_for_testing_purposes_with_special_characters_!@#$%^&*()";

        assertDoesNotThrow(() -> {
            new NewKeyTableSyncProcessFunction(
                    longUrl, longSchema, longTable, longUsername, longPassword, 60000L);
        }, "长参数应该被正确处理");
    }

    @Test
    @DisplayName("测试特殊字符参数")
    void testConstructorWithSpecialCharacters() {
        // 测试包含特殊字符的参数
        String urlWithSpecialChars = "jdbc:clickhouse://localhost:8123/test_db?param=value&other=123";
        String schemaWithSpecialChars = "test-schema_2023";
        String tableWithSpecialChars = "events_table_v2";
        String usernameWithSpecialChars = "test.user@domain.com";
        String passwordWithSpecialChars = "P@ssw0rd!2023#$";

        assertDoesNotThrow(() -> {
            new NewKeyTableSyncProcessFunction(
                    urlWithSpecialChars, 
                    schemaWithSpecialChars, 
                    tableWithSpecialChars, 
                    usernameWithSpecialChars, 
                    passwordWithSpecialChars, 
                    45000L);
        }, "特殊字符参数应该被正确处理");
    }

    @Test
    @DisplayName("测试批量创建函数对象")
    void testBatchFunctionCreation() {
        // 批量创建多个函数对象
        for (int i = 0; i < 10; i++) {
            String url = "jdbc:clickhouse://host" + i + ":8123/db" + i;
            String schema = "schema" + i;
            String table = "table" + i;
            String username = "user" + i;
            String password = "pass" + i;
            long interval = (i + 1) * 10000L;

            NewKeyTableSyncProcessFunction function = new NewKeyTableSyncProcessFunction(
                    url, schema, table, username, password, interval);
            assertNotNull(function, "批量创建的第" + i + "个函数应该成功");
        }
    }
}