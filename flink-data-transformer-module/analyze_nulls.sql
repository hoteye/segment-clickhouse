-- 分析events表中各字段的null值分布
SELECT 
    'service' as field, 
    countIf(isNull(service)) as null_count, 
    count() as total, 
    round(countIf(isNull(service)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'service_instance' as field, 
    countIf(isNull(service_instance)) as null_count, 
    count() as total, 
    round(countIf(isNull(service_instance)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'operation_name' as field, 
    countIf(isNull(operation_name)) as null_count, 
    count() as total, 
    round(countIf(isNull(operation_name)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'peer' as field, 
    countIf(isNull(peer)) as null_count, 
    count() as total, 
    round(countIf(isNull(peer)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'span_type' as field, 
    countIf(isNull(span_type)) as null_count, 
    count() as total, 
    round(countIf(isNull(span_type)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'span_layer' as field, 
    countIf(isNull(span_layer)) as null_count, 
    count() as total, 
    round(countIf(isNull(span_layer)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'is_error' as field, 
    countIf(isNull(is_error)) as null_count, 
    count() as total, 
    round(countIf(isNull(is_error)) * 100.0 / count(), 2) as null_percentage 
FROM events 

UNION ALL 

SELECT 
    'tag_status_code' as field, 
    countIf(isNull(tag_status_code)) as null_count, 
    count() as total, 
    round(countIf(isNull(tag_status_code)) * 100.0 / count(), 2) as null_percentage 
FROM events 

ORDER BY null_percentage DESC; 