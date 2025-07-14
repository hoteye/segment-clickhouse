package com.o11y.stream.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.o11y.domain.model.alarm.AlarmRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmRuleDeserializationSchema 单元测试
 * 
 * 测试告警规则反序列化功能：
 * 1. 正常JSON数据反序列化
 * 2. 异常数据处理
 * 3. 类型信息获取
 * 4. 流结束检查
 */
public class AlarmRuleDeserializationSchemaTest {

    private AlarmRuleDeserializationSchema schema;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        schema = new AlarmRuleDeserializationSchema();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("测试构造函数")
    void testConstructor() {
        AlarmRuleDeserializationSchema newSchema = new AlarmRuleDeserializationSchema();
        assertNotNull(newSchema, "Schema应该成功创建");
    }

    @Test
    @DisplayName("测试正常JSON数据反序列化")
    void testDeserializeValidJson() throws IOException {
        // 创建测试用的告警规则映射
        Map<String, AlarmRule> originalRules = createTestAlarmRules();
        
        // 序列化为JSON字节数组
        byte[] jsonBytes = objectMapper.writeValueAsBytes(originalRules);
        
        // 反序列化
        Map<String, AlarmRule> deserializedRules = schema.deserialize(jsonBytes);
        
        // 验证结果
        assertNotNull(deserializedRules, "反序列化结果不应该为null");
        assertEquals(2, deserializedRules.size(), "应该包含2个规则");
        
        // 验证第一个规则
        assertTrue(deserializedRules.containsKey("user-service|login"), "应该包含用户服务登录规则");
        AlarmRule userRule = deserializedRules.get("user-service|login");
        assertEquals("user-service", userRule.service, "服务名应该正确");
        assertEquals("login", userRule.operatorName, "操作名应该正确");
        assertEquals(100.0, userRule.avgDurationLow, 0.001, "低阈值应该正确");
        
        // 验证第二个规则
        assertTrue(deserializedRules.containsKey("payment-service|pay"), "应该包含支付服务规则");
        AlarmRule paymentRule = deserializedRules.get("payment-service|pay");
        assertEquals("payment-service", paymentRule.service, "服务名应该正确");
        assertEquals("pay", paymentRule.operatorName, "操作名应该正确");
    }

    @Test
    @DisplayName("测试空JSON对象反序列化")
    void testDeserializeEmptyJson() throws IOException {
        byte[] emptyJsonBytes = "{}".getBytes();
        
        Map<String, AlarmRule> result = schema.deserialize(emptyJsonBytes);
        
        assertNotNull(result, "空JSON对象应该返回空映射");
        assertTrue(result.isEmpty(), "结果映射应该是空的");
    }

    @Test
    @DisplayName("测试无效JSON数据处理")
    void testDeserializeInvalidJson() throws IOException {
        byte[] invalidJsonBytes = "invalid json string".getBytes();
        
        Map<String, AlarmRule> result = schema.deserialize(invalidJsonBytes);
        
        assertNull(result, "无效JSON应该返回null");
    }

    @Test
    @DisplayName("测试畸形JSON数据处理")
    void testDeserializeMalformedJson() throws IOException {
        byte[] malformedJsonBytes = "{\"key\": }".getBytes();
        
        Map<String, AlarmRule> result = schema.deserialize(malformedJsonBytes);
        
        assertNull(result, "畸形JSON应该返回null");
    }

    @Test
    @DisplayName("测试空字节数组处理")
    void testDeserializeEmptyBytes() throws IOException {
        byte[] emptyBytes = new byte[0];
        
        Map<String, AlarmRule> result = schema.deserialize(emptyBytes);
        
        assertNull(result, "空字节数组应该返回null");
    }

    @Test
    @DisplayName("测试null字节数组处理")
    void testDeserializeNullBytes() {
        assertThrows(Exception.class, () -> {
            schema.deserialize(null);
        }, "null字节数组应该抛出异常");
    }

    @Test
    @DisplayName("测试isEndOfStream方法")
    void testIsEndOfStream() {
        Map<String, AlarmRule> testRules = createTestAlarmRules();
        
        // 非null映射
        assertFalse(schema.isEndOfStream(testRules), "非null映射应该返回false");
        
        // 空映射
        assertFalse(schema.isEndOfStream(new HashMap<>()), "空映射应该返回false");
        
        // null映射
        assertFalse(schema.isEndOfStream(null), "null映射应该返回false");
    }

    @Test
    @DisplayName("测试getProducedType方法")
    void testGetProducedType() {
        var typeInfo = schema.getProducedType();
        
        assertNotNull(typeInfo, "类型信息不应该为null");
        assertEquals(Map.class, typeInfo.getTypeClass(), "类型类应该是Map");
    }

