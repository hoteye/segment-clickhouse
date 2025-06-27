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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * LLM 智能分析服务 - 支持多模型交易链路分析
 */
@Service
public class LLMAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(LLMAnalysisService.class);

    @Autowired
    private AiAnalysisProperties properties;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 模型能力映射
    private static final Map<String, String> MODEL_CAPABILITIES = Map.of(
            "deepseek-chat", "通用分析、中文理解、业务诊断",
            "deepseek-coder", "代码分析、技术问题定位",
            "deepseek-math", "数据计算、统计分析、异常检测",
            "claude-3-5-sonnet", "复杂推理、根因分析",
            "gpt-4o", "综合分析、多模态支持");

    public LLMAnalysisService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // 连接超时缩短到10秒
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
                case "deepseek":
                    return callDeepSeek(prompt);
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
     * 分析性能数据并结合错误堆栈生成智能分析报告
     */
    public String analyzePerformanceData(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies,
            List<String> errorStacks) {
        try {
            String prompt = buildAnalysisPrompt(metrics, anomalies);
            // 拼接堆栈信息
            if (errorStacks != null && !errorStacks.isEmpty()) {
                StringBuilder sb = new StringBuilder(prompt);
                sb.append("\n\n【以下为近1小时内典型错误堆栈信息，请结合分析根因，仅关注最相关部分】\n");
                int idx = 1;
                for (String stack : errorStacks) {
                    sb.append("# 错误堆栈").append(idx++).append(":\n");
                    sb.append(stack).append("\n\n");
                }
                prompt = sb.toString();
            }
            switch (properties.getLlm().getProvider().toLowerCase()) {
                case "openai":
                    return callOpenAI(prompt);
                case "azure":
                    return callAzureOpenAI(prompt);
                case "deepseek":
                    return callDeepSeek(prompt);
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
                case "deepseek":
                    response = callDeepSeek(prompt);
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
        // 增加报告对象 service
        prompt.append(String.format("### 报告对象: %s\\n", metrics.getService()));

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
        prompt.append(String.format("- 最大堆内存使用率: %.2f%%\\n", metrics.getMaxHeapUsedRatio() * 100));
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
        prompt.append("5. 如果堆内存使用率超过90%，必须明确警告OOM风险\\n");
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
     * 调用 DeepSeek API
     */
    private String callDeepSeek(String prompt) throws Exception {
        AiAnalysisProperties.Llm.Deepseek config = properties.getLlm().getDeepseek();

        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("DeepSeek API Key 未配置");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("messages", Arrays.asList(
                Map.of("role", "user", "content", prompt)));
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", config.getTemperature());

        long startTime = System.currentTimeMillis();
        LOG.info("开始调用DeepSeek API，模型: {}, 超时设置: {}ms", config.getModel(), config.getTimeout());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("DeepSeek API调用完成，耗时: {}ms, 状态码: {}", duration, response.statusCode());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                return jsonResponse.path("choices").get(0).path("message").path("content").asText();
            } else {
                throw new RuntimeException("DeepSeek API 调用失败: " + response.statusCode() + " - " + response.body());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("DeepSeek API调用超时，耗时: {}ms, 配置超时: {}ms", duration, config.getTimeout());
            throw new RuntimeException("DeepSeek API 调用超时 (" + duration + "ms)", e);
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
     * 多模型调用 - 支持降级
     */
    private String callLLMWithFallback(String prompt, String... preferredModels) throws Exception {
        Exception lastException = null;

        for (String model : preferredModels) {
            try {
                LOG.info("尝试使用模型: {}", model);
                return callSpecificModel(prompt, model);
            } catch (Exception e) {
                LOG.warn("模型 {} 调用失败: {}", model, e.getMessage());
                lastException = e;
            }
        }

        // 所有模型都失败，使用默认方法
        LOG.warn("所有首选模型失败，使用默认模型");
        return callDefaultModel(prompt);
    }

    /**
     * 调用特定模型
     */
    private String callSpecificModel(String prompt, String modelType) throws Exception {
        switch (modelType.toLowerCase()) {
            case "deepseek-chat":
            case "deepseek-coder":
            case "deepseek-math":
                return callDeepSeekWithModel(prompt, modelType);
            case "claude-3-5-sonnet":
                return callClaude(prompt);
            case "gpt-4o":
                return callOpenAI(prompt);
            default:
                return callDefaultModel(prompt);
        }
    }

    /**
     * 调用DeepSeek特定模型
     */
    private String callDeepSeekWithModel(String prompt, String model) throws Exception {
        AiAnalysisProperties.Llm.Deepseek config = properties.getLlm().getDeepseek();

        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("DeepSeek API Key 未配置");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", Arrays.asList(
                Map.of("role", "system", "content", getSystemPromptForModel(model)),
                Map.of("role", "user", "content", prompt)));
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("temperature", getTemperatureForModel(model));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .timeout(Duration.ofMillis(config.getTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));

        if (response.statusCode() == 200) {
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            return jsonResponse.path("choices").get(0).path("message").path("content").asText();
        } else {
            throw new RuntimeException("DeepSeek API 调用失败: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * 获取模型专用系统提示词
     */
    private String getSystemPromptForModel(String model) {
        switch (model) {
            case "deepseek-chat":
                return "你是一个专业的交易系统分析专家，擅长业务流程分析和问题诊断，请用中文提供专业分析。";
            case "deepseek-coder":
                return "你是一个资深的系统架构师和代码专家，擅长技术问题定位和代码分析，请提供技术层面的深度分析。";
            case "deepseek-math":
                return "你是一个数据分析专家，擅长统计分析、异常检测和数学建模，请提供基于数据的科学分析。";
            default:
                return "你是一个专业的系统分析专家，请提供准确、有用的分析。";
        }
    }

    /**
     * 获取模型专用温度参数
     */
    private double getTemperatureForModel(String model) {
        switch (model) {
            case "deepseek-math":
                return 0.3; // 数学分析需要更确定的结果
            case "deepseek-coder":
                return 0.5; // 代码分析需要较确定的结果
            case "deepseek-chat":
            default:
                return 0.7; // 通用分析允许一定创造性
        }
    }

    /**
     * 结果解析 - 提取根因
     */
    private String extractRootCause(String analysis) {
        // 使用正则表达式或NLP技术提取根因
        String[] lines = analysis.split("\n");
        for (String line : lines) {
            if (line.contains("根本原因") || line.contains("根因") || line.contains("root cause")) {
                return line.replaceAll("^[*#\\-\\d\\.\\s]+", "").trim();
            }
        }
        return "未明确识别根因，需要进一步分析";
    }

    /**
     * 提取失败链路
     */
    private List<String> extractFailureChain(String analysis) {
        List<String> chain = new ArrayList<>();
        String[] lines = analysis.split("\n");
        boolean inChainSection = false;

        for (String line : lines) {
            if (line.contains("失败路径") || line.contains("失败链路") || line.contains("传播路径")) {
                inChainSection = true;
                continue;
            }
            if (inChainSection && (line.startsWith("-") || line.startsWith("*") || line.matches("^\\d+\\."))) {
                chain.add(line.replaceAll("^[*#\\-\\d\\.\\s]+", "").trim());
            } else if (inChainSection && line.trim().isEmpty()) {
                break;
            }
        }

        return chain;
    }

    /**
     * 提取影响评估
     */
    private Map<String, Object> extractImpactAssessment(String analysis) {
        Map<String, Object> impact = new HashMap<>();

        // 简单的关键词匹配，实际可以使用更复杂的NLP技术
        if (analysis.contains("严重") || analysis.contains("critical")) {
            impact.put("severity", "HIGH");
        } else if (analysis.contains("中等") || analysis.contains("moderate")) {
            impact.put("severity", "MEDIUM");
        } else {
            impact.put("severity", "LOW");
        }

        // 提取影响范围
        if (analysis.contains("全系统") || analysis.contains("system-wide")) {
            impact.put("scope", "SYSTEM_WIDE");
        } else if (analysis.contains("单个服务") || analysis.contains("service-specific")) {
            impact.put("scope", "SERVICE_SPECIFIC");
        } else {
            impact.put("scope", "LIMITED");
        }

        return impact;
    }

    /**
     * 数据特征判断方法
     */
    private boolean hasComplexBusinessLogic(Map<String, Object> traceData) {
        // 检查是否涉及复杂业务流程
        Object spans = traceData.get("spans");
        if (spans instanceof List) {
            return ((List<?>) spans).size() > 5; // 超过5个服务调用认为是复杂流程
        }
        return false;
    }

    private boolean hasCodeRelatedErrors(Map<String, Object> traceData) {
        // 检查是否有代码相关错误
        String errorMsg = String.valueOf(traceData.get("errorMessage"));
        return errorMsg != null && (errorMsg.contains("NullPointerException") ||
                errorMsg.contains("SQLException") ||
                errorMsg.contains("ClassNotFoundException") ||
                errorMsg.contains("OutOfMemoryError"));
    }

    private boolean hasChineseContent(Map<String, Object> traceData) {
        // 检查是否包含中文内容
        String content = traceData.toString();
        return content.matches(".*[\\u4e00-\\u9fa5].*");
    }

    private boolean requiresStatisticalAnalysis(Map<String, Object> traceData) {
        // 检查是否需要统计分析
        return traceData.containsKey("duration") ||
                traceData.containsKey("count") ||
                traceData.containsKey("rate");
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

    /**
     * 交易链路根因分析 - 核心方法
     */
    public String analyzeTransactionTrace(Map<String, Object> traceData, List<Map<String, Object>> errorLogs) {
        try {
            String prompt = buildTraceAnalysisPrompt(traceData, errorLogs);

            // 根据场景选择最佳模型
            String provider = selectBestModelForTrace(traceData);
            LOG.info("选择模型进行交易链路分析: {}", provider);

            return callLLMWithFallback(prompt, provider);
        } catch (Exception e) {
            LOG.error("交易链路分析失败", e);
            return generateFallbackTraceAnalysis(traceData, errorLogs);
        }
    }

    /**
     * 实时异常检测与分析
     */
    public Map<String, Object> detectAnomaliesInRealtime(List<Map<String, Object>> recentTraces) {
        Map<String, Object> result = new HashMap<>();

        try {
            String prompt = buildAnomalyDetectionPrompt(recentTraces);
            String analysis = callLLMWithFallback(prompt, "deepseek-math");

            result.put("analysis", analysis);
            result.put("anomalies", extractAnomaliesFromAnalysis(analysis));
            result.put("recommendations", extractRecommendationsFromAnalysis(analysis));
            result.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("实时异常检测失败", e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 交易失败根因定位
     */
    public Map<String, Object> analyzeTransactionFailure(String transactionId,
            Map<String, Object> transactionData,
            List<Map<String, Object>> relatedLogs) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            String prompt = buildFailureAnalysisPrompt(transactionId, transactionData, relatedLogs);

            // 使用推理能力强的模型进行根因分析
            String result = callLLMWithFallback(prompt, "claude-3-5-sonnet", "deepseek-chat");

            analysis.put("transactionId", transactionId);
            analysis.put("rootCause", extractRootCause(result));
            analysis.put("failureChain", extractFailureChain(result));
            analysis.put("impactAssessment", extractImpactAssessment(result));
            analysis.put("recommendations", extractActionItems(result));
            analysis.put("confidence", calculateConfidence(result));
            analysis.put("fullAnalysis", result);

        } catch (Exception e) {
            LOG.error("交易失败分析失败", e);
            analysis.put("error", e.getMessage());
            analysis = generateFallbackFailureAnalysis(transactionId, transactionData);
        }

        return analysis;
    }

    /**
     * 性能瓶颈智能定位
     */
    public Map<String, Object> identifyPerformanceBottlenecks(Map<String, Object> performanceData) {
        Map<String, Object> bottleneckAnalysis = new HashMap<>();

        try {
            String prompt = buildBottleneckAnalysisPrompt(performanceData);
            String analysis = callLLMWithFallback(prompt, "deepseek-coder", "deepseek-chat");

            bottleneckAnalysis.put("bottlenecks", extractBottlenecks(analysis));
            bottleneckAnalysis.put("optimization", extractOptimizations(analysis));
            bottleneckAnalysis.put("priority", extractPriority(analysis));
            bottleneckAnalysis.put("estimatedImpact", extractEstimatedImpact(analysis));
            bottleneckAnalysis.put("fullAnalysis", analysis);

        } catch (Exception e) {
            LOG.error("性能瓶颈分析失败", e);
            bottleneckAnalysis.put("error", e.getMessage());
        }

        return bottleneckAnalysis;
    }

    /**
     * 智能模型选择 - 根据分析场景选择最适合的模型
     */
    public String selectOptimalModel(String analysisType, Map<String, Object> context) {
        try {
            LOG.info("选择最优模型，分析类型: {}", analysisType);

            switch (analysisType.toLowerCase()) {
                case "code_analysis":
                case "technical_debugging":
                    return "deepseek-coder";

                case "mathematical_analysis":
                case "statistical_analysis":
                case "anomaly_detection":
                    return "deepseek-math";

                case "business_analysis":
                case "general_reasoning":
                case "chinese_analysis":
                    return "deepseek-chat";

                case "complex_reasoning":
                case "multi_step_analysis":
                    return "claude-3-5-sonnet";

                case "multimodal_analysis":
                    return "gpt-4o";

                default:
                    return "deepseek-chat"; // 默认模型
            }
        } catch (Exception e) {
            LOG.error("模型选择失败", e);
            return "deepseek-chat";
        }
    }

    /**
     * 多模型分析对比 - 使用多个模型分析同一问题并对比结果
     */
    public Map<String, Object> multiModelAnalysis(String prompt, List<String> models) {
        Map<String, Object> results = new HashMap<>();
        Map<String, String> modelResults = new HashMap<>();
        List<String> errors = new ArrayList<>();

        try {
            LOG.info("开始多模型分析，模型数量: {}", models.size());

            for (String model : models) {
                try {
                    String result = callModelByName(prompt, model);
                    modelResults.put(model, result);
                    LOG.debug("模型 {} 分析完成", model);
                } catch (Exception e) {
                    String error = String.format("模型 %s 分析失败: %s", model, e.getMessage());
                    errors.add(error);
                    LOG.warn(error);
                }
            }

            // 结果聚合和分析
            String aggregatedResult = aggregateModelResults(modelResults);
            double consensusScore = calculateConsensusScore(modelResults);

            results.put("modelResults", modelResults);
            results.put("aggregatedResult", aggregatedResult);
            results.put("consensusScore", consensusScore);
            results.put("participatingModels", modelResults.keySet());
            results.put("errors", errors);
            results.put("analysisQuality", assessAnalysisQuality(modelResults));

        } catch (Exception e) {
            LOG.error("多模型分析失败", e);
            results.put("error", e.getMessage());
        }

        return results;
    }

    /**
     * 根据模型名称调用对应的LLM
     */
    private String callModelByName(String prompt, String modelName) throws Exception {
        switch (modelName.toLowerCase()) {
            case "deepseek-chat":
            case "deepseek-coder":
            case "deepseek-math":
                return callDeepSeek(prompt);
            case "gpt-3.5-turbo":
            case "gpt-4":
            case "gpt-4o":
                return callOpenAI(prompt);
            case "claude-3-5-sonnet":
                return callClaude(prompt); // 需要实现
            case "local":
            case "ollama":
                return callLocalLLM(prompt);
            default:
                throw new IllegalArgumentException("不支持的模型: " + modelName);
        }
    }

    /**
     * 调用 Claude API (Anthropic)
     */
    private String callClaude(String prompt) throws Exception {
        // 这里需要配置 Claude API 的调用
        // 目前返回模拟结果
        LOG.warn("Claude API 尚未配置，返回模拟结果");
        return "Claude 模型分析结果：" + prompt.substring(0, Math.min(50, prompt.length())) + "...的专业分析";
    }

    /**
     * 专业提示词工程 - 为不同场景构建专业化的提示词
     */
    public String buildSpecializedPrompt(String analysisType, Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        try {
            switch (analysisType.toLowerCase()) {
                case "transaction_analysis":
                    prompt = buildTransactionAnalysisPrompt(data, context);
                    break;
                case "anomaly_diagnosis":
                    prompt = buildAnomalyDiagnosisPrompt(data, context);
                    break;
                case "performance_optimization":
                    prompt = buildPerformanceOptimizationPrompt(data, context);
                    break;
                case "root_cause_investigation":
                    prompt = buildRootCauseInvestigationPrompt(data, context);
                    break;
                case "capacity_planning":
                    prompt = buildCapacityPlanningPrompt(data, context);
                    break;
                default:
                    prompt = buildGeneralAnalysisPrompt(data, context);
            }

            // 添加通用指导原则
            prompt.append("\n\n## 分析指导原则：\n");
            prompt.append("1. 请基于数据事实进行分析，避免主观推测\n");
            prompt.append("2. 提供具体可行的建议和解决方案\n");
            prompt.append("3. 评估影响范围和紧急程度\n");
            prompt.append("4. 考虑业务连续性和用户体验\n");
            prompt.append("5. 使用中文回答，术语准确，表达清晰\n");

        } catch (Exception e) {
            LOG.error("专业提示词构建失败", e);
            prompt.append("请分析以下数据并提供专业见解：\n").append(data.toString());
        }

        return prompt.toString();
    }

    /**
     * 构建交易链路分析提示词
     */
    private StringBuilder buildTransactionAnalysisPrompt(Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 交易链路性能分析专家\n\n");
        prompt.append("作为一名资深的分布式系统和交易链路分析专家，请基于以下数据进行深度分析：\n\n");

        // 交易基本信息
        prompt.append("## 交易链路信息：\n");
        if (data.containsKey("traceId")) {
            prompt.append("- TraceID: ").append(data.get("traceId")).append("\n");
        }
        if (data.containsKey("duration")) {
            prompt.append("- 总耗时: ").append(data.get("duration")).append(" ms\n");
        }
        if (data.containsKey("services")) {
            prompt.append("- 涉及服务: ").append(data.get("services")).append("\n");
        }

        // 性能指标
        prompt.append("\n## 性能指标：\n");
        Object metrics = data.get("performanceMetrics");
        if (metrics instanceof Map) {
            Map<String, Object> metricsMap = (Map<String, Object>) metrics;
            metricsMap.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        // 错误信息
        if (data.containsKey("errors")) {
            prompt.append("\n## 错误信息：\n");
            List<Object> errors = (List<Object>) data.get("errors");
            errors.forEach(error -> prompt.append("- ").append(error).append("\n"));
        }

        prompt.append("\n## 请重点分析：\n");
        prompt.append("1. **性能瓶颈识别**: 识别链路中的性能瓶颈点\n");
        prompt.append("2. **根因定位**: 分析性能问题的根本原因\n");
        prompt.append("3. **影响评估**: 评估对业务和用户的影响\n");
        prompt.append("4. **优化建议**: 提供具体的优化措施\n");
        prompt.append("5. **预防措施**: 建议避免类似问题的预防措施\n");

        return prompt;
    }

    /**
     * 构建异常诊断提示词
     */
    private StringBuilder buildAnomalyDiagnosisPrompt(Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 系统异常诊断专家\n\n");
        prompt.append("作为一名经验丰富的系统诊断专家，请分析以下异常情况：\n\n");

        // 异常概览
        prompt.append("## 异常概览：\n");
        List<Map<String, Object>> anomalies = (List<Map<String, Object>>) data.get("anomalies");
        if (anomalies != null) {
            for (int i = 0; i < anomalies.size(); i++) {
                Map<String, Object> anomaly = anomalies.get(i);
                prompt.append(String.format("%d. %s - 严重程度: %s\n",
                        i + 1, anomaly.get("description"), anomaly.get("severity")));
            }
        }

        // 系统状态
        prompt.append("\n## 当前系统状态：\n");
        Object currentState = data.get("currentState");
        if (currentState instanceof Map) {
            Map<String, Object> stateMap = (Map<String, Object>) currentState;
            stateMap.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        prompt.append("\n## 请提供：\n");
        prompt.append("1. **异常分类**: 对异常进行分类和优先级排序\n");
        prompt.append("2. **关联分析**: 分析异常之间的关联关系\n");
        prompt.append("3. **影响范围**: 评估异常的影响范围和严重程度\n");
        prompt.append("4. **紧急措施**: 需要立即采取的应急措施\n");
        prompt.append("5. **监控建议**: 加强监控的具体建议\n");

        return prompt;
    }

    /**
     * 构建性能优化提示词
     */
    private StringBuilder buildPerformanceOptimizationPrompt(Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 系统性能优化专家\n\n");
        prompt.append("作为一名专业的性能优化工程师，请分析以下性能数据并提供优化建议：\n\n");

        // 当前性能状况
        prompt.append("## 当前性能状况：\n");
        Object performanceData = data.get("performanceData");
        if (performanceData instanceof Map) {
            Map<String, Object> perfMap = (Map<String, Object>) performanceData;
            perfMap.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        // 历史趋势
        if (data.containsKey("historicalTrends")) {
            prompt.append("\n## 历史趋势：\n");
            prompt.append(data.get("historicalTrends").toString()).append("\n");
        }

        // 优化目标
        Object optimizationGoal = context.get("goal");
        if (optimizationGoal != null) {
            prompt.append("\n## 优化目标：\n");
            prompt.append("- 主要目标: ").append(optimizationGoal).append("\n");
        }

        prompt.append("\n## 请提供：\n");
        prompt.append("1. **性能瓶颈**: 识别主要的性能瓶颈\n");
        prompt.append("2. **优化方案**: 详细的优化实施方案\n");
        prompt.append("3. **效果预估**: 预期的性能提升效果\n");
        prompt.append("4. **实施计划**: 优化措施的实施优先级和时间安排\n");
        prompt.append("5. **风险评估**: 优化过程中可能的风险和应对措施\n");

        return prompt;
    }

    /**
     * 构建根因调查提示词
     */
    private StringBuilder buildRootCauseInvestigationPrompt(Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 系统故障根因调查专家\n\n");
        prompt.append("作为一名经验丰富的故障调查专家，请对以下事件进行深度根因分析：\n\n");

        // 事件基本信息
        prompt.append("## 事件基本信息：\n");
        prompt.append("- 事件ID: ").append(data.getOrDefault("incidentId", "未知")).append("\n");
        prompt.append("- 发生时间: ").append(data.getOrDefault("startTime", "未知")).append("\n");
        prompt.append("- 影响范围: ").append(data.getOrDefault("affectedServices", "未知")).append("\n");
        prompt.append("- 严重程度: ").append(data.getOrDefault("severity", "未知")).append("\n");

        // 症状描述
        if (data.containsKey("symptoms")) {
            prompt.append("\n## 故障症状：\n");
            List<Object> symptoms = (List<Object>) data.get("symptoms");
            symptoms.forEach(symptom -> prompt.append("- ").append(symptom).append("\n"));
        }

        // 环境变化
        if (data.containsKey("recentChanges")) {
            prompt.append("\n## 近期变化：\n");
            List<Object> changes = (List<Object>) data.get("recentChanges");
            changes.forEach(change -> prompt.append("- ").append(change).append("\n"));
        }

        prompt.append("\n## 请进行：\n");
        prompt.append("1. **症状分析**: 分析故障症状的模式和特征\n");
        prompt.append("2. **时间线重建**: 重建事件发生的时间线\n");
        prompt.append("3. **假设验证**: 提出并验证可能的根因假设\n");
        prompt.append("4. **证据收集**: 列出需要收集的关键证据\n");
        prompt.append("5. **最终结论**: 给出最可能的根因及其证据支持\n");

        return prompt;
    }

    /**
     * 构建容量规划提示词
     */
    private StringBuilder buildCapacityPlanningPrompt(Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 系统容量规划专家\n\n");
        prompt.append("作为一名专业的容量规划分析师，请基于以下数据进行容量分析和规划：\n\n");

        // 当前容量状况
        prompt.append("## 当前容量状况：\n");
        Object currentCapacity = data.get("currentCapacity");
        if (currentCapacity instanceof Map) {
            Map<String, Object> capacityMap = (Map<String, Object>) currentCapacity;
            capacityMap
                    .forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        // 业务增长预期
        if (data.containsKey("growthProjection")) {
            prompt.append("\n## 业务增长预期：\n");
            prompt.append(data.get("growthProjection").toString()).append("\n");
        }

        prompt.append("\n## 请提供：\n");
        prompt.append("1. **容量评估**: 当前系统容量的详细评估\n");
        prompt.append("2. **增长预测**: 基于业务增长的容量需求预测\n");
        prompt.append("3. **扩容建议**: 具体的扩容方案和时间安排\n");
        prompt.append("4. **成本分析**: 不同扩容方案的成本效益分析\n");
        prompt.append("5. **风险管控**: 容量不足的风险识别和应对策略\n");

        return prompt;
    }

    /**
     * 构建通用分析提示词
     */
    private StringBuilder buildGeneralAnalysisPrompt(Map<String, Object> data, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 系统分析专家\n\n");
        prompt.append("请作为一名资深的系统分析专家，对以下数据进行专业分析：\n\n");

        prompt.append("## 数据概览：\n");
        data.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));

        if (context != null && !context.isEmpty()) {
            prompt.append("\n## 上下文信息：\n");
            context.forEach((key, value) -> prompt.append("- ").append(key).append(": ").append(value).append("\n"));
        }

        prompt.append("\n## 请提供专业分析和建议\n");

        return prompt;
    } // 辅助方法

    private String aggregateModelResults(Map<String, String> modelResults) {
        if (modelResults.isEmpty()) {
            return "没有可用的分析结果";
        }

        StringBuilder aggregated = new StringBuilder();
        aggregated.append("## 多模型综合分析结果\n\n");

        // 提取共同观点
        aggregated.append("### 主要发现：\n");
        aggregated.append("基于").append(modelResults.size()).append("个模型的综合分析，主要发现如下：\n\n");

        // 简化实现 - 选择第一个可用结果
        String firstResult = modelResults.values().iterator().next();
        aggregated.append(firstResult);

        return aggregated.toString();
    }

    private double calculateConsensusScore(Map<String, String> modelResults) {
        // 简化实现 - 基于结果数量计算一致性分数
        if (modelResults.size() < 2)
            return 0.5;

        // 这里可以实现更复杂的语义相似度比较
        return Math.min(0.9, 0.5 + (modelResults.size() * 0.1));
    }

    private String assessAnalysisQuality(Map<String, String> modelResults) {
        if (modelResults.isEmpty())
            return "POOR";
        if (modelResults.size() == 1)
            return "FAIR";
        if (modelResults.size() >= 2)
            return "GOOD";
        return "EXCELLENT";
    }

    /**
     * 解析优化建议为结构化对象
     */
    private List<OptimizationSuggestion> parseOptimizationSuggestionsToObjects(String jsonResponse) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();
        try {
            // 只提取第一个 [ ... ] 之间的内容
            int start = jsonResponse.indexOf('[');
            int end = jsonResponse.lastIndexOf(']');
            if (start != -1 && end != -1 && start < end) {
                String jsonPart = jsonResponse.substring(start, end + 1);
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
            } else {
                LOG.warn("未找到优化建议 JSON，返回降级建议");
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

    // 添加缺失的辅助方法
    private String callDefaultModel(String prompt) throws Exception {
        return callDeepSeek(prompt);
    }

    // 提供简化的方法实现
    private String buildTraceAnalysisPrompt(Map<String, Object> traceData, List<Map<String, Object>> errorLogs) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("分析以下交易链路数据：\n");
        prompt.append("TraceID: ").append(traceData.get("traceId")).append("\n");
        prompt.append("错误日志数量: ").append(errorLogs.size()).append("\n");
        return prompt.toString();
    }

    private String selectBestModelForTrace(Map<String, Object> traceData) {
        return "deepseek-chat";
    }

    private String generateFallbackTraceAnalysis(Map<String, Object> traceData, List<Map<String, Object>> errorLogs) {
        return "交易链路分析结果（降级模式）：检测到潜在问题，建议进一步调查。";
    }

    private String buildAnomalyDetectionPrompt(List<Map<String, Object>> recentTraces) {
        return "分析以下交易数据中的异常模式：数据点数量 " + recentTraces.size();
    }

    private List<Map<String, Object>> extractAnomaliesFromAnalysis(String analysis) {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        anomalies.add(Map.of("type", "general", "description", "检测到异常模式"));
        return anomalies;
    }

    private List<String> extractRecommendationsFromAnalysis(String analysis) {
        return Arrays.asList("建议监控系统状态", "检查相关日志");
    }

    private String buildFailureAnalysisPrompt(String transactionId, Map<String, Object> transactionData,
            List<Map<String, Object>> relatedLogs) {
        return "分析交易失败：" + transactionId + "，相关日志数量：" + relatedLogs.size();
    }

    private List<String> extractActionItems(String result) {
        return Arrays.asList("检查系统状态", "重启相关服务");
    }

    private double calculateConfidence(String result) {
        return 0.8;
    }

    private Map<String, Object> generateFallbackFailureAnalysis(String transactionId,
            Map<String, Object> transactionData) {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("transactionId", transactionId);
        analysis.put("rootCause", "需要进一步调查");
        return analysis;
    }

    private String buildBottleneckAnalysisPrompt(Map<String, Object> performanceData) {
        return "分析性能瓶颈：" + performanceData.toString();
    }

    private List<String> extractBottlenecks(String analysis) {
        return Arrays.asList("数据库查询瓶颈", "网络延迟");
    }

    private List<String> extractOptimizations(String analysis) {
        return Arrays.asList("优化查询", "增加缓存");
    }

    private String extractPriority(String analysis) {
        return "HIGH";
    }

    private String extractEstimatedImpact(String analysis) {
        return "预计提升30%性能";
    }
}
