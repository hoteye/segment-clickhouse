package com.o11y.ai.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 性能异常数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAnomaly {

    private String anomalyId;
    private LocalDateTime detectedAt;
    private AnomalyType type;
    private Severity severity;
    private String name;
    private String description;
    private String metric;
    private double actualValue;
    private double expectedValue;
    private double deviationPercentage;
    private String affectedComponent;
    private long durationSeconds;
    private String rootCause;
    private String recommendedAction;
    private Map<String, Object> context;

    /**
     * 异常类型枚举
     */
    public enum AnomalyType {
        // JVM 相关异常
        JVM_HEAP_MEMORY_HIGH("JVM堆内存过高"),
        JVM_NON_HEAP_MEMORY_HIGH("JVM非堆内存过高"),
        JVM_THREAD_COUNT_HIGH("JVM线程数过高"),
        JVM_CPU_USAGE_HIGH("JVM CPU使用率过高"),
        JVM_GC_TIME_HIGH("JVM GC时间过长"),
        JVM_GC_FREQUENCY_HIGH("JVM GC频率过高"),

        // 数据库相关异常
        DATABASE_QUERY_SLOW("数据库查询响应慢"),
        DATABASE_CONNECTION_HIGH("数据库连接数过高"),
        DATABASE_ERROR_RATE_HIGH("数据库错误率过高"),
        DATABASE_DEADLOCK("数据库死锁"),

        // 应用性能异常
        APPLICATION_RESPONSE_TIME_HIGH("应用响应时间过长"),
        APPLICATION_THROUGHPUT_LOW("应用吞吐量过低"),
        APPLICATION_ERROR_RATE_HIGH("应用错误率过高"),
        APPLICATION_TIMEOUT("应用请求超时"),

        // 系统资源异常
        SYSTEM_CPU_HIGH("系统CPU使用率过高"),
        SYSTEM_MEMORY_HIGH("系统内存使用率过高"),
        SYSTEM_DISK_HIGH("系统磁盘使用率过高"),
        SYSTEM_NETWORK_HIGH("网络流量异常"),

        // 链路追踪异常
        TRACING_SPAN_DURATION_HIGH("链路Span耗时过长"),
        TRACING_ERROR_RATE_HIGH("链路错误率过高"),
        TRACING_SPAN_COUNT_HIGH("单链路Span数量过多"),

        // 业务逻辑异常
        BUSINESS_METRIC_ANOMALY("业务指标异常"),
        CUSTOM_THRESHOLD_BREACH("自定义阈值突破");

        private final String description;

        AnomalyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 严重程度枚举
     */
    public enum Severity {
        CRITICAL("严重", 1),
        HIGH("高", 2),
        MEDIUM("中等", 3),
        LOW("低", 4),
        INFO("信息", 5);

        private final String description;
        private final int priority;

        Severity(String description, int priority) {
            this.description = description;
            this.priority = priority;
        }

        public String getDescription() {
            return description;
        }

        public int getPriority() {
            return priority;
        }
    }
}
