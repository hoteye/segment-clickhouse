package com.o11y.flink.serde;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.flink.rule.AlarmRule;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Map;

/**
 * AlarmRule 的 JSON 反序列化实现，适配 Flink Kafka Source。
 * 支持 Map<String, AlarmRule> 批量规则格式。
 */
public class AlarmRuleDeserializationSchema implements DeserializationSchema<Map<String, AlarmRule>> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(AlarmRuleDeserializationSchema.class);

    @Override
    public Map<String, AlarmRule> deserialize(byte[] message) throws IOException {
        try {
            return objectMapper.readValue(message, new TypeReference<Map<String, AlarmRule>>() {
            });
        } catch (Exception e) {
            LOG.warn("Failed to deserialize AlarmRule map: {}", new String(message), e);
            return null;
        }
    }

    @Override
    public boolean isEndOfStream(Map<String, AlarmRule> nextElement) {
        return false;
    }

    @Override
    public TypeInformation<Map<String, AlarmRule>> getProducedType() {
        return TypeInformation.of(new TypeHint<Map<String, AlarmRule>>() {
        });
    }
}
