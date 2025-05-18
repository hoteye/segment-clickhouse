package com.psbc;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import java.sql.*;
import java.util.concurrent.ConcurrentSkipListSet;

class DatabaseServiceTest {
    @Test
    void testAddColumns_addStringAndNumeric() throws Exception {
        // mock Connection/Statement/ResultSet
        Connection conn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        Mockito.when(conn.createStatement()).thenReturn(stmt);
        Mockito.when(stmt.execute(Mockito.anyString())).thenReturn(true);

        DatabaseService db = Mockito.spy(new DatabaseService("jdbc:mock", "test_schema", "test_table", "u", "p"));
        // 直接设置 db.connection 字段为 mock
        java.lang.reflect.Field connField = DatabaseService.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(db, conn);
        // columns 初始为空
        db.getColumns().clear();

        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        missingFields.add("tag_foo");
        missingFields.add("tag_bar_type_Int64");
        missingFields.add("tag_baz_type_ABC"); // 非法类型

        // 只注册一次静态 mock
        try (org.mockito.MockedStatic<TransformerUtils> mocked = Mockito.mockStatic(TransformerUtils.class)) {
            mocked.when(() -> TransformerUtils.isClickhouseNumericType("Int64")).thenReturn(true);
            mocked.when(() -> TransformerUtils.isClickhouseNumericType("ABC")).thenReturn(false);

            db.addColumns(missingFields);

            // tag_foo 应被加为 Nullable(String)
            Mockito.verify(stmt).execute(Mockito.contains("tag_foo Nullable(String)"));
            // tag_bar_type_Int64 应被加为 Nullable(Int64)
            Mockito.verify(stmt).execute(Mockito.contains("tag_bar_type_Int64 Nullable(Int64)"));
            // tag_baz_type_ABC 不应被加
            Mockito.verify(stmt, Mockito.never()).execute(Mockito.contains("tag_baz_type_ABC"));

            // columns 应包含前两者
            Assertions.assertTrue(db.getColumns().contains("tag_foo"));
            Assertions.assertTrue(db.getColumns().contains("tag_bar_type_Int64"));
            // missingFields 应只剩非法类型
            Assertions.assertTrue(missingFields.contains("tag_baz_type_ABC"));
            Assertions.assertFalse(missingFields.contains("tag_foo"));
            Assertions.assertFalse(missingFields.contains("tag_bar_type_Int64"));
        }
    }
}
