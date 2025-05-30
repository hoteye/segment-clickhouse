package com.o11y.flink.serde;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import segment.v3.Segment.SegmentObject;
import java.io.IOException;

/**
 * SegmentObject 反序列化
 */
public class SegmentDeserializationSchema implements DeserializationSchema<SegmentObject> {
    private static final Logger LOG = LoggerFactory.getLogger(SegmentDeserializationSchema.class);

    @Override
    public SegmentObject deserialize(byte[] message) throws IOException {
        try {
            SegmentObject obj = SegmentObject.parseFrom(message);
            LOG.debug("Deserialization succeeded, byte length: {}, traceId: {}", message.length, obj.getTraceId());
            return obj;
        } catch (Exception e) {
            LOG.error("Deserialization failed, byte length: {}, exception: {}", message.length, e.getMessage(), e);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(16, message.length); i++) {
                hex.append(String.format("%02x ", message[i]));
            }
            LOG.error("First 16 bytes: {}", hex.toString());
            throw new IOException("Failed to deserialize consumer record due to: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isEndOfStream(SegmentObject nextElement) {
        return false;
    }

    @Override
    public TypeInformation<SegmentObject> getProducedType() {
        return TypeInformation.of(SegmentObject.class);
    }
}
