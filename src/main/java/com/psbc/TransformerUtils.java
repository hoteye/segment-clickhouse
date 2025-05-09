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

public class TransformerUtils {
    private static final Logger logger = LoggerFactory.getLogger(TransformerUtils.class);

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
            Map<String, Map<String, String>> mappings) throws Exception {

        PreparedStatement stmt = databaseService.getStatement();

        // 遍历 spans 列表
        for (SpanObject span : segment.getSpansList()) {
            int index = 1;

            // 遍历 segment 的映射关系
            Map<String, String> segmentMappings = mappings.get("segment");
            for (Map.Entry<String, String> entry : segmentMappings.entrySet()) {
                String fieldName = entry.getKey();
                String fieldType = entry.getValue();

                Object value = getFieldValue(segment, fieldName);
                setPreparedStatementValue(stmt, index++, value, fieldType);
            }

            // 遍历 span 的映射关系
            Map<String, String> spanMappings = mappings.get("spans");
            for (Map.Entry<String, String> entry : spanMappings.entrySet()) {
                String fieldName = entry.getKey();
                String fieldType = entry.getValue();

                Object value = getFieldValue(span, fieldName);
                setPreparedStatementValue(stmt, index++, value, fieldType);
            }

            // 处理 refs 映射关系（取第一个 ref）
            if (!span.getRefsList().isEmpty()) {
                SegmentReference ref = span.getRefsList().get(0);
                Map<String, String> refsMappings = mappings.get("refs");
                for (Map.Entry<String, String> entry : refsMappings.entrySet()) {
                    String fieldName = entry.getKey();
                    String fieldType = entry.getValue();

                    Object value = getFieldValue(ref, fieldName);
                    setPreparedStatementValue(stmt, index++, value, fieldType);
                }
            }

            // 处理 tags 映射关系
            setTagOrLog(stmt, span.getTagsList(), "tag_", databaseService.getColumns(), invalidFields, missingFields);

            // 处理 logs 映射关系
            for (Log log : span.getLogsList()) {
                setTagOrLog(stmt, log.getDataList(), "log_", databaseService.getColumns(), invalidFields,
                        missingFields);
            }

            stmt.addBatch();
        }
    }

    /**
     * 根据字段名称获取对象的值
     */
    private static Object getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
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
                stmt.setInt(index, (Boolean) value ? 1 : 0);
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

            // Check if the field name is valid
            if (!Tools.isValidFieldName(sanitizedKey)) {
                invalidFields.add(sanitizedKey); // Record invalid fields
                continue;
            }

            // Check if the field exists in columnNames
            if (!columnNames.contains(sanitizedKey)) {
                missingFields.add(sanitizedKey); // Record fields not in columnNames
                logger.info("Field {} needs to be added.", missingFields);
                continue;
            }

            // Set field value into PreparedStatement
            stmt.setString(columnNames.indexOf(sanitizedKey) + 1, keyValue.getValue());
        }
    }

}