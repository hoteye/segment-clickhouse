package com.o11y.flink.operator.avgspanduration;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.o11y.flink.operator.FlinkOperator;

import java.util.List;
import java.util.Map;

/**
 * 平均span耗时算子模板，实现 FlinkOperator 接口，可直接注册到 OperatorRegistry。
 */
public class AvgSpanDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(AvgSpanDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        // 假设 input 为 DataStream<SegmentObject>
        DataStream<Double> avgSpanDurationStream = ((DataStream<segment.v3.Segment.SegmentObject>) input)
                .windowAll(TumblingProcessingTimeWindows.of(Time.seconds(7)))
                .aggregate(new AvgSpanDurationAggregateFunction());
        avgSpanDurationStream.addSink(new RichSinkFunction<Double>() {
            @Override
            public void invoke(Double value, Context context) {
                LOG.debug("7s window avg span duration: {} ms", value);
            }
        }).name(NAME);
        return avgSpanDurationStream;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
