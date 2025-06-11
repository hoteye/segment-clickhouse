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
 * Dubbo入口平均耗时聚合算子，用于计算指定服务的调用链路耗时统计。
 * 
 * 功能说明：
 * 1. 输入：接收 SegmentObject 数据流，包含完整的调用链 Span 信息
 * 2. 过滤：根据传入的 serviceNames 参数过滤出指定服务的调用链数据
 * 3. 窗口聚合：
 * - 窗口时长：以参数 windowSeconds 指定的秒数为单位（默认7秒）
 * - 事件时间：使用 spanType="Entry" 类型的 span.endTime 作为事件时间
 * - 窗口类型：基于事件时间的滚动窗口（TumblingEventTimeWindows）
 * 4. 计算指标：
 * - 平均耗时：窗口内所有 Entry span 的平均调用耗时
 * - 最大耗时：窗口内所有 Entry span 的最大调用耗时
 * - 消息数量：窗口内收到的 Entry span 总数
 * 5. 双层聚合：
 * - 第一层：按 traceSegmentId 分组，计算每个调用链的指标
 * - 第二层：全局聚合，计算所有调用链的汇总指标
 * 
 * 使用场景：
 * - 服务调用耗时监控
 * - 性能瓶颈分析
 * - 服务质量监控告警
 * 
 * 实现说明：该算子实现了 FlinkOperator 接口，可通过 OperatorRegistry 注册使用。
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
                    LOG.info("segmentId={}", traceSegmentId);
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
                .aggregate(new AvgMaxAggregateFunction2(),
                        new ProcessWindowFunction<Tuple2<Double, Long>, Tuple3<Double, Long, Long>, String, TimeWindow>() {
                            @Override
                            public void process(String key, Context context, Iterable<Tuple2<Double, Long>> elements,
                                    Collector<Tuple3<Double, Long, Long>> out) {
                                Tuple2<Double, Long> result = elements.iterator().next();
                                out.collect(Tuple3.of(result.f0, result.f1, context.window().getStart()));
                            }
                        })
                .returns(Types.TUPLE(Types.DOUBLE, Types.LONG, Types.LONG));
    }

    // 步骤4：全局窗口聚合
    protected DataStream<Tuple3<Double, Long, Long>> aggregateGlobal(
            DataStream<Tuple3<Double, Long, Long>> perTraceAgg) {
        return perTraceAgg.windowAll(TumblingEventTimeWindows.of(Time.seconds(windowSeconds))).aggregate(
                new GlobalAvgMaxAggregateFunctionWithWindowStart(),
                new AllWindowFunction<Tuple3<Double, Long, Long>, Tuple3<Double, Long, Long>, TimeWindow>() {
                    @Override
                    public void apply(TimeWindow window, Iterable<Tuple3<Double, Long, Long>> elements,
                            Collector<Tuple3<Double, Long, Long>> out) {
                        for (Tuple3<Double, Long, Long> e : elements) {
                            out.collect(e);
                        }
                    }
                });
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

    // 全局窗口聚合函数，输入 Tuple3<avg, count, windowStart>，输出全局 avg 和 max 及窗口 start
    public static class GlobalAvgMaxAggregateFunctionWithWindowStart
            implements
            AggregateFunction<Tuple3<Double, Long, Long>, Tuple4<Double, Long, Long, Long>, Tuple3<Double, Long, Long>> {
        @Override
        public Tuple4<Double, Long, Long, Long> createAccumulator() {
            return Tuple4.of(0.0, 0L, Long.MIN_VALUE, Long.MIN_VALUE);
        }

        @Override
        public Tuple4<Double, Long, Long, Long> add(Tuple3<Double, Long, Long> value,
                Tuple4<Double, Long, Long, Long> acc) {
            double sumAvg = acc.f0 + value.f0;
            long count = acc.f1 + 1;
            long max = Math.max(acc.f2, value.f1);
            long windowStart = value.f2; // 所有输入的 windowStart 都一样
            return Tuple4.of(sumAvg, count, max, windowStart);
        }

        @Override
        public Tuple3<Double, Long, Long> getResult(Tuple4<Double, Long, Long, Long> acc) {
            double avg = acc.f1 == 0 ? 0.0 : acc.f0 / acc.f1;
            return Tuple3.of(avg, acc.f2, acc.f3);
        }

        @Override
        public Tuple4<Double, Long, Long, Long> merge(Tuple4<Double, Long, Long, Long> a,
                Tuple4<Double, Long, Long, Long> b) {
            // windowStart 取 a/b 任意一个即可
            return Tuple4.of(a.f0 + b.f0, a.f1 + b.f1, Math.max(a.f2, b.f2), a.f3);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
