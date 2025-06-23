package com.o11y.ai.repository;

import com.o11y.ai.model.PerformanceMetrics;
import com.o11y.ai.model.PerformanceAnomaly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

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
     */
    public List<PerformanceMetrics> getServiceMetrics(LocalDateTime startTime, LocalDateTime endTime,
            String serviceName) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT ")
                .append("service_name, ")
                .append("avg(latency) as avg_latency, ")
                .append("max(latency) as max_latency, ")
                .append("min(latency) as min_latency, ")
                .append("count(*) as request_count, ")
                .append("countIf(is_error = 1) as error_count, ")
                .append("avg(cpu_usage) as avg_cpu, ")
                .append("avg(memory_usage) as avg_memory, ")
                .append("toDateTime(intDiv(toUInt32(timestamp), 300) * 300) as time_window ")
                .append("FROM service_agg_result ")
                .append("WHERE timestamp >= ? AND timestamp <= ?");

        if (serviceName != null) {
            sqlBuilder.append(" AND service_name = ?");
        }

        sqlBuilder.append(" GROUP BY service_name, time_window ")
                .append("ORDER BY time_window DESC");

        Object[] params = serviceName != null
                ? new Object[] { startTime, endTime, serviceName }
                : new Object[] { startTime, endTime };

        return clickHouseJdbcTemplate.query(sqlBuilder.toString(), new PerformanceMetricsRowMapper(), params);
    }

    /**
     * 查询异常数据
     */
    public List<PerformanceAnomaly> getAnomalies(LocalDateTime startTime, LocalDateTime endTime) {
        String sql = "SELECT " +
                "service_name, " +
                "metric_name, " +
                "metric_value, " +
                "threshold_value, " +
                "anomaly_type, " +
                "severity, " +
                "timestamp, " +
                "description " +
                "FROM (" +
                "SELECT " +
                "service_name, " +
                "'response_time' as metric_name, " +
                "latency as metric_value, " +
                "1000 as threshold_value, " +
                "'HIGH_LATENCY' as anomaly_type, " +
                "'HIGH' as severity, " +
                "timestamp, " +
                "concat('High response time detected: ', toString(latency), 'ms') as description " +
                "FROM service_agg_result " +
                "WHERE timestamp >= ? AND timestamp <= ? AND latency > 1000 " +
                "UNION ALL " +
                "SELECT " +
                "service_name, " +
                "'error_rate' as metric_name, " +
                "(countIf(is_error = 1) / count(*)) * 100 as metric_value, " +
                "5.0 as threshold_value, " +
                "'HIGH_ERROR_RATE' as anomaly_type, " +
                "'HIGH' as severity, " +
                "timestamp, " +
                "concat('High error rate detected: ', toString(round((countIf(is_error = 1) / count(*)) * 100, 2)), '%') as description "
                +
                "FROM service_agg_result " +
                "WHERE timestamp >= ? AND timestamp <= ? " +
                "GROUP BY service_name, timestamp " +
                "HAVING (countIf(is_error = 1) / count(*)) * 100 > 5.0" +
                ") " +
                "ORDER BY timestamp DESC";

        return clickHouseJdbcTemplate.query(sql, new PerformanceAnomalyRowMapper(), startTime, endTime, startTime,
                endTime);
    }

    /**
     * 保存性能分析报告
     */
    public void savePerformanceReport(String reportId, String reportContent, LocalDateTime createdTime) {
        String sql = "INSERT INTO ai_performance_reports (report_id, content, created_time, status) " +
                "VALUES (?, ?, ?, 'GENERATED')";

        clickHouseJdbcTemplate.update(sql, reportId, reportContent, createdTime);
    }

    /**
     * 性能指标行映射器
     */
    private static class PerformanceMetricsRowMapper implements RowMapper<PerformanceMetrics> {
        @Override
        public PerformanceMetrics mapRow(ResultSet rs, int rowNum) throws SQLException {
            return PerformanceMetrics.builder()
                    .avgResponseTime(rs.getDouble("avg_latency"))
                    .maxResponseTime(rs.getDouble("max_latency"))
                    .totalRequests(rs.getLong("request_count"))
                    .failedRequests(rs.getLong("error_count"))
                    .avgCpuUsage(rs.getDouble("avg_cpu"))
                    .avgMemoryUsage(rs.getDouble("avg_memory"))
                    .startTime(rs.getTimestamp("time_window").toLocalDateTime())
                    .endTime(rs.getTimestamp("time_window").toLocalDateTime().plusMinutes(5))
                    .errorRate(rs.getLong("request_count") > 0
                            ? (double) rs.getLong("error_count") / rs.getLong("request_count") * 100
                            : 0.0)
                    .build();
        }
    }

    /**
     * 性能异常行映射器
     */
    private static class PerformanceAnomalyRowMapper implements RowMapper<PerformanceAnomaly> {
        @Override
        public PerformanceAnomaly mapRow(ResultSet rs, int rowNum) throws SQLException {
            return PerformanceAnomaly.builder()
                    .detectedAt(rs.getTimestamp("timestamp").toLocalDateTime())
                    .name(rs.getString("metric_name"))
                    .description(rs.getString("description"))
                    .metric(rs.getString("metric_name"))
                    .actualValue(rs.getDouble("metric_value"))
                    .expectedValue(rs.getDouble("threshold_value"))
                    .affectedComponent(rs.getString("service_name"))
                    .type(mapAnomalyType(rs.getString("anomaly_type")))
                    .severity(mapSeverity(rs.getString("severity")))
                    .deviationPercentage(
                            calculateDeviation(rs.getDouble("metric_value"), rs.getDouble("threshold_value")))
                    .build();
        }

        private PerformanceAnomaly.AnomalyType mapAnomalyType(String type) {
            switch (type) {
                case "HIGH_LATENCY":
                    return PerformanceAnomaly.AnomalyType.APPLICATION_RESPONSE_TIME_HIGH;
                case "HIGH_ERROR_RATE":
                    return PerformanceAnomaly.AnomalyType.APPLICATION_ERROR_RATE_HIGH;
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
}
