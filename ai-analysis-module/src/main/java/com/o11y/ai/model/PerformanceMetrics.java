package com.o11y.ai.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 简化的性能指标数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetrics {

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int timeRangeHours;

    // JVM 指标
    private double avgHeapUsed;
    private double maxHeapUsed;
    private int avgThreadCount;
    private double avgCpuUsage;
    private long totalGcTime;

    // 应用指标
    private long totalRequests;
    private double avgResponseTime;
    private double maxResponseTime;
    private long failedRequests;
    private double errorRate;
    private double avgThroughput;

    // 数据库指标
    private long totalQueries;
    private double avgQueryDuration;
    private long slowQueries;
    private int avgActiveConnections;

    // 系统指标
    private double avgSystemCpuUsage;
    private double avgMemoryUsage;
    private double avgDiskUsage;

    // 自定义指标
    private Map<String, Object> customMetrics;
}
