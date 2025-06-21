package com.o11y.flink.util;

import com.o11y.shared.util.OperatorParamLoader;
import com.o11y.infrastructure.database.DatabaseService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatorParamLoaderTest {
    private DatabaseService dbService;
    private Connection connection;
    private PreparedStatement pstmt;
    private ResultSet resultSet;
    private KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() throws Exception {
        dbService = mock(DatabaseService.class);
        connection = mock(Connection.class);
        pstmt = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        producer = mock(KafkaProducer.class);

        when(dbService.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(pstmt);
        when(pstmt.executeQuery()).thenReturn(resultSet);
    }

    @Test
    void testLoadParams() throws Exception {
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("paramKey")).thenReturn("k1", "k2");
        when(resultSet.getString("paramValue")).thenReturn("v1", "v2");

        Map<String, String> params = OperatorParamLoader.loadParams(dbService, "TestOp");
        assertEquals(2, params.size());
        assertEquals("v1", params.get("k1"));
        assertEquals("v2", params.get("k2"));
    }

    @Test
    void testLoadParamList() throws Exception {
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("paramKey")).thenReturn("k1", "k1");
        when(resultSet.getString("paramValue")).thenReturn("v1", "v2");

        Map<String, List<String>> params = OperatorParamLoader.loadParamList(dbService, "TestOp");
        assertEquals(1, params.size());
        assertTrue(params.containsKey("k1"));
        assertEquals(Arrays.asList("v1", "v2"), params.get("k1"));
    }

    @Test
    void testNewParams() throws Exception {
        Map<String, List<String>> params = new HashMap<>();
        params.put("k1", Arrays.asList("v1", "v2"));
        params.put("k2", Collections.singletonList("v3"));

        OperatorParamLoader.newParams(dbService, "TestOp", params, producer);

        verify(connection, atLeastOnce()).prepareStatement(anyString());
        verify(pstmt, atLeastOnce()).setString(anyInt(), anyString());
        verify(pstmt, atLeastOnce()).executeUpdate();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertEquals("flink-operator-param-update", record.topic());
        assertEquals("TestOp", record.key());
        assertTrue(record.value().contains("new"));
    }

    @Test
    void testUpdateParams() throws Exception {
        Map<String, List<String>> params = new HashMap<>();
        params.put("k1", Arrays.asList("v1", "v2"));

        OperatorParamLoader.updateParams(dbService, "TestOp", params, producer);

        verify(connection, atLeastOnce()).prepareStatement(anyString());
        verify(pstmt, atLeastOnce()).setString(anyInt(), anyString());
        verify(pstmt, atLeastOnce()).executeUpdate();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertEquals("flink-operator-param-update", record.topic());
        assertEquals("TestOp", record.key());
        assertTrue(record.value().contains("update"));
    }
}
