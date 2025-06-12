package com.o11y.flink.operator;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.util.Collector;
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
 * Dubbo入口平均耗时聚合算子
 */
public class DubboEntryAvgDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(DubboEntryAvgDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();
    private int windowSeconds = 7;

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        List<String> serviceNames = params.get("service");
        String spanType = params.get("spanType").get(0);
        windowSeconds = Integer.parseInt(params.get("windowSeconds").get(0));
        DataStream<SegmentObject> filtered = filterByService(input, serviceNames);
        DataStream<Tuple3<String, Long, Long>> durationStream = extractEntrySpan(filtered, spanType);
        DataStream<Tuple3<Double, Long, Long>> perTraceAgg = aggregateByTrace(durationStream);
        DataStream<Tuple3<Double, Long, Long>> globalAgg = aggregateGlobal(perTraceAgg);
        return globalAgg;
    }

    // 步骤1：服务名过滤
    protected DataStream<SegmentObject> filterByService(DataStream<?> input, List<String> serviceNames) {
        return ((DataStream<SegmentObject>) input)
                .filter(segment -> serviceNames.contains(segment.getService()));
    }

    // 步骤2：提取 Entry 类型 span
    protected DataStream<Tuple3<String, Long, Long>> extractEntrySpan(DataStream<SegmentObject> filtered,
            String spanType) {
        return filtered
                .flatMap((SegmentObject segment, Collector<Tuple3<String, Long, Long>> out) -> {
                    String traceSegmentId = segment.getTraceSegmentId();
                    int totalSpans = segment.getSpansCount();
                    for (int i = 0; i < totalSpans; i++) {
                        var span = segment.getSpans(i);
                        if (spanType.equals(span.getSpanType().name())) {
                            long duration = span.getEndTime() - span.getStartTime();
                            long endTime = span.getEndTime();
                            out.collect(Tuple3.of(traceSegmentId, duration, endTime));
                        }
                    }
                    LOG.debug("segmentId={}", traceSegmentId);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple3<String, Long, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((tuple, ts) -> tuple.f2));
    }

    // 步骤3：trace 分组窗口聚合
    protected DataStream<Tuple3<Double, Long, Long>> aggregateByTrace(
            DataStream<Tuple3<String, Long, Long>> durationStream) {
        return durationStream
                .keyBy(t -> t.f0)
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .aggregate(new TraceAvgMaxAggregateFunction(),
                        new ProcessWindowFunction<Tuple2<Double, Long>, Tuple3<Double, Long, Long>, String, TimeWindow>() {
                            @Override
                            public void process(String key, Context context,
                                    Iterable<Tuple2<Double, Long>> elements,
                                    Collector<Tuple3<Double, Long, Long>> out) {
                                Tuple2<Double, Long> result = elements.iterator().next();
                                out.collect(Tuple3.of(result.f0, result.f1, context.window().getEnd()));
                            }
                        })
                .returns(Types.TUPLE(Types.DOUBLE, Types.LONG, Types.LONG));
    }

    // 步骤4：全局窗口聚合
    protected DataStream<Tuple3<Double, Long, Long>> aggregateGlobal(
            DataStream<Tuple3<Double, Long, Long>> perTraceAgg) {
        return perTraceAgg
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .apply(new AllWindowFunction<Tuple3<Double, Long, Long>, Tuple3<Double, Long, Long>, TimeWindow>() {
                    @Override
                    public void apply(TimeWindow window, Iterable<Tuple3<Double, Long, Long>> values,
                            Collector<Tuple3<Double, Long, Long>> out) {
                        double sum = 0;
                        long max = Long.MIN_VALUE;
                        long count = 0;
                        for (Tuple3<Double, Long, Long> v : values) {
                            sum += v.f0;
                            max = Math.max(max, v.f1);
                            count++;
                        }
                        double avg = count == 0 ? 0 : sum / count;
                        out.collect(Tuple3.of(avg, max, window.getEnd()));
                    }
                });
    }

    // 聚合函数：trace 级别平均耗时和最大耗时
    public static class TraceAvgMaxAggregateFunction
            implements AggregateFunction<Tuple3<String, Long, Long>, Tuple3<Long, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Long, Long, Long> createAccumulator() {
            return Tuple3.of(0L, 0L, Long.MIN_VALUE);
        }

        @Override
        public Tuple3<Long, Long, Long> add(Tuple3<String, Long, Long> value, Tuple3<Long, Long, Long> acc) {
            long sum = acc.f0 + value.f1;
            long count = acc.f1 + 1;
            long max = Math.max(acc.f2, value.f1);
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
