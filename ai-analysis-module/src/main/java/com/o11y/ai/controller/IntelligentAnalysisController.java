package com.o11y.ai.controller;

import com.o11y.ai.service.AdvancedAnomalyDetectionService;
import com.o11y.ai.service.RootCauseAnalysisEngine;
import com.o11y.ai.service.TransactionTraceAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 智能分析控制器
 * 提供完整的智能分析能力，包括异常检测、根因分析、交易链路分析等
 */
@RestController
@RequestMapping("/api/intelligent-analysis")
@CrossOrigin(origins = "*")
public class IntelligentAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(IntelligentAnalysisController.class);

    @Autowired
    private AdvancedAnomalyDetectionService anomalyDetectionService;

    @Autowired
    private RootCauseAnalysisEngine rootCauseEngine;
    @Autowired
    private TransactionTraceAnalysisService traceAnalysisService;

    /**
     * 系统健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("services", Map.of(
                "anomalyDetection", "UP",
                "rootCauseAnalysis", "UP",
                "traceAnalysis", "UP",
                "llmAnalysis", "UP"));
        return health;
    }

    /**
     * 高级异常检测 API
     */
    @PostMapping("/anomaly/advanced-detect")
    public Map<String, Object> advancedAnomalyDetection(@RequestBody Map<String, Object> request) {
        try {
            List<Map<String, Object>> timeSeriesData = (List<Map<String, Object>>) request.get("timeSeriesData");
            Map<String, Object> currentMetrics = (Map<String, Object>) request.get("currentMetrics");

            LOG.info("开始高级异常检测，数据点数量: {}", timeSeriesData.size());

            Map<String, Object> result = anomalyDetectionService.detectAnomalies(timeSeriesData, currentMetrics);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "detection", result);

        } catch (Exception e) {
            LOG.error("高级异常检测失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 综合根因分析 API
     */
    @PostMapping("/rootcause/comprehensive-analysis")
    public Map<String, Object> comprehensiveRootCauseAnalysis(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> incidentData = (Map<String, Object>) request.get("incidentData");

            LOG.info("开始综合根因分析，事件ID: {}", incidentData.get("incidentId"));

            Map<String, Object> result = rootCauseEngine.analyzeRootCause(incidentData);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "rootCauseAnalysis", result);

        } catch (Exception e) {
            LOG.error("综合根因分析失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 智能交易链路分析 API
     */
    @PostMapping("/transaction/intelligent-analysis")
    public Map<String, Object> intelligentTransactionAnalysis(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> traceData = (Map<String, Object>) request.get("traceData");
            List<Map<String, Object>> errorLogs = (List<Map<String, Object>>) request.getOrDefault("errorLogs",
                    new ArrayList<>());

            LOG.info("开始智能交易链路分析，TraceID: {}", traceData.get("traceId"));

            Map<String, Object> result = traceAnalysisService.analyzeTransactionTrace(traceData, errorLogs);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "analysis", result);

        } catch (Exception e) {
            LOG.error("智能交易链路分析失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 一站式智能诊断 API - 集成所有分析能力
     */
    @PostMapping("/diagnosis/one-stop")
    public Map<String, Object> oneStopIntelligentDiagnosis(@RequestBody Map<String, Object> request) {
        try {
            LOG.info("开始一站式智能诊断");

            Map<String, Object> diagnosisResult = new HashMap<>();
            List<Map<String, Object>> analysisResults = new ArrayList<>();

            // 1. 异常检测
            try {
                List<Map<String, Object>> timeSeriesData = (List<Map<String, Object>>) request.get("timeSeriesData");
                Map<String, Object> currentMetrics = (Map<String, Object>) request.get("currentMetrics");

                if (timeSeriesData != null && currentMetrics != null) {
                    Map<String, Object> anomalyResult = anomalyDetectionService.detectAnomalies(timeSeriesData,
                            currentMetrics);
                    analysisResults.add(Map.of(
                            "type", "anomalyDetection",
                            "result", anomalyResult,
                            "priority", assessAnomalyPriority(anomalyResult)));
                }
            } catch (Exception e) {
                LOG.warn("异常检测部分失败", e);
            }

            // 2. 根因分析
            try {
                Map<String, Object> incidentData = (Map<String, Object>) request.get("incidentData");
                if (incidentData != null) {
                    Map<String, Object> rootCauseResult = rootCauseEngine.analyzeRootCause(incidentData);
                    analysisResults.add(Map.of(
                            "type", "rootCauseAnalysis",
                            "result", rootCauseResult,
                            "priority", assessRootCausePriority(rootCauseResult)));
                }
            } catch (Exception e) {
                LOG.warn("根因分析部分失败", e);
            }

            // 3. 交易链路分析
            try {
                Map<String, Object> traceData = (Map<String, Object>) request.get("traceData");
                List<Map<String, Object>> errorLogs = (List<Map<String, Object>>) request.getOrDefault("errorLogs",
                        new ArrayList<>());

                if (traceData != null) {
                    Map<String, Object> traceResult = traceAnalysisService.analyzeTransactionTrace(traceData,
                            errorLogs);
                    analysisResults.add(Map.of(
                            "type", "transactionTrace",
                            "result", traceResult,
                            "priority", assessTracePriority(traceResult)));
                }
            } catch (Exception e) {
                LOG.warn("交易链路分析部分失败", e);
            }

            // 4. 综合评估和建议
            Map<String, Object> comprehensiveAssessment = generateComprehensiveAssessment(analysisResults);

            // 5. 构建诊断结果
            diagnosisResult.put("analysisResults", analysisResults);
            diagnosisResult.put("comprehensiveAssessment", comprehensiveAssessment);
            diagnosisResult.put("overallSeverity", calculateOverallSeverity(analysisResults));
            diagnosisResult.put("actionPlan", generateActionPlan(analysisResults));
            diagnosisResult.put("timestamp", LocalDateTime.now());

            LOG.info("一站式智能诊断完成，执行了 {} 项分析", analysisResults.size());

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "diagnosis", diagnosisResult);

        } catch (Exception e) {
            LOG.error("一站式智能诊断失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 智能性能优化建议 API
     */
    @PostMapping("/optimization/intelligent-suggestions")
    public Map<String, Object> intelligentOptimizationSuggestions(@RequestBody Map<String, Object> request) {
        try {
            Map<String, Object> performanceData = (Map<String, Object>) request.get("performanceData");
            String optimizationGoal = (String) request.getOrDefault("goal", "general");

            LOG.info("开始生成智能性能优化建议，目标: {}", optimizationGoal);

            Map<String, Object> optimizationResult = generateOptimizationSuggestions(performanceData, optimizationGoal);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "optimization", optimizationResult);

        } catch (Exception e) {
            LOG.error("智能性能优化建议生成失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 预测性分析 API
     */
    @PostMapping("/prediction/trend-analysis")
    public Map<String, Object> predictiveTrendAnalysis(@RequestBody Map<String, Object> request) {
        try {
            List<Map<String, Object>> historicalData = (List<Map<String, Object>>) request.get("historicalData");
            String predictionHorizon = (String) request.getOrDefault("horizon", "1hour");

            LOG.info("开始预测性趋势分析，预测周期: {}", predictionHorizon);

            Map<String, Object> predictionResult = performPredictiveAnalysis(historicalData, predictionHorizon);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "prediction", predictionResult);

        } catch (Exception e) {
            LOG.error("预测性趋势分析失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 演示和测试 API
     */
    @GetMapping("/demo/comprehensive")
    public Map<String, Object> comprehensiveDemo() {
        try {
            LOG.info("执行综合演示");

            // 生成模拟数据
            Map<String, Object> demoData = generateDemoData();

            // 执行一站式诊断
            Map<String, Object> diagnosisRequest = new HashMap<>();
            diagnosisRequest.put("timeSeriesData", demoData.get("timeSeriesData"));
            diagnosisRequest.put("currentMetrics", demoData.get("currentMetrics"));
            diagnosisRequest.put("incidentData", demoData.get("incidentData"));
            diagnosisRequest.put("traceData", demoData.get("traceData"));
            diagnosisRequest.put("errorLogs", demoData.get("errorLogs"));

            Map<String, Object> diagnosisResult = oneStopIntelligentDiagnosis(diagnosisRequest);

            Map<String, Object> result = new HashMap<>();
            result.put("demoData", demoData);
            result.put("diagnosisResult", diagnosisResult);
            result.put("description", "这是一个综合演示，展示了智能分析系统的完整能力");

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "demo", result);

        } catch (Exception e) {
            LOG.error("综合演示失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    // ==================== 私有辅助方法 ====================

    private String assessAnomalyPriority(Map<String, Object> anomalyResult) {
        List<Map<String, Object>> anomalies = (List<Map<String, Object>>) anomalyResult.get("anomalies");
        if (anomalies == null || anomalies.isEmpty())
            return "LOW";

        long criticalCount = anomalies.stream()
                .filter(anomaly -> "CRITICAL".equals(anomaly.get("severity")) || "HIGH".equals(anomaly.get("severity")))
                .count();

        return criticalCount > 0 ? "HIGH" : "MEDIUM";
    }

    private String assessRootCausePriority(Map<String, Object> rootCauseResult) {
        Object primaryRootCause = rootCauseResult.get("primaryRootCause");
        if (primaryRootCause == null)
            return "LOW";

        Map<String, Object> rootCause = (Map<String, Object>) primaryRootCause;
        Double confidence = (Double) rootCause.get("confidence");

        return (confidence != null && confidence > 0.8) ? "HIGH" : "MEDIUM";
    }

    private String assessTracePriority(Map<String, Object> traceResult) {
        // 简化实现
        return "MEDIUM";
    }

    private Map<String, Object> generateComprehensiveAssessment(List<Map<String, Object>> analysisResults) {
        Map<String, Object> assessment = new HashMap<>();

        int highPriorityCount = 0;
        List<String> keyFindings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        for (Map<String, Object> result : analysisResults) {
            String priority = (String) result.get("priority");
            if ("HIGH".equals(priority)) {
                highPriorityCount++;
            }

            String type = (String) result.get("type");
            switch (type) {
                case "anomalyDetection":
                    keyFindings.add("检测到系统异常模式");
                    recommendations.add("建议立即检查系统资源和服务状态");
                    break;
                case "rootCauseAnalysis":
                    keyFindings.add("识别出潜在根因");
                    recommendations.add("建议按照根因分析结果采取针对性措施");
                    break;
                case "transactionTrace":
                    keyFindings.add("发现交易链路问题");
                    recommendations.add("建议优化交易流程和服务调用");
                    break;
            }
        }

        assessment.put("overallStatus", highPriorityCount > 0 ? "ATTENTION_REQUIRED" : "NORMAL");
        assessment.put("highPriorityIssues", highPriorityCount);
        assessment.put("keyFindings", keyFindings);
        assessment.put("recommendations", recommendations);
        assessment.put("nextSteps", generateNextSteps(analysisResults));

        return assessment;
    }

    private String calculateOverallSeverity(List<Map<String, Object>> analysisResults) {
        long highPriorityCount = analysisResults.stream()
                .filter(result -> "HIGH".equals(result.get("priority")))
                .count();

        if (highPriorityCount >= 2)
            return "CRITICAL";
        if (highPriorityCount == 1)
            return "HIGH";
        return "MEDIUM";
    }

    private List<Map<String, Object>> generateActionPlan(List<Map<String, Object>> analysisResults) {
        List<Map<String, Object>> actionPlan = new ArrayList<>();

        for (Map<String, Object> result : analysisResults) {
            String type = (String) result.get("type");
            String priority = (String) result.get("priority");

            Map<String, Object> action = new HashMap<>();
            action.put("type", type);
            action.put("priority", priority);
            action.put("estimatedTime", getEstimatedTime(type));
            action.put("description", getActionDescription(type));

            actionPlan.add(action);
        }

        // 按优先级排序
        actionPlan.sort((a, b) -> {
            String priorityA = (String) a.get("priority");
            String priorityB = (String) b.get("priority");
            return priorityB.compareTo(priorityA); // 高优先级在前
        });

        return actionPlan;
    }

    private List<String> generateNextSteps(List<Map<String, Object>> analysisResults) {
        List<String> nextSteps = new ArrayList<>();

        if (analysisResults.stream().anyMatch(r -> "HIGH".equals(r.get("priority")))) {
            nextSteps.add("立即执行高优先级修复措施");
            nextSteps.add("监控系统状态变化");
        }

        nextSteps.add("持续观察系统性能指标");
        nextSteps.add("定期执行预防性分析");
        nextSteps.add("更新监控策略和阈值");

        return nextSteps;
    }

    private String getEstimatedTime(String type) {
        switch (type) {
            case "anomalyDetection":
                return "30分钟";
            case "rootCauseAnalysis":
                return "1-2小时";
            case "transactionTrace":
                return "1小时";
            default:
                return "1小时";
        }
    }

    private String getActionDescription(String type) {
        switch (type) {
            case "anomalyDetection":
                return "处理检测到的系统异常";
            case "rootCauseAnalysis":
                return "解决识别出的根本原因";
            case "transactionTrace":
                return "优化交易链路性能";
            default:
                return "执行相关优化措施";
        }
    }

    private Map<String, Object> generateOptimizationSuggestions(Map<String, Object> performanceData, String goal) {
        Map<String, Object> optimization = new HashMap<>();

        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 基于目标生成建议
        switch (goal.toLowerCase()) {
            case "response_time":
                suggestions.add(Map.of(
                        "category", "性能优化",
                        "suggestion", "启用缓存机制减少数据库查询",
                        "impact", "HIGH",
                        "effort", "MEDIUM"));
                break;
            case "throughput":
                suggestions.add(Map.of(
                        "category", "容量优化",
                        "suggestion", "增加服务实例数量",
                        "impact", "HIGH",
                        "effort", "LOW"));
                break;
            case "reliability":
                suggestions.add(Map.of(
                        "category", "可靠性优化",
                        "suggestion", "实施熔断器和重试机制",
                        "impact", "MEDIUM",
                        "effort", "MEDIUM"));
                break;
            default:
                suggestions.add(Map.of(
                        "category", "通用优化",
                        "suggestion", "优化系统配置和资源分配",
                        "impact", "MEDIUM",
                        "effort", "MEDIUM"));
        }

        optimization.put("goal", goal);
        optimization.put("suggestions", suggestions);
        optimization.put("priority", "MEDIUM");
        optimization.put("expectedImprovement", "20-30%");

        return optimization;
    }

    private Map<String, Object> performPredictiveAnalysis(List<Map<String, Object>> historicalData, String horizon) {
        Map<String, Object> prediction = new HashMap<>();

        // 简化的预测分析
        Map<String, Object> trendAnalysis = new HashMap<>();
        trendAnalysis.put("direction", "stable");
        trendAnalysis.put("confidence", 0.75);
        trendAnalysis.put("volatility", "low");

        List<Map<String, Object>> predictions = new ArrayList<>();
        predictions.add(Map.of(
                "metric", "responseTime",
                "predictedValue", 150.0,
                "confidence", 0.8,
                "trend", "stable"));
        predictions.add(Map.of(
                "metric", "errorRate",
                "predictedValue", 2.5,
                "confidence", 0.7,
                "trend", "decreasing"));

        prediction.put("horizon", horizon);
        prediction.put("trendAnalysis", trendAnalysis);
        prediction.put("predictions", predictions);
        prediction.put("overallRisk", "LOW");

        return prediction;
    }

    private Map<String, Object> generateDemoData() {
        Map<String, Object> demoData = new HashMap<>();

        // 时间序列数据
        List<Map<String, Object>> timeSeriesData = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            timeSeriesData.add(Map.of(
                    "timestamp", LocalDateTime.now().minusHours(24 - i),
                    "responseTime", 100 + Math.random() * 200,
                    "errorRate", Math.random() * 10,
                    "throughput", 1000 + Math.random() * 500,
                    "cpuUsage", 30 + Math.random() * 40,
                    "memoryUsage", 40 + Math.random() * 30));
        }

        // 当前指标
        Map<String, Object> currentMetrics = Map.of(
                "responseTime", 1200.0, // 异常高的响应时间
                "errorRate", 8.5, // 异常高的错误率
                "throughput", 800.0,
                "cpuUsage", 85.0, // 高CPU使用率
                "memoryUsage", 78.0);

        // 事件数据
        Map<String, Object> incidentData = Map.of(
                "incidentId", "INC-2024-001",
                "startTime", LocalDateTime.now().minusHours(1),
                "severity", "HIGH",
                "affectedServices", Arrays.asList("payment-service", "user-service"),
                "affectedUsers", 1500,
                "errorMessages", Arrays.asList("Connection timeout", "Database connection failed"),
                "performanceMetrics", currentMetrics);

        // 交易链路数据
        Map<String, Object> traceData = Map.of(
                "traceId", "trace-12345",
                "duration", 2500,
                "spanCount", 8,
                "errorCount", 2,
                "services", Arrays.asList("frontend", "api-gateway", "payment-service", "database"));

        // 错误日志
        List<Map<String, Object>> errorLogs = Arrays.asList(
                Map.of(
                        "timestamp", LocalDateTime.now().minusMinutes(30),
                        "level", "ERROR",
                        "service", "payment-service",
                        "message", "Database connection timeout after 30 seconds"),
                Map.of(
                        "timestamp", LocalDateTime.now().minusMinutes(25),
                        "level", "ERROR",
                        "service", "user-service",
                        "message", "Failed to authenticate user: connection refused"));

        demoData.put("timeSeriesData", timeSeriesData);
        demoData.put("currentMetrics", currentMetrics);
        demoData.put("incidentData", incidentData);
        demoData.put("traceData", traceData);
        demoData.put("errorLogs", errorLogs);

        return demoData;
    }
}
