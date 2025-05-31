package com.o11y.flink.operator;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 最大span耗时算子模板，实现 FlinkOperator 接口，可直接注册到 OperatorRegistry。
 */
public class MaxSpanDurationAggregateFunctionOperator implements FlinkOperator {
    private static final Logger LOG = LoggerFactory.getLogger(MaxSpanDurationAggregateFunctionOperator.class);
    private final String NAME = this.getClass().getSimpleName();

    @Override
    public DataStream<?> apply(DataStream<?> input, Map<String, List<String>> params) {
        // 假设 input 为 DataStream<SegmentObject>
        DataStream<Long> maxSpanDurationStream = ((DataStream<segment.v3.Segment.SegmentObject>) input)
                .windowAll(TumblingProcessingTimeWindows.of(Time.seconds(7)))
                .aggregate(new MaxSpanDurationAggregateFunction());
        maxSpanDurationStream.addSink(new RichSinkFunction<Long>() {
            @Override
            public void invoke(Long value, Context context) {
                LOG.debug("7s window max span duration: {} ms", value);
            }
        }).name(NAME);
        return maxSpanDurationStream;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
