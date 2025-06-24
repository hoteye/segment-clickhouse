package com.o11y.ai.controller;

import com.o11y.ai.service.PerformanceAnalysisService;
import com.o11y.ai.service.ReportStorageService;
import com.o11y.ai.service.LLMAnalysisService;
import com.o11y.ai.repository.ClickHouseRepository;
import com.o11y.ai.model.PerformanceReport;
import com.o11y.ai.model.OptimizationSuggestion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 性能分析 REST API 控制器
 */
@RestController
@RequestMapping("/api/ai-analysis")
public class PerformanceAnalysisController {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceAnalysisController.class);

    @Autowired
    private PerformanceAnalysisService analysisService;

    @Autowired
    private ReportStorageService reportService;
    @Autowired
    private LLMAnalysisService llmAnalysisService;

    @Autowired
    private ClickHouseRepository clickHouseRepository;

    /**
     * 生成性能分析报告
     */
    @PostMapping("/reports/generate")
    public ResponseEntity<?> generateReport(@RequestParam(defaultValue = "1") int timeRangeHours) {
        try {
            LOG.info("收到生成报告请求，时间范围: {}小时", timeRangeHours);

            PerformanceReport report = analysisService.generateAnalysisReport(timeRangeHours);
            if (report == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "无法生成报告，可能缺少数据"));
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            LOG.error("生成报告失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "生成报告失败: " + e.getMessage()));
        }
    }

    /**
     * 获取最近的报告列表
     */
    @GetMapping("/reports")
    public ResponseEntity<List<PerformanceReport>> getRecentReports(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<PerformanceReport> reports = reportService.getRecentReports(limit);
            return ResponseEntity.ok(reports);

        } catch (Exception e) {
            LOG.error("获取报告列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据ID获取特定报告
     */
    @GetMapping("/reports/{reportId}")
    public ResponseEntity<?> getReportById(@PathVariable String reportId) {
        try {
            PerformanceReport report = reportService.getReportById(reportId);
            if (report == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            LOG.error("获取报告失败: {}", reportId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取报告失败: " + e.getMessage()));
        }
    }

    /**
     * 触发定时分析任务
     */
    @PostMapping("/analysis/trigger")
    public ResponseEntity<?> triggerAnalysis() {
        try {
            analysisService.scheduledAnalysis();
            return ResponseEntity.ok(Map.of("message", "分析任务已触发"));

        } catch (Exception e) {
            LOG.error("触发分析任务失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "触发分析任务失败: " + e.getMessage()));
        }
    }

    /**
     * 获取优化建议
     */
    @PostMapping("/suggestions")
    public ResponseEntity<?> getOptimizationSuggestions(@RequestParam(defaultValue = "1") int timeRangeHours) {
        try {
            LOG.info("收到获取优化建议请求，时间范围: {}小时", timeRangeHours);

            List<OptimizationSuggestion> suggestions = analysisService.generateOptimizationSuggestions(timeRangeHours);

            return ResponseEntity.ok(Map.of(
                    "suggestions", suggestions,
                    "total", suggestions.size(),
                    "timestamp", System.currentTimeMillis()));

        } catch (Exception e) {
            LOG.error("获取优化建议失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取优化建议失败: " + e.getMessage()));
        }
    }

    /**
     * LLM 服务健康检查
     */
    @GetMapping("/llm/health")
    public ResponseEntity<?> checkLlmHealth() {
        try {
            Map<String, Object> status = llmAnalysisService.getStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            LOG.error("LLM 健康检查失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "健康检查失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AI Analysis Module",
                "timestamp", System.currentTimeMillis()));
    }

    // ========== ClickHouse 数据查询 API ==========

    /**
     * 获取错误调用链详情
     */
    @GetMapping("/traces/errors")
    public ResponseEntity<?> getErrorTraces(
            @RequestParam(defaultValue = "1") int hoursAgo,
            @RequestParam(required = false) String serviceName) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hoursAgo);

            List<Map<String, Object>> errorTraces = clickHouseRepository.getErrorTraces(startTime, endTime,
                    serviceName);

            return ResponseEntity.ok(Map.of(
                    "data", errorTraces,
                    "total", errorTraces.size(),
                    "timeRange", Map.of(
                            "startTime", startTime,
                            "endTime", endTime,
                            "hours", hoursAgo)));

        } catch (Exception e) {
            LOG.error("获取错误调用链失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取错误调用链失败: " + e.getMessage()));
        }
    }

    /**
     * 获取慢请求详情
     */
    @GetMapping("/traces/slow")
    public ResponseEntity<?> getSlowRequests(
            @RequestParam(defaultValue = "1") int hoursAgo,
            @RequestParam(defaultValue = "1000") long durationThreshold,
            @RequestParam(required = false) String serviceName) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hoursAgo);

            List<Map<String, Object>> slowRequests = clickHouseRepository.getSlowRequests(
                    startTime, endTime, durationThreshold, serviceName);

            return ResponseEntity.ok(Map.of(
                    "data", slowRequests,
                    "total", slowRequests.size(),
                    "threshold", durationThreshold + "ms",
                    "timeRange", Map.of(
                            "startTime", startTime,
                            "endTime", endTime,
                            "hours", hoursAgo)));

        } catch (Exception e) {
            LOG.error("获取慢请求失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取慢请求失败: " + e.getMessage()));
        }
    }

    /**
     * 获取服务拓扑关系
     */
    @GetMapping("/topology/services")
    public ResponseEntity<?> getServiceTopology(@RequestParam(defaultValue = "1") int hoursAgo) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hoursAgo);

            List<Map<String, Object>> topology = clickHouseRepository.getServiceTopology(startTime, endTime);

            return ResponseEntity.ok(Map.of(
                    "data", topology,
                    "total", topology.size(),
                    "timeRange", Map.of(
                            "startTime", startTime,
                            "endTime", endTime,
                            "hours", hoursAgo)));

        } catch (Exception e) {
            LOG.error("获取服务拓扑失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取服务拓扑失败: " + e.getMessage()));
        }
    }

    /**
     * 获取 events 表样例数据
     */
    @GetMapping("/data/events/sample")
    public ResponseEntity<?> getEventsSample(
            @RequestParam(defaultValue = "1") int hoursAgo,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(hoursAgo);

            List<Map<String, Object>> events = clickHouseRepository.getEventsSample(startTime, endTime, limit);

            return ResponseEntity.ok(Map.of(
                    "data", events,
                    "total", events.size(),
                    "limit", limit,
                    "timeRange", Map.of(
                            "startTime", startTime,
                            "endTime", endTime,
                            "hours", hoursAgo)));

        } catch (Exception e) {
            LOG.error("获取events样例数据失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取events样例数据失败: " + e.getMessage()));
        }
    }

    /**
     * 获取 events 表结构
     */
    @GetMapping("/data/events/schema")
    public ResponseEntity<?> getEventsSchema() {
        try {
            List<Map<String, Object>> schema = clickHouseRepository.getEventsTableSchema();

            return ResponseEntity.ok(Map.of(
                    "schema", schema,
                    "fieldCount", schema.size()));

        } catch (Exception e) {
            LOG.error("获取events表结构失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取events表结构失败: " + e.getMessage()));
        }
    }

    /**
     * 从 ClickHouse 获取最近的报告列表
     */
    @GetMapping("/reports/clickhouse")
    public ResponseEntity<?> getRecentReportsFromClickHouse(@RequestParam(defaultValue = "10") int limit) {
        try {
            List<Map<String, Object>> reports = clickHouseRepository.getPerformanceReportsFromClickHouse(limit);
            return ResponseEntity.ok(reports);

        } catch (Exception e) {
            LOG.error("从 ClickHouse 获取报告列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取报告失败: " + e.getMessage()));
        }
    }

    /**
     * 从 ClickHouse 根据ID获取特定报告
     */
    @GetMapping("/reports/clickhouse/{reportId}")
    public ResponseEntity<?> getReportByIdFromClickHouse(@PathVariable String reportId) {
        try {
            Map<String, Object> report = clickHouseRepository.getPerformanceReportByIdFromClickHouse(reportId);
            if (report == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            LOG.error("从 ClickHouse 获取报告失败: {}", reportId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取报告失败: " + e.getMessage()));
        }
    }
}