    @Test
    @DisplayName("测试单个规则的JSON反序列化")
    void testDeserializeSingleRule() throws IOException {
        // 创建只包含一个规则的映射
        AlarmRule singleRule = new AlarmRule();
        singleRule.service = "test-service";
        singleRule.operatorName = "test-operation";
        singleRule.operatorClass = "TestController";
        singleRule.avgDurationLow = 50.0;
        singleRule.avgDurationMid = 100.0;
        singleRule.avgDurationHigh = 200.0;
        singleRule.successRateLow = 0.99;
        singleRule.successRateMid = 0.95;
        singleRule.successRateHigh = 0.90;
        
        Map<String, AlarmRule> singleRuleMap = new HashMap<>();
        singleRuleMap.put(singleRule.combine(), singleRule);
        
        byte[] jsonBytes = objectMapper.writeValueAsBytes(singleRuleMap);
        Map<String, AlarmRule> result = schema.deserialize(jsonBytes);
        
        assertNotNull(result, "结果不应该为null");
        assertEquals(1, result.size(), "应该包含1个规则");
        
        AlarmRule deserializedRule = result.get("test-service|test-operation");
        assertNotNull(deserializedRule, "规则不应该为null");
        assertEquals("test-service", deserializedRule.service, "服务名应该正确");
        assertEquals("test-operation", deserializedRule.operatorName, "操作名应该正确");
        assertEquals(50.0, deserializedRule.avgDurationLow, 0.001, "低阈值应该正确");
    }

    @Test
    @DisplayName("测试复杂JSON结构反序列化")
    void testDeserializeComplexJson() throws IOException {
        // 创建复杂的告警规则
        Map<String, AlarmRule> complexRules = new HashMap<>();
        
        for (int i = 0; i < 5; i++) {
            AlarmRule rule = new AlarmRule();
            rule.service = "service-" + i;
            rule.operatorName = "operation-" + i;
            rule.operatorClass = "Controller" + i;
            rule.avgDurationLow = 100.0 * (i + 1);
            rule.avgDurationMid = 200.0 * (i + 1);
            rule.avgDurationHigh = 400.0 * (i + 1);
            rule.successRateLow = 0.99 - (i * 0.01);
            rule.successRateMid = 0.95 - (i * 0.01);
            rule.successRateHigh = 0.90 - (i * 0.01);
            
            complexRules.put(rule.combine(), rule);
        }
        
        byte[] jsonBytes = objectMapper.writeValueAsBytes(complexRules);
        Map<String, AlarmRule> result = schema.deserialize(jsonBytes);
        
        assertNotNull(result, "结果不应该为null");
        assertEquals(5, result.size(), "应该包含5个规则");
        
        // 验证所有规则都被正确反序列化
        for (int i = 0; i < 5; i++) {
            String key = "service-" + i + "|operation-" + i;
            assertTrue(result.containsKey(key), "应该包含规则: " + key);
            
            AlarmRule rule = result.get(key);
            assertEquals("service-" + i, rule.service, "服务名应该正确");
            assertEquals("operation-" + i, rule.operatorName, "操作名应该正确");
        }
    }

    @Test
    @DisplayName("测试JSON中包含null值的处理")
    void testDeserializeJsonWithNullValues() throws IOException {
        String jsonWithNulls = "{\"key1\": null, \"key2\": {\"service\": \"test\", \"operatorName\": null}}";
        byte[] jsonBytes = jsonWithNulls.getBytes();
        
        Map<String, AlarmRule> result = schema.deserialize(jsonBytes);
        
        // 根据实际的Jackson配置，这可能返回null或包含null值的映射
        // 这里主要测试不抛出异常
        assertDoesNotThrow(() -> {
            schema.deserialize(jsonBytes);
        }, "包含null值的JSON应该被优雅处理");
    }

    @Test
    @DisplayName("测试非常大的JSON数据处理")
    void testDeserializeLargeJson() throws IOException {
        // 创建大量规则
        Map<String, AlarmRule> largeRuleSet = new HashMap<>();
        
        for (int i = 0; i < 100; i++) {
            AlarmRule rule = new AlarmRule();
            rule.service = "service-" + i;
            rule.operatorName = "operation-" + i;
            rule.avgDurationLow = 100.0;
            largeRuleSet.put(rule.combine(), rule);
        }
        
        byte[] jsonBytes = objectMapper.writeValueAsBytes(largeRuleSet);
        Map<String, AlarmRule> result = schema.deserialize(jsonBytes);
        
        assertNotNull(result, "大JSON数据应该能正常反序列化");
        assertEquals(100, result.size(), "应该包含100个规则");
    }

    /**
     * 创建测试用的告警规则映射
     */
    private Map<String, AlarmRule> createTestAlarmRules() {
        Map<String, AlarmRule> rules = new HashMap<>();
        
        // 用户服务登录规则
        AlarmRule userRule = new AlarmRule();
        userRule.service = "user-service";
        userRule.operatorName = "login";
        userRule.operatorClass = "UserController";
        userRule.avgDurationLow = 100.0;
        userRule.avgDurationMid = 200.0;
        userRule.avgDurationHigh = 400.0;
        userRule.successRateLow = 0.995;
        userRule.successRateMid = 0.99;
        userRule.successRateHigh = 0.985;
        rules.put(userRule.combine(), userRule);
        
        // 支付服务规则
        AlarmRule paymentRule = new AlarmRule();
        paymentRule.service = "payment-service";
        paymentRule.operatorName = "pay";
        paymentRule.operatorClass = "PaymentController";
        paymentRule.avgDurationLow = 200.0;
        paymentRule.avgDurationMid = 500.0;
        paymentRule.avgDurationHigh = 1000.0;
        paymentRule.successRateLow = 0.998;
        paymentRule.successRateMid = 0.995;
        paymentRule.successRateHigh = 0.99;
        rules.put(paymentRule.combine(), paymentRule);
        
        return rules;
    }
}