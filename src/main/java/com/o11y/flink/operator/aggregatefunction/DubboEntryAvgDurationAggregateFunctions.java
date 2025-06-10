package com.o11y.flink.operator.aggregatefunction;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;

/**
 * 针对 DubboEntryAvgDurationAggregateFunctionOperator 的窗口内聚合算子实现（原
 * AvgMaxAggregateFunction2 和 GlobalAvgMaxAggregateFunction）。
 * 如需进一步解耦可单独拆分。
 */
public class DubboEntryAvgDurationAggregateFunctions {
    public static class AvgMaxAggregateFunction2
            implements AggregateFunction<Tuple3<String, Long, Long>, Tuple3<Long, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Long, Long, Long> createAccumulator() {
            return Tuple3.of(0L, 0L, 0L);
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
            double avg = acc.f1 == 0 ? 0.0 : (double) acc.f0 / acc.f1;
            return Tuple2.of(avg, acc.f2);
        }

        @Override
        public Tuple3<Long, Long, Long> merge(Tuple3<Long, Long, Long> a, Tuple3<Long, Long, Long> b) {
            return Tuple3.of(a.f0 + b.f0, a.f1 + b.f1, Math.max(a.f2, b.f2));
        }
    }

    public static class GlobalAvgMaxAggregateFunction
            implements AggregateFunction<Tuple2<Double, Long>, Tuple3<Double, Long, Long>, Tuple2<Double, Long>> {
        @Override
        public Tuple3<Double, Long, Long> createAccumulator() {
            return Tuple3.of(0.0, 0L, 0L);
        }

        @Override
        public Tuple3<Double, Long, Long> add(Tuple2<Double, Long> value, Tuple3<Double, Long, Long> acc) {
            double sum = acc.f0 + value.f0;
            long count = acc.f1 + 1;
            long max = Math.max(acc.f2, value.f1);
            return Tuple3.of(sum, count, max);
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
}
