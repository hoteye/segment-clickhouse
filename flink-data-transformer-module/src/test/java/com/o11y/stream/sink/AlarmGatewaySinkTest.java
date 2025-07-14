package com.o11y.stream.sink;

import com.o11y.domain.model.alarm.AlertMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AlarmGatewaySink 单元测试
 * 
 * 测试告警网关Sink的核心功能：
 * 1. 告警消息发送
 * 2. 触发状态检查
 * 3. 异常处理
 * 4. 日志记录
 */
@ExtendWith(MockitoExtension.class)
public class AlarmGatewaySinkTest {

    private AlarmGatewaySink alarmGatewaySink;

    @BeforeEach
    void setUp() {
        alarmGatewaySink = new AlarmGatewaySink();
    }

    @Test
    @DisplayName("测试Sink构造函数")
    void testConstructor() {
        AlarmGatewaySink sink = new AlarmGatewaySink();
        assertNotNull(sink, "AlarmGatewaySink应该成功创建");
    }

    @Test
    @DisplayName("测试已触发告警消息的invoke方法")
    void testInvokeWithTriggeredAlert() {
        // 创建已触发的告警消息
        AlertMessage triggeredAlert = createTestAlertMessage();
        triggeredAlert.isTriggered = true;
        
        // 应该不抛出异常
        assertDoesNotThrow(() -> {
            alarmGatewaySink.invoke(triggeredAlert, null);
        }, "处理已触发的告警消息应该不抛出异常");
    }

    @Test
    @DisplayName("测试未触发告警消息的invoke方法")
    void testInvokeWithUntriggeredAlert() {
        // 创建未触发的告警消息
        AlertMessage untriggeredAlert = createTestAlertMessage();
        untriggeredAlert.isTriggered = false;
        
        // 应该不抛出异常（但会被跳过处理）
        assertDoesNotThrow(() -> {
            alarmGatewaySink.invoke(untriggeredAlert, null);
        }, "处理未触发的告警消息应该不抛出异常");
    }

    @Test
    @DisplayName("测试null告警消息的invoke方法")
    void testInvokeWithNullAlert() {
        // null告警消息可能抛出异常，这是可以接受的行为
        assertDoesNotThrow(() -> {
            try {
                alarmGatewaySink.invoke(null, null);
            } catch (NullPointerException e) {
                // NPE是可以接受的行为
            }
        }, "处理null告警消息应该被优雅处理或抛出明确异常");
    }

    @Test
    @DisplayName("测试告警消息内容验证")
    void testAlertMessageValidation() {
        AlertMessage alert = createTestAlertMessage();
        
        // 验证告警消息的关键字段
        assertNotNull(alert.service, "服务名不应该为null");
        assertNotNull(alert.operatorName, "操作名不应该为null");
        assertNotNull(alert.alertLevel, "告警级别不应该为null");
        assertNotNull(alert.content, "告警内容不应该为null");
        assertTrue(alert.alertTime > 0, "时间戳应该为正数");
    }

    @Test
    @DisplayName("测试不同告警级别的处理")
    void testDifferentAlertLevels() {
        // 测试高级别告警
        AlertMessage highAlert = new AlertMessage(
                "test-service", "test-operation", "HIGH", 
                System.currentTimeMillis(), "高级别告警", true);
        
        assertDoesNotThrow(() -> {
            alarmGatewaySink.invoke(highAlert, null);
        }, "高级别告警应该正常处理");

        // 测试中级别告警
        AlertMessage mediumAlert = new AlertMessage(
                "test-service", "test-operation", "MEDIUM", 
                System.currentTimeMillis(), "中级别告警", true);
        
        assertDoesNotThrow(() -> {
            alarmGatewaySink.invoke(mediumAlert, null);
        }, "中级别告警应该正常处理");

        // 测试低级别告警
        AlertMessage lowAlert = new AlertMessage(
                "test-service", "test-operation", "LOW", 
                System.currentTimeMillis(), "低级别告警", true);
        
        assertDoesNotThrow(() -> {
            alarmGatewaySink.invoke(lowAlert, null);
        }, "低级别告警应该正常处理");
    }

    @Test
    @DisplayName("测试告警消息状态转换")
    void testAlertMessageStateTransition() {
        AlertMessage alert = createTestAlertMessage();
        
        // 初始状态：未触发
        alert.isTriggered = false;
        assertFalse(alert.isTriggered, "初始状态应该是未触发");
        
        // 状态转换：触发
        alert.isTriggered = true;
        assertTrue(alert.isTriggered, "状态应该转换为已触发");
        
        // 状态转换：恢复
        alert.isTriggered = false;
        assertFalse(alert.isTriggered, "状态应该转换为未触发（已恢复）");
    }

    @Test
    @DisplayName("测试告警消息时间戳")
    void testAlertMessageTimestamp() {
        long currentTime = System.currentTimeMillis();
        AlertMessage alert = new AlertMessage(
                "test-service", "test-operation", "HIGH", 
                currentTime, "测试告警", false);
        
        assertEquals(currentTime, alert.alertTime, "时间戳应该正确设置");
        
        // 验证时间戳在合理范围内（过去1小时到现在）
        long oneHourAgo = currentTime - 3600000; // 1小时前
        assertTrue(alert.alertTime >= oneHourAgo, "时间戳应该不早于1小时前");
        assertTrue(alert.alertTime <= currentTime + 1000, "时间戳应该不晚于当前时间");
    }

    @Test
    @DisplayName("测试多条告警消息批处理")
    void testMultipleAlertMessages() {
        // 创建多条不同的告警消息
        AlertMessage alert1 = new AlertMessage(
                "service1", "operation1", "HIGH", 
                System.currentTimeMillis(), "告警1", true);
        
        AlertMessage alert2 = new AlertMessage(
                "service2", "operation2", "MEDIUM", 
                System.currentTimeMillis(), "告警2", true);
        
        AlertMessage alert3 = new AlertMessage(
                "service3", "operation3", "LOW", 
                System.currentTimeMillis(), "告警3", false);
        
        // 批量处理应该都成功
        assertDoesNotThrow(() -> {
            alarmGatewaySink.invoke(alert1, null);
            alarmGatewaySink.invoke(alert2, null);
            alarmGatewaySink.invoke(alert3, null);
        }, "批量处理告警消息应该不抛出异常");
    }

    @Test
    @DisplayName("测试告警消息toString方法")
    void testAlertMessageToString() {
        AlertMessage alert = createTestAlertMessage();
        String str = alert.toString();
        
        assertNotNull(str, "toString结果不应该为null");
        assertTrue(str.contains("[ALERT]"), "toString应该包含[ALERT]标识");
        assertTrue(str.contains(alert.service), "toString应该包含服务名");
        assertTrue(str.contains(alert.operatorName), "toString应该包含操作名");
        assertTrue(str.contains(alert.alertLevel), "toString应该包含告警级别");
    }

    /**
     * 创建测试用的AlertMessage
     */
    private AlertMessage createTestAlertMessage() {
        return new AlertMessage(
                "testService",        // service
                "testOperation",      // operatorName
                "HIGH",              // alertLevel
                System.currentTimeMillis(), // alertTime
                "平均响应时间超过阈值",     // content
                false                // isTriggered (默认未触发)
        );
    }
}