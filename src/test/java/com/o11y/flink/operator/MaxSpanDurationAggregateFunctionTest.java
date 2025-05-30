package com.o11y.flink.operator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SpanObject;

public class MaxSpanDurationAggregateFunctionTest {
    private SegmentObject buildSegment(long... durations) {
        SegmentObject.Builder segBuilder = SegmentObject.newBuilder();
        for (long d : durations) {
            SpanObject span = SpanObject.newBuilder().setStartTime(0L).setEndTime(d).build();
            segBuilder.addSpans(span);
        }
        return segBuilder.build();
    }

    @Test
    public void testNoSpan() {
        MaxSpanDurationAggregateFunction agg = new MaxSpanDurationAggregateFunction();
        Long acc = agg.createAccumulator();
        SegmentObject seg = buildSegment();
        acc = agg.add(seg, acc);
        Assertions.assertEquals(0L, acc);
        Assertions.assertEquals(0L, agg.getResult(acc));
    }

    @Test
    public void testSingleSpan() {
        MaxSpanDurationAggregateFunction agg = new MaxSpanDurationAggregateFunction();
        Long acc = agg.createAccumulator();
        SegmentObject seg = buildSegment(123L);
        acc = agg.add(seg, acc);
        Assertions.assertEquals(123L, acc);
        Assertions.assertEquals(123L, agg.getResult(acc));
    }

    @Test
    public void testMultiSpan() {
        MaxSpanDurationAggregateFunction agg = new MaxSpanDurationAggregateFunction();
        Long acc = agg.createAccumulator();
        SegmentObject seg = buildSegment(100L, 200L, 300L);
        acc = agg.add(seg, acc);
        Assertions.assertEquals(300L, acc);
        Assertions.assertEquals(300L, agg.getResult(acc));
    }

    @Test
    public void testMerge() {
        MaxSpanDurationAggregateFunction agg = new MaxSpanDurationAggregateFunction();
        Long acc1 = agg.add(buildSegment(100L, 200L), agg.createAccumulator());
        Long acc2 = agg.add(buildSegment(300L, 500L), agg.createAccumulator());
        Long merged = agg.merge(acc1, acc2);
        Assertions.assertEquals(500L, merged);
    }
}
