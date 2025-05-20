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
        // Directly set db.connection field to mock
        java.lang.reflect.Field connField = DatabaseService.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(db, conn);
        // columns initially empty
        db.getColumns().clear();

        ConcurrentSkipListSet<String> missingFields = new ConcurrentSkipListSet<>();
        missingFields.add("tag_foo");
        missingFields.add("tag_bar_type_Int64");
        missingFields.add("tag_baz_type_ABC"); // Illegal type

        // Only register static mock once
        try (org.mockito.MockedStatic<TransformerUtils> mocked = Mockito.mockStatic(TransformerUtils.class)) {
            mocked.when(() -> TransformerUtils.isClickhouseSupportedType("Int64")).thenReturn(true);
            mocked.when(() -> TransformerUtils.isClickhouseSupportedType("ABC")).thenReturn(false);
            // mock toClickHouseType 返回正确类型
            mocked.when(() -> TransformerUtils.toClickHouseType("Int64")).thenReturn("Int64");
            mocked.when(() -> TransformerUtils.toClickHouseType("ABC")).thenReturn(null);

            db.addColumns(missingFields);

            // tag_foo should be added as Nullable(String)
            Mockito.verify(stmt).execute(Mockito.contains("tag_foo Nullable(String)"));
            // tag_bar_type_Int64 should be added as Nullable(Int64)
            Mockito.verify(stmt).execute(Mockito.contains("tag_bar_type_Int64 Nullable(Int64)"));
            // tag_baz_type_ABC should not be added
            Mockito.verify(stmt, Mockito.never()).execute(Mockito.contains("tag_baz_type_ABC"));

            // columns should contain the first two
            Assertions.assertTrue(db.getColumns().contains("tag_foo"));
            Assertions.assertTrue(db.getColumns().contains("tag_bar_type_Int64"));
            // missingFields should only contain illegal type
            Assertions.assertTrue(missingFields.contains("tag_baz_type_ABC"));
            Assertions.assertFalse(missingFields.contains("tag_foo"));
            Assertions.assertFalse(missingFields.contains("tag_bar_type_Int64"));
        }
    }
}
