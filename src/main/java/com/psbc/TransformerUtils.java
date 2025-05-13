package com.psbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.psbc.utilities.Tools;

import segment.v3.Segment.KeyStringValuePair;
import segment.v3.Segment.Log;
import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SegmentReference;
import segment.v3.Segment.SpanObject;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import java.lang.reflect.Field;
import java.util.Arrays;

public class TransformerUtils {
    private static final Logger logger = LoggerFactory.getLogger(TransformerUtils.class);

    // 定义 ClickHouse 支持的数字类型为静态共享列表
    public static final List<String> CLICKHOUSE_NUMERIC_TYPES = Arrays.asList(
            "Int8", "UInt8", "Int16", "UInt16", "Int32", "UInt32",
            "Int64", "UInt64", "Int128", "UInt128", "Int256", "UInt256",
            "Float32", "Float64", "Decimal32", "Decimal64", "Decimal128", "Decimal256");

    public static void mapSegmentObjectToStatement(SegmentObject segment, PreparedStatement stmt,
            List<String> columnIndexMap) throws Exception {
        // Map fields of SegmentObject
        mapObjectFieldsToStatement(segment, stmt, columnIndexMap);

        // Map fields of SpanObject
        for (SpanObject span : segment.getSpansList()) {
            mapObjectFieldsToStatement(span, stmt, columnIndexMap);

            // Process Refs information
            if (!span.getRefsList().isEmpty()) {
                mapObjectFieldsToStatement(span.getRefsList().get(0), stmt, columnIndexMap);
            }
        }
    }

    private static void mapObjectFieldsToStatement(Object obj, PreparedStatement stmt,
            List<String> columnIndexMap) throws Exception {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true); // Allow access to private fields
            String fieldName = field.getName();
            Object value = field.get(obj);

