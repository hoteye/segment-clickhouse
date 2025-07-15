package com.example.consumer.service;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import com.example.provider.service.HaiService;

@Service
public class HaiServiceConsumer {

    @DubboReference(url = "dubbo://192.168.100.6:8746")
    private HaiService haiService;

    public String sayHello(String name) {
        return haiService.sayHello(name);
    }

    public String sayHai(String name) {
        return haiService.sayHai(name);
    }

    public String sayGoodbye(String name) {
        return haiService.sayGoodbye(name);
    }

    public String sayThankYou(String name) {
        return haiService.sayThankYou(name);
    }

    public String sayWelcome(String name) {
        return haiService.sayWelcome(name);
    }

    public String sayGoodMorning(String name) {
        return haiService.sayGoodMorning(name);
    }
}