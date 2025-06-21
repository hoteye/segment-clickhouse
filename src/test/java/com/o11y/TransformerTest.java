package com.o11y;

import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.application.service.TransformerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SpanLayer;
import segment.v3.Segment.SpanObject;
import segment.v3.Segment.SegmentReference;
import segment.v3.Segment.SpanType;
import segment.v3.Segment.KeyStringValuePair;
import segment.v3.Segment.Log;
import segment.v3.Segment.RefType;

import java.sql.Statement;
import java.util.TimeZone;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class TransformerTest {
        private DatabaseService databaseService;
        private TransformerService transformerService;

        @BeforeEach
        void setUp() throws Exception {
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

                // Initialize real database connection
                databaseService = new DatabaseService("jdbc:clickhouse://192.168.100.6:8123/default", "default",
                                "events_test",
                                "root", "123456").initConnection();

                transformerService = new TransformerService(databaseService, null, 1, 1000);

                try (Statement stmt = databaseService.getConnection().createStatement()) {
                        // Drop table if exists
                        stmt.execute("DROP TABLE IF EXISTS events_test");

                        stmt.execute("CREATE TABLE IF NOT EXISTS  events_test (\r\n" + //
                                        "    trace_id String,               -- Globally unique Trace ID\r\n" + //
                                        "    trace_segment_id String,       -- Current Segment ID\r\n" + //
                                        "    service Nullable(String),                -- Service name\r\n" + //
                                        "    service_instance Nullable(String),       -- Service instance name\r\n" + //
                                        "    is_size_limited Nullable(UInt8),         -- Whether size is limited (0 or 1)\r\n"
                                        + //
                                        "    span_id Nullable(Int32),                 -- Span ID\r\n" + //
                                        "    parent_span_id Nullable(Int32),          -- Parent Span ID\r\n" + //
                                        "    start_time DateTime64(3),      -- Span start time, accurate to milliseconds\r\n"
                                        + //
                                        "    end_time DateTime64(3),        -- Span end time, accurate to milliseconds\r\n"
                                        + //
                                        "    operation_name Nullable(String),         -- Operation name\r\n" + //
                                        "    peer Nullable(String),                   -- Remote address\r\n" + //
                                        "    span_type Nullable(String),              -- Span type (Entry, Exit, Local)\r\n"
                                        + //
                                        "    span_layer Nullable(String),             -- Span layer (Http, Database, RPCFramework, etc.)\r\n"
                                        + //
                                        "    component_id Nullable(Int32),            -- Component ID\r\n" + //
                                        "    is_error Nullable(UInt8),                -- Whether it is an error Span (0 or 1)\r\n"
                                        + //
                                        "    skip_analysis Nullable(UInt8),           -- Whether to skip analysis (0 or 1)\r\n"
                                        + //
                                        "    refs_ref_type Nullable(String),          -- Reference type (CrossProcess, CrossThread)\r\n"
                                        + //
                                        "    refs_trace_id Nullable(String),          -- Referenced Trace ID\r\n" + //
                                        "    refs_parent_trace_segment_id Nullable(String), -- Parent Segment ID\r\n" + //
                                        "    refs_parent_span_id Nullable(Int32),     -- Parent Span ID\r\n" + //
                                        "    refs_parent_service Nullable(String),    -- Parent service name\r\n" + //
                                        "    refs_parent_service_instance Nullable(String), -- Parent service instance name\r\n"
                                        + //
                                        "    refs_parent_endpoint Nullable(String),   -- Parent endpoint name\r\n" + //
                                        "    refs_network_address_used_at_peer Nullable(String), -- Network address\r\n"
                                        // 新增日期类型字段
                                        + "    tag_test_type_date Date,\r\n"
                                        + "    tag_test_type_date32 Date32,\r\n"
                                        + "    tag_test_type_datetime DateTime,\r\n"
                                        + "    tag_test_type_datetime32 DateTime32,\r\n"
                                        + "    tag_test_type_datetime64 DateTime64(3)\r\n"
                                        // ...existing code...
                                        + ",\r\n" + //
                                        "    tag_status_code Nullable(String),\r\n" + //
                                        "    log_stack Nullable(String),\r\n" + //
                                        "    tag_Available_Memory Nullable(String),\r\n" + //
                                        "    tag_http_status_code Nullable(String),\r\n" + //
                                        "    tag_http_url Nullable(String),\r\n" + //
                                        "    tag_Processor_Name Nullable(String),\r\n" + //
                                        "    log_forward_url Nullable(String),\r\n" + //
                                        "    tag_http_method Nullable(String),\r\n" + //
                                        "    tag_Total_Memory Nullable(String),\r\n" + //
                                        "    log_event Nullable(String),\r\n" + //
                                        "    log_message Nullable(String),\r\n" + //
                                        "    tag_url Nullable(String),\r\n" + //
                                        "    log_error_kind Nullable(String),\r\n" + //
                                        "    tag_thread_current_user_time_type_Int64 Nullable(Int64),\r\n" + //
                                        "    tag_error_test_type_Int64 Nullable(Int64),\r\n" + //
                                        "    tag_test_Float_type_Float64 Nullable(Float64),\r\n" + //
                                        ") ENGINE = MergeTree()\r\n" + //
                                        "ORDER BY (start_time, trace_id);\r\n" + //
                                        "");
                }
        }

        @Test
        void testInsertToDb() throws Exception {
                // 固定测试用例中的日期，保证断言与插入一致
                String randomTraceId = "trace-" + Math.random();
                String randomTraceSegmentId = "segment-" + Math.random();
                String randomService = "service-" + Math.random();
                String randomServiceInstance = "instance-" + Math.random();
                boolean randomIsSizeLimited = Math.random() > 0.5;

                int randomSpanId = (int) (Math.random() * 1000);
                int randomParentSpanId = (int) (Math.random() * 1000);
                long randomStartTime = System.currentTimeMillis();
                long randomEndTime = randomStartTime + (long) (Math.random() * 10000);
                String randomOperationName = "operation-" + Math.random();
                String randomPeer = "peer-" + Math.random();
                SpanType randomSpanType = SpanType.values()[(int) (Math.random() * (SpanType.values().length - 1))];
                SpanLayer randomSpanLayer = SpanLayer.values()[(int) (Math.random() * (SpanLayer.values().length - 1))];
                int randomComponentId = (int) (Math.random() * 100);
                boolean randomIsError = Math.random() > 0.5;
                boolean randomSkipAnalysis = Math.random() > 0.5;

                RefType randomRefType = RefType.CrossThread;
                String randomRefTraceId = "ref-trace-" + Math.random();
                String randomRefParentSegmentId = "ref-segment-" + Math.random();
                int randomRefParentSpanId = (int) (Math.random() * 1000);
                String randomRefParentService = "ref-service-" + Math.random();
                String randomRefParentServiceInstance = "ref-instance-" + Math.random();
                String randomRefParentEndpoint = "ref-endpoint-" + Math.random();
                String randomRefNetworkAddressUsedAtPeer = "ref-peer-address-" + Math.random();
                // 固定日期
                java.time.LocalDate randomDate = java.time.LocalDate.of(2025, 5, 17);
                java.time.LocalDate randomDate32 = java.time.LocalDate.of(2025, 5, 18);
                java.time.LocalDateTime randomDateTime = java.time.LocalDateTime.of(2025, 5, 19, 12, 34, 56);
                java.time.LocalDateTime randomDateTime32 = java.time.LocalDateTime.of(2025, 5, 20, 8, 0, 0);
                java.time.LocalDateTime randomDateTime64 = java.time.LocalDateTime.of(2025, 5, 21, 12, 0, 0, 0);

                long randomThreadCurrentUserTime = (long) (Math.random() * 1_000_000);
                long errorTest = (long) (Math.random() * 1_000_000);
                Double randomTestFloat = Math.random() * 10444444000104444000.0;

                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd HH:mm:ss");
                java.time.format.DateTimeFormatter dtfMs = java.time.format.DateTimeFormatter
                                .ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId(randomTraceId)
                                .setTraceSegmentId(randomTraceSegmentId)
                                .setService(randomService)
                                .setServiceInstance(randomServiceInstance)
                                .setIsSizeLimited(randomIsSizeLimited ? true : false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(randomSpanId)
                                                .setParentSpanId(randomParentSpanId)
                                                .setStartTime(randomStartTime)
                                                .setEndTime(randomEndTime)
                                                .setOperationName(randomOperationName)
                                                .setPeer(randomPeer)
                                                .setSpanType(randomSpanType)
                                                .setSpanLayer(randomSpanLayer)
                                                .setComponentId(randomComponentId)
                                                .setIsError(randomIsError)
                                                .setSkipAnalysis(randomSkipAnalysis)
                                                .addRefs(SegmentReference.newBuilder()
                                                                .setRefType(randomRefType)
                                                                .setTraceId(randomRefTraceId)
                                                                .setParentTraceSegmentId(randomRefParentSegmentId)
                                                                .setParentSpanId(randomRefParentSpanId)
                                                                .setParentService(randomRefParentService)
                                                                .setParentServiceInstance(
                                                                                randomRefParentServiceInstance)
                                                                .setParentEndpoint(randomRefParentEndpoint)
                                                                .setNetworkAddressUsedAtPeer(
                                                                                randomRefNetworkAddressUsedAtPeer)
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("Processor_Name")
                                                                .setValue("Processor_Name-fixed-1")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("http_method")
                                                                .setValue("http_method-fixed-2")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("thread_current_user_time_type_Int64")
                                                                .setValue(String.valueOf(randomThreadCurrentUserTime))
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("error_test_type_Int64")
                                                                .setValue(String.valueOf(errorTest) + "error")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_Float_type_Float64")
                                                                .setValue(String.valueOf(randomTestFloat))
                                                                .build())
                                                // 新增日期类型 tag，全部格式化为 ClickHouse 可识别格式
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_date")
                                                                .setValue(randomDate.toString())
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_date32")
                                                                .setValue(randomDate32.toString())
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime")
                                                                .setValue(randomDateTime.format(dtf))
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime32")
                                                                .setValue(randomDateTime32.format(dtf))
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime64")
                                                                .setValue(randomDateTime64.format(dtfMs))
                                                                .build())
                                                .addLogs(Log.newBuilder()
                                                                .setTime(System.currentTimeMillis())
                                                                .addData(KeyStringValuePair.newBuilder()
                                                                                .setKey("forward_url")
                                                                                .setValue("forward_url-fixed-1")
                                                                                .build())
                                                                .addData(KeyStringValuePair.newBuilder()
                                                                                .setKey("event")
                                                                                .setValue("log-event-fixed-2")
                                                                                .build())
                                                                .build())
                                                .build())
                                .build();

                transformerService.insertToDb(segment.toByteArray());

                // Verify if the insertion was successful and compare values
                try (var stmt = databaseService.getConnection()
                                .prepareStatement("SELECT * FROM events_test WHERE trace_id = ?")) {
                        stmt.setString(1, randomTraceId);
                        var resultSet = stmt.executeQuery();

                        if (resultSet.next()) {
                                // Compare fields of SegmentObject
                                assertEquals(randomTraceId, resultSet.getString("trace_id"));
                                assertEquals(randomTraceSegmentId, resultSet.getString("trace_segment_id"));
                                assertEquals(randomService, resultSet.getString("service"));
                                assertEquals(randomServiceInstance, resultSet.getString("service_instance"));
                                assertEquals(randomIsSizeLimited ? 1 : 0, resultSet.getInt("is_size_limited"));

                                // Compare fields of SpanObject
                                assertEquals(randomSpanId, resultSet.getInt("span_id"));
                                assertEquals(randomParentSpanId, resultSet.getInt("parent_span_id"));
                                assertEquals(randomStartTime, resultSet.getTimestamp("start_time").getTime());
                                assertEquals(randomEndTime, resultSet.getTimestamp("end_time").getTime());
                                assertEquals(randomOperationName, resultSet.getString("operation_name"));
                                assertEquals(randomPeer, resultSet.getString("peer"));
                                assertEquals(randomSpanType.name(), resultSet.getString("span_type"));
                                assertEquals(randomSpanLayer.name(), resultSet.getString("span_layer"));
                                assertEquals(randomComponentId, resultSet.getInt("component_id"));
                                assertEquals(randomIsError ? 1 : 0, resultSet.getInt("is_error"));
                                assertEquals(randomSkipAnalysis ? 1 : 0, resultSet.getInt("skip_analysis"));

                                // Compare fields of SegmentReference
                                assertEquals(randomRefType.toString(), resultSet.getString("refs_ref_type"));
                                assertEquals(randomRefTraceId, resultSet.getString("refs_trace_id"));
                                assertEquals(randomRefParentSegmentId,
                                                resultSet.getString("refs_parent_trace_segment_id"));
                                assertEquals(randomRefParentSpanId, resultSet.getInt("refs_parent_span_id"));
                                assertEquals(randomRefParentService, resultSet.getString("refs_parent_service"));
                                assertEquals(randomRefParentServiceInstance,
                                                resultSet.getString("refs_parent_service_instance"));
                                assertEquals(randomRefParentEndpoint, resultSet.getString("refs_parent_endpoint"));
                                assertEquals(randomRefNetworkAddressUsedAtPeer,
                                                resultSet.getString("refs_network_address_used_at_peer"));

                                // Compare fields of Tags
                                assertEquals("Processor_Name-fixed-1", resultSet.getString("tag_Processor_Name"));
                                assertEquals("http_method-fixed-2", resultSet.getString("tag_http_method"));
                                assertEquals("log-event-fixed-2", resultSet.getString("log_event"));
                                assertEquals("forward_url-fixed-1", resultSet.getString("log_forward_url"));

                                // 校验日期类型字段
                                assertEquals(randomDate.toString(), resultSet.getDate("tag_test_type_date").toString());
                                assertEquals(randomDate32.toString(),
                                                resultSet.getDate("tag_test_type_date32").toString());
                                assertEquals(randomDateTime.toLocalDate().toString(),
                                                resultSet.getTimestamp("tag_test_type_datetime").toLocalDateTime()
                                                                .toLocalDate().toString());
                                assertEquals(randomDateTime32.toLocalDate().toString(),
                                                resultSet.getTimestamp("tag_test_type_datetime32").toLocalDateTime()
                                                                .toLocalDate().toString());
                                assertEquals(randomDateTime64.toLocalDate().toString(),
                                                resultSet.getTimestamp("tag_test_type_datetime64").toLocalDateTime()
                                                                .toLocalDate().toString());
                                assertEquals(randomThreadCurrentUserTime,
                                                resultSet.getLong("tag_thread_current_user_time_type_Int64"));
                                assertEquals(0, resultSet.getLong("tag_error_test_type_Int64"));
                                assertEquals(randomTestFloat, resultSet.getDouble("tag_test_Float_type_Float64"));
                                System.out.println("All values match successfully!");
                        } else {
                                fail("No data found for trace_id: " + randomTraceId);
                        }
                }
        }
}