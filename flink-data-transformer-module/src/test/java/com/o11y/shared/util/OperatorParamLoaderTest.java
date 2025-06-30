package com.o11y.shared.util;

import com.o11y.infrastructure.database.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OperatorParamLoader单元测试
 * 测试参数加载和热更新功能
 */
public class OperatorParamLoaderTest {

    @Mock
    private DatabaseService mockDatabaseService;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // 设置mock行为
        when(mockDatabaseService.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
    }

    @Test
    @DisplayName("测试加载单值参数")
    void testLoadParams() throws Exception {
        // 设置ResultSet mock返回数据
        when(mockResultSet.next())
                .thenReturn(true) // 第一行
                .thenReturn(true) // 第二行
                .thenReturn(false); // 结束

        when(mockResultSet.getString("paramKey"))
                .thenReturn("key1")
                .thenReturn("key2");

        when(mockResultSet.getString("paramValue"))
                .thenReturn("value1")
                .thenReturn("value2");

        // 执行测试
        Map<String, String> result = OperatorParamLoader.loadParams(mockDatabaseService, "TestOperator");

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertEquals(2, result.size(), "应该加载2个参数");
        assertEquals("value1", result.get("key1"), "key1的值应该是value1");
        assertEquals("value2", result.get("key2"), "key2的值应该是value2");

        // 验证方法调用
        verify(mockDatabaseService).getConnection();
        verify(mockConnection).prepareStatement(anyString());
        verify(mockPreparedStatement).setString(1, "TestOperator");
        verify(mockPreparedStatement).executeQuery();
    }

    @Test
    @DisplayName("测试加载多值参数列表")
    void testLoadParamList() throws Exception {
        // 设置ResultSet mock返回数据（同一个key有多个值）
        when(mockResultSet.next())
                .thenReturn(true) // 第一行
                .thenReturn(true) // 第二行
                .thenReturn(true) // 第三行
                .thenReturn(false); // 结束

        when(mockResultSet.getString("paramKey"))
                .thenReturn("key1")
                .thenReturn("key1") // 同一个key
                .thenReturn("key2");

        when(mockResultSet.getString("paramValue"))
                .thenReturn("value1")
                .thenReturn("value2") // key1的第二个值
                .thenReturn("value3");

        // 执行测试
        Map<String, List<String>> result = OperatorParamLoader.loadParamList(mockDatabaseService, "TestOperator");

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertEquals(2, result.size(), "应该有2个不同的key");

        List<String> key1Values = result.get("key1");
        assertNotNull(key1Values, "key1的值列表不应为空");
        assertEquals(2, key1Values.size(), "key1应该有2个值");
        assertTrue(key1Values.contains("value1"), "key1应该包含value1");
        assertTrue(key1Values.contains("value2"), "key1应该包含value2");

        List<String> key2Values = result.get("key2");
        assertNotNull(key2Values, "key2的值列表不应为空");
        assertEquals(1, key2Values.size(), "key2应该有1个值");
        assertTrue(key2Values.contains("value3"), "key2应该包含value3");
    }

    @Test
    @DisplayName("测试加载空结果")
    void testLoadEmptyParams() throws Exception {
        // 设置ResultSet mock返回空结果
        when(mockResultSet.next()).thenReturn(false);

        // 执行测试
        Map<String, String> result = OperatorParamLoader.loadParams(mockDatabaseService, "NonExistentOperator");

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertTrue(result.isEmpty(), "结果应该是空的");
        assertEquals(0, result.size(), "结果大小应该为0");
    }

    @Test
    @DisplayName("测试数据库异常处理")
    void testDatabaseException() throws Exception {
        // 设置mock抛出异常
        when(mockDatabaseService.getConnection()).thenThrow(new RuntimeException("数据库连接失败"));

        // 执行测试
        Map<String, String> result = OperatorParamLoader.loadParams(mockDatabaseService, "TestOperator");

        // 验证异常处理
        assertNotNull(result, "即使发生异常，结果也不应为空");
        assertTrue(result.isEmpty(), "发生异常时，结果应该是空的");
    }

    @Test
    @DisplayName("测试SQL预处理语句参数设置")
    void testSqlParameterSetting() throws Exception {
        // 设置基本的mock行为
        when(mockResultSet.next()).thenReturn(false);

        // 执行测试
        OperatorParamLoader.loadParams(mockDatabaseService, "MyTestOperator");

        // 验证SQL参数设置
        verify(mockPreparedStatement).setString(1, "MyTestOperator");
    }

