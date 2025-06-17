package com.o11y.flink.operator.model;

import java.io.Serializable;

/**
 * 聚合结果 POJO，与 flink_operator_agg_result 表字段保持一致
 */
public class ServiceAggResult implements Serializable {
    public long windowStart; // window_start
    public int windowSize; // windowSize
    public String operatorName; // operator_name
    public String operatorClass; // operator_class
    public String service; // service
    public String instance; // instance
    public String method; // method
    public Double avgDuration; // avg_duration
    public Long maxDuration; // max_duration
    public Double errorRate; // error_rate
    public String dataCenter; // data_center
    public String region; // region
    public String env; // env
    public Long totalCount; // total_count
    public Long errorCount; // error_count
    public Long successCount; // success_count

    public String getKey() {
        return service + "|" + operatorName;
    }

    public ServiceAggResult() {
    }

    public ServiceAggResult(long windowStart, int windowSize, String operatorName, String operatorClass, String service,
            String instance,
            String method,
            Double avgDuration, Long maxDuration, Double errorRate, String dataCenter, String region, String env,
            Long totalCount, Long errorCount, Long successCount) {
        this.windowStart = windowStart;
        this.windowSize = windowSize;
        this.operatorName = operatorName;
        this.operatorClass = operatorClass;
        this.service = service;
        this.instance = instance;
        this.method = method;
        this.avgDuration = avgDuration;
        this.maxDuration = maxDuration;
        this.errorRate = errorRate;
        this.dataCenter = dataCenter;
        this.region = region;
        this.env = env;
        this.totalCount = totalCount;
        this.errorCount = errorCount;
        this.successCount = successCount;
    }

    @Override
    public String toString() {
        return "ServiceAggResult{" +
                "windowStart=" + windowStart +
                ", windowSize=" + windowSize +
                ", operatorName='" + operatorName + '\'' +
                ", operatorClass='" + operatorClass + '\'' +
                ", service='" + service + '\'' +
                ", instance='" + instance + '\'' +
                ", method='" + method + '\'' +
                ", avgDuration=" + avgDuration +
                ", maxDuration=" + maxDuration +
                ", errorRate=" + errorRate +
                ", dataCenter='" + dataCenter + '\'' +
                ", region='" + region + '\'' +
                ", env='" + env + '\'' +
                ", totalCount=" + totalCount +
                ", errorCount=" + errorCount +
                ", successCount=" + successCount +
                '}';
    }
}
