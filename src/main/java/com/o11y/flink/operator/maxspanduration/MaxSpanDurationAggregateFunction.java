package com.o11y.flink.operator.maxspanduration;

import org.apache.flink.api.common.functions.AggregateFunction;
import segment.v3.Segment.SegmentObject;

/**
 * 7秒窗口内统计所有span最大时长的聚合算子
 */
public class MaxSpanDurationAggregateFunction implements AggregateFunction<SegmentObject, Long, Long> {
    @Override
    public Long createAccumulator() {
        return 0L;
    }

    @Override
    public Long add(SegmentObject value, Long acc) {
        long max = acc;
        for (int i = 0; i < value.getSpansCount(); i++) {
            long duration = value.getSpans(i).getEndTime() - value.getSpans(i).getStartTime();
            if (duration > max) {
                max = duration;
            }
        }
        return max;
    }

    @Override
    public Long getResult(Long acc) {
        return acc;
    }

    @Override
    public Long merge(Long a, Long b) {
        return Math.max(a, b);
    }
}
