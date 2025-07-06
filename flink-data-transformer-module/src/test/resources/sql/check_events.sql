-- 检查总数据量
SELECT count(*) as total_count FROM integration.events;

-- 检查时间范围内的数据
SELECT count(*) as in_time_range
FROM integration.events 
WHERE start_time >= now() - INTERVAL 7 DAY 
AND start_time < now();

-- 检查service和operation_name非空的数据
SELECT count(*) as valid_service_op
FROM integration.events 
WHERE service IS NOT NULL 
AND operation_name IS NOT NULL;

-- 检查成功请求的数据
SELECT count(*) as success_requests
FROM integration.events 
WHERE is_error = 0;

-- 检查响应时间合理的数据
SELECT count(*) as valid_duration
FROM integration.events 
WHERE (end_time - start_time) * 1000 < 60000 
AND (end_time - start_time) > 0;

-- 检查所有条件都满足的数据
SELECT count(*) as all_conditions_met
FROM integration.events 
WHERE start_time >= now() - INTERVAL 7 DAY
AND start_time < now()
AND service IS NOT NULL 
AND operation_name IS NOT NULL
AND is_error = 0
AND (end_time - start_time) * 1000 < 60000
AND (end_time - start_time) > 0;

-- 检查按小时分组后的数据分布
SELECT 
    toHour(start_time) as hour_of_day,
    service,
    operation_name,
    count(*) as sample_count
FROM integration.events 
WHERE start_time >= now() - INTERVAL 7 DAY
AND start_time < now()
AND service IS NOT NULL 
AND operation_name IS NOT NULL
AND is_error = 0
AND (end_time - start_time) * 1000 < 60000
AND (end_time - start_time) > 0
GROUP BY hour_of_day, service, operation_name
HAVING count(*) >= 10
ORDER BY hour_of_day, service, operation_name; 