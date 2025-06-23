package com.o11y;

import com.o11y.infrastructure.database.DatabaseService;
import com.o11y.shared.util.SegmentObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SpanLayer;
import segment.v3.Segment.SpanObject;
import segment.v3.Segment.SegmentReference;
import segment.v3.Segment.SpanType;
import segment.v3.Segment.KeyStringValuePair;
import segment.v3.Segment.Log;
import segment.v3.Segment.RefType;

import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SegmentObjectMapper.insertSegmentObjectToEvents 方法的专门测试类
 * 
 * <p>
 * 测试覆盖场景：
 * <ul>
 * <li>基本字段映射正确</li>
 * <li>动态字段处理（Tags 和 Logs）</li>
 * <li>类型转换和验证</li>
 * <li>缺失字段检测</li>
 * <li>无效字段处理</li>
 * <li>批量插入行为</li>
 * <li>异常处理</li>
 * <li>日期时间类型字段</li>
 * <li>复杂集成场景</li>
 * </ul>
 * 
 * @see SegmentObjectMapper#insertSegmentObjectToEvents
 * @author DDD Architecture Team
 * @since 1.0.0
 */
class InsertSegmentObjectToEventsTest {

        @Mock
        private DatabaseService mockDatabaseService;

        @Mock
        private PreparedStatement mockStatement;

        @Mock
        private Connection mockConnection;

        private ConcurrentSkipListSet<String> invalidFields;
        private ConcurrentSkipListSet<String> missingFields;

        @BeforeEach
        void setUp() throws Exception {
                MockitoAnnotations.openMocks(this);
                invalidFields = new ConcurrentSkipListSet<>();
                missingFields = new ConcurrentSkipListSet<>();

                // 设置基本 mock 行为
                when(mockDatabaseService.getStatement()).thenReturn(mockStatement);
                when(mockDatabaseService.getConnection()).thenReturn(mockConnection);

                // 模拟基本的表结构
                List<String> basicColumns = Arrays.asList(
                                "trace_id", "trace_segment_id", "service", "service_instance", "is_size_limited",
                                "span_id", "parent_span_id", "start_time", "end_time", "operation_name", "peer",
                                "span_type", "span_layer", "component_id", "is_error", "skip_analysis",
                                "refs_ref_type", "refs_trace_id", "refs_parent_trace_segment_id", "refs_parent_span_id",
                                "refs_parent_service", "refs_parent_service_instance", "refs_parent_endpoint",
                                "refs_network_address_used_at_peer",
                                "tag_http_method", "tag_status_code", "log_event", "log_message");
                when(mockDatabaseService.getColumns()).thenReturn(basicColumns);
        }

        @Test
        @DisplayName("测试基本字段映射 - 单个 Span")
        void testBasicFieldMapping() throws Exception {
                // 准备测试数据
                SegmentObject segment = createBasicTestSegment();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields);

                // 验证基本字段设置
                verify(mockStatement).setString(1, "test-trace-id");
                verify(mockStatement).setString(2, "test-segment-id");
                verify(mockStatement).setString(3, "test-service");
                verify(mockStatement).setString(4, "test-instance");
                verify(mockStatement).setInt(5, 0); // is_size_limited = false // 验证 Span 字段
                verify(mockStatement).setInt(6, 1); // span_id
                verify(mockStatement).setInt(7, 0); // parent_span_id
                verify(mockStatement).setString(10, "test-operation"); // operation_name
                verify(mockStatement).setString(11, "test-peer"); // peer
                verify(mockStatement).setString(12, "Entry"); // span_type
                verify(mockStatement).setString(13, "Http"); // span_layer
                verify(mockStatement).setInt(14, 49); // component_id
                verify(mockStatement).setInt(15, 0); // is_error = false// 验证执行 - 应该调用addBatch()而不是executeUpdate()
                verify(mockStatement).addBatch();

