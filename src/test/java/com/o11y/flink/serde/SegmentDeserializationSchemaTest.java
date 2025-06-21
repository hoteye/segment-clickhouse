package com.o11y.flink.serde;

import com.o11y.stream.source.SegmentDeserializationSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import segment.v3.Segment.SegmentObject;
import java.io.IOException;

public class SegmentDeserializationSchemaTest {
    @Test
    public void testDeserializeValidMessage() throws IOException {
        // 构造一个合法的 SegmentObject 并序列化
        SegmentObject obj = SegmentObject.newBuilder().setTraceId("test-trace").build();
        byte[] bytes = obj.toByteArray();
        SegmentDeserializationSchema schema = new SegmentDeserializationSchema();
        SegmentObject result = schema.deserialize(bytes);
        Assertions.assertEquals("test-trace", result.getTraceId());
    }

    @Test
    public void testDeserializeInvalidMessage() {
        // 构造一个非法的字节流
        byte[] bytes = new byte[] { 0x01, 0x02, 0x03 };
        SegmentDeserializationSchema schema = new SegmentDeserializationSchema();
        Assertions.assertThrows(IOException.class, () -> schema.deserialize(bytes));
    }

    @Test
    public void testIsEndOfStreamAlwaysFalse() {
        SegmentDeserializationSchema schema = new SegmentDeserializationSchema();
        Assertions.assertFalse(schema.isEndOfStream(SegmentObject.getDefaultInstance()));
    }

    @Test
    public void testGetProducedType() {
        SegmentDeserializationSchema schema = new SegmentDeserializationSchema();
        Assertions.assertEquals(SegmentObject.class, schema.getProducedType().getTypeClass());
    }
}
