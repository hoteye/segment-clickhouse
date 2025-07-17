package com.example.consumer.controller;

import com.example.consumer.service.HelloServiceConsumer;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Trace;

import io.micrometer.core.annotation.Timed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.consumer.util.ErrorLogUtil;

@RestController
public class HelloController {
    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);

    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    CentralProcessor processor = hal.getProcessor();
    GlobalMemory memory = hal.getMemory();

    private final HelloServiceConsumer helloServiceConsumer;

    public HelloController(HelloServiceConsumer helloServiceConsumer) {
        this.helloServiceConsumer = helloServiceConsumer;
    }

    @Trace(operationName = "before-method")
    private void before(String methodName) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(15, 24));
        ActiveSpan.info("Before executing method: " + methodName);
        ActiveSpan.tag("methodName", methodName);
    }

    @Trace(operationName = "after-method")
    private void after(String methodName) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(8, 24));
        ActiveSpan.info("After executing method: " + methodName);
    }

    @Timed(value = "dubbo_consumer_sayHello_latency", description = "sayHello方法延迟", extraTags = { "module",
            "dubbo-consumer" })
    @GetMapping("/sayHello")
    public String sayHello(@RequestParam String name) throws InterruptedException {
        logger.info("开始处理sayHello请求，参数: {}", name);

        try {
            int delay = ThreadLocalRandom.current().nextInt(30, 91);
            Thread.sleep(delay);
            if (name.equals("somebody")) {
                delay = ThreadLocalRandom.current().nextInt(120, 180);
                Thread.sleep(delay);
            }
            long availableMemory = memory.getAvailable();
            long totalMemory = memory.getTotal();
            ActiveSpan.tag("Processor-Name", processor.getProcessorIdentifier().getName());
            ActiveSpan.tag("Available-Memory.type.Int64", String.valueOf(availableMemory));
            ActiveSpan.tag("Total-Memory.type.Int64", String.valueOf(totalMemory));
            ActiveSpan.info("Processing echo request");
            ActiveSpan.error(new RuntimeException("Simulated error"));
            ActiveSpan.debug("debugMsg");
            String result = helloServiceConsumer.sayHello(name);
            if ("Lisa Moore".equals(name)) {
                after("sayHello");
            }
            logger.info("sayHello请求处理成功，结果: {}", result);
            return result;
        } catch (Exception e) {
            ErrorLogUtil.logError(logger, "sayHello请求处理失败", e, "参数: " + name);
            throw e;
        }
    }

    @Timed(value = "dubbo_consumer_sayHai_latency", description = "sayHai方法延迟", extraTags = { "module",
            "dubbo-consumer" })
    @GetMapping("/sayHai")
    public String sayHai(@RequestParam String name) throws InterruptedException {
        int delay = ThreadLocalRandom.current().nextInt(130, 291);
        Thread.sleep(delay);
        if (name.equals("somebody")) {
            delay = ThreadLocalRandom.current().nextInt(120, 180);
            Thread.sleep(delay);
        }
        String result = helloServiceConsumer.sayHai(name);
        if ("Lisa Moore".equals(name)) {
            after("sayHai");
        }
        return result;
    }

    @Timed(value = "dubbo_consumer_sayGoodbye_latency", description = "sayGoodbye方法延迟", extraTags = { "module",
            "dubbo-consumer" })
    @GetMapping("/sayGoodbye")
    public String sayGoodbye(@RequestParam String name) throws InterruptedException {
        logger.info("开始处理sayGoodbye请求，参数: {}", name);

        try {
            if (name.equals("errorbody")) {
                logger.warn("检测到错误测试参数: {}", name);
                int b = 1 / 0; // 故意制造异常
            }
            String result = helloServiceConsumer.sayGoodbye(name);
            if ("Lisa Moore".equals(name)) {
                after("sayGoodbye");
            }
            // 模拟一个消耗内存的操作
            List<byte[]> memoryHog = new ArrayList<>();
            for (int i = 0; i < 70; i++) {
                memoryHog.add(new byte[1024 * 1024]); // 每次分配 1MB 的内存
                Thread.sleep(10); // 模拟分配的延迟
            }
            int delay = ThreadLocalRandom.current().nextInt(90, 129);
            Thread.sleep(delay);
            logger.info("sayGoodbye请求处理成功，结果: {}", result);
            return result;
        } catch (Exception e) {
            ErrorLogUtil.logError(logger, "sayGoodbye请求处理失败", e, "参数: " + name);
            throw e;
        }
    }

    @Timed(value = "dubbo_consumer_sayThankYou_latency", description = "sayThankYou方法延迟", extraTags = { "module",
            "dubbo-consumer" })
    @GetMapping("/sayThankYou")
    public String sayThankYou(@RequestParam String name) throws InterruptedException {
        if (name.equals("errorbody")) {
            int b = 1 / 0; // 故意制造异常
        }
        String result = helloServiceConsumer.sayThankYou(name);
        if ("Lisa Moore".equals(name)) {
            after("sayThankYou");
        }
        // 模拟一个消耗内存的操作
        List<byte[]> memoryHog = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            memoryHog.add(new byte[1024 * 1024]); // 每次分配 1MB 的内存
            Thread.sleep(10); // 模拟分配的延迟
        }
        int delay = ThreadLocalRandom.current().nextInt(90, 129);
        Thread.sleep(delay);
        return result;
    }

    @Timed(value = "dubbo_consumer_sayWelcome_latency", description = "sayWelcome方法延迟", extraTags = { "module",
            "dubbo-consumer" })
    @GetMapping("/sayWelcome")
    public String sayWelcome(@RequestParam String name) throws InterruptedException {
        int delay = ThreadLocalRandom.current().nextInt(70, 171);
        Thread.sleep(delay);
        String result = helloServiceConsumer.sayWelcome(name);
        if ("Lisa Moore".equals(name)) {
            after("sayWelcome");
        }
        return result;
    }

    @Timed(value = "dubbo_consumer_sayGoodMorning_latency", description = "sayGoodMorning方法延迟", extraTags = { "module",
            "dubbo-consumer" })
    @GetMapping("/sayGoodMorning")
    public String sayGoodMorning(@RequestParam String name) throws InterruptedException {
        before("sayGoodMorning");
        int delay = ThreadLocalRandom.current().nextInt(102, 131);
        Thread.sleep(delay);
        String result = helloServiceConsumer.sayGoodMorning(name);
        if ("Lisa Moore".equals(name)) {
            after("sayGoodMorning");
        }
        return result;
    }

}