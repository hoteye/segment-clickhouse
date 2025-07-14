package com.o11y.stream.operator.aggregate;

import com.o11y.domain.model.aggregation.ServiceAggResult;
import org.apache.flink.api.java.tuple.Tuple5;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import segment.v3.Segment.SegmentObject;
import segment.v3.Segment.SpanObject;
import segment.v3.Segment.SpanType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AggregateOperator 核心算子单元测试
 * 
 * 测试聚合算子的核心功能，包括：
 * 1. Entry span 提取功能
 * 2. 服务维度窗口聚合功能  
 * 3. 性能指标计算正确性
 * 4. 参数配置和异常处理
 */
@ExtendWith(MockitoExtension.class)
public class AggregateOperatorTest {

    private AggregateOperator aggregateOperator;
    private StreamExecutionEnvironment env;

    @BeforeEach
    void setUp() {
        aggregateOperator = new AggregateOperator();
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // 设置并行度为1便于测试
    }

    @AfterEach
    void tearDown() {
        // 清理资源
        aggregateOperator = null;
        env = null;
    }

    @Test
    @DisplayName("测试算子名称获取")
    void testGetName() {
        String name = aggregateOperator.getName();
        assertEquals("AggregateOperator", name, "算子名称应该是类的简单名称");
    }

    @Test
    @DisplayName("测试参数构造函数")
    void testParameterizedConstructor() {
        int windowSeconds = 10;
        double avgThreshold = 100.0;
        long maxThreshold = 1000L;
        
        AggregateOperator operator = new AggregateOperator(windowSeconds, avgThreshold, maxThreshold);
        assertNotNull(operator, "带参构造函数应该成功创建算子实例");
        assertEquals("AggregateOperator", operator.getName(), "算子名称应该正确");
    }

