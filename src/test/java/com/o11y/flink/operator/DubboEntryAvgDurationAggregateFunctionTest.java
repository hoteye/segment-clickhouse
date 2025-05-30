package com.o11y.flink.operator;

import org.apache.flink.api.java.tuple.Tuple2;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DubboEntryAvgDurationAggregateFunctionTest {
    static class Agg extends DubboEntryAvgDurationAggregateFunction {
    }

    @Test
    public void testAvg() {
        Agg agg = new Agg();
        Tuple2<Long, Long> acc = agg.createAccumulator();
        acc = agg.add(100L, acc);
        acc = agg.add(200L, acc);
        acc = agg.add(300L, acc);
        Assertions.assertEquals(200.0, agg.getResult(acc), 0.001);
    }

    @Test
    public void testMerge() {
        Agg agg = new Agg();
        Tuple2<Long, Long> acc1 = agg.createAccumulator();
        Tuple2<Long, Long> acc2 = agg.createAccumulator();
        acc1 = agg.add(100L, acc1);
        acc1 = agg.add(200L, acc1);
        acc2 = agg.add(300L, acc2);
        acc2 = agg.add(500L, acc2);
        Tuple2<Long, Long> merged = agg.merge(acc1, acc2);
        Assertions.assertEquals(275.0, agg.getResult(merged), 0.001);
    }

    @Test
    public void testEmpty() {
        Agg agg = new Agg();
        Tuple2<Long, Long> acc = agg.createAccumulator();
        Assertions.assertEquals(0.0, agg.getResult(acc), 0.001);
    }
}
