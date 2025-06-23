package com.o11y.stream.source;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import segment.v3.Segment.SegmentObject;
import java.io.IOException;

/**
 * Segment 对象反序列化器。
 * 
 * <p>
 * 负责将从 Kafka 接收到的字节数组反序列化为 SegmentObject 对象。
 * 使用 Protocol Buffers 格式进行数据解析，确保高效的序列化/反序列化性能。
 * 
 * <p>
 * <strong>功能特性：</strong>
 * <ul>
 * <li>Protocol Buffers 格式解析</li>
 * <li>异常处理和错误包装</li>
 * <li>类型信息提供</li>
 * <li>流式处理优化</li>
 * </ul>
 * 
 * @see SegmentObject Skywalking Segment 数据模型
 * @see DeserializationSchema Flink 反序列化接口
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class SegmentDeserializationSchema implements DeserializationSchema<SegmentObject> {

    /**
     * 将字节数组反序列化为 SegmentObject。
     * 
     * <p>
     * 使用 Protocol Buffers 的 parseFrom 方法解析二进制数据。
     * 如果解析失败，会抛出 IOException 并包含详细错误信息。
     * 
     * @param message 来自 Kafka 的二进制消息数据
     * @return 解析后的 SegmentObject 实例
     * @throws IOException 如果反序列化失败，包含原始异常信息
     */
    @Override
    public SegmentObject deserialize(byte[] message) throws IOException {
        try {
            return SegmentObject.parseFrom(message);
        } catch (Exception e) {
            throw new IOException("Failed to deserialize consumer record due to: " + e.getMessage(), e);
        }
    }

    /**
     * 判断是否到达流的末尾。
     * 
     * <p>
     * 对于 Kafka 流数据源，始终返回 false，表示这是一个无界流，
     * 数据会持续不断地从 Kafka 中消费。
     * 
     * @param nextElement 下一个元素
     * @return 始终返回 false，表示流不会结束
     */
    @Override
    public boolean isEndOfStream(SegmentObject nextElement) {
        return false;
    }

    /**
     * 获取生成的数据类型信息。
     * 
     * <p>
     * 为 Flink 提供类型信息，用于序列化和类型安全检查。
     * 这对于 Flink 的内部类型系统和性能优化非常重要。
     * 
     * @return SegmentObject 的类型信息
     */
    @Override
    public TypeInformation<SegmentObject> getProducedType() {
        return TypeInformation.of(SegmentObject.class);
    }
}
