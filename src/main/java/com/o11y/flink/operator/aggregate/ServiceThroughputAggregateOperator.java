package com.o11y.flink.operator.aggregate;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import com.o11y.flink.operator.base.FlinkOperator;
import com.o11y.flink.operator.model.ServiceAggAndAlarm;
import com.o11y.flink.operator.model.ServiceAggResult;

import segment.v3.Segment.SegmentObject;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 吞吐量聚合与告警
 */
public class ServiceThroughputAggregateOperator implements FlinkOperator {
    private int windowSeconds = 7;
    private long throughputThreshold = 100L;
    private final String NAME = this.getClass().getSimpleName();

    public ServiceThroughputAggregateOperator() {
    }

    public ServiceThroughputAggregateOperator(int windowSeconds, long throughputThreshold) {
        this.windowSeconds = windowSeconds;
        this.throughputThreshold = throughputThreshold;
    }

    @Override
    public ServiceAggAndAlarm apply(DataStream<?> input, Map<String, List<String>> params) {
        // 不再做空判断，参数缺失时直接抛出异常，便于启动阶段发现配置问题
        windowSeconds = Integer.parseInt(params.get("windowSize").get(0));
        throughputThreshold = Long.parseLong(params.get("throughputThreshold").get(0));
        DataStream<SegmentObject> segmentStream = (DataStream<SegmentObject>) input;
        DataStream<Tuple3<String, String, Long>> countStream = extractEntrySpan(segmentStream);
        DataStream<ServiceAggResult> aggStream = aggregateByService(countStream);
        DataStream<String> alarmStream = alarmOnThreshold(aggStream);
        return new ServiceAggAndAlarm(aggStream, alarmStream);
    }

    /**
     * 步骤1：提取 Entry 类型 span，输出 Tuple3<service, operatorName, 1L>
     * 
     * @param stream 原始 SegmentObject 数据流
     * @return DataStream<Tuple3<service, operatorName, 1L>>
     *         用于统计吞吐量
     *         事件时间戳由 assignTimestampsAndWatermarks 指定
     */
    protected DataStream<Tuple3<String, String, Long>> extractEntrySpan(DataStream<SegmentObject> stream) {
        return stream
                .flatMap((SegmentObject segment, org.apache.flink.util.Collector<Tuple3<String, String, Long>> out) -> {
                    String service = segment.getService();
                    for (int i = 0; i < segment.getSpansCount(); i++) {
                        var span = segment.getSpans(i);
                        if ("Entry".equals(span.getSpanType().name())) {
                            String operatorName = span.getOperationName();
                            out.collect(Tuple3.of(service, operatorName, 1L));
                        }
                    }
                })
                .returns(Types.TUPLE(Types.STRING, Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<Tuple3<String, String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                        .withTimestampAssigner((tuple, ts) -> ts));
    }

    /**
     * 步骤2：按 service、operator_name 分组窗口聚合，输出 ServiceAggResult
     * 
     * @param countStream 经过 extractEntrySpan 处理后的数据流
     * @return DataStream<ServiceAggResult>
     *         其中 totalCount 为窗口内的吞吐量，windowStart 为窗口起始时间戳，windowSize 为窗口长度（秒）
     *         使用事件时间的滚动窗口，窗口长度由 windowSeconds 决定
     */
    protected DataStream<ServiceAggResult> aggregateByService(
            DataStream<Tuple3<String, String, Long>> countStream) {
        final int windowSizeFinal = this.windowSeconds;
        final String operatorClassName = getName();
        return countStream
                .keyBy(t -> Tuple2.of(t.f0, t.f1), Types.TUPLE(Types.STRING, Types.STRING))
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .reduce(
                        (a, b) -> Tuple3.of(a.f0, a.f1, a.f2 + b.f2),
                        new org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction<Tuple3<String, String, Long>, ServiceAggResult, Tuple2<String, String>, org.apache.flink.streaming.api.windowing.windows.TimeWindow>() {
                            /**
                             * 处理窗口内的吞吐量聚合，输出 ServiceAggResult。
                             * 
                             * @param key      分组 key
                             * @param context  窗口上下文
                             * @param elements 聚合元素
                             * @param out      输出收集器
                             */
                            @Override
                            public void process(Tuple2<String, String> key, Context context,
                                    Iterable<Tuple3<String, String, Long>> elements,
                                    org.apache.flink.util.Collector<ServiceAggResult> out) {
                                Tuple3<String, String, Long> t = elements.iterator().next();
                                ServiceAggResult serviceAggResult = new ServiceAggResult();
                                serviceAggResult.service = t.f0;
                                serviceAggResult.operatorName = t.f1;
                                serviceAggResult.totalCount = t.f2;
                                serviceAggResult.windowStart = context.window().getStart();
                                serviceAggResult.windowSize = windowSizeFinal;
                                serviceAggResult.operatorClass = operatorClassName;
                                out.collect(serviceAggResult);
                            }
                        })
                .returns(ServiceAggResult.class);
    }

    /**
     * 步骤3：根据聚合结果输出告警信息
     * 
     * @param aggStream 聚合后的 Tuple4<service, operatorName, count, windowStart> 数据流
     * @return DataStream<String> 中文自然语言告警内容
     */
    protected DataStream<String> alarmOnThreshold(DataStream<ServiceAggResult> aggStream) {
        final long throughputThresholdFinal = this.throughputThreshold;
        return aggStream
                .flatMap((ServiceAggResult value, org.apache.flink.util.Collector<String> out) -> {
                    String service = value.service;
                    String operatorName = value.operatorName;
                    long count = value.totalCount;
                    long windowStart = value.windowStart;
                    if (count < throughputThresholdFinal) {
                        out.collect(String.format("【告警】服务[%s]的[%s]方法在窗口时间[%s]的吞吐量为%d，低于阈值%d。", service, operatorName,
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.Instant.ofEpochMilli(windowStart)),
                                count, throughputThresholdFinal));
                    }
                }).returns(String.class);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
