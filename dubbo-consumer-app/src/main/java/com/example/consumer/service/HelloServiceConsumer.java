package com.example.consumer.service;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import com.example.provider.service.HelloService;

@Service
public class HelloServiceConsumer {

    @DubboReference(url = "dubbo://192.168.100.6:8745")
    private HelloService helloService;

    public String sayHello(String name) {
        return helloService.sayHello(name);
    }

    public String sayHai(String name) {
        return helloService.sayHai(name); // 调用 sayHai 方法
    }

    public String sayGoodbye(String name) {
        return helloService.sayGoodbye(name);
    }

    public String sayThankYou(String name) {
        return helloService.sayThankYou(name);
    }

    public String sayWelcome(String name) {
        return helloService.sayWelcome(name);
    }

    public String sayGoodMorning(String name) {
        return helloService.sayGoodMorning(name);
    }
}