                // 验证无错误字段
                assertTrue(invalidFields.isEmpty());
                assertTrue(missingFields.isEmpty());
        }

        @Test
        @DisplayName("测试多个 Spans 的处理")
        void testMultipleSpans() throws Exception {
                // 准备包含多个 Spans 的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("multi-trace-id")
                                .setTraceSegmentId("multi-segment-id")
                                .setService("multi-service")
                                .setServiceInstance("multi-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("span-1")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .build())
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(2)
                                                .setParentSpanId(1)
                                                .setStartTime(1500L)
                                                .setEndTime(2500L)
                                                .setOperationName("span-2")
                                                .setSpanType(SpanType.Local)
                                                .setSpanLayer(SpanLayer.Database)
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields); // 验证执行了两个批次(每个Span一个)
                verify(mockStatement, times(2)).addBatch(); // 验证第一个 Span 的数据
                verify(mockStatement).setInt(6, 1); // first span_id
                verify(mockStatement).setString(10, "span-1"); // first operation_name

                // 验证第二个 Span 的数据
                verify(mockStatement).setInt(6, 2); // second span_id
                verify(mockStatement).setString(10, "span-2"); // second operation_name
        }

        @Test
        @DisplayName("测试 Tags 字段处理")
        void testTagsProcessing() throws Exception {
                // 准备包含 Tags 的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("tags-trace-id")
                                .setTraceSegmentId("tags-segment-id")
                                .setService("tags-service")
                                .setServiceInstance("tags-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("tags-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("http.method")
                                                                .setValue("GET")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("status.code")
                                                                .setValue("200")
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields); // 验证
                                                // Tags
                                                // 字段设置
                verify(mockStatement).setString(25, "GET"); // tag_http_method (index 25 based on column order)
                verify(mockStatement).setString(26, "200"); // tag_status_code (index 26 based on column order)

                verify(mockStatement).addBatch();
        }

        @Test
        @DisplayName("测试 Logs 字段处理")
        void testLogsProcessing() throws Exception {
                // 准备包含 Logs 的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("logs-trace-id")
                                .setTraceSegmentId("logs-segment-id")
                                .setService("logs-service")
                                .setServiceInstance("logs-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("logs-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addLogs(Log.newBuilder()
                                                                .setTime(1500L)
                                                                .addData(KeyStringValuePair.newBuilder()
                                                                                .setKey("event")
                                                                                .setValue("error.occurred")
                                                                                .build())
                                                                .addData(KeyStringValuePair.newBuilder()
                                                                                .setKey("message")
                                                                                .setValue("Database connection failed")
                                                                                .build())
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields); // 验证
                                                // Logs
                                                // 字段设置
                verify(mockStatement).setString(27, "error.occurred"); // log_event
                verify(mockStatement).setString(28, "Database connection failed"); // log_message

                verify(mockStatement).addBatch();
        }

        @Test
        @DisplayName("测试缺失字段检测")
        void testMissingFieldsDetection() throws Exception {
                // 准备包含缺失字段的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("missing-trace-id")
                                .setTraceSegmentId("missing-segment-id")
                                .setService("missing-service")
                                .setServiceInstance("missing-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("missing-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("unknown.field") // 这个字段在表中不存在
                                                                .setValue("some-value")
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields);

                // 验证缺失字段被正确记录
                assertTrue(missingFields.contains("tag_unknown_field"));
                assertEquals(1, missingFields.size());

                // 无无效字段
                assertTrue(invalidFields.isEmpty());

                verify(mockStatement).addBatch();
        }

        @Test
        @DisplayName("测试无效字段名处理")
        void testInvalidFieldNameHandling() throws Exception {
                // 准备包含无效字段名的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("invalid-trace-id")
                                .setTraceSegmentId("invalid-segment-id")
                                .setService("invalid-service")
                                .setServiceInstance("invalid-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("invalid-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("field with spaces") // 包含空格的无效字段名
                                                                .setValue("some-value")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("field-with-hyphen") // 包含连字符的无效字段名
                                                                .setValue("another-value")
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields); // 验证无效字段名被正确记录（空格未被替换，因此无效字段名）

                assertTrue(invalidFields.contains("tag_field with spaces"));
                // 连字符被替换为下划线，所以这个字段名是有效的，应该被添加到missingFields而不是invalidFields
                assertFalse(invalidFields.contains("tag_field_with_hyphen"));
                assertTrue(missingFields.contains("tag_field_with_hyphen"));
                assertEquals(1, invalidFields.size());

                // 有一个缺失字段：tag_field_with_hyphen
                assertEquals(1, missingFields.size());

                verify(mockStatement).addBatch();
        }

        @Test
        @DisplayName("测试 References 字段处理")
        void testReferencesProcessing() throws Exception {
                // 准备包含 References 的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("refs-trace-id")
                                .setTraceSegmentId("refs-segment-id")
                                .setService("refs-service")
                                .setServiceInstance("refs-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("refs-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addRefs(SegmentReference.newBuilder()
                                                                .setRefType(RefType.CrossProcess)
                                                                .setTraceId("ref-trace-123")
                                                                .setParentTraceSegmentId("ref-segment-456")
                                                                .setParentSpanId(789)
                                                                .setParentService("ref-service")
                                                                .setParentServiceInstance("ref-instance")
                                                                .setParentEndpoint("ref-endpoint")
                                                                .setNetworkAddressUsedAtPeer("192.168.1.100:8080")
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields);

                // 验证 References 字段
                verify(mockStatement).setString(17, "CrossProcess"); // refs_ref_type
                verify(mockStatement).setString(18, "ref-trace-123"); // refs_trace_id
                verify(mockStatement).setString(19, "ref-segment-456"); // refs_parent_trace_segment_id
                verify(mockStatement).setInt(20, 789); // refs_parent_span_id
                verify(mockStatement).setString(21, "ref-service"); // refs_parent_service
                verify(mockStatement).setString(22, "ref-instance"); // refs_parent_service_instance
                                                                     // verify(mockStatement).setString(23,
                                                                     // "ref-endpoint"); //
                                                                     // refs_parent_endpoint
                verify(mockStatement).setString(24, "192.168.1.100:8080"); // refs_network_address_used_at_peer

                verify(mockStatement).addBatch();
        }

        @Test
        @DisplayName("测试日期和时间类型字段处理")
        void testDateTimeFieldProcessing() throws Exception {
                // 模拟包含日期时间类型字段的列结构
                List<String> dateTimeColumns = Arrays.asList(
                                "trace_id", "trace_segment_id", "service", "service_instance", "is_size_limited",
                                "span_id", "parent_span_id", "start_time", "end_time", "operation_name", "peer",
                                "span_type", "span_layer", "component_id", "is_error", "skip_analysis",
                                "refs_ref_type", "refs_trace_id", "refs_parent_trace_segment_id", "refs_parent_span_id",
                                "refs_parent_service", "refs_parent_service_instance", "refs_parent_endpoint",
                                "refs_network_address_used_at_peer",
                                "tag_test_type_date", "tag_test_type_date32",
                                "tag_test_type_datetime", "tag_test_type_datetime32", "tag_test_type_datetime64");

                when(mockDatabaseService.getColumns()).thenReturn(dateTimeColumns);

                // 创建包含日期时间字段的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("datetime-trace-id")
                                .setTraceSegmentId("datetime-segment-id")
                                .setService("datetime-service")
                                .setServiceInstance("datetime-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("datetime-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_date")
                                                                .setValue("2025-05-17")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_date32")
                                                                .setValue("2025-05-18")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime")
                                                                .setValue("2025-05-19 12:34:56")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime32")
                                                                .setValue("2025-05-20 08:00:00")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime64")
                                                                .setValue("2025-05-21 12:00:00.000")
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields); // 验证日期时间字段设置
                verify(mockStatement).setDate(25, Date.valueOf("2025-05-17")); // tag_test_type_date
                verify(mockStatement).setDate(26, Date.valueOf("2025-05-18")); // tag_test_type_date32
                verify(mockStatement).setTimestamp(27, Timestamp.valueOf("2025-05-19 12:34:56")); // tag_test_type_datetime
                verify(mockStatement).setTimestamp(28, Timestamp.valueOf("2025-05-20 08:00:00")); // tag_test_type_datetime32
                verify(mockStatement).setTimestamp(29, Timestamp.valueOf("2025-05-21 12:00:00")); // tag_test_type_datetime64

                verify(mockStatement).addBatch();

                assertTrue(invalidFields.isEmpty());
                assertTrue(missingFields.isEmpty());
        }

        @Test
        @DisplayName("测试错误值和类型转换处理")
        void testErrorValueAndTypeConversion() throws Exception {
                // 模拟包含类型化字段的列结构
                List<String> typedColumns = Arrays.asList(
                                "trace_id", "trace_segment_id", "service", "service_instance", "is_size_limited",
                                "span_id", "parent_span_id", "start_time", "end_time", "operation_name", "peer",
                                "span_type", "span_layer", "component_id", "is_error", "skip_analysis",
                                "refs_ref_type", "refs_trace_id", "refs_parent_trace_segment_id", "refs_parent_span_id",
                                "refs_parent_service", "refs_parent_service_instance", "refs_parent_endpoint",
                                "refs_network_address_used_at_peer",
                                "tag_thread_current_user_time_type_Int64", "tag_error_test_type_Int64",
                                "tag_test_Float_type_Float64", "tag_Processor_Name");
                when(mockDatabaseService.getColumns()).thenReturn(typedColumns);

                // 创建包含错误值的测试数据
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("error-value-trace-id")
                                .setTraceSegmentId("error-value-segment-id")
                                .setService("error-value-service")
                                .setServiceInstance("error-value-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("error-value-operation")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("thread_current_user_time_type_Int64")
                                                                .setValue("1234567890123") // 正确的数值
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("error_test_type_Int64")
                                                                .setValue("12345error") // 包含错误后缀的数值
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_Float_type_Float64")
                                                                .setValue("3.141592653589793") // 浮点数值
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("Processor_Name")
                                                                .setValue("Intel Core i7-9700K") // 处理器名称
                                                                .build())
                                                .build())
                                .build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields); // 验证字段设置
                verify(mockStatement).setLong(25, 1234567890123L); // tag_thread_current_user_time_type_Int64
                verify(mockStatement).setDouble(27, 3.141592653589793d); // tag_test_Float_type_Float64
                verify(mockStatement).setString(28, "Intel Core i7-9700K"); // tag_Processor_Name

                verify(mockStatement).addBatch();

                assertTrue(invalidFields.isEmpty());
                assertTrue(missingFields.isEmpty());
        }

        @Test
        @DisplayName("测试综合复杂场景 - 模拟集成测试数据")
        void testComplexIntegrationScenario() throws Exception {
                // 模拟完整的表结构（基于集成测试）
                List<String> fullColumns = Arrays.asList(
                                "trace_id", "trace_segment_id", "service", "service_instance", "is_size_limited",
                                "span_id", "parent_span_id", "start_time", "end_time", "operation_name", "peer",
                                "span_type", "span_layer", "component_id", "is_error", "skip_analysis",
                                "refs_ref_type", "refs_trace_id", "refs_parent_trace_segment_id", "refs_parent_span_id",
                                "refs_parent_service", "refs_parent_service_instance", "refs_parent_endpoint",
                                "refs_network_address_used_at_peer",
                                "tag_Processor_Name", "tag_http_method", "tag_thread_current_user_time_type_Int64",
                                "tag_error_test_type_Int64", "tag_test_Float_type_Float64",
                                "tag_test_type_date", "tag_test_type_date32", "tag_test_type_datetime",
                                "tag_test_type_datetime32", "tag_test_type_datetime64",
                                "log_event", "log_forward_url");
                when(mockDatabaseService.getColumns()).thenReturn(fullColumns);

                // 创建复杂的测试数据（模拟集成测试场景）
                long currentTime = System.currentTimeMillis();
                SegmentObject segment = SegmentObject.newBuilder()
                                .setTraceId("integration-trace-123")
                                .setTraceSegmentId("integration-segment-456")
                                .setService("integration-service")
                                .setServiceInstance("integration-instance")
                                .setIsSizeLimited(true)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(100)
                                                .setParentSpanId(99)
                                                .setStartTime(currentTime)
                                                .setEndTime(currentTime + 5000)
                                                .setOperationName("integration-operation")
                                                .setPeer("integration-peer")
                                                .setSpanType(SpanType.Exit)
                                                .setSpanLayer(SpanLayer.Database)
                                                .setComponentId(88)
                                                .setIsError(true)
                                                .setSkipAnalysis(false)
                                                .addRefs(SegmentReference.newBuilder()
                                                                .setRefType(RefType.CrossThread)
                                                                .setTraceId("ref-trace-789")
                                                                .setParentTraceSegmentId("ref-segment-012")
                                                                .setParentSpanId(345)
                                                                .setParentService("ref-service")
                                                                .setParentServiceInstance("ref-instance")
                                                                .setParentEndpoint("ref-endpoint")
                                                                .setNetworkAddressUsedAtPeer("ref-peer-address")
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
                                                                .setValue("1234567890")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("error_test_type_Int64")
                                                                .setValue("56789error")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_Float_type_Float64")
                                                                .setValue("10444444000104444000.0")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_date")
                                                                .setValue("2025-05-17")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_date32")
                                                                .setValue("2025-05-18")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime")
                                                                .setValue("2025-05-19 12:34:56")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime32")
                                                                .setValue("2025-05-20 08:00:00")
                                                                .build())
                                                .addTags(KeyStringValuePair.newBuilder()
                                                                .setKey("test_type_datetime64")
                                                                .setValue("2025-05-21 12:00:00.000")
                                                                .build())
                                                .addLogs(Log.newBuilder()
                                                                .setTime(currentTime + 1000)
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

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields);

                // 验证基本字段
                verify(mockStatement).setString(1, "integration-trace-123");
                verify(mockStatement).setString(2, "integration-segment-456");
                verify(mockStatement).setString(3, "integration-service");
                verify(mockStatement).setString(4, "integration-instance");
                verify(mockStatement).setInt(5, 1); // is_size_limited = true // 验证 Span 字段
                verify(mockStatement).setInt(6, 100);
                verify(mockStatement).setInt(7, 99);
                verify(mockStatement).setString(10, "integration-operation");
                verify(mockStatement).setString(11, "integration-peer");
                verify(mockStatement).setString(12, "Exit");
                verify(mockStatement).setString(13, "Database");
                verify(mockStatement).setInt(14, 88);
                verify(mockStatement).setInt(15, 1); // is_error = true // 验证 Reference 字段
                verify(mockStatement).setString(17, "CrossThread");
                verify(mockStatement).setString(18, "ref-trace-789");
                verify(mockStatement).setString(19, "ref-segment-012");
                verify(mockStatement).setInt(20, 345);
                verify(mockStatement).setString(21, "ref-service");
                verify(mockStatement).setString(22, "ref-instance");
                verify(mockStatement).setString(23, "ref-endpoint");
                verify(mockStatement).setString(24, "ref-peer-address");// 验证所有标签和日志字段
                verify(mockStatement).setString(25, "Processor_Name-fixed-1"); // tag_Processor_Name
                verify(mockStatement).setString(26, "http_method-fixed-2"); // tag_http_method
                verify(mockStatement).setLong(27, 1234567890L); // tag_thread_current_user_time_type_Int64
                verify(mockStatement).setDouble(29, 1.0444444000104444E19d); // tag_test_Float_type_Float64
                verify(mockStatement).setDate(30, Date.valueOf("2025-05-17")); // tag_test_type_date
                verify(mockStatement).setDate(31, Date.valueOf("2025-05-18")); // tag_test_type_date32
                verify(mockStatement).setTimestamp(32, Timestamp.valueOf("2025-05-19 12:34:56")); // tag_test_type_datetime
                verify(mockStatement).setTimestamp(33, Timestamp.valueOf("2025-05-20 08:00:00")); // tag_test_type_datetime32
                verify(mockStatement).setTimestamp(34, Timestamp.valueOf("2025-05-21 12:00:00")); // tag_test_type_datetime64
                verify(mockStatement).setString(35, "log-event-fixed-2"); // log_event
                verify(mockStatement).setString(36, "forward_url-fixed-1"); // log_forward_url

                verify(mockStatement).addBatch();

                assertTrue(invalidFields.isEmpty());
                assertTrue(missingFields.isEmpty());
        }

        @Test
        @DisplayName("测试异常处理")
        void testExceptionHandling() throws Exception {
                // 模拟数据库异常
                doThrow(new RuntimeException("Database connection failed")).when(mockStatement).addBatch();

                SegmentObject segment = createBasicTestSegment();

                // 验证异常会被抛出
                assertThrows(RuntimeException.class, () -> {
                        SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                        missingFields);
                });
        }

        @Test
        @DisplayName("测试极端数据场景")
        void testExtremeDataScenario() throws Exception {
                // 创建极端数据：大量标签和日志
                SegmentObject.Builder segmentBuilder = SegmentObject.newBuilder()
                                .setTraceId("extreme-trace-id")
                                .setTraceSegmentId("extreme-segment-id")
                                .setService("extreme-service")
                                .setServiceInstance("extreme-instance")
                                .setIsSizeLimited(true);

                SpanObject.Builder spanBuilder = SpanObject.newBuilder()
                                .setSpanId(1)
                                .setParentSpanId(0)
                                .setStartTime(1000L)
                                .setEndTime(2000L)
                                .setOperationName("extreme-operation")
                                .setSpanType(SpanType.Entry)
                                .setSpanLayer(SpanLayer.Http);

                // 添加大量标签（模拟极端情况）
                for (int i = 0; i < 50; i++) {
                        spanBuilder.addTags(KeyStringValuePair.newBuilder()
                                        .setKey("extreme.tag." + i)
                                        .setValue("value-" + i)
                                        .build());
                }

                // 添加大量日志
                for (int i = 0; i < 20; i++) {
                        spanBuilder.addLogs(Log.newBuilder()
                                        .setTime(1000L + i * 100)
                                        .addData(KeyStringValuePair.newBuilder()
                                                        .setKey("log.event." + i)
                                                        .setValue("event-" + i)
                                                        .build())
                                        .build());
                }

                SegmentObject segment = segmentBuilder.addSpans(spanBuilder.build()).build();

                // 执行测试
                SegmentObjectMapper.insertSegmentObjectToEvents(mockDatabaseService, segment, invalidFields,
                                missingFields);

                // 验证基本执行
                verify(mockStatement).addBatch();

                // 验证缺失字段被记录（因为大部分极端字段不在表中）
                assertFalse(missingFields.isEmpty());

                // 验证基本字段仍然正确设置
                verify(mockStatement).setString(1, "extreme-trace-id");
                verify(mockStatement).setString(2, "extreme-segment-id");
        }

        // 辅助方法：创建基本测试数据
        private SegmentObject createBasicTestSegment() {
                return SegmentObject.newBuilder()
                                .setTraceId("test-trace-id")
                                .setTraceSegmentId("test-segment-id")
                                .setService("test-service")
                                .setServiceInstance("test-instance")
                                .setIsSizeLimited(false)
                                .addSpans(SpanObject.newBuilder()
                                                .setSpanId(1)
                                                .setParentSpanId(0)
                                                .setStartTime(1000L)
                                                .setEndTime(2000L)
                                                .setOperationName("test-operation")
                                                .setPeer("test-peer")
                                                .setSpanType(SpanType.Entry)
                                                .setSpanLayer(SpanLayer.Http)
                                                .setComponentId(49)
                                                .setIsError(false)
                                                .setSkipAnalysis(false)
                                                .build())
                                .build();
        }
}