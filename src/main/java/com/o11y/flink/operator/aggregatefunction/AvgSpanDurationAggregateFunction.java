package com.o11y.flink.operator.aggregatefunction;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import segment.v3.Segment.SegmentObject;

/**
 * 7秒窗口内统计所有span平均时长的聚合算子
 */
public class AvgSpanDurationAggregateFunction implements AggregateFunction<SegmentObject, Tuple2<Long, Long>, Double> {
    @Override
    public Tuple2<Long, Long> createAccumulator() {
        return Tuple2.of(0L, 0L);
    }

    @Override
    public Tuple2<Long, Long> add(SegmentObject value, Tuple2<Long, Long> acc) {
        long sum = acc.f0;
        long count = acc.f1;
        for (int i = 0; i < value.getSpansCount(); i++) {
            long duration = value.getSpans(i).getEndTime() - value.getSpans(i).getStartTime();
            sum += duration;
            count++;
        }
        return Tuple2.of(sum, count);
    }

    @Override
    public Double getResult(Tuple2<Long, Long> acc) {
        return acc.f1 == 0 ? 0.0 : (double) acc.f0 / acc.f1;
    }

    @Override
    public Tuple2<Long, Long> merge(Tuple2<Long, Long> a, Tuple2<Long, Long> b) {
        return Tuple2.of(a.f0 + b.f0, a.f1 + b.f1);
    }
}
