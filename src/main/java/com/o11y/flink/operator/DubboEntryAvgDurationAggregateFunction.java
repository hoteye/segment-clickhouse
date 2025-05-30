package com.o11y.flink.operator;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;

/**
 * 7秒窗口内统计 dubbo-provider-a Entry span 平均耗时的聚合算子
 */
public class DubboEntryAvgDurationAggregateFunction implements AggregateFunction<Long, Tuple2<Long, Long>, Double> {
    @Override
    public Tuple2<Long, Long> createAccumulator() {
        return Tuple2.of(0L, 0L);
    }

    @Override
    public Tuple2<Long, Long> add(Long value, Tuple2<Long, Long> acc) {
        return Tuple2.of(acc.f0 + value, acc.f1 + 1);
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
