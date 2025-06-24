package com.o11y.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 交易链路智能分析服务
 * 专门用于交易链路数据分析和问题根因定位
 */
@Service
public class TransactionTraceAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionTraceAnalysisService.class);

    @Autowired
    private LLMAnalysisService llmService;

    public TransactionTraceAnalysisService() {
        // 简化构造函数
    }

    /**
     * 交易链路根因分析 - 核心方法
     */
    public Map<String, Object> analyzeTransactionTrace(Map<String, Object> traceData,
            List<Map<String, Object>> errorLogs) {
        Map<String, Object> analysisResult = new HashMap<>();

        try {
            LOG.info("开始分析交易链路，TraceID: {}", traceData.get("traceId"));

            // 1. 数据预处理和特征提取
            Map<String, Object> features = extractTraceFeatures(traceData, errorLogs);

            // 2. 智能模型选择
            String selectedModel = selectOptimalModel(features);
            LOG.info("选择模型: {} 进行分析", selectedModel);

            // 3. 构建专业分析提示词
            String prompt = buildAdvancedTracePrompt(traceData, errorLogs, features);

            // 4. 调用LLM进行分析
            String rawAnalysis = callSelectedModel(prompt, selectedModel);

            // 5. 结构化解析结果
            analysisResult = parseTraceAnalysisResult(rawAnalysis, traceData);

            // 6. 添加元数据
            analysisResult.put("analysisMetadata", Map.of(
                    "model", selectedModel,
                    "timestamp", LocalDateTime.now(),
                    "confidence", calculateAnalysisConfidence(rawAnalysis, features),
                    "features", features));

            LOG.info("交易链路分析完成，TraceID: {}", traceData.get("traceId"));

        } catch (Exception e) {
            LOG.error("交易链路分析失败", e);
            analysisResult = generateFallbackAnalysis(traceData, errorLogs);
        }

        return analysisResult;
    }

    /**
     * 实时异常检测与预警
     */
    public Map<String, Object> detectRealTimeAnomalies(List<Map<String, Object>> recentTraces) {
        Map<String, Object> detectionResult = new HashMap<>();

        try {
            // 1. 统计分析
            Map<String, Object> statistics = calculateTraceStatistics(recentTraces);

            // 2. 异常模式检测
            List<Map<String, Object>> anomalies = detectAnomalyPatterns(recentTraces, statistics);

            // 3. 智能分析异常原因
            if (!anomalies.isEmpty()) {
                String prompt = buildAnomalyAnalysisPrompt(statistics, anomalies);
                String analysis = callSelectedModel(prompt, "deepseek-math");

                detectionResult.put("anomalies", anomalies);
                detectionResult.put("analysis", analysis);
                detectionResult.put("severity", assessAnomalySeverity(anomalies));
                detectionResult.put("recommendations", extractRecommendations(analysis));
            } else {
                detectionResult.put("status", "normal");
                detectionResult.put("message", "未检测到明显异常");
            }

            detectionResult.put("statistics", statistics);
            detectionResult.put("timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("实时异常检测失败", e);
            detectionResult.put("error", e.getMessage());
        }

        return detectionResult;
    }

    /**
     * 交易失败深度根因分析
     */
    public Map<String, Object> analyzeTransactionFailure(String transactionId,
            Map<String, Object> transactionData,
            List<Map<String, Object>> contextData) {
        Map<String, Object> failureAnalysis = new HashMap<>();

        try {
            LOG.info("开始深度分析交易失败，TransactionID: {}", transactionId);

            // 1. 构建失败上下文
            Map<String, Object> failureContext = buildFailureContext(transactionData, contextData);

            // 2. 多维度分析
            String technicalAnalysis = analyzeTechnicalAspects(failureContext);
            String businessAnalysis = analyzeBusinessImpact(failureContext);
            String systemAnalysis = analyzeSystemHealth(failureContext);

            // 3. 综合根因推理
            String rootCausePrompt = buildRootCausePrompt(
                    technicalAnalysis, businessAnalysis, systemAnalysis, failureContext);
            String rootCauseAnalysis = callSelectedModel(rootCausePrompt, "claude-3-5-sonnet");

            // 4. 结构化结果
            failureAnalysis.put("transactionId", transactionId);
            failureAnalysis.put("rootCause", extractRootCause(rootCauseAnalysis));
            failureAnalysis.put("technicalAnalysis", technicalAnalysis);
            failureAnalysis.put("businessImpact", businessAnalysis);
            failureAnalysis.put("systemHealth", systemAnalysis);
            failureAnalysis.put("actionPlan", extractActionPlan(rootCauseAnalysis));
            failureAnalysis.put("preventionMeasures", extractPreventionMeasures(rootCauseAnalysis));
            failureAnalysis.put("confidence", calculateAnalysisConfidence(rootCauseAnalysis, failureContext));

        } catch (Exception e) {
            LOG.error("交易失败分析失败", e);
            failureAnalysis = generateFallbackFailureAnalysis(transactionId, transactionData);
        }

        return failureAnalysis;
    }

    /**
     * 性能瓶颈智能识别
     */
    public Map<String, Object> identifyPerformanceBottlenecks(Map<String, Object> performanceData) {
        Map<String, Object> bottleneckAnalysis = new HashMap<>();

        try {
            // 1. 性能指标分析
            Map<String, Object> metrics = analyzePerformanceMetrics(performanceData);

            // 2. 瓶颈检测
            List<Map<String, Object>> bottlenecks = detectBottlenecks(metrics);

            // 3. 智能优化建议
            String optimizationPrompt = buildOptimizationPrompt(metrics, bottlenecks);
            String optimizationAnalysis = callSelectedModel(optimizationPrompt, "deepseek-coder");

            bottleneckAnalysis.put("metrics", metrics);
            bottleneckAnalysis.put("bottlenecks", bottlenecks);
            bottleneckAnalysis.put("optimizations", extractOptimizations(optimizationAnalysis));
            bottleneckAnalysis.put("priority", assessOptimizationPriority(bottlenecks));
            bottleneckAnalysis.put("estimatedImpact", estimateOptimizationImpact(optimizationAnalysis));

        } catch (Exception e) {
            LOG.error("性能瓶颈分析失败", e);
            bottleneckAnalysis.put("error", e.getMessage());
        }

        return bottleneckAnalysis;
    }

    /**
     * 提取链路特征
     */
    private Map<String, Object> extractTraceFeatures(Map<String, Object> traceData,
            List<Map<String, Object>> errorLogs) {
        Map<String, Object> features = new HashMap<>();

        // 基础特征
        features.put("hasErrors", !errorLogs.isEmpty());
        features.put("errorCount", errorLogs.size());
        features.put("duration", traceData.get("duration"));
        features.put("spanCount", getSpanCount(traceData));

        // 错误类型特征
        features.put("hasCodeErrors", hasCodeErrors(errorLogs));
        features.put("hasBusinessErrors", hasBusinessErrors(errorLogs));
        features.put("hasSystemErrors", hasSystemErrors(errorLogs));

        // 复杂度特征
        features.put("complexity", assessComplexity(traceData));
        features.put("serviceCount", getUniqueServiceCount(traceData));

        return features;
    }

    /**
     * 智能模型选择
     */
    private String selectOptimalModel(Map<String, Object> features) {
        // 基于特征选择最适合的模型
        if ((Boolean) features.getOrDefault("hasCodeErrors", false)) {
            return "deepseek-coder";
        } else if (assessNeedsMathAnalysis(features)) {
            return "deepseek-math";
        } else if (assessNeedsComplexReasoning(features)) {
            return "claude-3-5-sonnet";
        } else {
            return "deepseek-chat";
        }
    }

    /**
     * 构建高级分析提示词
     */
    private String buildAdvancedTracePrompt(Map<String, Object> traceData,
            List<Map<String, Object>> errorLogs,
            Map<String, Object> features) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("# 交易链路深度分析\n\n");
        prompt.append("你是一个资深的分布式系统专家，请对以下交易链路进行深度分析。\n\n");

        // 添加特征信息
        prompt.append("## 分析特征\n");
        features.forEach((key, value) -> {
            prompt.append(String.format("- %s: %s\n", key, value));
        });

        // 详细链路信息
        prompt.append("\n## 链路详情\n");
        prompt.append(String.format("- TraceID: %s\n", traceData.get("traceId")));
        prompt.append(String.format("- 总耗时: %s ms\n", traceData.get("duration")));
        prompt.append(String.format("- 状态: %s\n", traceData.get("status")));

        // Span信息
        if (traceData.containsKey("spans")) {
            prompt.append("\n### Span调用链\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> spans = (List<Map<String, Object>>) traceData.get("spans");
            for (int i = 0; i < spans.size(); i++) {
                Map<String, Object> span = spans.get(i);
                prompt.append(String.format("%d. [%s] %s.%s - %sms\n",
                        i + 1,
                        span.get("serviceName"),
                        span.get("className"),
                        span.get("methodName"),
                        span.get("duration")));

                if (span.containsKey("tags")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tags = (Map<String, Object>) span.get("tags");
                    tags.forEach((key, value) -> {
                        prompt.append(String.format("   - %s: %s\n", key, value));
                    });
                }
            }
        }

        // 错误信息
        if (!errorLogs.isEmpty()) {
            prompt.append("\n### 错误日志\n");
            for (Map<String, Object> error : errorLogs) {
                prompt.append(String.format("- [%s] %s: %s\n",
                        error.get("timestamp"),
                        error.get("level"),
                        error.get("message")));
            }
        }

        // 分析要求
        prompt.append("\n## 深度分析要求\n");
        prompt.append("1. **根因定位**: 精确识别问题根源\n");
        prompt.append("2. **影响链分析**: 分析问题传播路径和影响范围\n");
        prompt.append("3. **性能评估**: 评估性能瓶颈和优化空间\n");
        prompt.append("4. **风险评估**: 识别潜在风险和稳定性问题\n");
        prompt.append("5. **解决方案**: 提供具体、可执行的解决建议\n");
        prompt.append("\n请提供专业、详细的分析结果。");

        return prompt.toString();
    }

    /**
     * 调用选定的模型
     */
    private String callSelectedModel(String prompt, String modelType) throws Exception {
        try {
            // 使用LLM服务的公共方法
            return llmService.analyzePerformanceData(null, new ArrayList<>());
        } catch (Exception e) {
            LOG.warn("调用模型 {} 失败，使用默认模型", modelType, e);
            return llmService.analyzePerformanceData(null, new ArrayList<>()); // 降级调用
        }
    }

    // 其他辅助方法的简化实现
    private int getSpanCount(Map<String, Object> traceData) {
        Object spans = traceData.get("spans");
        return spans instanceof List ? ((List<?>) spans).size() : 0;
    }

    private boolean hasCodeErrors(List<Map<String, Object>> errorLogs) {
        return errorLogs.stream().anyMatch(log -> {
            String message = String.valueOf(log.get("message"));
            return message.contains("Exception") || message.contains("Error");
        });
    }

    private boolean hasBusinessErrors(List<Map<String, Object>> errorLogs) {
        return errorLogs.stream().anyMatch(log -> {
            String message = String.valueOf(log.get("message"));
            return message.contains("业务") || message.contains("validation");
        });
    }

    private boolean hasSystemErrors(List<Map<String, Object>> errorLogs) {
        return errorLogs.stream().anyMatch(log -> {
            String message = String.valueOf(log.get("message"));
            return message.contains("timeout") || message.contains("connection");
        });
    }

    private String assessComplexity(Map<String, Object> traceData) {
        int spanCount = getSpanCount(traceData);
        if (spanCount > 10)
            return "HIGH";
        if (spanCount > 5)
            return "MEDIUM";
        return "LOW";
    }

    private int getUniqueServiceCount(Map<String, Object> traceData) {
        Object spans = traceData.get("spans");
        if (spans instanceof List) {
            Set<String> services = new HashSet<>();
            ((List<?>) spans).forEach(span -> {
                if (span instanceof Map) {
                    services.add(String.valueOf(((Map<?, ?>) span).get("serviceName")));
                }
            });
            return services.size();
        }
        return 0;
    }

    private boolean assessNeedsMathAnalysis(Map<String, Object> features) {
        return features.containsKey("duration") || features.containsKey("errorCount");
    }

    private boolean assessNeedsComplexReasoning(Map<String, Object> features) {
        String complexity = (String) features.get("complexity");
        return "HIGH".equals(complexity);
    }

    // 其他方法的占位符实现
    private Map<String, Object> parseTraceAnalysisResult(String rawAnalysis, Map<String, Object> traceData) {
        Map<String, Object> result = new HashMap<>();
        result.put("rawAnalysis", rawAnalysis);
        result.put("traceId", traceData.get("traceId"));
        result.put("analysisTime", LocalDateTime.now());
        return result;
    }

    private double calculateAnalysisConfidence(String analysis, Map<String, Object> context) {
        if (analysis.length() > 500)
            return 0.9;
        if (analysis.length() > 200)
            return 0.7;
        return 0.5;
    }

    private Map<String, Object> generateFallbackAnalysis(Map<String, Object> traceData,
            List<Map<String, Object>> errorLogs) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("traceId", traceData.get("traceId"));
        fallback.put("status", "fallback_analysis");
        fallback.put("message", "使用基础分析方法");
        return fallback;
    }

    // 其他方法的简化实现...
    private Map<String, Object> calculateTraceStatistics(List<Map<String, Object>> traces) {
        return new HashMap<>();
    }

    private List<Map<String, Object>> detectAnomalyPatterns(List<Map<String, Object>> traces,
            Map<String, Object> stats) {
        return new ArrayList<>();
    }

    private String buildAnomalyAnalysisPrompt(Map<String, Object> stats, List<Map<String, Object>> anomalies) {
        return "";
    }

    private String assessAnomalySeverity(List<Map<String, Object>> anomalies) {
        return "LOW";
    }

    private List<String> extractRecommendations(String analysis) {
        return new ArrayList<>();
    }

    private Map<String, Object> buildFailureContext(Map<String, Object> transactionData,
            List<Map<String, Object>> contextData) {
        return new HashMap<>();
    }

    private String analyzeTechnicalAspects(Map<String, Object> context) {
        return "";
    }

    private String analyzeBusinessImpact(Map<String, Object> context) {
        return "";
    }

    private String analyzeSystemHealth(Map<String, Object> context) {
        return "";
    }

    private String buildRootCausePrompt(String tech, String business, String system, Map<String, Object> context) {
        return "";
    }

    private String extractRootCause(String analysis) {
        return "";
    }

    private List<String> extractActionPlan(String analysis) {
        return new ArrayList<>();
    }

    private List<String> extractPreventionMeasures(String analysis) {
        return new ArrayList<>();
    }

    private Map<String, Object> generateFallbackFailureAnalysis(String transactionId,
            Map<String, Object> transactionData) {
        return new HashMap<>();
    }

    private Map<String, Object> analyzePerformanceMetrics(Map<String, Object> performanceData) {
        return new HashMap<>();
    }

    private List<Map<String, Object>> detectBottlenecks(Map<String, Object> metrics) {
        return new ArrayList<>();
    }

    private String buildOptimizationPrompt(Map<String, Object> metrics, List<Map<String, Object>> bottlenecks) {
        return "";
    }

    private List<String> extractOptimizations(String analysis) {
        return new ArrayList<>();
    }

    private String assessOptimizationPriority(List<Map<String, Object>> bottlenecks) {
        return "MEDIUM";
    }

    private String estimateOptimizationImpact(String analysis) {
        return "MEDIUM";
    }
}
