package com.o11y.ai.repository;

import com.o11y.ai.model.PerformanceMetrics;
import com.o11y.ai.model.PerformanceAnomaly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse 数据访问层
 * 
 * 负责从 ClickHouse 查询性能数据和异常数据
 */
@Repository
public class ClickHouseRepository {

    @Autowired
    private JdbcTemplate clickHouseJdbcTemplate;

    /**
     * 查询指定时间窗口内的服务性能指标
     * 从 flink_operator_agg_result 表查询聚合后的性能数据
     */
    public List<PerformanceMetrics> getServiceMetrics(LocalDateTime startTime, LocalDateTime endTime,
            String serviceName, int timeRangeHours) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ")
                .append("service, ")
                .append("instance, ")
                .append("method, ")
                .append("operator_name, ")
                .append("avg(avg_duration) as avg_duration, ")
                .append("max(max_duration) as max_duration, ")
                .append("avg(error_rate) as avg_error_rate, ")
                .append("sum(total_count) as total_requests, ")
                .append("sum(error_count) as total_errors, ")
                .append("sum(success_count) as total_success, ")
                .append("toDateTime(intDiv(toUInt32(window_start), 300) * 300) as time_window ")
                .append("FROM flink_operator_agg_result ")
                .append("WHERE window_start >= ? AND window_start <= ?");

        if (serviceName != null) {
            sqlBuilder.append(" AND service = ?");
        }

        sqlBuilder.append(" GROUP BY service, instance, method, operator_name, time_window ")
                .append("ORDER BY time_window DESC");

        Object[] params = serviceName != null
                ? new Object[] { startTime, endTime, serviceName }
                : new Object[] { startTime, endTime };

