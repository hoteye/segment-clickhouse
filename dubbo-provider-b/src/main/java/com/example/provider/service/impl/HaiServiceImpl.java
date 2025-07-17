package com.example.provider.service.impl;

import com.example.provider.service.HaiService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DubboService // Dubbo 注解，暴露服务
public class HaiServiceImpl implements HaiService {

    private static final Logger logger = LoggerFactory.getLogger(HaiServiceImpl.class);

    // 注入 JdbcTemplate
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public String sayHello(String name) {
        logger.info("[dubbo-provider-b] 开始调用 sayHello，参数: {}", name);
        simulateDelay();
        String result = "Hello " + name;
        logger.info("[dubbo-provider-b] sayHello 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayHai(String name) {
        logger.info("[dubbo-provider-b] 开始调用 sayHai，参数: {}", name);
        simulateDelay();
        // 插入消息到数据库
        String sql = "INSERT INTO messages (content) VALUES (?)";
        jdbcTemplate.update(sql, name);
        String result = "Hai " + name;
        logger.info("[dubbo-provider-b] sayHai 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayGoodbye(String name) {
        logger.info("[dubbo-provider-b] 开始调用 sayGoodbye，参数: {}", name);
        simulateDelay();
        String result = "Goodbye " + name;
        logger.info("[dubbo-provider-b] sayGoodbye 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayThankYou(String name) {
        logger.info("[dubbo-provider-b] 开始调用 sayThankYou，参数: {}", name);
        simulateDelay();
        // 插入消息到数据库
        String sql = "INSERT INTO messages (content) VALUES (?)";
        jdbcTemplate.update(sql, name);
        String result = "Thank you " + name;
        logger.info("[dubbo-provider-b] sayThankYou 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayWelcome(String name) {
        logger.info("[dubbo-provider-b] 开始调用 sayWelcome，参数: {}", name);
        simulateDelay();
        String result = "Welcome " + name;
        logger.info("[dubbo-provider-b] sayWelcome 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayGoodMorning(String name) {
        logger.info("[dubbo-provider-b] 开始调用 sayGoodMorning，参数: {}", name);
        simulateDelay();
        String result = "Good morning " + name;
        logger.info("[dubbo-provider-b] sayGoodMorning 调用完成，结果: {}", result);
        return result;
    }

    // 模拟 500-600 毫秒的随机延迟，每 2 小时内随机触发，持续 1 分钟
    private void simulateDelay() {
        try {
            long currentTimeMillis = System.currentTimeMillis();
            long twoHoursInMillis = 2 * 60 * 60 * 1000; // 2 小时的毫秒数
            long oneMinuteInMillis = 60 * 1000; // 1 分钟的毫秒数

            // 计算当前时间是否在 2 小时内的随机触发窗口
            if ((currentTimeMillis % twoHoursInMillis) < oneMinuteInMillis) {
                // 在触发窗口内，随机延迟 500-600 毫秒
                long delay = ThreadLocalRandom.current().nextLong(500, 601);
                Thread.sleep(delay);
            } else {
                // 否则，默认延迟 30-90 毫秒
                long delay = ThreadLocalRandom.current().nextLong(30, 91);
                Thread.sleep(delay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
    }

}