package com.example.provider.service.impl;

import com.example.provider.service.HelloService;
import com.example.provider.service.HaiService;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.DubboReference;

import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DubboService // Dubbo 注解，暴露服务
public class HelloServiceImpl implements HelloService {

    private static final Logger logger = LoggerFactory.getLogger(HelloServiceImpl.class);

    @DubboReference(url = "dubbo://192.168.100.6:8746", timeout = 180)
    private HaiService haiService;

    @Override
    public String sayHello(String name) {
        logger.info("[dubbo-provider-app] 开始调用 sayHello，参数: {}", name);
        simulateDelay();
        String result = haiService.sayHello(name);
        logger.info("[dubbo-provider-app] sayHello 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayHai(String name) {
        logger.info("[dubbo-provider-app] 开始调用 sayHai，参数: {}", name);
        simulateDelay(90, 240);
        String result = haiService.sayHai(name);
        logger.info("[dubbo-provider-app] sayHai 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayGoodbye(String name) {
        logger.info("[dubbo-provider-app] 开始调用 sayGoodbye，参数: {}", name);
        simulateDelay();
        String result = haiService.sayGoodbye(name);
        logger.info("[dubbo-provider-app] sayGoodbye 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayThankYou(String name) {
        logger.info("[dubbo-provider-app] 开始调用 sayThankYou，参数: {}", name);
        simulateDelay();
        String result = haiService.sayThankYou(name);
        logger.info("[dubbo-provider-app] sayThankYou 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayWelcome(String name) {
        logger.info("[dubbo-provider-app] 开始调用 sayWelcome，参数: {}", name);
        simulateDelay();
        String result = haiService.sayWelcome(name);
        logger.info("[dubbo-provider-app] sayWelcome 调用完成，结果: {}", result);
        return result;
    }

    @Override
    public String sayGoodMorning(String name) {
        logger.info("[dubbo-provider-app] 开始调用 sayGoodMorning，参数: {}", name);
        simulateDelay();
        String result = haiService.sayGoodMorning(name);
        logger.info("[dubbo-provider-app] sayGoodMorning 调用完成，结果: {}", result);
        return result;
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