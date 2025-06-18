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
    public String content;
    public boolean isTriggered;

    public AlertMessage(String service, String operatorName, String alertLevel, long alertTime, String content,
            boolean isTriggered) {
        this.service = service;
        this.operatorName = operatorName;
        this.alertLevel = alertLevel;
        this.alertTime = alertTime;
        this.content = content;
        this.isTriggered = isTriggered;
    }

    @Override
    public String toString() {
        return String.format("[ALERT] %s | %s | %s | %d | %s | %s", service, operatorName, alertLevel, alertTime,
                content, isTriggered);
    }
}
