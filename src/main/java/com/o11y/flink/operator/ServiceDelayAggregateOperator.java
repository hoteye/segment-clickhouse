package com.o11y.flink.operator;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import segment.v3.Segment.SegmentObject;
import java.time.format.DateTimeFormatter;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 延迟（耗时）聚合与告警
 */
public class ServiceDelayAggregateOperator implements FlinkOperator {
    private int windowSeconds = 7;
    private double avgThreshold = 500.0;
    private long maxThreshold = 1000L;
    private final String NAME = this.getClass().getSimpleName();;

    public ServiceDelayAggregateOperator() {
    }

    public ServiceDelayAggregateOperator(int windowSeconds, double avgThreshold, long maxThreshold) {
        this.windowSeconds = windowSeconds;
        this.avgThreshold = avgThreshold;
        this.maxThreshold = maxThreshold;
    }

    @Override
    public ServiceAggAndAlarm apply(DataStream<?> input, Map<String, List<String>> params) {
        // 不再做空判断，参数缺失时直接抛出异常，便于启动阶段发现配置问题
        windowSeconds = Integer.parseInt(params.get("windowSize").get(0));
        avgThreshold = Double.parseDouble(params.get("avgThreshold").get(0));
        maxThreshold = Long.parseLong(params.get("maxThreshold").get(0));

        DataStream<SegmentObject> segmentStream = (DataStream<SegmentObject>) input;
        DataStream<Tuple3<String, String, Long>> durationStream = extractEntrySpan(segmentStream);
        DataStream<ServiceAggResult> aggStream = aggregateByService(durationStream);
        DataStream<String> alarmStream = alarmOnThreshold(aggStream);
        return new ServiceAggAndAlarm(aggStream, alarmStream);
    }

    /**
     * 步骤1：提取 Entry 类型 span，输出 Tuple3<service, operatorName, duration>
     * 
     * @param stream 原始 SegmentObject 数据流
     * @return DataStream<Tuple3<service, operatorName, duration>>
     *         其中 service 为服务名，operatorName 为操作名，duration 为耗时
     *         事件时间戳由 assignTimestampsAndWatermarks 指定
     */
    protected DataStream<Tuple3<String, String, Long>> extractEntrySpan(DataStream<SegmentObject> stream) {
        return stream.flatMap((SegmentObject segment, Collector<Tuple3<String, String, Long>> out) -> {
            String service = segment.getService();
            for (int i = 0; i < segment.getSpansCount(); i++) {
                var span = segment.getSpans(i);
                if ("Entry".equals(span.getSpanType().name())) {
                    String operatorName = span.getOperationName();
                    long duration = span.getEndTime() - span.getStartTime();
                    out.collect(Tuple3.of(service, operatorName, duration));
                }
            }
        })
                .returns(Types.TUPLE(Types.STRING, Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<Tuple3<String, String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                        .withTimestampAssigner((tuple, ts) -> ts));
    }

    /**
     * 步骤2：按 service、operator_name 分组窗口聚合，输出 ServiceDelayAggResult
     * 
     * @param durationStream 经过 extractEntrySpan 处理后的耗时数据流
     * @return DataStream<ServiceDelayAggResult>
     *         其中 avg 为平均耗时，max 为最大耗时，windowStart 为窗口起始时间戳，windowSize 为窗口长度（秒）
     *         使用事件时间的滚动窗口，窗口长度由 windowSeconds 决定
     */
    protected DataStream<ServiceAggResult> aggregateByService(
            DataStream<Tuple3<String, String, Long>> durationStream) {
        final int windowSizeFinal = this.windowSeconds;
        final String operatorClassName = getName();
        return durationStream
                .keyBy(t -> Tuple2.of(t.f0, t.f1), Types.TUPLE(Types.STRING, Types.STRING))
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .aggregate(new ServiceAvgMaxAggregateFunction(),
                        new ProcessWindowFunction<Tuple2<Double, Long>, ServiceAggResult, Tuple2<String, String>, TimeWindow>() {
                            @Override
                            public void process(Tuple2<String, String> key, Context context,
                                    Iterable<Tuple2<Double, Long>> elements,
                                    Collector<ServiceAggResult> out) {
                                Tuple2<Double, Long> result = elements.iterator().next();
                                ServiceAggResult serviceAggResult = new ServiceAggResult();
                                serviceAggResult.service = key.f0;
                                serviceAggResult.operatorName = key.f1;
                                serviceAggResult.avgDuration = result.f0;
                                serviceAggResult.maxDuration = result.f1;
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
     * @param aggStream 聚合后的 ServiceDelayAggResult 数据流
     * @return DataStream<String> 中文自然语言告警内容
     */
    protected DataStream<String> alarmOnThreshold(DataStream<ServiceAggResult> aggStream) {
        final double avgThresholdFinal = this.avgThreshold;
        final long maxThresholdFinal = this.maxThreshold;
        return aggStream.flatMap((ServiceAggResult value, Collector<String> out) -> {
            String service = value.service;
            String operatorName = value.operatorName;
            double avg = value.avgDuration;
            long max = value.maxDuration;
            long windowStart = value.windowStart;
            int windowSize = value.windowSize;
            if (avg > avgThresholdFinal) {
                out.collect(
                        String.format("【告警】服务[%s]的[%s]方法在窗口[%s, +%ds]的平均耗时为%.2fms，已超过阈值%.2fms。", service, operatorName,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        .withZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.Instant.ofEpochMilli(windowStart)),
                                windowSize, avg, avgThresholdFinal));
            }
            if (max > maxThresholdFinal) {
                out.collect(String.format("【告警】服务[%s]的[%s]方法在窗口[%s, +%ds]的最大耗时为%dms，已超过阈值%dms。", service, operatorName,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(java.time.Instant.ofEpochMilli(windowStart)),
                        windowSize, max, maxThresholdFinal));
            }
        }).returns(String.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static class ServiceAvgMaxAggregateFunction
            implements AggregateFunction<Tuple3<String, String, Long>, Tuple3<Long, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Long, Long, Long> createAccumulator() {
            return Tuple3.of(0L, 0L, Long.MIN_VALUE);
        }

        @Override
        public Tuple3<Long, Long, Long> add(Tuple3<String, String, Long> value, Tuple3<Long, Long, Long> acc) {
            long sum = acc.f0 + value.f2;
            long count = acc.f1 + 1;
            long max = Math.max(acc.f2, value.f2);
            return Tuple3.of(sum, count, max);
        }

        @Override
        public Tuple2<Double, Long> getResult(Tuple3<Long, Long, Long> acc) {
            double avg = acc.f1 == 0 ? 0 : (double) acc.f0 / acc.f1;
            return Tuple2.of(avg, acc.f2);
        }

        @Override
        public Tuple3<Long, Long, Long> merge(Tuple3<Long, Long, Long> a, Tuple3<Long, Long, Long> b) {
            long sum = a.f0 + b.f0;
            long count = a.f1 + b.f1;
            long max = Math.max(a.f2, b.f2);
            return Tuple3.of(sum, count, max);
        }
    }
}
