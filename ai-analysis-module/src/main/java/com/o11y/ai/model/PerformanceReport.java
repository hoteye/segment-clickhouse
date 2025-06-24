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
    private int timeRange;
    private String summary;
    private String intelligentAnalysis;
    private List<String> optimizationSuggestions;
    private ReportMetrics metrics;
    private List<PerformanceAnomaly> anomalies;
    private List<String> errorStacks;

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
}
