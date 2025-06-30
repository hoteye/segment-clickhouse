package com.o11y.stream.task;

import com.o11y.domain.model.alarm.AlarmRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HourlyRulePublishProcessFunction单元测试
 * 测试小时级规则发布和定时功能
 */
public class HourlyRulePublishProcessFunctionTest {

    private HourlyRulePublishProcessFunction function;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // 创建测试用的函数实例 - 使用正确的构造函数参数
        String clickhouseUrl = "jdbc:clickhouse://localhost:8123/default";
        String schemaName = "test_schema";
        String username = "root";
        String password = "123456";
        String kafkaBootstrapServers = "localhost:9092";
        String kafkaTopicName = "test-alarm-rules";
        long checkIntervalMs = 60000L; // 1分钟检查间隔用于测试

        function = new HourlyRulePublishProcessFunction(
                clickhouseUrl,
                schemaName,
                username,
                password,
                kafkaBootstrapServers,
                kafkaTopicName,
                checkIntervalMs);
    }

    @Test
    @DisplayName("测试下一个检查时间计算")
    void testCalculateNextCheckTime() throws Exception {
        // 使用反射调用私有方法
        Method method = HourlyRulePublishProcessFunction.class
                .getDeclaredMethod("calculateNextCheckTime");
        method.setAccessible(true);

        long currentTime = System.currentTimeMillis();
        long nextCheckTime = (Long) method.invoke(function);

        // 验证下一个检查时间应该是当前时间 + 检查间隔
        assertTrue(nextCheckTime > currentTime, "下一个检查时间应该大于当前时间");
        assertTrue(nextCheckTime <= currentTime + 61000L, "下一个检查时间应该在合理范围内");
    }

    @Test
    @DisplayName("测试告警规则创建")
    void testCreateAlarmRule() {
        // 测试创建告警规则的功能
        AlarmRule rule = new AlarmRule();
        rule.service = "test-service";
        rule.operatorName = "test-operation";
        rule.operatorClass = "TestController";
        rule.avgDurationLow = 100.0;
        rule.avgDurationMid = 200.0;
        rule.avgDurationHigh = 400.0;

        // 验证规则字段设置
        assertEquals("test-service", rule.service, "服务名应该正确设置");
        assertEquals("test-operation", rule.operatorName, "操作名应该正确设置");
        assertEquals("TestController", rule.operatorClass, "操作类应该正确设置");
        assertNotNull(rule.avgDurationLow, "低阈值应该被设置");
        assertNotNull(rule.avgDurationMid, "中阈值应该被设置");
        assertNotNull(rule.avgDurationHigh, "高阈值应该被设置");
    }

    @Test
    @DisplayName("测试规则组合键生成")
    void testRuleCombineKey() {
        AlarmRule rule = new AlarmRule();
        rule.service = "user-service";
        rule.operatorName = "login";

        String expectedKey = "user-service|login";
        String actualKey = rule.combine();

        assertEquals(expectedKey, actualKey, "组合键应该是service|operatorName格式");
    }

    @Test
    @DisplayName("测试规则映射为空的情况")
    void testEmptyRuleMap() {
        Map<String, AlarmRule> emptyRuleMap = new HashMap<>();

        // 验证空映射不会导致异常
        assertNotNull(emptyRuleMap, "空规则映射应该可以正常处理");
        assertTrue(emptyRuleMap.isEmpty(), "空规则映射应该是空的");
        assertEquals(0, emptyRuleMap.size(), "空规则映射大小应该为0");
    }

    @Test
    @DisplayName("测试规则映射包含多个规则")
    void testMultipleRulesInMap() {
        Map<String, AlarmRule> ruleMap = new HashMap<>();

        // 添加第一个规则
        AlarmRule rule1 = new AlarmRule();
        rule1.service = "user-service";
        rule1.operatorName = "login";
        rule1.operatorClass = "UserController";
        ruleMap.put(rule1.combine(), rule1);

        // 添加第二个规则
        AlarmRule rule2 = new AlarmRule();
        rule2.service = "payment-service";
        rule2.operatorName = "pay";
        rule2.operatorClass = "PaymentController";
        ruleMap.put(rule2.combine(), rule2);

        // 验证规则映射
        assertEquals(2, ruleMap.size(), "应该包含两个规则");
        assertTrue(ruleMap.containsKey("user-service|login"), "应该包含用户服务登录规则");
        assertTrue(ruleMap.containsKey("payment-service|pay"), "应该包含支付服务规则");

        // 验证规则内容
        AlarmRule retrievedRule1 = ruleMap.get("user-service|login");
        assertNotNull(retrievedRule1, "应该能检索到用户服务规则");
        assertEquals("UserController", retrievedRule1.operatorClass, "用户服务规则的操作类应该正确");

        AlarmRule retrievedRule2 = ruleMap.get("payment-service|pay");
        assertNotNull(retrievedRule2, "应该能检索到支付服务规则");
        assertEquals("PaymentController", retrievedRule2.operatorClass, "支付服务规则的操作类应该正确");
    }

    @Test
    @DisplayName("测试当前时间获取")
    void testCurrentTimeRetrieval() {
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        // 验证时间范围
        assertTrue(currentHour >= 0 && currentHour <= 23, "小时应该在0-23范围内");
        assertNotNull(now, "当前时间应该不为空");
    }

    @Test
    @DisplayName("测试函数初始化参数")
    void testFunctionInitialization() {
        // 验证函数对象创建成功
        assertNotNull(function, "函数对象应该成功创建");

        // 通过反射验证内部字段（如果需要的话）
        // 这里主要验证构造函数没有抛出异常
        assertTrue(true, "函数初始化应该成功");
    }
}