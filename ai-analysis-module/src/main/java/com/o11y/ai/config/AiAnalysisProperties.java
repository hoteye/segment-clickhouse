package com.o11y.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * AI 分析配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiAnalysisProperties {

    private Analysis analysis;
    private Llm llm;

    public Analysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    @Data
    public static class Analysis {
        private boolean enabled = true;
        private Window window = new Window();
        private Schedule schedule = new Schedule();
        private Thresholds thresholds = new Thresholds();

        @Data
        public static class Window {
            private int hours = 1;
        }

        @Data
        public static class Schedule {
            private boolean enabled = true;
            private String cron = "0 0 */1 * * ?";
        }

        @Data
        public static class Thresholds {
            private double responseTimeMs = 1000.0;
            private double errorRatePercent = 5.0;
            private double cpuUsagePercent = 80.0;
            private double memoryUsagePercent = 85.0;
        }
    }

    @Data
    public static class Llm {
        private String provider = "openai";
        private boolean enabled = true;
        private boolean fallbackEnabled = true;
        private Openai openai = new Openai();
        private Azure azure = new Azure();
        private Deepseek deepseek = new Deepseek();
        private Local local = new Local();

        @Data
        public static class Openai {
            private String apiKey;
            private String baseUrl = "https://api.openai.com/v1";
            private String model = "gpt-3.5-turbo";
            private int timeout = 30000;
            private int maxTokens = 2000;
            private double temperature = 0.7;
        }

        @Data
        public static class Azure {
            private String apiKey;
            private String endpoint;
            private String deploymentName;
            private String apiVersion = "2023-05-15";
        }

        @Data
        public static class Deepseek {
            private String apiKey;
            private String baseUrl = "https://api.deepseek.com/v1";
            private String model = "deepseek-reasoner";
            private int timeout = 30000;
            private int maxTokens = 3000;
            private double temperature = 0.7;
        }

        @Data
        public static class Local {
            private String url = "http://localhost:11434";
            private String model = "llama2";
            private int timeout = 60000;
        }
    }
}
