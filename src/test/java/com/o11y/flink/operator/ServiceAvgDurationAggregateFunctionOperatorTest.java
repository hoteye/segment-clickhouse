package com.o11y.flink.operator;

import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class ServiceAvgDurationAggregateFunctionOperatorTest {
    @Test
    public void testAlarmOnThreshold() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        List<Tuple5<String, String, Double, Long, Long>> input = Arrays.asList(
                Tuple5.of("order", "pay", 120.5, 300L, 1718160000000L),
                Tuple5.of("order", "pay", 80.0, 500L, 1718160060000L),
                Tuple5.of("user", "login", 60.0, 200L, 1718160120000L),
                Tuple5.of("user", "login", 200.0, 100L, 1718160180000L));
        DataStream<Tuple5<String, String, Double, Long, Long>> inputStream = env.fromCollection(input);
        ServiceAvgDurationAggregateFunctionOperator op = new ServiceAvgDurationAggregateFunctionOperator();
        DataStream<String> alarmStream = op.alarmOnThreshold(inputStream, 100.0, 250L);
        alarmStream.print();
        env.execute();
    }
}
