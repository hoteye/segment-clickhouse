package com.o11y.flink.operator.dubboentryavgduration;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.flink.operator.FlinkOperator;

import java.util.Map;
import java.time.Duration;
import java.util.List;
import segment.v3.Segment.SegmentObject;
import org.apache.flink.util.Collector;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.common.typeinfo.Types;

/**
 * Dubbo入口平均耗时算子模板，实现 FlinkOperator 接口，可直接注册到 OperatorRegistry。
 */
public class DubboEntryAvgDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(DubboEntryAvgDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();
    private static final int WINDOW_SECONDS = 7;

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        List<String> serviceNames = params.get("service");
        String spanType = params.get("spanType").get(0);
        DataStream<SegmentObject> filtered = ((DataStream<SegmentObject>) input)
                .filter(segment -> serviceNames.contains(segment.getService()));
        // 先将 Entry 类型 span 的 endTime 作为事件时间字段
        // 输出 Tuple3<traceId, duration, endTime>，以 endTime 作为事件时间
        DataStream<Tuple3<String, Long, Long>> durationStream = filtered
                .flatMap((SegmentObject segment, Collector<Tuple3<String, Long, Long>> out) -> {
                    String traceId = segment.getTraceId();
                    for (int i = 0; i < segment.getSpansCount(); i++) {
                        var span = segment.getSpans(i);
                        if (spanType.equals(span.getSpanType().name())) {
                            long duration = span.getEndTime() - span.getStartTime();
                            long endTime = span.getEndTime();
                            out.collect(Tuple3.of(traceId, duration, endTime));
                        }
                    }
                }).returns(Types.TUPLE(Types.STRING, Types.LONG, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<Tuple3<String, Long, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((tuple, ts) -> tuple.f2));
        // 第一步：先按 traceId 分组做窗口聚合，输出每个 traceId 的窗口平均和最大
        DataStream<Tuple2<Double, Long>> perTraceAgg = durationStream
                .keyBy(t -> t.f0)
                .window(TumblingEventTimeWindows.of(Time.seconds(WINDOW_SECONDS)))
                .aggregate(new AvgMaxAggregateFunction2());
        // 第二步：全局再做一次窗口聚合，输出全局唯一的平均和最大
        DataStream<Tuple2<Double, Long>> globalAgg = perTraceAgg
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(WINDOW_SECONDS)))
                .aggregate(new GlobalAvgMaxAggregateFunction());
        globalAgg.addSink(new RichSinkFunction<Tuple2<Double, Long>>() {
            @Override
            public void invoke(Tuple2<Double, Long> value, Context context) {
                LOG.info("{}s window dubboEntryAvgDuration (global): avg={} ms, max={} ms", WINDOW_SECONDS, value.f0,
                        value.f1);
            }
        }).name(NAME).setParallelism(1);
        return globalAgg;
    }

    // traceId 分组窗口聚合函数，输入 Tuple3<traceId, duration, endTime>，输出 Tuple2<avg, max>
    public static class AvgMaxAggregateFunction2
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
            double avg = acc.f1 == 0 ? 0.0 : ((double) acc.f0) / acc.f1;
            return Tuple2.of(avg, acc.f2);
        }

        @Override
        public Tuple3<Long, Long, Long> merge(Tuple3<Long, Long, Long> a, Tuple3<Long, Long, Long> b) {
            return Tuple3.of(a.f0 + b.f0, a.f1 + b.f1, Math.max(a.f2, b.f2));
        }
    }

    // 全局窗口聚合函数，输入 Tuple2<avg, max>，输出全局 avg 和 max
    public static class GlobalAvgMaxAggregateFunction
            implements AggregateFunction<Tuple2<Double, Long>, Tuple3<Double, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Double, Long, Long> createAccumulator() {
            return Tuple3.of(0.0, 0L, Long.MIN_VALUE);
        }

        @Override
        public Tuple3<Double, Long, Long> add(Tuple2<Double, Long> value, Tuple3<Double, Long, Long> acc) {
            double sumAvg = acc.f0 + value.f0;
            long count = acc.f1 + 1;
            long max = Math.max(acc.f2, value.f1);
            return Tuple3.of(sumAvg, count, max);
        }

        @Override
        public Tuple2<Double, Long> getResult(Tuple3<Double, Long, Long> acc) {
            double avg = acc.f1 == 0 ? 0.0 : acc.f0 / acc.f1;
            return Tuple2.of(avg, acc.f2);
        }

        @Override
        public Tuple3<Double, Long, Long> merge(Tuple3<Double, Long, Long> a, Tuple3<Double, Long, Long> b) {
            return Tuple3.of(a.f0 + b.f0, a.f1 + b.f1, Math.max(a.f2, b.f2));
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
