package com.example.provider.service.impl;

import com.example.provider.service.HaiService;
import com.example.provider.service.HelloService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.ThreadLocalRandom;

@DubboService // Dubbo 注解，暴露服务
public class HaiServiceImpl implements HaiService {

    // 引用 Dubbo 服务
    @DubboReference(url = "dubbo://192.168.100.6:8745", timeout = 180) // Added closing parenthesis
    private HelloService helloService;

    // 注入 JdbcTemplate
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public String sayHello(String name) {
        simulateDelay();
        return "Hello " + name;
    }

    @Override
    public String sayHai(String name) {
        simulateDelay();
        // 调用 HelloService 的 sayHai 方法
        String helloServiceResponse = helloService.sayHai(name);
        // 插入消息到数据库
        String sql = "INSERT INTO messages (content) VALUES (?)";
        jdbcTemplate.update(sql, name);
        return "Hai " + name + " | " + helloServiceResponse;
    }

    @Override
    public String sayGoodbye(String name) {
        simulateDelay();
        return "Goodbye " + name;
    }

    @Override
    public String sayThankYou(String name) {
        simulateDelay();
        // 插入消息到数据库
        String sql = "INSERT INTO messages (content) VALUES (?)";
        jdbcTemplate.update(sql, name);
        return "Thank you " + name;
    }

    @Override
    public String sayWelcome(String name) {
        simulateDelay();
        return "Welcome " + name;
    }

    @Override
    public String sayGoodMorning(String name) {
        simulateDelay();
        return "Good morning " + name;
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