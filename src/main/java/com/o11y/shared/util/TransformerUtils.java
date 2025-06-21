package com.o11y.shared.util;

import com.o11y.infrastructure.database.DatabaseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import segment.v3.Segment.KeyStringValuePair;
import segment.v3.Segment.Log;
import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SegmentReference;
import segment.v3.Segment.SpanObject;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Arrays;

/**
 * 数据转换工具类。
 * 
 * <p>
 * 提供 Segment 数据到 ClickHouse 的转换功能，包括数据类型映射、
 * 字段验证、批量插入等核心功能。是数据处理管道中的关键组件。
 * 
 * <p>
 * <strong>主要功能：</strong>
 * <ul>
 * <li>ClickHouse 数据类型验证和转换</li>
 * <li>SegmentObject 到数据库记录的映射</li>
 * <li>动态字段处理和表结构扩展</li>
 * <li>批量数据插入优化</li>
 * <li>Tag 和 Log 数据的扁平化处理</li>
 * </ul>
 * 
 * <p>
 * <strong>设计原则：</strong>
 * <ul>
 * <li>静态方法提供无状态的转换功能</li>
 * <li>类型安全和数据完整性保证</li>
 * <li>高性能的批量操作支持</li>
 * <li>灵活的数据类型映射机制</li>
 * </ul>
 * 
 * @see DatabaseService 数据库操作服务
 * @see SegmentObject Skywalking 数据模型
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class TransformerUtils {
    private static final Logger logger = LoggerFactory.getLogger(TransformerUtils.class);

    // 合并 ClickHouse 支持的所有类型为一个集合
    private static final List<String> CLICKHOUSE_SUPPORTED_TYPES = Arrays.asList(
            // Numeric types
            "Int8", "UInt8", "Int16", "UInt16", "Int32", "UInt32",
            "Int64", "UInt64", "Int128", "UInt128", "Int256", "UInt256",
            "Float32", "Float64",
            "Decimal32(4)",
            "Decimal64(8)",
            "Decimal128(18)",
            "Decimal256(18)",
            // Date/Time types
            "Date", "Date32", "DateTime", "DateTime32", "DateTime64");

    /**
     * 检查给定类型是否为 ClickHouse 支持的数据类型。
     * 
     * <p>
     * 支持的类型包括所有数值类型（整型、浮点型、小数类型）和日期时间类型。
     * 不支持的类型将被转换为 String 类型存储。
     * 
     * @param type 待检查的数据类型字符串，可以为 null 或空
     * @return true 如果类型被 ClickHouse 原生支持，false 否则
     */
    public static boolean isClickhouseSupportedType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        String normalized = type.toLowerCase();
        return CLICKHOUSE_SUPPORTED_TYPES.stream().anyMatch(t -> t.toLowerCase().equals(normalized));
    }

    /**
     * 将类型字符串转换为 ClickHouse 标准类型名称。
     * 
     * <p>
     * 执行类型标准化，确保类型名称符合 ClickHouse 的命名约定。
     * 对于不支持的类型，默认返回 "String" 类型。
     * 
     * <p>
     * <strong>转换规则：</strong>
     * <ul>
     * <li>忽略大小写进行匹配</li>
     * <li>支持的类型保持原始格式（如 Int32, Float64）</li>
     * <li>不支持的类型统一转换为 String</li>
     * </ul>
     * 
     * @param type 原始类型字符串，可以为 null 或空
     * @return ClickHouse 标准类型名称，未知类型返回 "String"
     */
    public static String toClickHouseType(String type) {
        if (type == null || type.isEmpty())
            return "String";
        String t = type.toLowerCase();
        return CLICKHOUSE_SUPPORTED_TYPES.stream()
                .filter(supportedType -> supportedType.toLowerCase().equals(t))
                .findFirst()
                .orElse("String");
    }

    /**
     * Insert a SegmentObject into the events table.
     * 
     * @param databaseService The database service instance.
     * @param segment         The segment object to insert.
     * @param invalidFields   Set of invalid fields.
     * @param missingFields   Set of missing fields.
     * @throws Exception if insertion fails.
     */
    public static void insertSegmentObjectToEvents(DatabaseService databaseService, SegmentObject segment,
            ConcurrentSkipListSet<String> invalidFields, ConcurrentSkipListSet<String> missingFields)
            throws Exception {
        PreparedStatement stmt = databaseService.getStatement();
        for (SpanObject span : segment.getSpansList()) {
            int index = 1;

            // Basic fields
            stmt.setString(index++, segment.getTraceId()); // trace_id
            stmt.setString(index++, segment.getTraceSegmentId()); // trace_segment_id
            stmt.setString(index++, segment.getService()); // service
            stmt.setString(index++, segment.getServiceInstance()); // service_instance
            stmt.setInt(index++, segment.getIsSizeLimited() ? 1 : 0); // is_size_limited

            stmt.setInt(index++, span.getSpanId()); // span_id
            stmt.setInt(index++, span.getParentSpanId()); // parent_span_id
            stmt.setLong(index++, span.getStartTime()); // start_time
            stmt.setLong(index++, span.getEndTime()); // end_time
            stmt.setString(index++, span.getOperationName()); // operation_name
            stmt.setString(index++, span.getPeer()); // peer
            stmt.setString(index++, span.getSpanType().name()); // span_type
            stmt.setString(index++, span.getSpanLayer().name()); // span_layer
            stmt.setInt(index++, span.getComponentId()); // component_id
            stmt.setInt(index++, span.getIsError() ? 1 : 0); // is_error
            stmt.setInt(index++, span.getSkipAnalysis() ? 1 : 0); // skip_analysis

            for (int i = index; i <= databaseService.getColumns().size(); i++) {
                stmt.setString(i, null); // Initialize other fields to null
            }
            // Refs information (take the first record)
            if (!span.getRefsList().isEmpty()) {
                SegmentReference ref = span.getRefsList().get(0);
                stmt.setString(index++, ref.getRefType().name()); // refs_ref_type
                stmt.setString(index++, ref.getTraceId()); // refs_trace_id
                stmt.setString(index++, ref.getParentTraceSegmentId()); // refs_parent_trace_segment_id
                stmt.setInt(index++, ref.getParentSpanId()); // refs_parent_span_id
                stmt.setString(index++, ref.getParentService()); // refs_parent_service
                stmt.setString(index++, ref.getParentServiceInstance()); // refs_parent_service_instance
                stmt.setString(index++, ref.getParentEndpoint()); // refs_parent_endpoint
                stmt.setString(index++, ref.getNetworkAddressUsedAtPeer()); // refs_network_address_used_at_peer
            }

            // Tags information
            setTagOrLog(stmt, span.getTagsList(), "tag_", databaseService.getColumns(), invalidFields, missingFields);

            // Logs information (log.time is discarded)
            for (Log log : span.getLogsList()) {
                setTagOrLog(stmt, log.getDataList(), "log_", databaseService.getColumns(), invalidFields,
                        missingFields);
            }

            stmt.addBatch();
        }
    }

    /**
     * Set Tags or Logs information into PreparedStatement
     *
     * @param stmt          PreparedStatement object
     * @param keyValuePairs List of key-value pairs for Tags or Logs
     * @param prefix        Prefix to distinguish between Tags and Logs
     * @param columnNames   List of column names
     * @param invalidFields List to record invalid fields
     * @param missingFields List to record fields not in columnNames
     * @throws Exception If setting field values fails
     */
    public static void setTagOrLog(PreparedStatement stmt, List<KeyStringValuePair> keyValuePairs,
            String prefix, List<String> columnNames,
            ConcurrentSkipListSet<String> invalidFields, ConcurrentSkipListSet<String> missingFields) throws Exception {
        for (KeyStringValuePair keyValue : keyValuePairs) {
            // Process field names, replace invalid characters
            String sanitizedKey = prefix + keyValue.getKey().replace(".", "_").replace("-", "_");
            String columnType = "String"; // Default type is String

            // Check if the field name ends with _type.xxx
            String[] parts = sanitizedKey.split("_type_");
            if (parts.length == 2) {
                columnType = parts[1]; // Extract the field type

                if (!TransformerUtils.isClickhouseSupportedType(columnType)) {
                    invalidFields.add(sanitizedKey); // Record invalid type
                    logger.warn("Invalid type '{}' for field '{}'. Skipping.", columnType, sanitizedKey);
                    continue; // Skip this field
                }
            }

            // Check if the field name is valid
            if (!Tools.isValidFieldName(sanitizedKey)) {
                invalidFields.add(sanitizedKey); // Record invalid fields
                logger.debug("Invalid field name '{}'. Skipping.", sanitizedKey);
                continue;
            }
            // Check if the field exists in columnNames
            if (!columnNames.contains(sanitizedKey)) {
                missingFields.add(sanitizedKey); // Record fields not in columnNames
                logger.debug("Field {} needs to be added.", sanitizedKey);
                continue;
            }

            // Set field value into PreparedStatement based on the type
            int columnIndex = columnNames.indexOf(sanitizedKey) + 1; // PreparedStatement index starts from 1
            String value = keyValue.getValue();

            try {
                switch (columnType.toLowerCase()) {
                    case "int8":
                    case "int16":
                    case "int32":
                        stmt.setInt(columnIndex, Integer.parseInt(value));
                        break;
                    case "int64":
                        stmt.setLong(columnIndex, Long.parseLong(value));
                        break;
                    case "uint8":
                    case "uint16":
                    case "uint32":
                    case "uint64":
                        stmt.setLong(columnIndex, Long.parseUnsignedLong(value));
                        break;
                    case "float32":
                        stmt.setFloat(columnIndex, Float.parseFloat(value));
                        break;
                    case "float64":
                        stmt.setDouble(columnIndex, Double.parseDouble(value));
                        break;
                    case "decimal32":
                    case "decimal64":
                    case "decimal128":
                    case "decimal256":
                        stmt.setBigDecimal(columnIndex, new java.math.BigDecimal(value));
                        break;
                    case "date":
                    case "date32":
                        stmt.setDate(columnIndex, java.sql.Date.valueOf(value)); // value: yyyy-MM-dd
                        break;
                    case "datetime":
                    case "datetime32":
                        stmt.setTimestamp(columnIndex, java.sql.Timestamp.valueOf(value)); // value: yyyy-MM-dd HH:mm:ss
                        break;
                    case "datetime64":
                        stmt.setTimestamp(columnIndex, java.sql.Timestamp.valueOf(value)); // value: yyyy-MM-dd
                                                                                           // HH:mm:ss[.fff]
                        break;
                    default: // Default to String
                        stmt.setString(columnIndex, value);
                        break;
                }
            } catch (IllegalArgumentException e) {
                logger.error("Failed to set value for field {}: {}", sanitizedKey, e.getMessage(), e);
            }
        }
    }

    public static List<String> getClickhouseSupportedTypes() {
        return CLICKHOUSE_SUPPORTED_TYPES;
    }

}