        return clickHouseJdbcTemplate.query(sqlBuilder.toString(), new PerformanceMetricsRowMapper(timeRangeHours), params);
    }

    /**
     * 查询异常数据
     * 基于 flink_operator_agg_result 表检测性能异常
     */
    public List<PerformanceAnomaly> getAnomalies(LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT " +
                "service, " +
                "instance, " +
                "method, " +
                "operator_name, " +
                "metric_name, " +
                "metric_value, " +
                "threshold_value, " +
                "anomaly_type, " +
                "severity, " +
                "window_start as timestamp, " +
                "description " +
                "FROM (" +
                // 高响应时间异常
                "SELECT " +
                "service, " +
                "instance, " +
                "method, " +
                "operator_name, " +
                "'avg_duration' as metric_name, " +
                "avg_duration as metric_value, " +
                "1000.0 as threshold_value, " +
                "'HIGH_RESPONSE_TIME' as anomaly_type, " +
                "'HIGH' as severity, " +
                "window_start, " +
                "concat('High response time detected: ', toString(round(avg_duration, 2)), 'ms for ', " +
                "coalesce(service, 'unknown'), '/', coalesce(method, 'unknown')) as description " +
                "FROM flink_operator_agg_result " +
                "WHERE window_start >= ? AND window_start <= ? " +
                "AND avg_duration > 1000.0 " +
                "AND avg_duration IS NOT NULL " +

                "UNION ALL " +

                // 高错误率异常
                "SELECT " +
                "service, " +
                "instance, " +
                "method, " +
                "operator_name, " +
                "'error_rate' as metric_name, " +
                "error_rate as metric_value, " +
                "5.0 as threshold_value, " +
                "'HIGH_ERROR_RATE' as anomaly_type, " +
                "CASE WHEN error_rate > 20 THEN 'CRITICAL' " +
                "     WHEN error_rate > 10 THEN 'HIGH' " +
                "     ELSE 'MEDIUM' END as severity, " +
                "window_start, " +
                "concat('High error rate detected: ', toString(round(error_rate, 2)), '% for ', " +
                "coalesce(service, 'unknown'), '/', coalesce(method, 'unknown')) as description " +
                "FROM flink_operator_agg_result " +
                "WHERE window_start >= ? AND window_start <= ? " +
                "AND error_rate > 5.0 " +
                "AND error_rate IS NOT NULL " +

                "UNION ALL " +

                // 异常高调用量（可能的性能瓶颈）
                "SELECT " +
                "service, " +
                "instance, " +
                "method, " +
                "operator_name, " +
                "'total_count' as metric_name, " +
                "total_count as metric_value, " +
                "10000.0 as threshold_value, " +
                "'HIGH_TRAFFIC' as anomaly_type, " +
                "'MEDIUM' as severity, " +
                "window_start, " +
                "concat('High traffic detected: ', toString(total_count), ' requests for ', " +
                "coalesce(service, 'unknown'), '/', coalesce(method, 'unknown')) as description " +
                "FROM flink_operator_agg_result " +
                "WHERE window_start >= ? AND window_start <= ? " +
                "AND total_count > 10000 " +
                "AND total_count IS NOT NULL " +
                ") " +
                "ORDER BY timestamp DESC, severity DESC";

        return clickHouseJdbcTemplate.query(sql, new PerformanceAnomalyRowMapper(),
                startTime, endTime, startTime, endTime, startTime, endTime);
    }

    /**
     * 保存性能分析报告
     */
    public void savePerformanceReport(String reportId, String reportContent, LocalDateTime createdTime) {
        String sql = "INSERT INTO ai_performance_reports (report_id, content, created_time, status) " +
                "VALUES (?, ?, ?, 'GENERATED')";

        // 转换 LocalDateTime 为 ClickHouse 兼容的格式
        String formattedTime = createdTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        clickHouseJdbcTemplate.update(sql, reportId, reportContent, formattedTime);
    }

    /**
     * 查询 events 表中的原始事件数据
     * 注意：由于 events 表结构可能动态变化，建议先查看实际表结构
     */
    public List<Map<String, Object>> getEventsSample(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        String sql = "SELECT * FROM events " +
                "WHERE start_time >= ? AND start_time <= ? " +
                "ORDER BY start_time DESC " +
                "LIMIT ?";

        return clickHouseJdbcTemplate.queryForList(sql, startTime, endTime, limit);
    }

    /**
     * 获取 events 表的列信息
     * 用于动态了解表结构
     */
    public List<Map<String, Object>> getEventsTableSchema() {
        String sql = "DESCRIBE TABLE events";
        return clickHouseJdbcTemplate.queryForList(sql);
    }

    /**
     * 查询服务调用链的错误详情
     * 从 events 表中查询具有错误的调用链详情
     */
    public List<Map<String, Object>> getErrorTraces(LocalDateTime startTime, LocalDateTime endTime,
            String serviceName) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ")
                .append("trace_id, ")
                .append("trace_segment_id, ")
                .append("service, ")
                .append("service_instance, ")
                .append("operation_name, ")
                .append("start_time, ")
                .append("end_time, ")
                .append("(end_time - start_time) * 1000 as duration_ms, ")
                .append("is_error, ")
                .append("log_error_kind, ")
                .append("log_message, ")
                .append("tag_http_status_code, ")
                .append("tag_http_method ")
                .append("FROM events ")
                .append("WHERE start_time >= ? AND start_time <= ? ")
                .append("AND is_error = 1 ");

        if (serviceName != null) {
            sqlBuilder.append("AND service = ? ");
        }

        sqlBuilder.append("ORDER BY start_time DESC ")
                .append("LIMIT 100"); // 限制返回数量

        Object[] params = serviceName != null
                ? new Object[] { startTime, endTime, serviceName }
                : new Object[] { startTime, endTime };

        return clickHouseJdbcTemplate.queryForList(sqlBuilder.toString(), params);
    }

    /**
     * 查询慢请求详情
     * 从 events 表中查询耗时超过阈值的请求
     */
    public List<Map<String, Object>> getSlowRequests(LocalDateTime startTime, LocalDateTime endTime,
            long durationThresholdMs, String serviceName) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ")
                .append("trace_id, ")
                .append("trace_segment_id, ")
                .append("service, ")
                .append("service_instance, ")
                .append("operation_name, ")
                .append("start_time, ")
                .append("end_time, ")
                .append("(end_time - start_time) * 1000 as duration_ms, ")
                .append("tag_http_method, ")
                .append("tag_url, ")
                .append("tag_db_statement, ")
                .append("tag_rpc_method_name ")
                .append("FROM events ")
                .append("WHERE start_time >= ? AND start_time <= ? ")
                .append("AND (end_time - start_time) * 1000 > ? ");

        if (serviceName != null) {
            sqlBuilder.append("AND service = ? ");
        }

        sqlBuilder.append("ORDER BY duration_ms DESC ")
                .append("LIMIT 100"); // 限制返回数量

        Object[] params = serviceName != null
                ? new Object[] { startTime, endTime, durationThresholdMs, serviceName }
                : new Object[] { startTime, endTime, durationThresholdMs };

        return clickHouseJdbcTemplate.queryForList(sqlBuilder.toString(), params);
    }

    /**
     * 查询服务拓扑关系
     * 从 events 表中分析服务间的调用关系
     */
    public List<Map<String, Object>> getServiceTopology(LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT " +
                "service as source_service, " +
                "refs_parent_service as target_service, " +
                "count(*) as call_count, " +
                "avg((end_time - start_time) * 1000) as avg_duration_ms, " +
                "sum(is_error) as error_count " +
                "FROM events " +
                "WHERE start_time >= ? AND start_time <= ? " +
                "AND refs_parent_service IS NOT NULL " +
                "AND refs_parent_service != '' " + "GROUP BY source_service, target_service " +
                "ORDER BY call_count DESC " +
                "LIMIT 50"; // 限制返回数量

        return clickHouseJdbcTemplate.queryForList(sql, startTime, endTime);
    }

    /**
     * 从 ClickHouse 检索性能分析报告列表
     */
    public List<Map<String, Object>> getPerformanceReportsFromClickHouse(int limit) {
        String sql = "SELECT report_id, content, created_time, status, analysis_window_start, analysis_window_end, service_name, report_type, metadata "
                +
                "FROM ai_performance_reports " +
                "ORDER BY created_time DESC " +
                "LIMIT ?";

        return clickHouseJdbcTemplate.queryForList(sql, limit);
    }

    /**
     * 根据 ID 从 ClickHouse 检索特定报告
     */
    public Map<String, Object> getPerformanceReportByIdFromClickHouse(String reportId) {
        String sql = "SELECT report_id, content, created_time, status, analysis_window_start, analysis_window_end, service_name, report_type, metadata "
                +
                "FROM ai_performance_reports " +
                "WHERE report_id = ? " +
                "ORDER BY created_time DESC " +
                "LIMIT 1";

        List<Map<String, Object>> results = clickHouseJdbcTemplate.queryForList(sql, reportId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 性能指标行映射器
     */
    private static class PerformanceMetricsRowMapper implements RowMapper<PerformanceMetrics> {
        private final int timeRangeHours;
        
        public PerformanceMetricsRowMapper(int timeRangeHours) {
            this.timeRangeHours = timeRangeHours;
        }
        
        @Override
        public PerformanceMetrics mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return PerformanceMetrics.builder()
                    .avgResponseTime(rs.getDouble("avg_duration")) // 使用 avg_duration 字段
                    .maxResponseTime(rs.getDouble("max_duration")) // 使用 max_duration 字段
                    .totalRequests(rs.getLong("total_requests")) // 使用 total_requests 聚合结果
                    .failedRequests(rs.getLong("total_errors")) // 使用 total_errors 聚合结果
                    .errorRate(rs.getDouble("avg_error_rate")) // 使用 avg_error_rate 字段
                    .startTime(rs.getTimestamp("time_window").toLocalDateTime())
                    .endTime(rs.getTimestamp("time_window").toLocalDateTime().plusMinutes(5))
                    .timeRangeHours(timeRangeHours) // 使用传入的时间范围
                    .build();
        }
    }

    /**
     * 性能异常行映射器
     */
    private static class PerformanceAnomalyRowMapper implements RowMapper<PerformanceAnomaly> {
        @Override
        public PerformanceAnomaly mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            String service = rs.getString("service");
            String method = rs.getString("method");
            String component = (service != null ? service : "unknown") +
                    (method != null ? "/" + method : "");

            return PerformanceAnomaly.builder()
                    .detectedAt(rs.getTimestamp("timestamp").toLocalDateTime())
                    .name(rs.getString("metric_name"))
                    .description(rs.getString("description"))
                    .metric(rs.getString("metric_name"))
                    .actualValue(rs.getDouble("metric_value"))
                    .expectedValue(rs.getDouble("threshold_value"))
                    .affectedComponent(component)
                    .type(mapAnomalyType(rs.getString("anomaly_type")))
                    .severity(mapSeverity(rs.getString("severity")))
                    .deviationPercentage(
                            calculateDeviation(rs.getDouble("metric_value"), rs.getDouble("threshold_value")))
                    .build();
        }

        private PerformanceAnomaly.AnomalyType mapAnomalyType(String type) {
            switch (type) {
                case "HIGH_RESPONSE_TIME":
                    return PerformanceAnomaly.AnomalyType.APPLICATION_RESPONSE_TIME_HIGH;
                case "HIGH_ERROR_RATE":
                    return PerformanceAnomaly.AnomalyType.APPLICATION_ERROR_RATE_HIGH;
                case "HIGH_TRAFFIC":
                    return PerformanceAnomaly.AnomalyType.APPLICATION_THROUGHPUT_LOW; // 高流量可能导致吞吐量问题
                default:
                    return PerformanceAnomaly.AnomalyType.CUSTOM_THRESHOLD_BREACH;
            }
        }

        private PerformanceAnomaly.Severity mapSeverity(String severity) {
            switch (severity.toUpperCase()) {
                case "HIGH":
                    return PerformanceAnomaly.Severity.HIGH;
                case "MEDIUM":
                    return PerformanceAnomaly.Severity.MEDIUM;
                case "LOW":
                    return PerformanceAnomaly.Severity.LOW;
                default:
                    return PerformanceAnomaly.Severity.MEDIUM;
            }
        }

        private double calculateDeviation(double actual, double expected) {
            if (expected == 0)
                return 0.0;
            return ((actual - expected) / expected) * 100;
        }
    }

    /**
     * 通过单次聚合查询获取所有性能指标
     */
    public PerformanceMetrics getAggregatedPerformanceMetrics(LocalDateTime startTime, LocalDateTime endTime, String service) {
        // 计算时间范围（分钟）
        int timeRangeMinutes = (int) Duration.between(startTime, endTime).toMinutes();
        // Also fetch baseline metrics from 24 hours ago
        LocalDateTime baselineStartTime = startTime.minusDays(1);
        LocalDateTime baselineEndTime = endTime.minusDays(1);

        String sql = "SELECT " +
            // Current App Metrics
            "countIf(start_time >= ? AND start_time <= ? AND span_layer IN ('Web', 'Http', 'RPCFramework')) as total_requests, " +
            "avgIf((end_time - start_time) * 1000, start_time >= ? AND start_time <= ? AND span_layer IN ('Web', 'Http', 'RPCFramework')) as avg_response_time, " +
            "maxIf((end_time - start_time) * 1000, start_time >= ? AND start_time <= ? AND span_layer IN ('Web', 'Http', 'RPCFramework')) as max_response_time, " +
            "sumIf(is_error, start_time >= ? AND start_time <= ? AND span_layer IN ('Web', 'Http', 'RPCFramework')) as failed_requests, " +

            // Baseline App Metrics
            "countIf(start_time >= ? AND start_time <= ? AND span_layer IN ('Web', 'Http', 'RPCFramework')) as baseline_total_requests, " +
            "avgIf((end_time - start_time) * 1000, start_time >= ? AND start_time <= ? AND span_layer IN ('Web', 'Http', 'RPCFramework')) as baseline_avg_response_time, " +

            // Current DB Metrics
            "countIf(start_time >= ? AND start_time <= ? AND span_layer = 'Database') as total_queries, " +
            "avgIf((end_time - start_time) * 1000, start_time >= ? AND start_time <= ? AND span_layer = 'Database') as avg_query_duration, " +
            "sumIf((end_time - start_time) * 1000 > 1000, start_time >= ? AND start_time <= ? AND span_layer = 'Database') as slow_queries, " +

            // Current JVM Heap Metrics
            "avgIf(tag_jvm_heap_used_type_Int64, start_time >= ? AND start_time <= ?) as avg_heap_used, " +
            "maxIf(tag_jvm_heap_used_type_Int64, start_time >= ? AND start_time <= ?) as max_heap_used, " +
            "avgIf(tag_jvm_heap_max_type_Int64, start_time >= ? AND start_time <= ?) as avg_heap_max, " +
            "avgIf(tag_jvm_heap_committed_type_Int64, start_time >= ? AND start_time <= ?) as heap_committed, " +
            "avgIf(tag_jvm_heap_init_type_Int64, start_time >= ? AND start_time <= ?) as heap_init, " +

            // Current JVM Non-Heap Metrics
            "avgIf(tag_jvm_nonheap_used_type_Int64, start_time >= ? AND start_time <= ?) as avg_nonheap_used, " +
            "maxIf(tag_jvm_nonheap_used_type_Int64, start_time >= ? AND start_time <= ?) as max_nonheap_used, " +
            "avgIf(tag_jvm_nonheap_committed_type_Int64, start_time >= ? AND start_time <= ?) as nonheap_committed, " +
            "avgIf(tag_jvm_nonheap_init_type_Int64, start_time >= ? AND start_time <= ?) as nonheap_init, " +
            "avgIf(tag_jvm_nonheap_max_type_Int64, start_time >= ? AND start_time <= ?) as nonheap_max, " +

            // Current Thread Metrics
            "avgIf(tag_thread_count_type_Int64, start_time >= ? AND start_time <= ? AND tag_thread_count_type_Int64 IS NOT NULL) as avg_thread_count, " +
            "maxIf(tag_thread_peak_count_type_Int64, start_time >= ? AND start_time <= ? AND tag_thread_peak_count_type_Int64 IS NOT NULL) as max_thread_peak_count, " +
            "avgIf(tag_thread_daemon_count_type_Int64, start_time >= ? AND start_time <= ? AND tag_thread_daemon_count_type_Int64 IS NOT NULL) as thread_daemon_count, " +
            "avgIf(tag_thread_total_started_count_type_Int64, start_time >= ? AND start_time <= ? AND tag_thread_total_started_count_type_Int64 IS NOT NULL) as thread_total_started_count, " +

            // Current CPU Metrics (CPU时间是纳秒单位，需要计算CPU使用率)
            "avgIf(tag_thread_current_cpu_time_type_Int64, start_time >= ? AND start_time <= ? AND tag_thread_current_cpu_time_type_Int64 IS NOT NULL) as avg_cpu_time_ns, " +
            "avgIf(tag_thread_current_user_time_type_Int64, start_time >= ? AND start_time <= ? AND tag_thread_current_user_time_type_Int64 IS NOT NULL) as avg_user_time_ns, " +

            // Current System Memory Metrics
            "avgIf(tag_Total_Memory_type_Int64, start_time >= ? AND start_time <= ?) as avg_total_memory, " +
            "avgIf(tag_Available_Memory_type_Int64, start_time >= ? AND start_time <= ?) as avg_available_memory, " +
            
            // Current GC Metrics (基于实际ClickHouse字段)
            "avgIf(tag_gc_total_time_type_Int64, start_time >= ? AND start_time <= ? AND tag_gc_total_time_type_Int64 IS NOT NULL) as avg_gc_total_time, " +
            "avgIf(tag_gc_total_collections_type_Int64, start_time >= ? AND start_time <= ? AND tag_gc_total_collections_type_Int64 IS NOT NULL) as avg_gc_total_collections, " +
            "avgIf(tag_gc_g1_old_generation_count_type_Int64, start_time >= ? AND start_time <= ? AND tag_gc_g1_old_generation_count_type_Int64 IS NOT NULL) as avg_g1_old_gen_count, " +
            "avgIf(tag_gc_g1_old_generation_time_type_Int64, start_time >= ? AND start_time <= ? AND tag_gc_g1_old_generation_time_type_Int64 IS NOT NULL) as avg_g1_old_gen_time, " +
            "avgIf(tag_gc_g1_young_generation_count_type_Int64, start_time >= ? AND start_time <= ? AND tag_gc_g1_young_generation_count_type_Int64 IS NOT NULL) as avg_g1_young_gen_count, " +
            "avgIf(tag_gc_g1_young_generation_time_type_Int64, start_time >= ? AND start_time <= ? AND tag_gc_g1_young_generation_time_type_Int64 IS NOT NULL) as avg_g1_young_gen_time " +

            "FROM events " +
            "WHERE service = ? AND ((start_time >= ? AND start_time <= ?) OR (start_time >= ? AND start_time <= ?))";

        return clickHouseJdbcTemplate.queryForObject(sql, new Object[]{
            // Current time range for app metrics
            startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime,
            // Baseline time range for app metrics
            baselineStartTime, baselineEndTime, baselineStartTime, baselineEndTime,
            // Current time range for DB metrics
            startTime, endTime, startTime, endTime, startTime, endTime,
            // Current time range for JVM heap metrics
            startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime,
            // Current time range for JVM non-heap metrics
            startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime,
            // Current time range for thread metrics
            startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime,
            // Current time range for CPU metrics
            startTime, endTime, startTime, endTime,
            // Current time range for system memory metrics
            startTime, endTime, startTime, endTime,
            // Current time range for GC metrics (6个字段 x 2参数 = 12个参数)
            startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime, startTime, endTime,
            // Service and WHERE clause time ranges
            service, startTime, endTime, baselineStartTime, baselineEndTime
        }, (rs, rowNum) -> {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.setStartTime(startTime);
            metrics.setEndTime(endTime);
            metrics.setService(service);
            metrics.setTimeRangeHours(timeRangeMinutes / 60); // 转换为小时用于向后兼容

            // App Metrics
            metrics.setTotalRequests(rs.getLong("total_requests"));
            metrics.setAvgResponseTime(rs.getDouble("avg_response_time"));
            metrics.setMaxResponseTime(rs.getDouble("max_response_time"));
            metrics.setFailedRequests(rs.getLong("failed_requests"));
            if (metrics.getTotalRequests() > 0) {
                metrics.setErrorRate(metrics.getFailedRequests() / (double) metrics.getTotalRequests());
                // 计算平均吞吐量: 总请求数 / 时间范围（秒）
                double timeRangeSeconds = timeRangeMinutes * 60.0;
                metrics.setAvgThroughput(metrics.getTotalRequests() / timeRangeSeconds);
            }

            // DB Metrics
            metrics.setTotalQueries(rs.getLong("total_queries"));
            metrics.setAvgQueryDuration(rs.getDouble("avg_query_duration"));
            metrics.setSlowQueries(rs.getLong("slow_queries"));

            // JVM Heap Metrics
            metrics.setAvgHeapUsed(rs.getDouble("avg_heap_used"));
            metrics.setMaxHeapUsed(rs.getDouble("max_heap_used"));
            metrics.setHeapCommitted(rs.getLong("heap_committed"));
            metrics.setHeapInit(rs.getLong("heap_init"));
            metrics.setHeapMax(rs.getLong("avg_heap_max"));
            double avgHeapMax = rs.getDouble("avg_heap_max");
            if (avgHeapMax > 0) {
                metrics.setMaxHeapUsedRatio(rs.getDouble("max_heap_used") / avgHeapMax);
            }

            // JVM Non-Heap Metrics
            metrics.setAvgNonHeapUsed(rs.getDouble("avg_nonheap_used"));
            metrics.setMaxNonHeapUsed(rs.getDouble("max_nonheap_used"));
            metrics.setNonHeapCommitted(rs.getLong("nonheap_committed"));
            metrics.setNonHeapInit(rs.getLong("nonheap_init"));
            metrics.setNonHeapMax(rs.getLong("nonheap_max"));

            // Thread Metrics
            double avgThreadCount = rs.getDouble("avg_thread_count");
            metrics.setAvgThreadCount(rs.wasNull() ? 0 : (int) avgThreadCount);
            double maxThreadPeak = rs.getDouble("max_thread_peak_count");
            metrics.setMaxThreadPeakCount(rs.wasNull() ? 0 : (int) maxThreadPeak);
            double threadDaemon = rs.getDouble("thread_daemon_count");
            metrics.setThreadDaemonCount(rs.wasNull() ? 0 : (int) threadDaemon);
            double threadTotalStarted = rs.getDouble("thread_total_started_count");
            metrics.setThreadTotalStartedCount(rs.wasNull() ? 0 : (int) threadTotalStarted);

            // CPU Metrics - 转换纳秒到合适的单位
            // CPU时间是累积值，需要计算CPU使用率
            double avgCpuTimeNs = rs.getDouble("avg_cpu_time_ns");
            if (!rs.wasNull() && avgCpuTimeNs > 0) {
                // 简化计算：将CPU时间转换为秒并计算相对使用率
                // 这里使用启发式方法，实际CPU使用率需要时间间隔计算
                double timeRangeSeconds = timeRangeMinutes * 60.0;
                double cpuTimeSeconds = avgCpuTimeNs / 1_000_000_000.0;
                // 估算CPU使用率（这是累积时间，实际使用率会更低）
                metrics.setAvgCpuUsage(Math.min(100.0, (cpuTimeSeconds / timeRangeSeconds) * 100));
            } else {
                metrics.setAvgCpuUsage(0.0);
            }

            // System Memory Metrics
            double totalMemory = rs.getDouble("avg_total_memory");
            double availableMemory = rs.getDouble("avg_available_memory");
            if (totalMemory > 0 && availableMemory > 0) {
                double usedMemory = totalMemory - availableMemory;
                metrics.setAvgMemoryUsage((usedMemory / totalMemory) * 100);
                metrics.setAvgSystemCpuUsage(metrics.getAvgCpuUsage()); // 系统CPU使用率暂时使用线程CPU时间
            }
            
            // GC Metrics (基于实际ClickHouse字段)
            double avgGcTotalTime = rs.getDouble("avg_gc_total_time");
            metrics.setTotalGcTime(rs.wasNull() ? 0L : (long) avgGcTotalTime);
            
            double avgGcTotalCollections = rs.getDouble("avg_gc_total_collections");
            metrics.setTotalGcCollections(rs.wasNull() ? 0L : (long) avgGcTotalCollections);
            
            double avgG1OldGenCount = rs.getDouble("avg_g1_old_gen_count");
            metrics.setG1OldGenerationCount(rs.wasNull() ? 0L : (long) avgG1OldGenCount);
            
            double avgG1OldGenTime = rs.getDouble("avg_g1_old_gen_time");
            metrics.setG1OldGenerationTime(rs.wasNull() ? 0L : (long) avgG1OldGenTime);
            
            double avgG1YoungGenCount = rs.getDouble("avg_g1_young_gen_count");
            metrics.setG1YoungGenerationCount(rs.wasNull() ? 0L : (long) avgG1YoungGenCount);
            
            double avgG1YoungGenTime = rs.getDouble("avg_g1_young_gen_time");
            metrics.setG1YoungGenerationTime(rs.wasNull() ? 0L : (long) avgG1YoungGenTime);
            
            // 计算GC衍生指标
            if (metrics.getTotalGcCollections() > 0) {
                metrics.setAvgGcTimePerCollection((double) metrics.getTotalGcTime() / metrics.getTotalGcCollections());
            }
            
            // GC时间占总时间比例
            double timeRangeMillis = timeRangeMinutes * 60.0 * 1000.0;
            if (timeRangeMillis > 0) {
                metrics.setGcTimeRatio((metrics.getTotalGcTime() / timeRangeMillis) * 100);
            }
            
            // GC频率 (次/小时) - 转换分钟为小时
            double timeRangeHours = timeRangeMinutes / 60.0;
            if (timeRangeHours > 0) {
                metrics.setYoungGenGcFrequency((double) metrics.getG1YoungGenerationCount() / timeRangeHours);
                metrics.setOldGenGcFrequency((double) metrics.getG1OldGenerationCount() / timeRangeHours);
            }

            // Baseline Metrics
            PerformanceMetrics baselineMetrics = new PerformanceMetrics();
            baselineMetrics.setTotalRequests(rs.getLong("baseline_total_requests"));
            baselineMetrics.setAvgResponseTime(rs.getDouble("baseline_avg_response_time"));
            metrics.setBaselineMetrics(baselineMetrics);

            return metrics;
        });
    }

    /**
     * 获取指定时间范围内的不同服务名称列表
     */
    public List<String> getDistinctServices(LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT DISTINCT service FROM events WHERE start_time >= ? AND start_time <= ? AND service IS NOT NULL AND service != '' ORDER BY service";
        return clickHouseJdbcTemplate.queryForList(sql, String.class, startTime, endTime);
    }
}
