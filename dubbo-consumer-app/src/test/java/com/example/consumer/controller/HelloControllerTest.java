package com.example.consumer.controller;

import org.junit.jupiter.api.Test;

import com.example.consumer.service.HaiServiceConsumer;
import com.example.consumer.service.HelloServiceConsumer;

import static org.junit.jupiter.api.Assertions.*;

public class HelloControllerTest {

    private HelloController helloController = new HelloController(
            new HelloServiceConsumer(),
            new HaiServiceConsumer());

    @Test
    void testPerformComplexCalculation() {
        assertDoesNotThrow(() -> helloController.performComplexCalculation());
    }

}
