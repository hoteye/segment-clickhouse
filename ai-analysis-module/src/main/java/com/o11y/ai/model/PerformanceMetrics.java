package com.o11y.ai.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.security.PrivateKey;
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

    /**
     * 服务名称（用于分组统计和多服务分析）
     */
    private String service;
    // JVM 指标
    private double maxHeapUsedRatio; // 最大堆内存使用率
    private double avgHeapUsed; // 平均堆内存使用率
    private double maxHeapUsed; // 最大堆内存使用率
    private double avgNonHeapUsed; // 平均非堆内存使用率
    private double maxNonHeapUsed; // 最大非堆内存使用率
    private int avgThreadCount; // 平均线程数
    private int maxThreadPeakCount; // 最大线程峰值数
    private int threadDaemonCount; // 守护线程数
    private int threadTotalStartedCount; // 总线程启动数
    private long heapCommitted; // 堆内存已提交
    private long heapInit; // 堆内存初始大小
    private long heapMax; // 堆内存最大大小
    private long nonHeapCommitted; // 非堆内存已提交
    private long nonHeapInit; // 非堆内存初始大小
    private long nonHeapMax; // 非堆内存最大大小
    private double avgCpuUsage; // 平均 CPU 使用率
    private long totalGcTime; // 总 GC 时间

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
