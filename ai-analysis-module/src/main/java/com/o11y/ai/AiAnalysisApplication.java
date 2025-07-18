package com.o11y.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 分析模块启动类
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AiAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAnalysisApplication.class, args);
    }
}
