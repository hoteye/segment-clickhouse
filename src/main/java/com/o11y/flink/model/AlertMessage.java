package com.o11y.flink.model;

import java.io.Serializable;

/**
 * 结构化告警消息对象，便于下游系统处理。
 */
public class AlertMessage implements Serializable {
    public String service;
    public String operatorName;
    public String alertLevel;
    public long alertTime;
    public String content; // 分析报告或简要描述

    public AlertMessage(String service, String operatorName, String alertLevel, long alertTime, String content) {
        this.service = service;
        this.operatorName = operatorName;
        this.alertLevel = alertLevel;
        this.alertTime = alertTime;
        this.content = content;
    }

    @Override
    public String toString() {
        return String.format("[ALERT] %s | %s | %s | %d | %s", service, operatorName, alertLevel, alertTime, content);
    }
}
