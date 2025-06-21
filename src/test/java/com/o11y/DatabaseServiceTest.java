package com.o11y;

import com.o11y.infrastructure.database.DatabaseService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import java.sql.*;

class DatabaseServiceTest {

    @Test
    void testInitStatementAndGetStatement() throws Exception {
        // mock Connection/PreparedStatement
        Connection conn = Mockito.mock(Connection.class);
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        Mockito.when(conn.prepareStatement(Mockito.anyString())).thenReturn(ps);

        DatabaseService db = Mockito.spy(new DatabaseService("jdbc:mock", "test_schema", "test_table", "u", "p"));
        java.lang.reflect.Field connField = DatabaseService.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(db, conn);

        // test initStatement
        db.initStatement("INSERT INTO test_schema.test_table (a) VALUES (?)");
        Mockito.verify(conn).prepareStatement(Mockito.contains("INSERT INTO test_schema.test_table"));

        // test getStatement（statement已存在）
        PreparedStatement s1 = db.getStatement();
        Assertions.assertNotNull(s1);
        // test getStatement（statement为null时自动初始化）
        java.lang.reflect.Field stmtField = DatabaseService.class.getDeclaredField("statement");
        stmtField.setAccessible(true);
        stmtField.set(db, null);
        Mockito.doReturn(ps).when(conn).prepareStatement(Mockito.anyString());
        Mockito.doReturn("INSERT INTO test_schema.test_table (a) VALUES (?)").when(db).buildInsertSQL();
        PreparedStatement s2 = db.getStatement();
        Assertions.assertNotNull(s2);
    }

    @Test
    void testBuildInsertSQL() throws Exception {
        // mock Connection/ResultSet
        Connection conn = Mockito.mock(Connection.class);
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);
        Mockito.when(conn.prepareStatement(Mockito.anyString())).thenReturn(ps);
        Mockito.when(conn.getMetaData()).thenReturn(Mockito.mock(DatabaseMetaData.class));
        DatabaseMetaData meta = conn.getMetaData();
        Mockito.when(meta.getColumns(null, "test_schema", "test_table", null)).thenReturn(rs);
        // mock 2 columns
        Mockito.when(rs.next()).thenReturn(true, true, false);
        Mockito.when(rs.getString("COLUMN_NAME")).thenReturn("col1", "col2");

        DatabaseService db = Mockito.spy(new DatabaseService("jdbc:mock", "test_schema", "test_table", "u", "p"));
        java.lang.reflect.Field connField = DatabaseService.class.getDeclaredField("connection");
        connField.setAccessible(true);
        connField.set(db, conn);

        String sql = db.buildInsertSQL();
        Assertions.assertTrue(sql.contains("INSERT INTO test_schema.test_table (col1, col2) VALUES (?, ?)") ||
                sql.contains("INSERT INTO test_schema.test_table (col2, col1) VALUES (?, ?)"));
        Mockito.verify(conn).prepareStatement(Mockito.anyString());
        Mockito.verify(rs, Mockito.times(1)).close();
    }
}
