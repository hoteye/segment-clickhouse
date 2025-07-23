package com.o11y.ai.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 性能分析报告模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceReport {

    private String reportId;
    private LocalDateTime generatedAt;
    private int timeRange; // 保持向后兼容，单位为分钟
    private String summary;
    private String intelligentAnalysis;
    private List<String> optimizationSuggestions;
    private ReportMetrics metrics;
    private List<PerformanceAnomaly> anomalies;
    private List<String> errorStacks;
    
    // 新增：慢交易分析详情
    private SlowTransactionAnalysis slowTransactionAnalysis;

    /**
     * 简化的指标数据模型
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportMetrics {
        private double avgResponseTime;
        private double avgThroughput;
        private double errorRate;
        private double avgCpuUsage;
        private double avgMemoryUsage;
        private long totalRequests;
        private long totalErrors;
    }

    /**
     * 慢交易分析详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowTransactionAnalysis {
        private List<SlowTransaction> topSlowTransactions;        // TOP慢交易列表
        private List<OperationPerformance> operationStats;       // 操作性能统计  
        private List<AnomalyTrace> anomalyTraces;               // 异常trace列表
        private List<DatabaseOperation> slowDatabaseOps;        // 慢数据库操作
        private String analysis;                                // AI分析结果
        private int totalAnalyzedTransactions;                  // 分析的总交易数
    }

    /**
     * 慢交易详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlowTransaction {
        private String traceId;
        private String traceSegmentId;
        private String service;
        private String serviceInstance;
        private String operationName;
        private double durationMs;
        private boolean isError;
        private String httpMethod;
        private String url;
        private String dbStatement;
        private String rpcMethodName;
        private Integer httpStatusCode;
        private String errorMessage;
        private LocalDateTime startTime;
    }

    /**
     * 操作性能统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationPerformance {
        private String operationName;
        private long totalCalls;
        private double avgDurationMs;
        private double maxDurationMs;
        private double minDurationMs;
        private double p50DurationMs;
        private double p90DurationMs;
        private double p95DurationMs;
        private double p99DurationMs;
        private long errorCount;
        private double errorRate;
    }

    /**
     * 异常trace信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyTrace {
        private String traceId;
        private String service;
        private String operationName;
        private double durationMs;
        private boolean isError;
        private LocalDateTime startTime;
        private Integer httpStatusCode;
        private String errorKind;
        private String errorMessage;
    }

    /**
     * 数据库操作性能
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseOperation {
        private String sqlStatement;
        private long executionCount;
        private double avgDurationMs;
        private double maxDurationMs;
        private long errorCount;
        private List<String> sampleTraceIds;
    }
}
