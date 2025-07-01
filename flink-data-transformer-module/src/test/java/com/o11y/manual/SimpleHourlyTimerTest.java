package com.o11y.manual;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 简化的小时级定时器测试
 * 验证整点时间计算逻辑
 */
public class SimpleHourlyTimerTest {

    public static void main(String[] args) {
        System.out.println("=== 小时级定时器测试 ===");
        System.out.println();

        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println("当前时间: " + now.format(formatter));

        // 计算下一个整点
        LocalDateTime nextHour = calculateNextHourTime(now);
        System.out.println("下一个整点: " + nextHour.format(formatter));

        // 计算时间差
        long currentMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long nextHourMillis = nextHour.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long diffSeconds = (nextHourMillis - currentMillis) / 1000;
        long diffMinutes = diffSeconds / 60;

        System.out.println("时间差: " + diffMinutes + " 分钟 " + (diffSeconds % 60) + " 秒");
        System.out.println();

        // 验证逻辑
        if (nextHour.getMinute() == 0 && nextHour.getSecond() == 0) {
            System.out.println("✅ 整点计算正确！");
        } else {
            System.out.println("❌ 整点计算错误！");
        }

        if (nextHour.isAfter(now)) {
            System.out.println("✅ 时间顺序正确！");
        } else {
            System.out.println("❌ 时间顺序错误！");
        }

        // 模拟几个不同时间点的测试
        System.out.println();
        System.out.println("--- 模拟测试不同时间点 ---");

        testTimePoint("2024-01-01 10:30:45");
        testTimePoint("2024-01-01 11:59:59");
        testTimePoint("2024-01-01 12:00:00");
        testTimePoint("2024-01-01 23:45:30");

        System.out.println();
        System.out.println("=== 测试完成 ===");
    }

    /**
     * 计算下一个整点时间
     */
    private static LocalDateTime calculateNextHourTime(LocalDateTime now) {
        return now.plusHours(1)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }

    /**
     * 测试指定时间点
     */
    private static void testTimePoint(String timeStr) {
        LocalDateTime testTime = LocalDateTime.parse(timeStr,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        LocalDateTime nextHour = calculateNextHourTime(testTime);

        System.out.println(timeStr + " -> " +
                nextHour.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}