    @Test
    @DisplayName("测试参数键值的null处理")
    void testNullKeyValueHandling() throws Exception {
        // 设置ResultSet mock返回包含null的数据
        when(mockResultSet.next())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        when(mockResultSet.getString("paramKey"))
                .thenReturn("validKey")
                .thenReturn(null); // null key

        when(mockResultSet.getString("paramValue"))
                .thenReturn("validValue")
                .thenReturn("valueWithNullKey");

        // 执行测试
        Map<String, String> result = OperatorParamLoader.loadParams(mockDatabaseService, "TestOperator");

        // 验证结果（null key应该被处理）
        assertNotNull(result, "结果不应为空");
        assertTrue(result.containsKey("validKey"), "应该包含有效的key");
        assertEquals("validValue", result.get("validKey"), "有效key的值应该正确");

        // null key的处理取决于实现，这里主要确保不会抛出异常
    }

    @Test
    @DisplayName("测试相同参数键的覆盖行为")
    void testDuplicateKeyOverwrite() throws Exception {
        // 设置ResultSet mock返回重复的key
        when(mockResultSet.next())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        when(mockResultSet.getString("paramKey"))
                .thenReturn("duplicateKey")
                .thenReturn("duplicateKey"); // 重复的key

        when(mockResultSet.getString("paramValue"))
                .thenReturn("firstValue")
                .thenReturn("secondValue"); // 后面的值

        // 执行测试
        Map<String, String> result = OperatorParamLoader.loadParams(mockDatabaseService, "TestOperator");

        // 验证结果（后面的值应该覆盖前面的值）
        assertNotNull(result, "结果不应为空");
        assertEquals(1, result.size(), "应该只有一个key");
        assertEquals("secondValue", result.get("duplicateKey"), "后面的值应该覆盖前面的值");
    }

    @Test
    @DisplayName("测试参数列表的顺序保持")
    void testParamListOrder() throws Exception {
        // 设置ResultSet mock返回有序的数据
        when(mockResultSet.next())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        when(mockResultSet.getString("paramKey"))
                .thenReturn("orderedKey")
                .thenReturn("orderedKey")
                .thenReturn("orderedKey");

        when(mockResultSet.getString("paramValue"))
                .thenReturn("first")
                .thenReturn("second")
                .thenReturn("third");

        // 执行测试
        Map<String, List<String>> result = OperatorParamLoader.loadParamList(mockDatabaseService, "TestOperator");

        // 验证结果
        assertNotNull(result, "结果不应为空");
        List<String> values = result.get("orderedKey");
        assertNotNull(values, "值列表不应为空");
        assertEquals(3, values.size(), "应该有3个值");

        // 注意：由于使用了ArrayList，顺序应该被保持
        assertEquals("first", values.get(0), "第一个值应该是first");
        assertEquals("second", values.get(1), "第二个值应该是second");
        assertEquals("third", values.get(2), "第三个值应该是third");
    }

    @Test
    @DisplayName("测试资源清理")
    void testResourceCleanup() throws Exception {
        // 设置基本的mock行为
        when(mockResultSet.next()).thenReturn(false);

        // 执行测试
        OperatorParamLoader.loadParams(mockDatabaseService, "TestOperator");

        // 验证资源被正确关闭
        verify(mockResultSet).close();
        verify(mockPreparedStatement, atLeastOnce()).close();
    }

    @Test
    @DisplayName("测试特殊字符处理")
    void testSpecialCharacterHandling() throws Exception {
        // 设置ResultSet mock返回包含特殊字符的数据
        when(mockResultSet.next())
                .thenReturn(true)
                .thenReturn(false);

        when(mockResultSet.getString("paramKey"))
                .thenReturn("key_with-special.chars");

        when(mockResultSet.getString("paramValue"))
                .thenReturn("value with spaces & special chars: @#$%");

        // 执行测试
        Map<String, String> result = OperatorParamLoader.loadParams(mockDatabaseService, "TestOperator");

        // 验证结果
        assertNotNull(result, "结果不应为空");
        assertEquals(1, result.size(), "应该有一个参数");
        String key = "key_with-special.chars";
        assertTrue(result.containsKey(key), "应该包含特殊字符的key");
        assertEquals("value with spaces & special chars: @#$%", result.get(key), "特殊字符的值应该正确保存");
    }
}