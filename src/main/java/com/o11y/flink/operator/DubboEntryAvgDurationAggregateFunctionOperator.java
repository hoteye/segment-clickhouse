package com.o11y.flink.operator;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.List;
import segment.v3.Segment.SegmentObject;
import org.apache.flink.util.Collector;

/**
 * Dubbo入口平均耗时算子模板，实现 FlinkOperator 接口，可直接注册到 OperatorRegistry。
 */
public class DubboEntryAvgDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(DubboEntryAvgDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        List<String> serviceNames = params.get("service");
        String spanType = params.get("spanType").get(0);
        DataStream<SegmentObject> filtered = ((DataStream<SegmentObject>) input)
                .filter(segment -> serviceNames.contains(segment.getService()));
        DataStream<Long> durationStream = filtered.flatMap((SegmentObject segment, Collector<Long> out) -> {
            for (int i = 0; i < segment.getSpansCount(); i++) {
                var span = segment.getSpans(i);
                if (spanType.equals(span.getSpanType().name())) {
                    long duration = span.getEndTime() - span.getStartTime();
                    out.collect(duration);
                }
            }
        }).returns(Long.class);
        DataStream<Double> avgDuration = durationStream
                .windowAll(TumblingProcessingTimeWindows.of(Time.seconds(7)))
                .aggregate(new DubboEntryAvgDurationAggregateFunction());
        avgDuration.addSink(new RichSinkFunction<Double>() {
            @Override
            public void invoke(Double value, Context context) {
                LOG.info("7s window dubboEntryAvgDuration: {} ms", value);
            }
        }).name(NAME);
        return avgDuration;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
