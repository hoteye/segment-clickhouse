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
    private int timeRangeHours; // 保持向后兼容
    private int timeRangeMinutes; // 新的分钟字段

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
    
    // GC 指标 (基于ClickHouse events表的实际字段)
    private long totalGcTime; // 总 GC 时间 (tag_gc_total_time_type_Int64)
    private long totalGcCollections; // 总 GC 次数 (tag_gc_total_collections_type_Int64)
    private long g1OldGenerationCount; // G1老年代GC次数 (tag_gc_g1_old_generation_count_type_Int64)
    private long g1OldGenerationTime; // G1老年代GC时间 (tag_gc_g1_old_generation_time_type_Int64)
    private long g1YoungGenerationCount; // G1新生代GC次数 (tag_gc_g1_young_generation_count_type_Int64)
    private long g1YoungGenerationTime; // G1新生代GC时间 (tag_gc_g1_young_generation_time_type_Int64)
    
    // GC 衍生指标
    private double avgGcTimePerCollection; // 平均每次GC时间
    private double gcTimeRatio; // GC时间占总时间比例
    private double youngGenGcFrequency; // 新生代GC频率 (次/小时)
    private double oldGenGcFrequency; // 老年代GC频率 (次/小时)

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

    // 基线指标 (用于对比分析)
    private PerformanceMetrics baselineMetrics;
}
