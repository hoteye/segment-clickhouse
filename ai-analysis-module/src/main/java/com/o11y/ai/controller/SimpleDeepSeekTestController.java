package com.o11y.ai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 简化的 DeepSeek 测试控制器
 */
@RestController
@RequestMapping("/api/test")
public class SimpleDeepSeekTestController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 硬编码测试 DeepSeek API 调用
     */
    @GetMapping("/deepseek/simple")
    public Map<String, Object> testDeepSeekSimple() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 硬编码配置
            String apiKey = "sk-af87414f0b044883a76f2a297b6eaf86";
            String baseUrl = "https://api.deepseek.com/v1";
            String model = "deepseek-chat";

            // 简单的测试消息
            String prompt = "请简单介绍一下你自己";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "user", "content", prompt)));
            requestBody.put("max_tokens", 100);
            requestBody.put("temperature", 0.7);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

            result.put("status", "success");
            result.put("httpStatus", response.statusCode());
            result.put("prompt", prompt);

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String aiResponse = jsonResponse.path("choices").get(0).path("message").path("content").asText();
                result.put("deepseekResponse", aiResponse);
                result.put("message", "DeepSeek API 调用成功");
            } else {
                result.put("error", response.body());
                result.put("message", "DeepSeek API 调用失败");
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "异常: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }

        return result;
    }

    /**
     * 测试性能分析场景
     */
    @GetMapping("/deepseek/performance")
    public Map<String, Object> testDeepSeekPerformance() {
        Map<String, Object> result = new HashMap<>();

        try {
            // 硬编码配置
            String apiKey = "sk-af87414f0b044883a76f2a297b6eaf86";
            String baseUrl = "https://api.deepseek.com/v1";
            String model = "deepseek-chat";

            // 性能分析提示词
            String prompt = "分析以下系统性能数据：\n" +
                    "- 总请求数: 28086\n" +
                    "- 平均响应时间: 0.21ms\n" +
                    "- 错误率: 11.21%\n" +
                    "- CPU使用率: 50%\n" +
                    "- 内存使用率: 70%\n\n" +
                    "请提供简短的分析和建议（限100字以内）：";

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", Arrays.asList(
                    Map.of("role", "user", "content", prompt)));
            requestBody.put("max_tokens", 200);
            requestBody.put("temperature", 0.5);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            long endTime = System.currentTimeMillis();

            result.put("status", "success");
            result.put("httpStatus", response.statusCode());
            result.put("responseTime", endTime - startTime);
            result.put("prompt", prompt);

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String aiResponse = jsonResponse.path("choices").get(0).path("message").path("content").asText();
                result.put("analysisResult", aiResponse);
                result.put("message", "性能分析完成");
            } else {
                result.put("error", response.body());
                result.put("message", "分析失败");
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "异常: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }

        return result;
    }
}
