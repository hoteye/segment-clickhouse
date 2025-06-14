package com.o11y.flink;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import segment.v3.Segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

public class SegmentProducer {
    /**
     * 生成 SegmentObject 并批量发送到 Kafka topic，用于测试。
     * 
     * @param args 启动参数
     * @throws Exception Kafka 发送异常
     */
    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");

        String bootstrapServers = "localhost:9092";
        String topic = "test_flink_task";
        int partitions = 2;
        short replicationFactor = 1;
        KafkaTopicUtil.recreateTopic(bootstrapServers, topic, partitions, replicationFactor);

        Producer<String, byte[]> producer = new KafkaProducer<>(props);
        int messageCount = 34; // 生成SegmentObject的数量
        int increment = 3000; // 每条消息的时间间隔（毫秒）
        Random random = new Random();
        long now = System.currentTimeMillis();
        List<Long> durations = new ArrayList<>();
        long base = now - 60_000 * 240; // 当前时间的前 4 小时
        for (int i = 0; i < messageCount; i++) {
            String key = "key-" + (i % 2); // 轮流分到2个分区
            long endTime = base + i * increment + 1000; // 每条往后推进3秒
            long duration = (i % 2 == 0) ? 200 : 400; // 一半200ms，一半400ms
            long startTime = endTime - duration;
            durations.add(duration);
            String traceId = UUID.randomUUID().toString();
            String segmentId = UUID.randomUUID().toString();
            String service = (i % 2 == 0) ? "dubbo-provider-a" : "dubbo-provider-b";
            String operationName = "op-" + random.nextInt(10);

            Segment.SegmentObject.Builder segBuilder = Segment.SegmentObject.newBuilder()
                    .setTraceId(traceId)
                    .setTraceSegmentId(segmentId)
                    .setService(service)
                    .setServiceInstance(UUID.randomUUID().toString())
                    .setIsSizeLimited(random.nextBoolean());

            Segment.SpanObject.Builder spanBuilder = Segment.SpanObject.newBuilder()
                    .setSpanType(Segment.SpanType.Entry)
                    .setSpanId(random.nextInt(100))
                    .setParentSpanId(random.nextInt(100))
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setOperationName(operationName)
                    .setPeer("peer-" + random.nextInt(100))
                    .setComponentId(random.nextInt(100))
                    .setIsError(random.nextBoolean());
            segBuilder.addSpans(spanBuilder);
            Segment.SegmentObject segmentObject = segBuilder.build();

            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    "test_flink_task", key, segmentObject.toByteArray());
            producer.send(record);
        }
        producer.close();
        System.out.println(messageCount + "条SegmentObject写入完成");
        for (int i = 0; i < durations.size(); i++) {
            System.out.println("Segment " + i + " duration: " + durations.get(i) + " ms" + " startTime: "
                    + (base + i * 5000) + " endTime: " + (base + i * 5000 + 1000));
        }
    }
}
