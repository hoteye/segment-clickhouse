package com.o11y.ai.service;

import com.o11y.ai.config.AiAnalysisProperties;
import com.o11y.ai.model.PerformanceMetrics;
import com.o11y.ai.model.PerformanceAnomaly;
import com.o11y.ai.model.OptimizationSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * LLM 智能分析服务
 */
@Service
public class LLMAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(LLMAnalysisService.class);

    @Autowired
    private AiAnalysisProperties properties;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LLMAnalysisService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 分析性能数据并生成智能分析报告
     */
    public String analyzePerformanceData(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        try {
            String prompt = buildAnalysisPrompt(metrics, anomalies);

            switch (properties.getLlm().getProvider().toLowerCase()) {
                case "openai":
                    return callOpenAI(prompt);
                case "azure":
                    return callAzureOpenAI(prompt);
                case "ollama":
                case "local":
                    return callLocalLLM(prompt);
                default:
                    LOG.warn("未知的LLM提供商: {}, 使用默认分析", properties.getLlm().getProvider());
                    return generateFallbackAnalysis(metrics, anomalies);
            }
        } catch (Exception e) {
            LOG.error("LLM分析失败，使用降级分析", e);
            return generateFallbackAnalysis(metrics, anomalies);
        }
    }

    /**
     * 生成优化建议
     */
    public List<OptimizationSuggestion> generateOptimizationSuggestions(PerformanceMetrics metrics,
            List<PerformanceAnomaly> anomalies) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        try {
            String prompt = buildOptimizationPrompt(metrics, anomalies);
            String response = null;

            switch (properties.getLlm().getProvider().toLowerCase()) {
                case "openai":
                    response = callOpenAI(prompt);
                    break;
                case "azure":
                    response = callAzureOpenAI(prompt);
                    break;
                case "ollama":
                case "local":
                    response = callLocalLLM(prompt);
                    break;
                default:
                    return generateFallbackSuggestions(metrics, anomalies);
            }

            suggestions = parseOptimizationSuggestionsToObjects(response);

            // 设置建议的创建时间和来源
            suggestions.forEach(suggestion -> {
                suggestion.setCreatedTime(LocalDateTime.now());
                suggestion.setSource(
                        properties.getLlm().getProvider() + ":" + properties.getLlm().getOpenai().getModel());
            });

        } catch (Exception e) {
            LOG.error("生成优化建议失败，使用降级建议", e);
            return generateFallbackSuggestions(metrics, anomalies);
        }

        return suggestions;
    }

    /**
     * 构建性能分析的 prompt
     */
    private String buildAnalysisPrompt(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的应用性能分析专家。请分析以下性能数据并提供洞察:\\n\\n");

        // 基础指标
        prompt.append("## 基础性能指标\\n");
        prompt.append(String.format("- 总请求数: %d\\n", metrics.getTotalRequests()));
        prompt.append(String.format("- 平均响应时间: %.2f ms\\n", metrics.getAvgResponseTime()));
        prompt.append(String.format("- 最大响应时间: %.2f ms\\n", metrics.getMaxResponseTime()));
        prompt.append(String.format("- 错误率: %.2f%%\\n", metrics.getErrorRate() * 100));
        prompt.append(String.format("- 平均吞吐量: %.2f req/s\\n", metrics.getAvgThroughput()));

        // JVM 指标
        prompt.append("\\n## JVM 性能指标\\n");
        prompt.append(String.format("- 平均堆内存使用: %.2f MB\\n", metrics.getAvgHeapUsed() / 1024 / 1024));
        prompt.append(String.format("- 最大堆内存使用: %.2f MB\\n", metrics.getMaxHeapUsed() / 1024 / 1024));
        prompt.append(String.format("- 平均线程数: %d\\n", metrics.getAvgThreadCount()));
        prompt.append(String.format("- 平均CPU使用率: %.2f%%\\n", metrics.getAvgCpuUsage() * 100));
        prompt.append(String.format("- GC总时间: %d ms\\n", metrics.getTotalGcTime()));

        // 数据库指标
        prompt.append("\\n## 数据库性能指标\\n");
        prompt.append(String.format("- 总查询数: %d\\n", metrics.getTotalQueries()));
        prompt.append(String.format("- 平均查询时间: %.2f ms\\n", metrics.getAvgQueryDuration()));
        prompt.append(String.format("- 慢查询数: %d\\n", metrics.getSlowQueries()));
        prompt.append(String.format("- 平均活跃连接数: %d\\n", metrics.getAvgActiveConnections()));

        // 异常信息
        if (!anomalies.isEmpty()) {
            prompt.append("\\n## 检测到的性能异常\\n");
            for (PerformanceAnomaly anomaly : anomalies) {
                prompt.append(String.format("- %s: %s (实际值: %.2f, 期望值: %.2f, 偏差: %.1f%%)\\n",
                        anomaly.getName(), anomaly.getDescription(),
                        anomaly.getActualValue(), anomaly.getExpectedValue(),
                        anomaly.getDeviationPercentage()));
            }
        }

        prompt.append("\\n请基于以上数据提供:\\n");
        prompt.append("1. 系统性能总体评估\\n");
        prompt.append("2. 主要瓶颈和问题分析\\n");
        prompt.append("3. 性能趋势判断\\n");
        prompt.append("4. 关键风险点识别\\n");
        prompt.append("请用中文回答，语言专业且易懂。");

        return prompt.toString();
    }

    /**
     * 构建优化建议的 prompt
     */
    private String buildOptimizationPrompt(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("基于以下性能数据，请提供具体的优化建议:\\n\\n");
        prompt.append(buildAnalysisPrompt(metrics, anomalies));
        prompt.append("\\n\\n请以JSON格式返回优化建议，格式如下:\\n");
        prompt.append("[\\n");
        prompt.append("  {\\n");
        prompt.append("    \"category\": \"JVM/数据库/应用/系统\",\\n");
        prompt.append("    \"title\": \"建议标题\",\\n");
        prompt.append("    \"description\": \"详细描述\",\\n");
        prompt.append("    \"priority\": \"高/中/低\",\\n");
        prompt.append("    \"impactLevel\": \"高/中/低\",\\n");
        prompt.append("    \"implementationComplexity\": \"简单/中等/复杂\",\\n");
        prompt.append("    \"actionPlan\": \"具体实施步骤\",\\n");
        prompt.append("    \"expectedBenefit\": \"预期效果\"\\n");
        prompt.append("  }\\n");
        prompt.append("]\\n");

        return prompt.toString();
    }

    /**
     * 调用 OpenAI API
     */
    private String callOpenAI(String prompt) throws Exception {
        AiAnalysisProperties.Llm.Openai config = properties.getLlm().getOpenai();

        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAI API Key 未配置");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("messages", Arrays.asList(
                Map.of("role", "user", "content", prompt)));
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return jsonResponse.path("choices").get(0).path("message").path("content").asText();
        } else {
            throw new RuntimeException("OpenAI API 调用失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 调用 Azure OpenAI API
     */
    private String callAzureOpenAI(String prompt) throws Exception {
        AiAnalysisProperties.Llm.Azure config = properties.getLlm().getAzure();

        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Azure OpenAI API Key 未配置");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("messages", Arrays.asList(
                Map.of("role", "user", "content", prompt)));
        requestBody.put("max_tokens", 2000);
        requestBody.put("temperature", 0.7);

        String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                config.getEndpoint(), config.getDeploymentName(), config.getApiVersion());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("api-key", config.getApiKey())
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return jsonResponse.path("choices").get(0).path("message").path("content").asText();
        } else {
            throw new RuntimeException("Azure OpenAI API 调用失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 调用本地 LLM (Ollama)
     */
    private String callLocalLLM(String prompt) throws Exception {
        AiAnalysisProperties.Llm.Local config = properties.getLlm().getLocal();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl() + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(config.getTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return jsonResponse.path("response").asText();
        } else {
            throw new RuntimeException("本地 LLM 调用失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 解析优化建议
     */
    private List<String> parseOptimizationSuggestions(String response) {
        List<String> suggestions = new ArrayList<>();

        // 简单的文本解析，每行作为一个建议
        String[] lines = response.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && (line.startsWith("-") || line.startsWith("•") ||
                    line.matches("^\\d+\\..*") || line.length() > 10)) {
                // 清理格式字符
                line = line.replaceAll("^[-•\\d\\.\\s]+", "").trim();
                if (!line.isEmpty()) {
                    suggestions.add(line);
                }
            }
        }

        // 如果没有解析到建议，返回整个响应作为一个建议
        if (suggestions.isEmpty() && !response.trim().isEmpty()) {
            suggestions.add(response.trim());
        }

        return suggestions;
    }

    /**
     * 解析优化建议为结构化对象
     */
    private List<OptimizationSuggestion> parseOptimizationSuggestionsToObjects(String jsonResponse) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        try {
            // 尝试提取 JSON 部分
            String jsonPart = extractJsonFromResponse(jsonResponse);
            JsonNode jsonArray = objectMapper.readTree(jsonPart);

            if (jsonArray.isArray()) {
                for (JsonNode item : jsonArray) {
                    OptimizationSuggestion suggestion = new OptimizationSuggestion();
                    suggestion.setId(UUID.randomUUID().toString());
                    suggestion.setCategory(item.path("category").asText());
                    suggestion.setTitle(item.path("title").asText());
                    suggestion.setDescription(item.path("description").asText());
                    suggestion.setPriority(item.path("priority").asText());
                    suggestion.setImpactLevel(item.path("impactLevel").asText());
                    suggestion.setImplementationComplexity(item.path("implementationComplexity").asText());
                    suggestion.setActionPlan(item.path("actionPlan").asText());
                    suggestion.setExpectedBenefit(item.path("expectedBenefit").asText(""));
                    suggestion.setConfidenceScore(0.8); // 默认置信度

                    suggestions.add(suggestion);
                }
            }
        } catch (Exception e) {
            LOG.error("解析优化建议JSON失败", e);
            // 返回空列表，会由调用方使用降级方案
        }

        return suggestions;
    }

    /**
     * 从响应中提取 JSON 部分
     */
    private String extractJsonFromResponse(String response) {
        // 简单的 JSON 提取逻辑
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start != -1 && end != -1 && start < end) {
            return response.substring(start, end + 1);
        }

        return response;
    }

    /**
     * 生成降级分析报告
     */
    private String generateFallbackAnalysis(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        StringBuilder analysis = new StringBuilder();

        analysis.append("## 系统性能分析报告\\n\\n");

        // 基础性能评估
        analysis.append("### 基础性能评估\\n");
        if (metrics.getErrorRate() > 0.05) {
            analysis.append("⚠️ **错误率偏高**: 当前错误率为 ").append(String.format("%.2f%%", metrics.getErrorRate() * 100))
                    .append("，建议关注应用稳定性。\\n");
        }

        if (metrics.getAvgResponseTime() > 1000) {
            analysis.append("⚠️ **响应时间偏长**: 平均响应时间为 ").append(String.format("%.2f ms", metrics.getAvgResponseTime()))
                    .append("，可能影响用户体验。\\n");
        }

        // JVM 性能评估
        analysis.append("\\n### JVM 性能评估\\n");
        double heapUsageMB = metrics.getAvgHeapUsed() / 1024 / 1024;
        if (heapUsageMB > 1024) {
            analysis.append("⚠️ **内存使用较高**: 平均堆内存使用 ").append(String.format("%.2f MB", heapUsageMB))
                    .append("，建议监控内存泄漏风险。\\n");
        }

        if (metrics.getAvgThreadCount() > 200) {
            analysis.append("⚠️ **线程数较多**: 平均线程数为 ").append(metrics.getAvgThreadCount())
                    .append("，建议检查线程池配置。\\n");
        }

        // 数据库性能评估
        analysis.append("\\n### 数据库性能评估\\n");
        if (metrics.getSlowQueries() > 0) {
            analysis.append("⚠️ **存在慢查询**: 检测到 ").append(metrics.getSlowQueries())
                    .append(" 个慢查询，建议优化查询性能。\\n");
        }

        // 异常总结
        if (!anomalies.isEmpty()) {
            analysis.append("\\n### 检测到的异常\\n");
            long criticalCount = anomalies.stream().filter(a -> a.getSeverity() == PerformanceAnomaly.Severity.CRITICAL)
                    .count();
            long highCount = anomalies.stream().filter(a -> a.getSeverity() == PerformanceAnomaly.Severity.HIGH)
                    .count();

            if (criticalCount > 0) {
                analysis.append("🚨 **严重异常**: ").append(criticalCount).append(" 个，需要立即处理。\\n");
            }
            if (highCount > 0) {
                analysis.append("⚠️ **高优先级异常**: ").append(highCount).append(" 个，建议尽快处理。\\n");
            }
        }

        analysis.append("\\n### 总体建议\\n");
        analysis.append("建议定期监控系统性能指标，及时发现和解决性能瓶颈问题。");

        return analysis.toString();
    }

    /**
     * 生成降级优化建议
     */
    private List<OptimizationSuggestion> generateFallbackSuggestions(PerformanceMetrics metrics,
            List<PerformanceAnomaly> anomalies) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 基于指标生成基础建议
        if (metrics.getErrorRate() > 0.05) {
            OptimizationSuggestion suggestion = new OptimizationSuggestion();
            suggestion.setId(UUID.randomUUID().toString());
            suggestion.setCategory("应用");
            suggestion.setTitle("降低应用错误率");
            suggestion.setDescription("当前错误率偏高，建议检查异常处理逻辑和日志记录");
            suggestion.setPriority("高");
            suggestion.setImpactLevel("高");
            suggestion.setImplementationComplexity("中等");
            suggestion.setActionPlan("1. 分析错误日志 2. 优化异常处理 3. 加强监控告警");
            suggestion.setExpectedBenefit("提升应用稳定性，改善用户体验");
            suggestion.setCreatedTime(now);
            suggestion.setSource("fallback-analyzer");
            suggestion.setConfidenceScore(0.7);
            suggestions.add(suggestion);
        }

        if (metrics.getAvgResponseTime() > 1000) {
            OptimizationSuggestion suggestion = new OptimizationSuggestion();
            suggestion.setId(UUID.randomUUID().toString());
            suggestion.setCategory("性能");
            suggestion.setTitle("优化响应时间");
            suggestion.setDescription("响应时间偏长，建议优化代码性能和数据库查询");
            suggestion.setPriority("高");
            suggestion.setImpactLevel("高");
            suggestion.setImplementationComplexity("中等");
            suggestion.setActionPlan("1. 分析慢接口 2. 优化数据库查询 3. 添加缓存");
            suggestion.setExpectedBenefit("提升响应速度，改善用户体验");
            suggestion.setCreatedTime(now);
            suggestion.setSource("fallback-analyzer");
            suggestion.setConfidenceScore(0.7);
            suggestions.add(suggestion);
        }

        if (metrics.getAvgHeapUsed() / 1024 / 1024 > 1024) {
            OptimizationSuggestion suggestion = new OptimizationSuggestion();
            suggestion.setId(UUID.randomUUID().toString());
            suggestion.setCategory("JVM");
            suggestion.setTitle("优化内存使用");
            suggestion.setDescription("堆内存使用较高，建议进行内存调优");
            suggestion.setPriority("中");
            suggestion.setImpactLevel("中");
            suggestion.setImplementationComplexity("中等");
            suggestion.setActionPlan("1. 分析堆转储 2. 调整JVM参数 3. 优化对象生命周期");
            suggestion.setExpectedBenefit("降低内存使用，提升系统稳定性");
            suggestion.setCreatedTime(now);
            suggestion.setSource("fallback-analyzer");
            suggestion.setConfidenceScore(0.6);
            suggestions.add(suggestion);
        }

        return suggestions;
    }

    /**
     * 健康检查 - 测试 LLM 连通性
     */
    public boolean isHealthy() {
        try {
            String testPrompt = "请回答: 系统运行正常";
            String response = null;

            switch (properties.getLlm().getProvider().toLowerCase()) {
                case "openai":
                    response = callOpenAI(testPrompt);
                    break;
                case "azure":
                    response = callAzureOpenAI(testPrompt);
                    break;
                case "ollama":
                case "local":
                    response = callLocalLLM(testPrompt);
                    break;
                default:
                    return false;
            }

            return response != null && !response.trim().isEmpty();
        } catch (Exception e) {
            LOG.warn("LLM 健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取 LLM 提供商信息
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", properties.getLlm().getProvider());
        status.put("model", properties.getLlm().getOpenai().getModel());
        status.put("healthy", isHealthy());

        switch (properties.getLlm().getProvider().toLowerCase()) {
            case "openai":
                status.put("baseUrl", properties.getLlm().getOpenai().getBaseUrl());
                status.put("hasApiKey", properties.getLlm().getOpenai().getApiKey() != null &&
                        !properties.getLlm().getOpenai().getApiKey().trim().isEmpty());
                break;
            case "azure":
                status.put("endpoint", properties.getLlm().getAzure().getEndpoint());
                status.put("hasApiKey", properties.getLlm().getAzure().getApiKey() != null &&
                        !properties.getLlm().getAzure().getApiKey().trim().isEmpty());
                break;
            case "ollama":
            case "local":
                status.put("localUrl", properties.getLlm().getLocal().getUrl());
                break;
        }

        return status;
    }
}
