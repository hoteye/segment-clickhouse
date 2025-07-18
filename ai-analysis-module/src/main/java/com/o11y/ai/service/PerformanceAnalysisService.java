package com.o11y.ai.service;

import com.o11y.ai.config.AiAnalysisProperties;
import com.o11y.ai.model.PerformanceMetrics;
import com.o11y.ai.model.PerformanceAnomaly;
import com.o11y.ai.model.PerformanceReport;
import com.o11y.ai.model.OptimizationSuggestion;
import com.o11y.ai.repository.ClickHouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 智能性能分析服务
 * 
 * 主要功能：
 * 1. 定时收集性能数据
 * 2. 异常检测和分析
 * 3. 生成智能分析报告
 * 4. 提供 REST API 接口
 */
@Service
public class PerformanceAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceAnalysisService.class);

    @Autowired
    private AiAnalysisProperties properties;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LLMAnalysisService llmService;
    @Autowired
    private ReportStorageService reportService;

    @Autowired
    private ClickHouseRepository clickHouseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * 定时执行性能分析
     */
    @Scheduled(cron = "#{@aiAnalysisProperties.analysis.schedule.cron}")
    public void scheduledAnalysis() {
        if (!properties.getAnalysis().isEnabled() || !properties.getAnalysis().getSchedule().isEnabled()) {
            LOG.debug("定时分析已禁用");
            return;
        }

        try {
            LOG.info("开始执行定时性能分析");
            int timeRangeHours = properties.getAnalysis().getWindow().getHours();
            int timeRangeMinutes = timeRangeHours * 60; // 转换为分钟

            // 获取所有服务列表
            List<String> services = getAllServices(timeRangeMinutes);
            if (services.isEmpty()) {
                LOG.info("未找到服务，跳过生成报告");
                return;
            }

            for (String service : services) {
                LOG.info("开始为服务 {} 生成性能报告", service);
                // 异步调用，但由于是定时任务，我们不关心其返回结果，只确保它被执行
                generateAnalysisReport(timeRangeMinutes, service);
            }
            LOG.info("所有服务性能报告生成完成");

        } catch (Exception e) {
            LOG.error("定时性能分析失败", e);
        }
    }

    /**
     * 获取所有服务名称列表
     */
    private List<String> getAllServices(int timeRangeMinutes) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(timeRangeMinutes);
        String sql = "SELECT DISTINCT service FROM events WHERE start_time >= toDateTime(?) AND start_time <= toDateTime(?) AND service IS NOT NULL";

        List<String> services = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, startTimeStr);
            stmt.setString(2, endTimeStr);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String service = rs.getString("service");
                    if (service != null && !service.isEmpty()) {
                        services.add(service);
                    }
                }
            }
            LOG.info("获取到 {} 个服务", services.size());
        } catch (Exception e) {
            LOG.error("获取服务列表失败", e);
        }
        return services;
    }

    /**
     * 检查是否有足够的数据生成报告
     */
    public boolean hasEnoughData(int timeRangeMinutes, String service) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(timeRangeMinutes);
            PerformanceMetrics metrics = clickHouseRepository.getAggregatedPerformanceMetrics(startTime, endTime, service);
            return metrics != null && metrics.getTotalRequests() > 0;
        } catch (Exception e) {
            LOG.error("检查数据时发生错误", e);
            return false;
        }
    }

    /**
     * 生成性能分析报告
     */
    @Async
    public CompletableFuture<PerformanceReport> generateAnalysisReport(int timeRangeMinutes, String service) {
        long processStartTime = System.currentTimeMillis();
        LOG.info("=== 开始生成性能分析报告 ===");
        LOG.info("开始时间: {}", java.time.LocalDateTime.now());
        LOG.info("参数: 时间范围={}分钟, 服务={}", timeRangeMinutes, service);

        PerformanceReport report = PerformanceReport.builder()
                .reportId(UUID.randomUUID().toString())
                .generatedAt(LocalDateTime.now())
                .timeRange(timeRangeMinutes)
                .build();

        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(timeRangeMinutes);

            // 1. 收集性能数据 (统一查询)
            LOG.info("步骤1: 开始收集性能数据...");
            PerformanceMetrics metrics = clickHouseRepository.getAggregatedPerformanceMetrics(startTime, endTime, service);
            if (metrics == null || metrics.getTotalRequests() == 0) {
                LOG.warn("未收集到足够的服务 {} 性能数据，无法生成报告", service);
                report.setSummary("数据不足，无法生成报告");
                reportService.saveReport(report);
                clickHouseRepository.savePerformanceReport(report.getReportId(), convertReportToJson(report), report.getGeneratedAt());
                return CompletableFuture.completedFuture(report);
            }
            LOG.info("步骤1完成: 成功收集性能数据，总请求数: {}, 平均响应时间: {}ms",
                    metrics.getTotalRequests(), metrics.getAvgResponseTime());

            // 1.1 收集错误堆栈
            List<String> errorStacks = collectErrorStacks(timeRangeMinutes, service);
            LOG.info("步骤1.1: 收集到 {} 条错误堆栈", errorStacks.size());

            // 2. 异常检测
            LOG.info("步骤2: 开始异常检测...");
            List<PerformanceAnomaly> anomalies = detectAnomalies(metrics);
            LOG.info("步骤2完成: 检测到 {} 个异常", anomalies.size());

            // 4. LLM 智能分析
            LOG.info("步骤4: 开始LLM智能分析...");
            if (properties.getLlm().isEnabled()) {
                try {
                    LOG.info("步骤4a: 调用LLM分析性能数据和错误堆栈...");
                    String intelligentAnalysis = llmService.analyzePerformanceData(metrics, anomalies, errorStacks, timeRangeMinutes);
                    report.setIntelligentAnalysis(intelligentAnalysis);
                    report.setErrorStacks(errorStacks);

                    LOG.info("步骤4b: 调用LLM生成优化建议...");
                    List<OptimizationSuggestion> suggestions = llmService.generateOptimizationSuggestions(metrics, anomalies);
                    report.setOptimizationSuggestions(suggestions.stream()
                            .map(s -> s.getTitle() + ": " + s.getDescription())
                            .collect(java.util.stream.Collectors.toList()));

                } catch (Exception e) {
                    LOG.error("LLM分析失败，使用基础分析", e);
                    report.setIntelligentAnalysis(generateBasicAnalysis(metrics, anomalies));
                    report.setOptimizationSuggestions(generateBasicSuggestions(metrics, anomalies));
                    report.setErrorStacks(errorStacks);
                }
            } else {
                LOG.info("步骤4跳过: LLM已禁用，使用基础分析...");
                report.setIntelligentAnalysis(generateBasicAnalysis(metrics, anomalies));
                report.setOptimizationSuggestions(generateBasicSuggestions(metrics, anomalies));
                report.setErrorStacks(errorStacks);
            }

            // 5. 设置其他报告内容
            LOG.info("步骤5: 设置报告其他内容...");
            report.setMetrics(convertToReportMetrics(metrics));
            report.setAnomalies(anomalies);
            report.setSummary(generateSummary(metrics, anomalies, timeRangeMinutes));
            LOG.info("步骤5完成: 报告内容设置完成");

            // 6. 保存报告
            LOG.info("步骤6: 保存报告到文件系统...");
            reportService.saveReport(report);
            LOG.info("步骤6a完成: 性能分析报告已保存到文件系统");

            // 7. 保存到 ClickHouse
            LOG.info("步骤7: 保存报告到 ClickHouse...");
            clickHouseRepository.savePerformanceReport(report.getReportId(), convertReportToJson(report), report.getGeneratedAt());
            LOG.info("步骤7完成: 性能分析报告已保存到 ClickHouse");

            long processEndTime = System.currentTimeMillis();
            long duration = processEndTime - processStartTime;
            LOG.info("=== 性能分析报告生成完成 ===");
            LOG.info("结束时间: {}", java.time.LocalDateTime.now());
            LOG.info("总耗时: {}ms ({}秒)", duration, duration / 1000.0);
            LOG.info("报告ID: {}", report.getReportId());
            return CompletableFuture.completedFuture(report);

        } catch (Exception e) {
            long processEndTime = System.currentTimeMillis();
            long duration = processEndTime - processStartTime;
            LOG.error("=== 性能分析报告生成失败 ===");
            LOG.error("结束时间: {}", java.time.LocalDateTime.now());
            LOG.error("失败耗时: {}ms ({}秒)", duration, duration / 1000.0);
            LOG.error("生成性能分析报告失败", e);
            report.setSummary("报告生成过程中发生内部错误: " + e.getMessage());
            try {
                 reportService.saveReport(report);
                 clickHouseRepository.savePerformanceReport(report.getReportId(), convertReportToJson(report), report.getGeneratedAt());
            } catch (Exception saveEx) {
                LOG.error("保存失败报告时出错", saveEx);
            }
            return CompletableFuture.completedFuture(report);
        }
    }

    /**
     * 异常检测
     */
    private List<PerformanceAnomaly> detectAnomalies(PerformanceMetrics metrics) {
        List<PerformanceAnomaly> anomalies = new ArrayList<>();
        AiAnalysisProperties.Analysis.Thresholds thresholds = properties.getAnalysis().getThresholds();
        PerformanceMetrics baseline = metrics.getBaselineMetrics();

        // If baseline is not available, fall back to static thresholds
        if (baseline == null || baseline.getTotalRequests() == 0) {
            return detectAnomaliesWithStaticThresholds(metrics, thresholds);
        }

        // Dynamic Threshold: Compare current response time with baseline
        double responseTimeThreshold = baseline.getAvgResponseTime()
                * (1 + thresholds.getResponseTimeDeviationPercent() / 100.0);
        if (metrics.getAvgResponseTime() > responseTimeThreshold) {
            anomalies.add(createAnomaly(
                    PerformanceAnomaly.AnomalyType.APPLICATION_RESPONSE_TIME_HIGH,
                    "响应时间高于基线",
                    String.format("平均响应时间 %.2fms, 高于基线 %.2fms", metrics.getAvgResponseTime(),
                            baseline.getAvgResponseTime()),
                    metrics.getAvgResponseTime(),
                    baseline.getAvgResponseTime()));
        }

        // Dynamic Threshold: Compare current error rate with baseline
        double errorRateThreshold = baseline.getErrorRate() + thresholds.getErrorRateAbsoluteIncreasePercent() / 100.0;
        if (metrics.getErrorRate() > errorRateThreshold) {
            anomalies.add(createAnomaly(
                    PerformanceAnomaly.AnomalyType.APPLICATION_ERROR_RATE_HIGH,
                    "错误率高于基线",
                    String.format("错误率 %.2f%%, 高于基线 %.2f%%", metrics.getErrorRate() * 100,
                            baseline.getErrorRate() * 100),
                    metrics.getErrorRate(),
                    baseline.getErrorRate()));
        }

        return anomalies;
    }

    private List<PerformanceAnomaly> detectAnomaliesWithStaticThresholds(PerformanceMetrics metrics,
            AiAnalysisProperties.Analysis.Thresholds thresholds) {
        List<PerformanceAnomaly> anomalies = new ArrayList<>();
        // (Original static threshold logic can be kept here as a fallback)
        if (metrics.getAvgResponseTime() > thresholds.getResponseTimeMs()) {
            anomalies.add(createAnomaly(
                    PerformanceAnomaly.AnomalyType.APPLICATION_RESPONSE_TIME_HIGH,
                    "响应时间过高 (静态阈值)",
                    String.format("平均响应时间 %.2fms, 超过静态阈值 %.2fms", metrics.getAvgResponseTime(),
                            thresholds.getResponseTimeMs()),
                    metrics.getAvgResponseTime(),
                    thresholds.getResponseTimeMs()));
        }
        return anomalies;
    }

    private PerformanceAnomaly createAnomaly(PerformanceAnomaly.AnomalyType type, String name, String description,
            double actual, double expected) {
        return PerformanceAnomaly.builder()
                .anomalyId(UUID.randomUUID().toString())
                .detectedAt(LocalDateTime.now())
                .type(type)
                .severity(PerformanceAnomaly.Severity.HIGH) // Severity can also be dynamic
                .name(name)
                .description(description)
                .actualValue(actual)
                .expectedValue(expected)
                .deviationPercentage(expected > 0 ? ((actual - expected) / expected) * 100 : 0)
                .affectedComponent("应用服务")
                .build();
    }

    /**
     * 生成基础分析（降级方案）
     */
    private String generateBasicAnalysis(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("## 系统性能分析报告\n\n");

        analysis.append("### 基础性能指标\n");
        analysis.append(String.format("- 总请求数: %d\n", metrics.getTotalRequests()));
        analysis.append(String.format("- 平均响应时间: %.2f ms\n", metrics.getAvgResponseTime()));
        analysis.append(String.format("- 错误率: %.2f%%\n", metrics.getErrorRate() * 100));
        analysis.append(String.format("- 平均吞吐量: %.2f req/s\n", metrics.getAvgThroughput()));

        if (!anomalies.isEmpty()) {
            analysis.append("\n### 检测到的异常\n");
            for (PerformanceAnomaly anomaly : anomalies) {
                analysis.append(String.format("- %s: %s\n", anomaly.getName(), anomaly.getDescription()));
            }
        }

        return analysis.toString();
    }

    /**
     * 生成基础建议（降级方案）
     */
    private List<String> generateBasicSuggestions(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        List<String> suggestions = new ArrayList<>();

        if (metrics.getErrorRate() > 0.05) {
            suggestions.add("建议检查应用日志，分析错误原因");
        }

        if (metrics.getAvgResponseTime() > 1000) {
            suggestions.add("建议优化接口响应时间，检查数据库查询性能");
        }

        if (metrics.getSlowQueries() > 0) {
            suggestions.add("建议优化数据库慢查询，添加适当索引");
        }

        return suggestions;
    }

    /**
     * 生成优化建议
     */
    public List<OptimizationSuggestion> generateOptimizationSuggestions(int timeRangeHours, String service)
            throws Exception {
        LOG.info("开始为服务 {} 生成优化建议，时间范围: {}小时", service, timeRangeHours);
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(timeRangeHours);

            // 1. 收集性能数据 (统一查询)
            PerformanceMetrics metrics = clickHouseRepository.getAggregatedPerformanceMetrics(startTime, endTime,
                    service);
            if (metrics == null || metrics.getTotalRequests() == 0) {
                LOG.warn("未收集到足够的服务 {} 性能数据，无法生成优化建议", service);
                return Collections.emptyList();
            }

            List<PerformanceAnomaly> anomalies = detectAnomalies(metrics);

            // 2. 使用 LLM 生成优化建议
            if (properties.getLlm().isEnabled()) {
                try {
                    return llmService.generateOptimizationSuggestions(metrics, anomalies);
                } catch (Exception e) {
                    LOG.error("LLM生成优化建议失败，使用基础建议", e);
                    return generateBasicOptimizationSuggestions(metrics, anomalies);
                }
            } else {
                return generateBasicOptimizationSuggestions(metrics, anomalies);
            }

        } catch (Exception e) {
            LOG.error("生成优化建议失败", e);
            throw new RuntimeException("优化建议生成失败", e);
        }
    }

    /**
     * 生成基础优化建议
     */
    private List<OptimizationSuggestion> generateBasicOptimizationSuggestions(PerformanceMetrics metrics,
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
            suggestion.setSource("basic-analyzer");
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
            suggestion.setSource("basic-analyzer");
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
            suggestion.setSource("basic-analyzer");
            suggestion.setConfidenceScore(0.6);
            suggestions.add(suggestion);
        }

        return suggestions;
    }

    /**
     * 转换为报告指标
     */
    private PerformanceReport.ReportMetrics convertToReportMetrics(PerformanceMetrics metrics) {
        PerformanceReport.ReportMetrics reportMetrics = new PerformanceReport.ReportMetrics();
        reportMetrics.setAvgResponseTime(metrics.getAvgResponseTime());
        reportMetrics.setAvgThroughput(metrics.getAvgThroughput());
        reportMetrics.setErrorRate(metrics.getErrorRate());
        reportMetrics.setAvgCpuUsage(metrics.getAvgCpuUsage());
        reportMetrics.setAvgMemoryUsage(metrics.getAvgMemoryUsage());
        reportMetrics.setTotalRequests(metrics.getTotalRequests());
        reportMetrics.setTotalErrors(metrics.getFailedRequests());
        return reportMetrics;
    }

    /**
     * 生成报告摘要
     */
    private String generateSummary(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies, int timeRangeMinutes) {
        StringBuilder summary = new StringBuilder();

        summary.append("系统在过去").append(timeRangeMinutes).append("分钟内");
        summary.append("处理了").append(metrics.getTotalRequests()).append("个请求，");
        summary.append("平均响应时间").append(String.format("%.2f", metrics.getAvgResponseTime())).append("ms，");
        summary.append("错误率").append(String.format("%.2f%%", metrics.getErrorRate() * 100)).append("。");

        if (!anomalies.isEmpty()) {
            summary.append("检测到").append(anomalies.size()).append("个性能异常，建议关注。");
        } else {
            summary.append("未检测到明显的性能异常。");
        }

        return summary.toString();
    }

    /**
     * 将报告转换为JSON字符串
     */
    private String convertReportToJson(PerformanceReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            LOG.error("转换报告为JSON失败", e);
            return "{}";
        }
    }

    /**
     * 收集错误堆栈信息
     */
    private List<String> collectErrorStacks(int timeRangeMinutes, String service) {
        List<String> errorStacks = new ArrayList<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(timeRangeMinutes);

        // 按log_stack分组，统计每种堆栈的出现次数
        String sql = "SELECT substr(log_stack, 1, 1500) as stack_prefix, COUNT(*) as error_count " +
                "FROM events " +
                "WHERE is_error=1 AND start_time >= toDateTime(?) AND start_time <= toDateTime(?) " +
                "AND service = ? AND log_stack IS NOT NULL " +
                "GROUP BY substr(log_stack, 1, 1500) " +
                "ORDER BY error_count DESC " +
                "LIMIT 30";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, startTimeStr);
            stmt.setString(2, endTimeStr);
            stmt.setString(3, service);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String stack = rs.getString("stack_prefix");
                    int errorCount = rs.getInt("error_count");
                    if (stack != null && !stack.isEmpty()) {
                        // 添加错误次数信息到堆栈前面
                        String stackWithCount = String.format("【出现%d次】\n%s", errorCount, stack);
                        errorStacks.add(stackWithCount);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("收集 log_stack 失败", e);
        }

        LOG.info("收集到 {} 种不同的错误堆栈", errorStacks.size());
        return errorStacks;
    }
}