    @Test
    @DisplayName("测试缺少windowSize参数时抛出异常")
    void testMissingWindowSizeParameter() {
        // 创建不包含windowSize的参数映射
        Map<String, List<String>> params = new HashMap<>();
        params.put("otherParam", List.of("value"));

        DataStream<SegmentObject> mockStream = env.fromElements(createTestSegmentObject());

        // 应该抛出IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            aggregateOperator.apply(mockStream, params);
        }, "缺少windowSize参数应该抛出IllegalStateException");

        assertTrue(exception.getMessage().contains("windowSize"), 
                "异常信息应该包含windowSize");
        assertTrue(exception.getMessage().contains("missing"), 
                "异常信息应该说明参数缺失");
    }

    @Test
    @DisplayName("测试windowSize参数为空列表时抛出异常")
    void testEmptyWindowSizeParameter() {
        // 创建包含空windowSize列表的参数映射
        Map<String, List<String>> params = new HashMap<>();
        params.put("windowSize", new ArrayList<>());

        DataStream<SegmentObject> mockStream = env.fromElements(createTestSegmentObject());

        // 应该抛出IllegalStateException
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            aggregateOperator.apply(mockStream, params);
        }, "空的windowSize参数应该抛出IllegalStateException");

        assertTrue(exception.getMessage().contains("windowSize"), 
                "异常信息应该包含windowSize");
    }

    @Test
    @DisplayName("测试Entry Span提取功能")
    void testExtractEntrySpan() {
        // 创建测试用的SegmentObject
        SegmentObject segment = createTestSegmentObject();
        
        DataStream<SegmentObject> sourceStream = env.fromElements(segment);
        
        // 测试extractEntrySpan方法（使用反射或创建可测试的版本）
        // 由于extractEntrySpan是protected方法，我们创建一个测试可访问的版本
        TestableAggregateOperator testableOperator = new TestableAggregateOperator();
        DataStream<Tuple5<String, String, Boolean, Long, Long>> result = 
                testableOperator.extractEntrySpan(sourceStream);
        
        assertNotNull(result, "Entry span提取结果不应该为null");
    }

    @Test
    @DisplayName("测试ServiceAvgMaxAggregateFunction累加器创建")
    void testAggregateFunction_CreateAccumulator() {
        AggregateOperator.ServiceAvgMaxAggregateFunction function = 
                new AggregateOperator.ServiceAvgMaxAggregateFunction();
        
        var accumulator = function.createAccumulator();
        
        assertNotNull(accumulator, "累加器不应该为null");
        assertEquals(0L, accumulator.f0, "初始总和应该为0");
        assertEquals(0L, accumulator.f1, "初始计数应该为0");
        assertEquals(Long.MIN_VALUE, accumulator.f2, "初始最大值应该为Long.MIN_VALUE");
    }

    @Test
    @DisplayName("测试ServiceAvgMaxAggregateFunction数据添加")
    void testAggregateFunction_Add() {
        AggregateOperator.ServiceAvgMaxAggregateFunction function = 
                new AggregateOperator.ServiceAvgMaxAggregateFunction();
        
        var accumulator = function.createAccumulator();
        var value = org.apache.flink.api.java.tuple.Tuple3.of("service", "operation", 100L);
        
        var result = function.add(value, accumulator);
        
        assertEquals(100L, result.f0, "总和应该是100");
        assertEquals(1L, result.f1, "计数应该是1");
        assertEquals(100L, result.f2, "最大值应该是100");
    }

    @Test
    @DisplayName("测试ServiceAvgMaxAggregateFunction多次添加")
    void testAggregateFunction_MultipleAdd() {
        AggregateOperator.ServiceAvgMaxAggregateFunction function = 
                new AggregateOperator.ServiceAvgMaxAggregateFunction();
        
        var accumulator = function.createAccumulator();
        var value1 = org.apache.flink.api.java.tuple.Tuple3.of("service", "operation", 100L);
        var value2 = org.apache.flink.api.java.tuple.Tuple3.of("service", "operation", 200L);
        var value3 = org.apache.flink.api.java.tuple.Tuple3.of("service", "operation", 50L);
        
        accumulator = function.add(value1, accumulator);
        accumulator = function.add(value2, accumulator);
        accumulator = function.add(value3, accumulator);
        
        assertEquals(350L, accumulator.f0, "总和应该是350");
        assertEquals(3L, accumulator.f1, "计数应该是3");
        assertEquals(200L, accumulator.f2, "最大值应该是200");
    }

    @Test
    @DisplayName("测试ServiceAvgMaxAggregateFunction结果获取")
    void testAggregateFunction_GetResult() {
        AggregateOperator.ServiceAvgMaxAggregateFunction function = 
                new AggregateOperator.ServiceAvgMaxAggregateFunction();
        
        // 构造累加器状态：总和300，计数3，最大值150
        var accumulator = org.apache.flink.api.java.tuple.Tuple3.of(300L, 3L, 150L);
        
        var result = function.getResult(accumulator);
        
        assertEquals(100.0, result.f0, 0.001, "平均值应该是100.0");
        assertEquals(150L, result.f1, "最大值应该是150");
    }

    @Test
    @DisplayName("测试ServiceAvgMaxAggregateFunction空累加器结果")
    void testAggregateFunction_GetResult_EmptyAccumulator() {
        AggregateOperator.ServiceAvgMaxAggregateFunction function = 
                new AggregateOperator.ServiceAvgMaxAggregateFunction();
        
        var accumulator = function.createAccumulator(); // 空累加器
        
        var result = function.getResult(accumulator);
        
        assertEquals(0.0, result.f0, 0.001, "空累加器的平均值应该是0.0");
        assertEquals(Long.MIN_VALUE, result.f1, "空累加器的最大值应该是Long.MIN_VALUE");
    }

    @Test
    @DisplayName("测试ServiceAvgMaxAggregateFunction累加器合并")
    void testAggregateFunction_Merge() {
        AggregateOperator.ServiceAvgMaxAggregateFunction function = 
                new AggregateOperator.ServiceAvgMaxAggregateFunction();
        
        // 第一个累加器：总和100，计数2，最大值80
        var acc1 = org.apache.flink.api.java.tuple.Tuple3.of(100L, 2L, 80L);
        // 第二个累加器：总和200，计数3，最大值120
        var acc2 = org.apache.flink.api.java.tuple.Tuple3.of(200L, 3L, 120L);
        
        var merged = function.merge(acc1, acc2);
        
        assertEquals(300L, merged.f0, "合并后总和应该是300");
        assertEquals(5L, merged.f1, "合并后计数应该是5");
        assertEquals(120L, merged.f2, "合并后最大值应该是120");
    }

    @Test
    @DisplayName("测试正确的参数配置下的apply方法")
    void testApplyWithValidParameters() {
        // 创建包含正确windowSize的参数映射
        Map<String, List<String>> params = new HashMap<>();
        params.put("windowSize", List.of("5"));

        // 创建测试用的流
        DataStream<SegmentObject> sourceStream = env.fromElements(createTestSegmentObject());

        // 应该能正常执行不抛出异常
        assertDoesNotThrow(() -> {
            DataStream<ServiceAggResult> result = aggregateOperator.apply(sourceStream, params);
            assertNotNull(result, "apply方法应该返回非null的结果流");
        }, "正确的参数配置应该不抛出异常");
    }

    /**
     * 创建测试用的SegmentObject
     */
    private SegmentObject createTestSegmentObject() {
        // 创建Entry类型的SpanObject
        SpanObject entrySpan = SpanObject.newBuilder()
                .setOperationName("testOperation")
                .setStartTime(1000L)
                .setEndTime(1100L)
                .setSpanType(SpanType.Entry)
                .setIsError(false)
                .build();

        // 创建其他类型的SpanObject（应该被过滤）
        SpanObject localSpan = SpanObject.newBuilder()
                .setOperationName("localOperation")
                .setStartTime(1050L)
                .setEndTime(1080L)
                .setSpanType(SpanType.Local)
                .setIsError(false)
                .build();

        // 创建SegmentObject
        return SegmentObject.newBuilder()
                .setService("testService")
                .setTraceId("test-trace-123")
                .addSpans(entrySpan)
                .addSpans(localSpan)
                .build();
    }

    /**
     * 测试可访问的AggregateOperator子类
     * 将protected方法暴露为public以便测试
     */
    private static class TestableAggregateOperator extends AggregateOperator {
        @Override
        public DataStream<Tuple5<String, String, Boolean, Long, Long>> extractEntrySpan(
                DataStream<SegmentObject> stream) {
            return super.extractEntrySpan(stream);
        }

        @Override
        public DataStream<ServiceAggResult> aggregateByService(
                DataStream<Tuple5<String, String, Boolean, Long, Long>> inputStream) {
            return super.aggregateByService(inputStream);
        }
    }
}