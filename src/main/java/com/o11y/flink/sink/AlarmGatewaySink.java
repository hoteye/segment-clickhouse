package com.o11y.flink.sink;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * 模拟告警网关方法，可替换为实际 HTTP/Kafka/MQ Sink
 */
public class AlarmGatewaySink implements SinkFunction<String> {
    /**
     * 发送告警信息到网关（可替换为 HTTP/Kafka/MQ Sink）。
     * 
     * @param value   告警内容
     * @param context Flink sink 上下文
     */
    @Override
    public void invoke(String value, Context context) {
        // 实际生产可改为 HTTP、Kafka、MQ 等
        System.out.println("[ALARM_GATEWAY] " + value);
    }
}
