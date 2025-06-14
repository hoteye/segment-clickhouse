package com.o11y.flink.operator.aggregate;

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

import com.o11y.flink.operator.base.FlinkOperator;
import com.o11y.flink.operator.model.ServiceAggAndAlarm;
import com.o11y.flink.operator.model.ServiceAggResult;

import segment.v3.Segment.SegmentObject;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 成功率聚合与告警
 */
public class ServiceSuccessRateAggregateOperator implements FlinkOperator {
    private int windowSeconds = 7;
    private double successRateThreshold = 0.95;
    private final String NAME = this.getClass().getSimpleName();;

    public ServiceSuccessRateAggregateOperator() {
    }

    public ServiceSuccessRateAggregateOperator(int windowSeconds, double successRateThreshold) {
        this.windowSeconds = windowSeconds;
        this.successRateThreshold = successRateThreshold;
    }

    @Override
    public ServiceAggAndAlarm apply(DataStream<?> input, Map<String, List<String>> params) {
        // 不再做空判断，参数缺失时直接抛出异常，便于启动阶段发现配置问题
        windowSeconds = Integer.parseInt(params.get("windowSize").get(0));
        successRateThreshold = Double.parseDouble(params.get("successRateThreshold").get(0));
        DataStream<SegmentObject> segmentStream = (DataStream<SegmentObject>) input;
        DataStream<Tuple3<String, String, Boolean>> successStream = extractEntrySpan(segmentStream);
        DataStream<ServiceAggResult> aggStream = aggregateByService(successStream);
        DataStream<String> alarmStream = alarmOnThreshold(aggStream);
        return new ServiceAggAndAlarm(aggStream, alarmStream);
    }

    /**
     * 步骤1：提取 Entry 类型 span，输出 Tuple3<service, operatorName, isSuccess>
     * 
     * @param stream 原始 SegmentObject 数据流
     * @return DataStream<Tuple3<service, operatorName, isSuccess>>
     *         其中 isSuccess 表示该调用是否成功
     *         事件时间戳由 assignTimestampsAndWatermarks 指定
     */
    protected DataStream<Tuple3<String, String, Boolean>> extractEntrySpan(DataStream<SegmentObject> stream) {
        return stream.flatMap((SegmentObject segment, Collector<Tuple3<String, String, Boolean>> out) -> {
            String service = segment.getService();
            for (int i = 0; i < segment.getSpansCount(); i++) {
                var span = segment.getSpans(i);
                if ("Entry".equals(span.getSpanType().name())) {
                    String operatorName = span.getOperationName();
                    boolean isSuccess = span.getIsError() ? false : true;
                    out.collect(Tuple3.of(service, operatorName, isSuccess));
                }
            }
        })
                .returns(Types.TUPLE(Types.STRING, Types.STRING, Types.BOOLEAN))
                .assignTimestampsAndWatermarks(WatermarkStrategy
                        .<Tuple3<String, String, Boolean>>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                        .withTimestampAssigner((tuple, ts) -> ts));
    }

    /**
     * 步骤2：按 service、operator_name 分组窗口聚合，输出 ServiceAggResult
     * 
     * @param successStream 经过 extractEntrySpan 处理后的数据流
     * @return DataStream<ServiceAggResult>
     *         其中 successRate 为成功率，windowStart 为窗口起始时间戳，windowSize 为窗口长度（秒）
     *         使用事件时间的滚动窗口，窗口长度由 windowSeconds 决定
     */
    protected DataStream<ServiceAggResult> aggregateByService(
            DataStream<Tuple3<String, String, Boolean>> successStream) {
        final int windowSizeFinal = this.windowSeconds;
        final String operatorClassName = getName();
        return successStream
                .keyBy(t -> Tuple2.of(t.f0, t.f1), Types.TUPLE(Types.STRING, Types.STRING))
                .window(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .aggregate(new SuccessRateAggregateFunction(),
                        new ProcessWindowFunction<Tuple2<Double, Long>, ServiceAggResult, Tuple2<String, String>, TimeWindow>() {
                            @Override
                            public void process(Tuple2<String, String> key, Context context,
                                    Iterable<Tuple2<Double, Long>> elements,
                                    Collector<ServiceAggResult> out) {
                                Tuple2<Double, Long> result = elements.iterator().next();
                                ServiceAggResult serviceAggResult = new ServiceAggResult();
                                serviceAggResult.service = key.f0;
                                serviceAggResult.operatorName = key.f1;
                                serviceAggResult.errorRate = 1 - result.f0; // successRate
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
     * @param aggStream 聚合后的 Tuple4<service, operatorName, successRate, windowStart>
     *                  数据流
     * @return DataStream<String> 中文自然语言告警内容
     */
    protected DataStream<String> alarmOnThreshold(DataStream<ServiceAggResult> aggStream) {
        final double successRateThresholdFinal = this.successRateThreshold;
        return aggStream.flatMap((ServiceAggResult value, Collector<String> out) -> {
            String service = value.service;
            String operatorName = value.operatorName;
            double successRate = 1 - value.errorRate;
            long windowStart = value.windowStart;
            if (successRate < successRateThresholdFinal) {
                out.collect(String.format("【告警】服务[%s]的[%s]方法在窗口时间[%s]的成功率为%.2f%%，低于阈值%.2f%%。", service, operatorName,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(java.time.Instant.ofEpochMilli(windowStart)),
                        successRate * 100, successRateThresholdFinal * 100));
            }
        }).returns(String.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static class SuccessRateAggregateFunction
            implements AggregateFunction<Tuple3<String, String, Boolean>, Tuple2<Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple2<Long, Long> createAccumulator() {
            return Tuple2.of(0L, 0L); // (successCount, totalCount)
        }

        @Override
        public Tuple2<Long, Long> add(Tuple3<String, String, Boolean> value, Tuple2<Long, Long> acc) {
            long success = acc.f0 + (value.f2 ? 1 : 0);
            long total = acc.f1 + 1;
            return Tuple2.of(success, total);
        }

        @Override
        public Tuple2<Double, Long> getResult(Tuple2<Long, Long> acc) {
            double rate = acc.f1 == 0 ? 1.0 : (double) acc.f0 / acc.f1;
            return Tuple2.of(rate, acc.f1);
        }

        @Override
        public Tuple2<Long, Long> merge(Tuple2<Long, Long> a, Tuple2<Long, Long> b) {
            return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1);
        }
    }
}
