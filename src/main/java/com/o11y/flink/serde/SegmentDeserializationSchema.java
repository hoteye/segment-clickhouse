package com.o11y.flink.serde;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import segment.v3.Segment.SegmentObject;
import java.io.IOException;

/**
 * SegmentObject 反序列化
 */
public class SegmentDeserializationSchema implements DeserializationSchema<SegmentObject> {
    @Override
    public SegmentObject deserialize(byte[] message) throws IOException {
        try {
            return SegmentObject.parseFrom(message);
        } catch (Exception e) {
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
