package com.o11y.ai.controller;

import com.o11y.ai.service.TransactionTraceAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 交易链路分析控制器
 * 提供交易链路数据分析和根因定位的REST API
 */
@RestController
@RequestMapping("/api/transaction-analysis")
public class TransactionAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionAnalysisController.class);

    @Autowired
    private TransactionTraceAnalysisService traceAnalysisService;

    /**
     * 分析单个交易链路
     */
    @PostMapping("/trace/analyze")
    public Map<String, Object> analyzeTrace(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> traceData = (Map<String, Object>) request.get("traceData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errorLogs = (List<Map<String, Object>>) request.getOrDefault("errorLogs",
                    new ArrayList<>());

            LOG.info("开始分析交易链路: {}", traceData.get("traceId"));

            Map<String, Object> result = traceAnalysisService.analyzeTransactionTrace(traceData, errorLogs);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "analysis", result);

        } catch (Exception e) {
            LOG.error("交易链路分析失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 实时异常检测
     */
    @PostMapping("/anomaly/detect")
    public Map<String, Object> detectAnomalies(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recentTraces = (List<Map<String, Object>>) request.get("traces");

            LOG.info("开始实时异常检测，样本数量: {}", recentTraces.size());

            Map<String, Object> result = traceAnalysisService.detectRealTimeAnomalies(recentTraces);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "detection", result);

        } catch (Exception e) {
            LOG.error("异常检测失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 交易失败深度分析
     */
    @PostMapping("/failure/analyze")
    public Map<String, Object> analyzeFailure(@RequestBody Map<String, Object> request) {
        try {
            String transactionId = (String) request.get("transactionId");
            @SuppressWarnings("unchecked")
            Map<String, Object> transactionData = (Map<String, Object>) request.get("transactionData");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contextData = (List<Map<String, Object>>) request.getOrDefault("contextData",
                    new ArrayList<>());

            LOG.info("开始深度分析交易失败: {}", transactionId);

            Map<String, Object> result = traceAnalysisService.analyzeTransactionFailure(
                    transactionId, transactionData, contextData);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "failureAnalysis", result);

        } catch (Exception e) {
            LOG.error("交易失败分析失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 性能瓶颈识别
     */
    @PostMapping("/performance/bottlenecks")
    public Map<String, Object> identifyBottlenecks(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> performanceData = (Map<String, Object>) request.get("performanceData");

            LOG.info("开始性能瓶颈识别");

            Map<String, Object> result = traceAnalysisService.identifyPerformanceBottlenecks(performanceData);

            return Map.of(
                    "status", "success",
                    "timestamp", LocalDateTime.now(),
                    "bottleneckAnalysis", result);

        } catch (Exception e) {
            LOG.error("性能瓶颈识别失败", e);
            return Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }

    /**
     * 获取分析能力说明
     */
    @GetMapping("/capabilities")
    public Map<String, Object> getAnalysisCapabilities() {
        return Map.of(
                "capabilities", Arrays.asList(
                        Map.of(
                                "name", "交易链路分析",
                                "description", "深度分析单个交易的完整调用链路，识别问题根因",
                                "endpoint", "/trace/analyze",
                                "features", Arrays.asList("根因定位", "影响链分析", "性能评估", "风险识别")),
                        Map.of(
                                "name", "实时异常检测",
                                "description", "基于最近交易数据检测异常模式和潜在问题",
                                "endpoint", "/anomaly/detect",
                                "features", Arrays.asList("异常模式识别", "风险预警", "统计分析", "趋势分析")),
                        Map.of(
                                "name", "交易失败分析",
                                "description", "深度分析交易失败的技术和业务原因",
                                "endpoint", "/failure/analyze",
                                "features", Arrays.asList("多维度分析", "根因推理", "影响评估", "解决方案")),
                        Map.of(
                                "name", "性能瓶颈识别",
                                "description", "智能识别系统性能瓶颈并提供优化建议",
                                "endpoint", "/performance/bottlenecks",
                                "features", Arrays.asList("瓶颈检测", "优化建议", "影响评估", "优先级排序"))),
                "supportedModels", Arrays.asList(
                        "DeepSeek-Chat (通用分析)",
                        "DeepSeek-Coder (代码问题)",
                        "DeepSeek-Math (数据分析)",
                        "Claude-3.5-Sonnet (复杂推理)"),
                "version", "1.0.0",
                "timestamp", LocalDateTime.now());
    }

    /**
     * 测试接口 - 模拟交易链路分析
     */
    @GetMapping("/demo/trace-analysis")
    public Map<String, Object> demoTraceAnalysis() {
        // 模拟交易数据
        Map<String, Object> mockTraceData = Map.of(
                "traceId", "demo-trace-" + System.currentTimeMillis(),
                "duration", 1250,
                "status", "error",
                "startTime", LocalDateTime.now().minusSeconds(5),
                "endTime", LocalDateTime.now(),
                "spans", Arrays.asList(
                        Map.of(
                                "serviceName", "user-service",
                                "className", "UserController",
                                "methodName", "login",
                                "duration", 150,
                                "status", "success"),
                        Map.of(
                                "serviceName", "auth-service",
                                "className", "AuthService",
                                "methodName", "validateToken",
                                "duration", 500,
                                "status", "success"),
                        Map.of(
                                "serviceName", "payment-service",
                                "className", "PaymentProcessor",
                                "methodName", "processPayment",
                                "duration", 600,
                                "status", "error",
                                "errorMessage", "Connection timeout to payment gateway")));

        List<Map<String, Object>> mockErrorLogs = Arrays.asList(
                Map.of(
                        "timestamp", LocalDateTime.now().minusSeconds(2),
                        "level", "ERROR",
                        "service", "payment-service",
                        "message", "java.net.SocketTimeoutException: Connect timed out"),
                Map.of(
                        "timestamp", LocalDateTime.now().minusSeconds(1),
                        "level", "WARN",
                        "service", "payment-service",
                        "message", "Payment gateway unavailable, retrying..."));

        try {
            Map<String, Object> result = traceAnalysisService.analyzeTransactionTrace(mockTraceData, mockErrorLogs);

            return Map.of(
                    "status", "success",
                    "demo", true,
                    "mockData", Map.of(
                            "traceData", mockTraceData,
                            "errorLogs", mockErrorLogs),
                    "analysis", result,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("演示分析失败", e);
            return Map.of(
                    "status", "error",
                    "demo", true,
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now());
        }
    }
}
