package com.example.provider.service.impl;

import com.example.provider.service.HelloService;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.concurrent.ThreadLocalRandom;

@DubboService // Dubbo 注解，暴露服务
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        simulateDelay();
        return "Hello " + name;
    }

    @Override
    public String sayHai(String name) {
        simulateDelay(90, 240);
        return "Hai " + name;
    }

    @Override
    public String sayGoodbye(String name) {
        simulateDelay();
        return "Goodbye " + name;
    }

    @Override
    public String sayThankYou(String name) {
        simulateDelay();
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

    // 模拟 30-90 毫秒的随机延迟
    private void simulateDelay() {
        try {
            long delay = ThreadLocalRandom.current().nextLong(30, 91); // 生成 30-90 毫秒的随机值
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
    }

    private void simulateDelay(int min, int max) {
        try {
            long delay = ThreadLocalRandom.current().nextLong(min, max); // 生成 min-max 毫秒的随机值
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
    }
}