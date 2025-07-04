package com.o11y.domain.model.alarm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 告警规则对象，包含多级阈值和告警模板。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlarmRule {
    public String service; // 服务名称，通常是应用名或模块名
    public String operatorName; // 方法名称，可以是交易码
    public String operatorClass; // 算子类名
    // 成功率 高 中 低
    public Double successRateHigh;
    public Double successRateMid;
    public Double successRateLow;
    // 交易量 高 中 低
    public Double trafficVolumeHigh;
    public Double trafficVolumeMid;
    public Double trafficVolumeLow;
    // 最大延迟 高 中 低
    public Double maxDurationHigh;
    public Double maxDurationMid;
    public Double maxDurationLow;
    // 平均延迟 高 中 低
    public Double avgDurationHigh;
    public Double avgDurationMid;
    public Double avgDurationLow;
    // 告警模板，支持占位符替换
    public String alarmTemplate;
    // 样本数量，用于记录生成该规则时使用的数据样本数
    public Integer sampleCount;

    public AlarmRule() {
    }

    public String combine() {
        return service + "|" + operatorName;
    }

    @Override
    public String toString() {
        return "AlarmRule{" +
                "service='" + service + '\'' +
                ", operatorName='" + operatorName + '\'' +
                ", operatorClass='" + operatorClass + '\'' +
                ", successRateHigh=" + successRateHigh +
                ", successRateMid=" + successRateMid +
                ", successRateLow=" + successRateLow +
                ", trafficVolumeHigh=" + trafficVolumeHigh +
                ", trafficVolumeMid=" + trafficVolumeMid +
                ", trafficVolumeLow=" + trafficVolumeLow +
                ", maxDurationHigh=" + maxDurationHigh +
                ", maxDurationMid=" + maxDurationMid +
                ", maxDurationLow=" + maxDurationLow +
                ", avgDurationHigh=" + avgDurationHigh +
                ", avgDurationMid=" + avgDurationMid +
                ", avgDurationLow=" + avgDurationLow +
                ", alarmTemplate='" + alarmTemplate + '\'' +
                ", sampleCount=" + sampleCount +
                '}';
    }
}