            // Check if the field is in the column mapping
            if (columnIndexMap.contains(fieldName)) {
                int columnIndex = columnIndexMap.indexOf(fieldName) + 1; // +1 because PreparedStatement index starts
                                                                         // from 1
                if (value instanceof String) {
                    stmt.setString(columnIndex, (String) value);
                } else if (value instanceof Integer) {
                    stmt.setInt(columnIndex, (Integer) value);
                } else if (value instanceof Long) {
                    stmt.setLong(columnIndex, (Long) value);
                } else if (value instanceof Boolean) {
                    stmt.setInt(columnIndex, (Boolean) value ? 1 : 0);
                } else {
                    stmt.setObject(columnIndex, value); // Other types
                }
            }
        }
    }

    static void insertSegmentObjectToEvents(DatabaseService databaseService, SegmentObject segment,
            ConcurrentSkipListSet<String> invalidFields, ConcurrentSkipListSet<String> missingFields,
            Map<String, Object> mappings) throws Exception {

        PreparedStatement stmt = databaseService.getStatement();
        int index = 1;

        for (Map.Entry<String, Object> entry : mappings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            System.out.println("Key: " + key);
            if (value instanceof Map) {
                // 如果是子节点，递归遍历
                traverseMappings((Map<String, Object>) value);
            } else if (value instanceof Iterable) {
                // 如果是列表，遍历列表
                for (Object item : (Iterable<?>) value) {
                    System.out.println("  Item: " + item);
                }
            } else {
                // 如果是叶子节点，直接打印
                System.out.println("  Value: " + value);
            }
        }
        // 处理 segment 的字段映射
        Map<String, Map<String, String>> segmentMappings = (Map<String, Map<String, String>>) mappings.get("segment");
        if (segmentMappings != null) {

            for (Map.Entry<String, Map<String, String>> entry : segmentMappings.entrySet()) {
                String fieldPath = entry.getValue().get("path");
                String fieldType = entry.getValue().get("type");

                Object value = getFieldValueByPath(segment, fieldPath);
                setPreparedStatementValue(stmt, index++, value, fieldType);
            }
        }

        // 遍历 spans 列表
        for (SpanObject span : segment.getSpansList()) {

            // 处理 spans 的字段映射
            Map<String, Map<String, String>> spanMappings = (Map<String, Map<String, String>>) mappings.get("spans");
            if (spanMappings != null) {
                for (Map.Entry<String, Map<String, String>> entry : spanMappings.entrySet()) {
                    String fieldPath = entry.getValue().get("path");
                    String fieldType = entry.getValue().get("type");

                    Object value = getFieldValueByPath(span, fieldPath);
                    setPreparedStatementValue(stmt, index++, value, fieldType);
                }
            }

            // 处理 refs 的字段映射（取第一个 ref）
            if (!span.getRefsList().isEmpty()) {
                SegmentReference ref = span.getRefsList().get(0);
                Map<String, Map<String, String>> refsMappings = (Map<String, Map<String, String>>) mappings.get("refs");
                if (refsMappings != null) {
                    for (Map.Entry<String, Map<String, String>> entry : refsMappings.entrySet()) {
                        String fieldPath = entry.getValue().get("path");
                        String fieldType = entry.getValue().get("type");

                        Object value = getFieldValueByPath(ref, fieldPath);
                        setPreparedStatementValue(stmt, index++, value, fieldType);
                    }
                }
            }

            // 处理 tags 的字段映射
            List<Map<String, String>> tagsMappings = (List<Map<String, String>>) mappings.get("tags");
            if (tagsMappings != null) {
                for (KeyStringValuePair tag : span.getTagsList()) {
                    for (Map<String, String> tagMapping : tagsMappings) {
                        String keyPath = tagMapping.get("path");
                        String valuePath = tagMapping.get("value");

                        String key = (String) getFieldValueByPath(tag, keyPath);
                        String value = (String) getFieldValueByPath(tag, valuePath);

                        stmt.setString(index++, key);
                        stmt.setString(index++, value);
                    }
                }
            }

            // 处理 logs 的字段映射
            List<Map<String, String>> logsMappings = (List<Map<String, String>>) mappings.get("logs");
            if (logsMappings != null) {
                for (Log log : span.getLogsList()) {
                    for (Map<String, String> logMapping : logsMappings) {
                        String timePath = logMapping.get("path");
                        String dataPath = logMapping.get("data");

                        Long time = (Long) getFieldValueByPath(log, timePath);
                        String data = (String) getFieldValueByPath(log, dataPath);

                        stmt.setLong(index++, time);
                        stmt.setString(index++, data);
                    }
                }
            }

            stmt.addBatch();
        }
    }

    /**
     * 根据路径获取对象的值，自动处理字段名称的下划线后缀
     */
    private static Object getFieldValueByPath(Object obj, String fieldPath) throws Exception {
        String[] pathParts = fieldPath.split("\\.");
        Object currentObj = obj;
        for (String part : pathParts) {
            // 自动添加下划线后缀
            String fieldNameWithUnderscore = part + "_";
            Field field = findField(currentObj.getClass(), fieldNameWithUnderscore);
            if (field == null) {
                throw new NoSuchFieldException("Field not found: " + fieldNameWithUnderscore);
            }
            field.setAccessible(true);
            currentObj = field.get(currentObj);
            if (currentObj == null) {
                return null;
            }
        }
        return currentObj;
    }

    /**
     * 在类及其父类中查找字段
     */
    private static Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 根据字段类型设置 PreparedStatement 的值
     */
    private static void setPreparedStatementValue(PreparedStatement stmt, int index, Object value, String fieldType)
            throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.NULL);
            return;
        }

        switch (fieldType.toLowerCase()) {
            case "string":
                stmt.setString(index, (String) value);
                break;
            case "integer":
                stmt.setInt(index, (Integer) value);
                break;
            case "long":
                stmt.setLong(index, (Long) value);
                break;
            case "boolean":
                stmt.setBoolean(index, (Boolean) value);
                break;
            default:
                stmt.setObject(index, value);
                break;
        }
    }

    /**
     * Insert SegmentObject into the events table
     */
    static void insertSegmentObjectToEvents(DatabaseService databaseService, SegmentObject segment,
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
    static void setTagOrLog(PreparedStatement stmt, List<KeyStringValuePair> keyValuePairs,
            String prefix, List<String> columnNames,
            ConcurrentSkipListSet<String> invalidFields, ConcurrentSkipListSet<String> missingFields) throws Exception {
        for (KeyStringValuePair keyValue : keyValuePairs) {
            // Process field names, replace invalid characters
            String sanitizedKey = prefix + keyValue.getKey().replace(".", "_").replace("-", "_");
            String columnType = "String"; // Default type is String

            // Check if the field name ends with _type.xxx
            if (sanitizedKey.contains("_type_")) {
                String[] parts = sanitizedKey.split("_type_");
                if (parts.length == 2) {
                    columnType = parts[1]; // Extract the field type

                    // Validate if the type is in CLICKHOUSE_NUMERIC_TYPES
                    if (!TransformerUtils.CLICKHOUSE_NUMERIC_TYPES.contains(columnType)) {
                        invalidFields.add(sanitizedKey + "_type_" + columnType); // Record invalid type
                        logger.warn("Invalid type '{}' for field '{}'. Skipping.", columnType, sanitizedKey);
                        continue; // Skip this field
                    }
                }
            }

            // Check if the field name is valid
            if (!Tools.isValidFieldName(sanitizedKey)) {
                invalidFields.add(sanitizedKey); // Record invalid fields
                continue;
            }

            // Check if the field exists in columnNames
            if (!columnNames.contains(sanitizedKey)) {
                missingFields.add(sanitizedKey); // Record fields not in columnNames
                logger.info("Field {} needs to be added.", sanitizedKey);
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
                    default: // Default to String
                        stmt.setString(columnIndex, value);
                        break;
                }
            } catch (NumberFormatException e) {
                logger.error("Failed to set value for field {}: {}", sanitizedKey, e.getMessage(), e);
            }
        }
    }

    /**
     * Traverse mappings and print keys and values
     *
     * @param mappings Mappings to traverse
     */
    public static void traverseMappings(Map<String, Object> mappings) {
        for (Map.Entry<String, Object> entry : mappings.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            System.out.println("Key: " + key);
            if (value instanceof Map) {
                // 如果是子节点，递归遍历
                traverseMappings((Map<String, Object>) value);
            } else if (value instanceof Iterable) {
                // 如果是列表，遍历列表
                for (Object item : (Iterable<?>) value) {
                    System.out.println("  Item: " + item);
                }
            } else {
                // 如果是叶子节点，直接打印
                System.out.println("  Value: " + value);
            }
        }
    }
}