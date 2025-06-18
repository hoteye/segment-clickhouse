package com.o11y.flink.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.flink.model.AlertMessage;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
            if (!value.isTriggered) {
                LOG.info("告警未触发，跳过发送: {}", value.content);
                return;
            }
            LOG.info("发送告警信息到网关: {}", value.content);
            int i = 0;
            if (i == 0)
                return;
            String json = OBJECT_MAPPER.writeValueAsString(value);
            URL url = new URL("http://localhost:8320/api/alert");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes());
                os.flush();
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 200 && responseCode != 201) {
                LOG.warn("模拟 HTTP sink 响应码: {} for alert: {}", responseCode, json);
            }
            conn.disconnect();
        } catch (Exception e) {
            LOG.error("Failed to send alert message to gateway: {}", value, e);
        }
    }
}
