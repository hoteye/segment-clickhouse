package com.o11y.flink.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.flink.model.AlertMessage;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * 模拟告警网关方法，可替换为实际 HTTP/Kafka/MQ Sink
 */
public class AlarmGatewaySink implements SinkFunction<AlertMessage> {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AlarmGatewaySink.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 发送告警信息到网关（可替换为 HTTP/Kafka/MQ Sink）。
     * 
     * @param value   告警内容
     * @param context Flink sink 上下文
     */
    @Override
    public void invoke(AlertMessage value, Context context) {
        try {
            // 输出结构化 JSON，便于下游系统处理
            String json = OBJECT_MAPPER.writeValueAsString(value);
            // 模拟发送到告警网关（实际应用中可替换为 HTTP 请求、Kafka 生产者等）
        } catch (Exception e) {
            LOG.error("Failed to send alert message to gateway: {}", value, e);
        }
    }
}
