package com.o11y.stream.operator.aggregate;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import com.o11y.stream.operator.base.FlinkOperator;
import com.o11y.domain.model.aggregation.ServiceAggResult;

import segment.v3.Segment.SegmentObject;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 服务延迟聚合算子。
 * 
 * <p>
 * 负责对 SegmentObject 流进行窗口聚合，计算服务的性能指标，
 * 包括平均延迟、最大延迟、成功率、吞吐量等关键指标。
 * 
 * <p>
 * <strong>聚合维度：</strong>
 * <ul>
 * <li>按服务名称（service）分组</li>
 * <li>按操作名称（operationName）细分</li>
 * <li>按时间窗口聚合（可配置窗口大小）</li>
 * </ul>
 * 
 * <p>
 * <strong>计算指标：</strong>
 * <ul>
 * <li>平均响应时间（avg_duration）</li>
 * <li>最大响应时间（max_duration）</li>
 * <li>总请求数（total_count）</li>
 * <li>成功请求数（success_count）</li>
 * <li>错误请求数（error_count）</li>
 * <li>成功率（success_rate）</li>
 * </ul>
 * 
 * <p>
 * <strong>窗口策略：</strong>
 * 使用滚动事件时间窗口，支持乱序数据处理和水位线机制。
 * 
 * @see FlinkOperator 算子基础接口
 * @see ServiceAggResult 聚合结果模型
 * @see SegmentObject Skywalking 数据模型
 * @author DDD Architecture Team
 * @since 1.0.0
 */
public class AggregateOperator implements FlinkOperator {
    private int windowSeconds = 7;
    private final String NAME = this.getClass().getSimpleName();;

    /**
     * 默认构造函数。
     * 使用默认的窗口大小（7秒）。
     */
    public AggregateOperator() {
    }

    /**
     * 参数化构造函数。
     * 
     * @param windowSeconds 窗口大小（秒）
     * @param avgThreshold  平均响应时间阈值（预留参数）
     * @param maxThreshold  最大响应时间阈值（预留参数）
     */
    public AggregateOperator(int windowSeconds, double avgThreshold, long maxThreshold) {
        this.windowSeconds = windowSeconds;
    }

    /**
     * 应用聚合算子到输入数据流。
     * 
     * <p>
     * 执行完整的聚合流程：
     * <ol>
     * <li>从参数中解析窗口大小配置</li>
     * <li>提取 Entry 类型的 Span 数据</li>
     * <li>按服务维度进行窗口聚合</li>
     * <li>输出聚合结果流</li>
     * </ol>
     * 
     * @param input  输入数据流（SegmentObject 类型）
     * @param params 算子参数，包含 windowSize 等配置
     * @return 服务聚合结果数据流
     * @throws RuntimeException 如果参数解析失败
     */
    @Override
    public DataStream<ServiceAggResult> apply(DataStream<?> input, Map<String, List<String>> params) {
        // 不再做空判断，参数缺失时直接抛出异常，便于启动阶段发现配置问题
        windowSeconds = Integer.parseInt(params.get("windowSize").get(0));
        DataStream<SegmentObject> segmentStream = (DataStream<SegmentObject>) input;
        DataStream<Tuple4<String, String, Boolean, Long>> baseAggStream = extractEntrySpan(segmentStream);
        DataStream<ServiceAggResult> aggStream = aggregateByService(baseAggStream);
        return aggStream;
    }

    /**
     * 步骤1：提取 Entry 类型 span，输出 Tuple4<service, operatorName, isSuccess, duration>
     * 
     * @param stream 原始 SegmentObject 数据流
     * @return DataStream<Tuple4<service, operatorName, isSuccess, duration>>
     *         其中 service 为服务名，operatorName 为操作名，isSuccess 是否成功，duration 为耗时
     *         事件时间戳由 assignTimestampsAndWatermarks 指定
     */
    protected DataStream<Tuple4<String, String, Boolean, Long>> extractEntrySpan(DataStream<SegmentObject> stream) {
        return stream.flatMap((SegmentObject segment, Collector<Tuple4<String, String, Boolean, Long>> out) -> {
            String service = segment.getService();
            for (int i = 0; i < segment.getSpansCount(); i++) {
                var span = segment.getSpans(i);
                if ("Entry".equals(span.getSpanType().name())) {
                    String operatorName = span.getOperationName();
                    boolean isSuccess = !span.getIsError();
                    long duration = span.getEndTime() - span.getStartTime();
                    out.collect(Tuple4.of(service, operatorName, isSuccess, duration));
                }
            }
        }).returns(Types.TUPLE(Types.STRING, Types.STRING, Types.BOOLEAN, Types.LONG))
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<Tuple4<String, String, Boolean, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                        .withTimestampAssigner((tuple, ts) -> ts));
    }

    /**
     * 步骤2：按 service、operator_name 分组窗口聚合，输出 ServiceDelayAggResult
     * 
     * @param baseAggStream 经过 extractEntrySpan 处理后的聚合基础数据流
     * @return DataStream<ServiceDelayAggResult>
     *         其中 avg 为平均耗时，max 为最大耗时，windowStart 为窗口起始时间戳，windowSize 为窗口长度（秒）
     *         使用事件时间的滚动窗口，窗口长度由 windowSeconds 决定
     */
    protected DataStream<ServiceAggResult> aggregateByService(
            DataStream<Tuple4<String, String, Boolean, Long>> inputStream) {
        final int windowSizeFinal = this.windowSeconds;
        final String operatorClassName = getName();
        return inputStream
                .keyBy(t -> Tuple2.of(t.f0, t.f1), Types.TUPLE(Types.STRING, Types.STRING))
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .apply(new WindowFunction<Tuple4<String, String, Boolean, Long>, ServiceAggResult, Tuple2<String, String>, TimeWindow>() {
                    @Override
                    public void apply(Tuple2<String, String> key,
                            TimeWindow window,
                            Iterable<Tuple4<String, String, Boolean, Long>> input, Collector<ServiceAggResult> out) {
                        long count = 0;
                        long successCount = 0;
                        long totalDuration = 0;
                        long maxDuration = Long.MIN_VALUE;
                        for (Tuple4<String, String, Boolean, Long> t : input) {
                            count++;
                            if (t.f2)
                                successCount++;
                            totalDuration += t.f3;
                            if (t.f3 > maxDuration)
                                maxDuration = t.f3;
                        }
                        ServiceAggResult result = new ServiceAggResult();
                        result.service = key.f0;
                        result.operatorName = key.f1;
                        result.avgDuration = (double) totalDuration / count;
                        result.maxDuration = maxDuration == Long.MIN_VALUE ? 0 : maxDuration;
                        result.errorRate = 1.0 - ((double) successCount / count);
                        result.totalCount = count;
                        result.successCount = successCount;
                        result.windowStart = window.getStart();
                        result.windowSize = windowSizeFinal;
                        result.operatorClass = operatorClassName;
                        out.collect(result);
                    }
                })
                .returns(ServiceAggResult.class);
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
