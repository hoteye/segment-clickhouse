package com.o11y.infrastructure.flink;

import com.o11y.domain.model.alarm.AlarmRule;
import com.o11y.domain.model.alarm.AlertMessage;
import com.o11y.domain.model.aggregation.ServiceAggResult;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AggAlertBroadcastFunction单元测试
 * 测试规则热更新和告警生成功能
 */
public class AggAlertBroadcastFunctionTest {

    private MapStateDescriptor<String, java.util.Map<String, AlarmRule>> ruleStateDescriptor;
    private AggAlertBroadcastFunction function;

    @BeforeEach
    void setUp() throws Exception {
        // 创建状态描述符
        ruleStateDescriptor = new MapStateDescriptor<>(
                "alarmRules",
                Types.STRING,
                TypeInformation.of(new TypeHint<java.util.Map<String, AlarmRule>>() {
                }));

        // 创建被测试的函数
        function = new AggAlertBroadcastFunction(ruleStateDescriptor);
    }

    @Test
    @DisplayName("测试高级别告警")
    void testHighLevelAlert() {
        // 创建只有高阈值的规则
        AlarmRule rule = new AlarmRule();
        rule.service = "user-service";
        rule.operatorClass = "UserController";
        rule.operatorName = "login";
        rule.avgDurationHigh = 100.0; // 只设置高阈值

        // 创建超过高阈值的数据
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "user-service";
        aggResult.operatorClass = "UserController";
        aggResult.operatorName = "login";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.avgDuration = 150.0; // 超过高阈值

        // 测试告警生成
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证结果
        assertNotNull(alert, "应该生成告警消息");
        assertEquals("HIGH", alert.alertLevel, "应该是高级别告警");
        assertTrue(alert.isTriggered, "告警应该被触发");
        assertEquals("user-service", alert.service, "服务名应该匹配");
    }

    @Test
    @DisplayName("测试中级别告警")
    void testMidLevelAlert() {
        // 创建只有中阈值的规则
        AlarmRule rule = new AlarmRule();
        rule.service = "payment-service";
        rule.operatorClass = "PaymentController";
        rule.operatorName = "pay";
        rule.avgDurationMid = 80.0; // 只设置中阈值

        // 创建超过中阈值的数据
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "payment-service";
        aggResult.operatorClass = "PaymentController";
        aggResult.operatorName = "pay";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.avgDuration = 90.0; // 超过中阈值

        // 测试告警生成
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证结果
        assertNotNull(alert, "应该生成告警消息");
        assertEquals("MID", alert.alertLevel, "应该是中级别告警");
        assertTrue(alert.isTriggered, "告警应该被触发");
    }

    @Test
    @DisplayName("测试低级别告警")
    void testLowLevelAlert() {
        // 创建只有低阈值的规则
        AlarmRule rule = new AlarmRule();
        rule.service = "order-service";
        rule.operatorClass = "OrderController";
        rule.operatorName = "create";
        rule.avgDurationLow = 50.0; // 只设置低阈值

        // 创建超过低阈值的数据
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "order-service";
        aggResult.operatorClass = "OrderController";
        aggResult.operatorName = "create";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.avgDuration = 60.0; // 超过低阈值

        // 测试告警生成
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证结果
        assertNotNull(alert, "应该生成告警消息");
        assertEquals("LOW", alert.alertLevel, "应该是低级别告警");
        assertTrue(alert.isTriggered, "告警应该被触发");
    }

    @Test
    @DisplayName("测试正常情况")
    void testNormalCase() {
        // 创建有阈值的规则
        AlarmRule rule = new AlarmRule();
        rule.service = "normal-service";
        rule.operatorClass = "NormalController";
        rule.operatorName = "normal";
        rule.avgDurationLow = 100.0; // 设置一个阈值

        // 创建低于阈值的数据
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "normal-service";
        aggResult.operatorClass = "NormalController";
        aggResult.operatorName = "normal";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.avgDuration = 50.0; // 低于阈值

        // 测试正常情况
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证结果
        assertNotNull(alert, "应该生成消息");
        assertEquals("NORMAL", alert.alertLevel, "应该是正常级别");
        assertFalse(alert.isTriggered, "不应该触发告警");
    }

    @Test
    @DisplayName("测试成功率告警")
    void testSuccessRateAlert() {
        // 创建成功率规则
        AlarmRule rule = new AlarmRule();
        rule.service = "api-service";
        rule.operatorClass = "ApiController";
        rule.operatorName = "process";
        rule.successRateHigh = 0.95; // 要求成功率高于95%

        // 创建低成功率数据
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "api-service";
        aggResult.operatorClass = "ApiController";
        aggResult.operatorName = "process";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.errorRate = 0.10; // 错误率10%，成功率90%，低于95%

        // 测试成功率告警
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证结果
        assertNotNull(alert, "应该生成告警消息");
        assertEquals("HIGH", alert.alertLevel, "低成功率应该是高级别告警");
        assertTrue(alert.isTriggered, "告警应该被触发");
    }

    @Test
    @DisplayName("测试空值处理")
    void testNullHandling() {
        // 创建规则但不设置阈值
        AlarmRule rule = new AlarmRule();
        rule.service = "test-service";
        rule.operatorClass = "TestController";
        rule.operatorName = "test";

        // 创建数据，某些值为null
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "test-service";
        aggResult.operatorClass = "TestController";
        aggResult.operatorName = "test";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.avgDuration = null; // 设置为null

        // 测试空值处理
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证不会抛出异常
        assertNotNull(alert, "空值情况下应该正常生成告警消息");
        assertEquals("NORMAL", alert.alertLevel, "空值情况下应该是正常级别");
        assertFalse(alert.isTriggered, "空值情况下不应该触发告警");
    }

    @Test
    @DisplayName("测试交易量告警")
    void testTrafficVolumeAlert() {
        // 创建交易量规则
        AlarmRule rule = new AlarmRule();
        rule.service = "gateway-service";
        rule.operatorClass = "GatewayController";
        rule.operatorName = "route";
        rule.trafficVolumeMid = 1000.0; // 设置中等交易量阈值

        // 创建超过阈值的数据
        ServiceAggResult aggResult = new ServiceAggResult();
        aggResult.service = "gateway-service";
        aggResult.operatorClass = "GatewayController";
        aggResult.operatorName = "route";
        aggResult.windowStart = System.currentTimeMillis();
        aggResult.windowSize = 60;
        aggResult.totalCount = 1500L; // 超过1000的阈值

        // 测试交易量告警
        AlertMessage alert = AggAlertBroadcastFunction.buildAlertReport(rule, aggResult);

        // 验证结果
        assertNotNull(alert, "应该生成交易量告警");
        assertEquals("MID", alert.alertLevel, "应该是中级别告警");
        assertTrue(alert.isTriggered, "告警应该被触发");
    }
}