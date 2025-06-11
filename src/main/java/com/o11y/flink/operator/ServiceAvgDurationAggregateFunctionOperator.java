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
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.api.common.typeinfo.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.time.Duration;
import java.util.List;
import segment.v3.Segment.SegmentObject;

/**
 * 按 service 分组，统计每 7 秒内各 service 的平均耗时和最大耗时
 */
public class ServiceAvgDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceAvgDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();
    private int windowSeconds = 7;

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        String spanType = params.get("spanType").get(0);
        windowSeconds = Integer.parseInt(params.get("windowSeconds").get(0));
        DataStream<SegmentObject> segmentStream = (DataStream<SegmentObject>) input;
        DataStream<Tuple4<String, Long, Long, Long>> durationStream = extractEntrySpan(segmentStream, spanType);
        DataStream<Tuple4<String, Double, Long, Long>> serviceAgg = aggregateByService(durationStream);
        return serviceAgg;
    }

    // 步骤2：提取 Entry 类型 span，输出 Tuple4<service, duration, endTime, endTime>
    protected DataStream<Tuple4<String, Long, Long, Long>> extractEntrySpan(DataStream<SegmentObject> filtered,
            String spanType) {
        return filtered
                .flatMap((SegmentObject segment,
                        org.apache.flink.util.Collector<Tuple4<String, Long, Long, Long>> out) -> {
                    String service = segment.getService();
                    String traceSegmentId = segment.getTraceSegmentId();
                    int totalSpans = segment.getSpansCount();
                    for (int i = 0; i < totalSpans; i++) {
                        var span = segment.getSpans(i);
                        if (spanType.equals(span.getSpanType().name())) {
                            long duration = span.getEndTime() - span.getStartTime();
                            long endTime = span.getEndTime();
                            out.collect(Tuple4.of(service, duration, endTime, endTime));
                        }
                    }
                    LOG.info("segmentId={}", traceSegmentId);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG, Types.LONG, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple4<String, Long, Long, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((tuple, ts) -> tuple.f2));
    }

    // 步骤3：按 service 分组窗口聚合，输出 Tuple4<service, avg, max, windowEnd>
    protected DataStream<Tuple4<String, Double, Long, Long>> aggregateByService(
            DataStream<Tuple4<String, Long, Long, Long>> durationStream) {
        return durationStream
                .keyBy(t -> t.f0)
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .aggregate(new ServiceAvgMaxAggregateFunction(),
                        new ProcessWindowFunction<Tuple2<Double, Long>, Tuple4<String, Double, Long, Long>, String, TimeWindow>() {
                            @Override
                            public void process(String service, Context context,
                                    Iterable<Tuple2<Double, Long>> elements,
                                    org.apache.flink.util.Collector<Tuple4<String, Double, Long, Long>> out) {
                                Tuple2<Double, Long> result = elements.iterator().next();
                                out.collect(Tuple4.of(service, result.f0, result.f1, context.window().getEnd()));
                            }
                        })
                .returns(Types.TUPLE(Types.STRING, Types.DOUBLE, Types.LONG, Types.LONG));
    }

    // 聚合函数：统计平均耗时和最大耗时
    public static class ServiceAvgMaxAggregateFunction
            implements
            AggregateFunction<Tuple4<String, Long, Long, Long>, Tuple3<Long, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Long, Long, Long> createAccumulator() {
            return Tuple3.of(0L, 0L, Long.MIN_VALUE);
        }

        @Override
        public Tuple3<Long, Long, Long> add(Tuple4<String, Long, Long, Long> value, Tuple3<Long, Long, Long> acc) {
            long sum = acc.f0 + value.f1;
            long count = acc.f1 + 1;
            long max = Math.max(acc.f2, value.f1);
            return Tuple3.of(sum, count, max);
        }

        @Override
        public Tuple2<Double, Long> getResult(Tuple3<Long, Long, Long> acc) {
            double avg = acc.f1 == 0 ? 0.0 : ((double) acc.f0) / acc.f1;
            return Tuple2.of(avg, acc.f2);
        }

        @Override
        public Tuple3<Long, Long, Long> merge(Tuple3<Long, Long, Long> a, Tuple3<Long, Long, Long> b) {
            return Tuple3.of(a.f0 + b.f0, a.f1 + b.f1, Math.max(a.f2, b.f2));
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
