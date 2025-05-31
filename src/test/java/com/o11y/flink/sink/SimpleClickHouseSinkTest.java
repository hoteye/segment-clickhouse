package com.o11y.flink.sink;

import org.apache.flink.configuration.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.o11y.DatabaseService;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class SimpleClickHouseSinkTest {
    private Map<String, String> clickhouseConfig;
    private Map<String, Integer> batchConfig;
    private DatabaseService mockDbService;
    private PreparedStatement mockStmt;

    @BeforeEach
    public void setup() throws Exception {
        clickhouseConfig = new HashMap<>();
        clickhouseConfig.put("url", "jdbc:clickhouse://localhost:8123");
        clickhouseConfig.put("schema_name", "default");
        clickhouseConfig.put("table_name", "events");
        clickhouseConfig.put("username", "default");
        clickhouseConfig.put("password", "");
        batchConfig = new HashMap<>();
        batchConfig.put("size", 2);
        batchConfig.put("interval", 10000);
        mockDbService = mock(DatabaseService.class);
        mockStmt = mock(PreparedStatement.class);
        when(mockDbService.getStatement()).thenReturn(mockStmt);
    }

    @Test
    public void testOpenAndClose() throws Exception {
        SimpleClickHouseSink sink = Mockito.spy(new SimpleClickHouseSink(clickhouseConfig, batchConfig));
        sink.setDatabaseService(mockDbService);
        sink.open(new Configuration());
        sink.close();
        verify(mockDbService, atLeast(0)).getStatement();
    }

}
