package com.o11y.flink.operator;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.time.Duration;
import java.util.List;
import segment.v3.Segment.SegmentObject;

/**
 * 按 service、operator_name 分组，统计每 7 秒内各组的平均耗时和最大耗时
 */
public class ServiceAvgDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceAvgDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();
    private int windowSeconds = 7;
    String spanType = "Entry";

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        spanType = params.get("spanType").get(0);
        windowSeconds = Integer.parseInt(params.get("windowSeconds").get(0));
        DataStream<SegmentObject> segmentStream = (DataStream<SegmentObject>) input;
        DataStream<Tuple3<String, String, Long>> durationStream = extractEntrySpan(segmentStream, spanType);
        DataStream<Tuple5<String, String, Double, Long, Long>> serviceAgg = aggregateByService(durationStream);
        return serviceAgg;
    }

    // 步骤2：提取 Entry 类型 span，输出 Tuple3<service, operatorName, duration>
    protected DataStream<Tuple3<String, String, Long>> extractEntrySpan(
            DataStream<SegmentObject> filtered,
            String spanType) {
        return filtered
                .flatMap((SegmentObject segment, Collector<Tuple3<String, String, Long>> out) -> {
                    String service = segment.getService();
                    int totalSpans = segment.getSpansCount();
                    for (int i = 0; i < totalSpans; i++) {
                        var span = segment.getSpans(i);
                        if (spanType.equals(span.getSpanType().name())) {
                            String operatorName = span.getOperationName();
                            long duration = span.getEndTime() - span.getStartTime();
                            out.collect(Tuple3.of(service, operatorName, duration));
                        }
                    }
                    LOG.info("segmentId={}", segment.getTraceSegmentId());
                })
                .returns(Types.TUPLE(Types.STRING, Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<Tuple3<String, String, Long>>forBoundedOutOfOrderness(
                                Duration.ofSeconds(2))
                        .withTimestampAssigner((tuple, ts) -> ts)); // 事件时间可用外部 source 时间戳
    }

    // 步骤3：按 service、operator_name 分组窗口聚合，输出 Tuple5<service, operatorName, avg, max,
    // windowStart>
    protected DataStream<Tuple5<String, String, Double, Long, Long>> aggregateByService(
            DataStream<Tuple3<String, String, Long>> durationStream) {
        return durationStream
                .keyBy(t -> Tuple2.of(t.f0, t.f1), Types.TUPLE(Types.STRING, Types.STRING))
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .aggregate(new ServiceAvgMaxAggregateFunction(),
                        new ProcessWindowFunction<Tuple2<Double, Long>, Tuple5<String, String, Double, Long, Long>, Tuple2<String, String>, TimeWindow>() {
                            @Override
                            public void process(Tuple2<String, String> key, Context context,
                                    Iterable<Tuple2<Double, Long>> elements,
                                    Collector<Tuple5<String, String, Double, Long, Long>> out) {
                                Tuple2<Double, Long> result = elements.iterator().next();
                                out.collect(
                                        Tuple5.of(key.f0, key.f1, result.f0, result.f1, context.window().getStart()));
                            }
                        })
                .returns(Types.TUPLE(Types.STRING, Types.STRING, Types.DOUBLE, Types.LONG, Types.LONG));
    }

    // 聚合函数：统计平均耗时和最大耗时
    public static class ServiceAvgMaxAggregateFunction
            implements
            AggregateFunction<Tuple3<String, String, Long>, Tuple3<Long, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Long, Long, Long> createAccumulator() {
            return Tuple3.of(0L, 0L, Long.MIN_VALUE);
        }

        @Override
        public Tuple3<Long, Long, Long> add(Tuple3<String, String, Long> value,
                Tuple3<Long, Long, Long> acc) {
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

    @Override
    public String getName() {
        return NAME;
    }
}
