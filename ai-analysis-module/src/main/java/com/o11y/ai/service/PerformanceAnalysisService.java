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

            // 获取所有服务列表
            List<String> services = getAllServices(timeRangeHours);
            if (services.isEmpty()) {
                LOG.info("未找到服务，跳过生成报告");
                return;
            }

            for (String service : services) {
                LOG.info("开始为服务 {} 生成性能报告", service);
                PerformanceReport report = generateAnalysisReport(timeRangeHours, service);
                if (report != null) {
                    reportService.saveReport(report);
                    LOG.info("服务 {} 性能报告生成完成，报告ID: {}", service, report.getReportId());
                }
            }
            LOG.info("所有服务性能报告生成完成");

        } catch (Exception e) {
            LOG.error("定时性能分析失败", e);
        }
    }

    /**
     * 获取所有服务名称列表
     */
    private List<String> getAllServices(int timeRangeHours) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(timeRangeHours);
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
     * 生成性能分析报告
     */
    public PerformanceReport generateAnalysisReport(int timeRangeHours, String service) {
        long startTime = System.currentTimeMillis();
        LOG.info("=== 开始生成性能分析报告 ===");
        LOG.info("开始时间: {}", java.time.LocalDateTime.now());
        LOG.info("参数: 时间范围={}小时, 服务={}", timeRangeHours, service);

        try {

            // 1. 收集性能数据
            LOG.info("步骤1: 开始收集性能数据...");
            PerformanceMetrics metrics = collectPerformanceMetrics(timeRangeHours, service);
            if (metrics == null) {
                LOG.warn("未收集到性能数据");
                return null;
            }
            LOG.info("步骤1完成: 成功收集性能数据，总请求数: {}, 平均响应时间: {}ms",
                    metrics.getTotalRequests(), metrics.getAvgResponseTime());

            // 1.1 收集错误堆栈
            List<String> errorStacks = collectErrorStacks(timeRangeHours);
            LOG.info("步骤1.1: 收集到 {} 条错误堆栈", errorStacks.size());

            // 2. 异常检测
            LOG.info("步骤2: 开始异常检测...");
            List<PerformanceAnomaly> anomalies = detectAnomalies(metrics);
            LOG.info("步骤2完成: 检测到 {} 个异常", anomalies.size());

            // 3. 生成报告
            LOG.info("步骤3: 创建报告对象...");
            PerformanceReport report = PerformanceReport.builder()
                    .reportId(UUID.randomUUID().toString()).generatedAt(LocalDateTime.now())
                    .timeRange(timeRangeHours)
                    .build();
            LOG.info("步骤3完成: 报告对象创建完成，报告ID: {}", report.getReportId());

            // 4. LLM 智能分析
            LOG.info("步骤4: 开始LLM智能分析...");
            LOG.info("LLM配置状态 - 启用: {}, 提供商: {}",
                    properties.getLlm().isEnabled(),
                    properties.getLlm().getProvider());
            if (properties.getLlm().isEnabled()) {
                try {
                    LOG.info("步骤4a: 调用LLM分析性能数据和错误堆栈...");
                    String intelligentAnalysis = llmService.analyzePerformanceData(metrics, anomalies, errorStacks);
                    LOG.info("步骤4a完成: LLM分析完成，分析长度: {} 字符", intelligentAnalysis.length());
                    report.setIntelligentAnalysis(intelligentAnalysis);
                    report.setErrorStacks(errorStacks);

                    LOG.info("步骤4b: 调用LLM生成优化建议...");
                    List<OptimizationSuggestion> suggestions = llmService.generateOptimizationSuggestions(metrics,
                            anomalies);
                    LOG.info("步骤4b完成: LLM生成了 {} 条优化建议", suggestions.size());
                    report.setOptimizationSuggestions(suggestions.stream()
                            .map(s -> s.getTitle() + ": " + s.getDescription())
                            .collect(java.util.stream.Collectors.toList()));

                } catch (Exception e) {
                    LOG.error("LLM分析失败，使用基础分析", e);
                    LOG.info("步骤4备用: 使用基础分析...");
                    report.setIntelligentAnalysis(generateBasicAnalysis(metrics, anomalies));
                    report.setOptimizationSuggestions(generateBasicSuggestions(metrics, anomalies));
                    report.setErrorStacks(errorStacks);
                    LOG.info("步骤4备用完成: 基础分析已完成");
                }
            } else {
                LOG.info("步骤4跳过: LLM已禁用，使用基础分析...");
                report.setIntelligentAnalysis(generateBasicAnalysis(metrics, anomalies));
                report.setOptimizationSuggestions(generateBasicSuggestions(metrics, anomalies));
                report.setErrorStacks(errorStacks);
                LOG.info("步骤4完成: 基础分析已完成");
            } // 5. 设置其他报告内容
            LOG.info("步骤5: 设置报告其他内容...");
            report.setMetrics(convertToReportMetrics(metrics));
            report.setAnomalies(anomalies);
            report.setSummary(generateSummary(metrics, anomalies));
            LOG.info("步骤5完成: 报告内容设置完成");

            // 6. 保存报告
            LOG.info("步骤6: 保存报告到文件系统...");
            try {
                reportService.saveReport(report);
                LOG.info("步骤6a完成: 性能分析报告已保存到文件系统");
            } catch (Exception e) {
                LOG.error("步骤6a失败: 保存报告到文件系统失败", e);
            }

            // 7. 保存到 ClickHouse
            LOG.info("步骤7: 保存报告到 ClickHouse...");
            try {
                String reportContent = convertReportToJson(report);
                clickHouseRepository.savePerformanceReport(report.getReportId(), reportContent,
                        report.getGeneratedAt());
                LOG.info("步骤7完成: 性能分析报告已保存到 ClickHouse");
            } catch (Exception e) {
                LOG.error("步骤7失败: 保存报告到 ClickHouse 失败", e);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOG.info("=== 性能分析报告生成完成 ===");
            LOG.info("结束时间: {}", java.time.LocalDateTime.now());
            LOG.info("总耗时: {}ms ({}秒)", duration, duration / 1000.0);
            LOG.info("报告ID: {}", report.getReportId());
            return report;

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOG.error("=== 性能分析报告生成失败 ===");
            LOG.error("结束时间: {}", java.time.LocalDateTime.now());
            LOG.error("失败耗时: {}ms ({}秒)", duration, duration / 1000.0);
            LOG.error("生成性能分析报告失败", e);
            throw new RuntimeException("性能分析报告生成失败", e);
        }
    }

    /**
     * 收集性能指标数据
     */
    private PerformanceMetrics collectPerformanceMetrics(int timeRangeHours, String service) throws Exception {
        LOG.info("--- 开始收集性能指标数据 ---");
        PerformanceMetrics metrics = new PerformanceMetrics();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(timeRangeHours);

        LOG.info("时间范围: {} 到 {}", startTime, endTime);

        metrics.setStartTime(startTime);
        metrics.setEndTime(endTime);
        metrics.setTimeRangeHours(timeRangeHours);
        metrics.setService(service);
        try (Connection conn = dataSource.getConnection()) {
            LOG.info("数据库连接已建立");

            // 收集基础应用指标
            LOG.info("收集应用性能指标...");
            collectApplicationMetrics(conn, metrics, startTime, endTime);
            LOG.info("应用指标收集完成");

            // 收集 JVM 指标（如果有的话）
            LOG.info("收集JVM指标...");
            collectJvmMetrics(conn, metrics, startTime, endTime);
            LOG.info("JVM指标收集完成");

            // 收集数据库指标
            LOG.info("收集数据库指标...");
            collectDatabaseMetrics(conn, metrics, startTime, endTime);
            LOG.info("数据库指标收集完成");

            // 收集系统指标
            LOG.info("收集系统指标...");
            collectSystemMetrics(conn, metrics, startTime, endTime);
            LOG.info("系统指标收集完成");
        }

        LOG.info("--- 性能指标数据收集完成 ---");
        return metrics;
    }

    /**
     * 收集应用性能指标
     */
    private void collectApplicationMetrics(Connection conn, PerformanceMetrics metrics,
            LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        LOG.info("执行应用指标查询...");
        String service = metrics.getService();
        String sql = "SELECT " +
                "COUNT(*) as total_requests, " +
                "AVG(end_time - start_time) * 1000 as avg_response_time, " +
                "MAX(end_time - start_time) * 1000 as max_response_time, " +
                "SUM(CASE WHEN is_error = 1 THEN 1 ELSE 0 END) as failed_requests, " +
                "COUNT(*) / (toUnixTimestamp(toDateTime(?, 'Asia/Shanghai')) - toUnixTimestamp(toDateTime(?, 'Asia/Shanghai'))) as avg_throughput "
                +
                "FROM events " +
                "WHERE start_time >= toDateTime(?, 'Asia/Shanghai') AND start_time <= toDateTime(?, 'Asia/Shanghai') AND service = ? ";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 明确指定 Asia/Shanghai 时区，格式化时间为 ClickHouse 兼容格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String endTimeStr = endTime.atZone(java.time.ZoneId.systemDefault())
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).format(formatter);
            String startTimeStr = startTime.atZone(java.time.ZoneId.systemDefault())
                    .withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).format(formatter);
            LOG.info("sql: {}, startTimeStr : {}, endTimeStr : {}, service : {}", sql, startTimeStr, endTimeStr,
                    service);
            LOG.info("查询参数: startTime={}, endTime={}, service={}", startTimeStr, endTimeStr, service);

            stmt.setString(1, endTimeStr);
            stmt.setString(2, startTimeStr);
            stmt.setString(3, startTimeStr);
            stmt.setString(4, endTimeStr);
            stmt.setString(5, service);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long totalRequests = rs.getLong("total_requests");
                    double avgResponseTime = rs.getDouble("avg_response_time");
                    double maxResponseTime = rs.getDouble("max_response_time");
                    long failedRequests = rs.getLong("failed_requests");
                    double avgThroughput = rs.getDouble("avg_throughput");

                    LOG.info("服务{}的查询结果: 总请求数={}, 平均响应时间={}ms, 最大响应时间={}ms, 失败请求数={}, 平均吞吐量={}",
                            service, totalRequests, avgResponseTime, maxResponseTime, failedRequests, avgThroughput);

                    metrics.setTotalRequests(totalRequests);
                    metrics.setAvgResponseTime(avgResponseTime);
                    metrics.setMaxResponseTime(maxResponseTime);
                    metrics.setFailedRequests(failedRequests);
                    metrics.setAvgThroughput(avgThroughput);

                    if (metrics.getTotalRequests() > 0) {
                        double errorRate = metrics.getFailedRequests() / (double) metrics.getTotalRequests();
                        metrics.setErrorRate(errorRate);
                        LOG.info("计算得出错误率: {}%", errorRate * 100);
                    }
                } else {
                    LOG.warn("应用指标查询没有返回结果");
                }
            }
        } catch (Exception e) {
            LOG.error("执行应用指标查询失败", e);
            throw e;
        }
    }

    /**
     * 收集 JVM 指标（从 events 表的 tag_jvm_ 和 tag_thread_ 相关字段）
     */
    private void collectJvmMetrics(Connection conn, PerformanceMetrics metrics,
            LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        String service = metrics.getService();
        String sql = "SELECT " +
                "MAX(tag_jvm_heap_used_type_Int64 / tag_jvm_heap_max_type_Int64) as max_heap_used_ratio, " +
                "AVG(tag_jvm_heap_used_type_Int64) as avg_heap_used, " +
                "MAX(tag_jvm_heap_used_type_Int64) as max_heap_used, " +
                "AVG(tag_jvm_nonheap_used_type_Int64) as avg_nonheap_used, " +
                "MAX(tag_jvm_nonheap_used_type_Int64) as max_nonheap_used, " +
                "AVG(tag_thread_count_type_Int64) as avg_thread_count, " +
                "MAX(tag_thread_peak_count_type_Int64) as max_thread_peak_count, " +
                "AVG(tag_thread_daemon_count_type_Int64) as avg_thread_daemon_count, " +
                "MAX(tag_thread_total_started_count_type_Int64) as max_thread_total_started_count, " +
                "AVG(tag_jvm_heap_committed_type_Int64) as avg_heap_committed, " +
                "AVG(tag_jvm_heap_init_type_Int64) as avg_heap_init, " +
                "AVG(tag_jvm_heap_max_type_Int64) as avg_heap_max, " +
                "AVG(tag_jvm_nonheap_committed_type_Int64) as avg_nonheap_committed, " +
                "AVG(tag_jvm_nonheap_init_type_Int64) as avg_nonheap_init, " +
                "AVG(tag_jvm_nonheap_max_type_Int64) as avg_nonheap_max " +
                "FROM events " +
                "WHERE start_time >= toDateTime(?) AND start_time <= toDateTime(?) AND service = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, startTimeStr);
            stmt.setString(2, endTimeStr);
            if (service != null)
                stmt.setString(3, service);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    metrics.setMaxHeapUsedRatio(rs.getDouble("max_heap_used_ratio"));
                    metrics.setAvgHeapUsed(rs.getDouble("avg_heap_used"));
                    metrics.setMaxHeapUsed(rs.getDouble("max_heap_used"));
                    metrics.setAvgNonHeapUsed(rs.getDouble("avg_nonheap_used"));
                    metrics.setMaxNonHeapUsed(rs.getDouble("max_nonheap_used"));
                    metrics.setAvgThreadCount(rs.getInt("avg_thread_count"));
                    metrics.setMaxThreadPeakCount(rs.getInt("max_thread_peak_count"));
                    metrics.setThreadDaemonCount(rs.getInt("avg_thread_daemon_count"));
                    metrics.setThreadTotalStartedCount(rs.getInt("max_thread_total_started_count"));
                    metrics.setHeapCommitted((long) rs.getDouble("avg_heap_committed"));
                    metrics.setHeapInit((long) rs.getDouble("avg_heap_init"));
                    metrics.setHeapMax((long) rs.getDouble("avg_heap_max"));
                    metrics.setNonHeapCommitted((long) rs.getDouble("avg_nonheap_committed"));
                    metrics.setNonHeapInit((long) rs.getDouble("avg_nonheap_init"));
                    metrics.setNonHeapMax((long) rs.getDouble("avg_nonheap_max"));
                    LOG.info("JVM指标收集完成: 最大堆内存使用率={} %, 平均堆内存={} Mb, 最大堆内存={} Mb, " +
                            "平均非堆内存={} Mb, 最大非堆内存={} Mb, 平均线程数={}, 最大线程峰值数={}, " +
                            "守护线程数={}, 总线程启动数={}, 堆已提交={} Mb, 堆初始大小={} Mb, 堆最大大小={} Mb, " +
                            "非堆已提交={} Mb, 非堆初始大小={} Mb, 非堆最大大小={} Mb",
                            metrics.getMaxHeapUsedRatio() * 100, metrics.getAvgHeapUsed() / 1024 / 1024,
                            metrics.getMaxHeapUsed() / 1024 / 1024,
                            metrics.getAvgNonHeapUsed() / 1024 / 1024, metrics.getMaxNonHeapUsed() / 1024 / 1024,
                            metrics.getAvgThreadCount(), metrics.getMaxThreadPeakCount(),
                            metrics.getThreadDaemonCount(), metrics.getThreadTotalStartedCount(),
                            metrics.getHeapCommitted() / 1024 / 1024, metrics.getHeapInit() / 1024 / 1024,
                            metrics.getHeapMax() / 1024 / 1024,
                            metrics.getNonHeapCommitted() / 1024 / 1024, metrics.getNonHeapInit() / 1024 / 1024,
                            metrics.getNonHeapMax() / 1024 / 1024);
                }
            }
        } catch (Exception e) {
            LOG.warn("收集JVM指标失败，使用默认值", e);
            metrics.setMaxHeapUsedRatio(0.8); // 80%
            metrics.setAvgHeapUsed(512 * 1024 * 1024); // 512MB
            metrics.setMaxHeapUsed(1024 * 1024 * 1024); // 1GB
            metrics.setAvgNonHeapUsed(128 * 1024 * 1024); // 128MB
            metrics.setMaxNonHeapUsed(256 * 1024 * 1024); // 256MB
            metrics.setAvgThreadCount(100);
            metrics.setMaxThreadPeakCount(120);
            metrics.setThreadDaemonCount(80);
            metrics.setThreadTotalStartedCount(200);
            metrics.setHeapCommitted(1024 * 1024 * 1024);
            metrics.setHeapInit(512 * 1024 * 1024);
            metrics.setHeapMax(2048 * 1024 * 1024);
            metrics.setNonHeapCommitted(256 * 1024 * 1024);
            metrics.setNonHeapInit(128 * 1024 * 1024);
            metrics.setNonHeapMax(512 * 1024 * 1024);
        }
    }

    /**
     * 收集数据库指标
     */
    private void collectDatabaseMetrics(Connection conn, PerformanceMetrics metrics,
            LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        String service = metrics.getService();
        String sql = "SELECT " +
                "COUNT(*) as total_queries, " +
                "AVG(end_time - start_time) * 1000 as avg_query_duration, " +
                "SUM(CASE WHEN (end_time - start_time) * 1000 > 1000 THEN 1 ELSE 0 END) as slow_queries " +
                "FROM events " +
                "WHERE start_time >= ? AND start_time <= ? " +
                "AND span_layer = 'Database' AND service = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, startTime);
            stmt.setObject(2, endTime);
            stmt.setString(3, service);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    metrics.setTotalQueries(rs.getLong("total_queries"));
                    metrics.setAvgQueryDuration(rs.getDouble("avg_query_duration"));
                    metrics.setSlowQueries(rs.getLong("slow_queries"));
                }
            }
        }

        // 设置默认连接数
        metrics.setAvgActiveConnections(5);
    }

    /**
     * 收集系统指标（从 events 表的系统相关字段）
     */
    private void collectSystemMetrics(Connection conn, PerformanceMetrics metrics,
            LocalDateTime startTime, LocalDateTime endTime) throws Exception {
        String service = metrics.getService();
        String sql = "SELECT " +
                "AVG(tag_Available_Memory_type_Int64) as avg_available_memory, " +
                "AVG(tag_Total_Memory_type_Int64) as avg_total_memory, " +
                "any(tag_Processor_Name) as processor_name, " +
                "any(tag_os_arch) as os_arch, " +
                "any(tag_os_name) as os_name, " +
                "any(tag_os_version) as os_version " +
                "FROM events " +
                "WHERE start_time >= toDateTime(?) AND start_time <= toDateTime(?) AND service = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, startTimeStr);
            stmt.setString(2, endTimeStr);
            stmt.setString(3, service);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long availableMemory = rs.getLong("avg_available_memory");
                    long totalMemory = rs.getLong("avg_total_memory");
                    String processorName = rs.getString("processor_name");
                    String osArch = rs.getString("os_arch");
                    String osName = rs.getString("os_name");
                    String osVersion = rs.getString("os_version");
                    // 这里可将采集到的系统指标存入 metrics 的自定义字段
                    Map<String, Object> custom = metrics.getCustomMetrics();
                    if (custom == null)
                        custom = new HashMap<>();
                    custom.put("availableMemory", availableMemory);
                    custom.put("totalMemory", totalMemory);
                    custom.put("processorName", processorName);
                    custom.put("osArch", osArch);
                    custom.put("osName", osName);
                    custom.put("osVersion", osVersion);
                    metrics.setCustomMetrics(custom);
                    // 也可根据需要设置 avgMemoryUsage、avgSystemCpuUsage 等
                    if (totalMemory > 0) {
                        metrics.setAvgMemoryUsage(1.0 - (availableMemory * 1.0 / totalMemory));
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("收集系统指标失败，使用默认值", e);
            metrics.setAvgSystemCpuUsage(0.6); // 60%
            metrics.setAvgMemoryUsage(0.7); // 70%
            metrics.setAvgDiskUsage(0.8); // 80%
        }
    }

    /**
     * 异常检测
     */
    private List<PerformanceAnomaly> detectAnomalies(PerformanceMetrics metrics) {
        List<PerformanceAnomaly> anomalies = new ArrayList<>();
        AiAnalysisProperties.Analysis.Thresholds thresholds = properties.getAnalysis().getThresholds();

        // 响应时间异常检测
        if (metrics.getAvgResponseTime() > thresholds.getResponseTimeMs()) {
            PerformanceAnomaly anomaly = new PerformanceAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setType(PerformanceAnomaly.AnomalyType.APPLICATION_RESPONSE_TIME_HIGH);
            anomaly.setSeverity(PerformanceAnomaly.Severity.HIGH);
            anomaly.setName("响应时间过高");
            anomaly.setDescription("平均响应时间超过阈值");
            anomaly.setActualValue(metrics.getAvgResponseTime());
            anomaly.setExpectedValue(thresholds.getResponseTimeMs());
            anomaly.setDeviationPercentage((metrics.getAvgResponseTime() - thresholds.getResponseTimeMs())
                    / thresholds.getResponseTimeMs() * 100);
            anomaly.setAffectedComponent("应用服务");
            anomalies.add(anomaly);
        }

        // 错误率异常检测
        if (metrics.getErrorRate() * 100 > thresholds.getErrorRatePercent()) {
            PerformanceAnomaly anomaly = new PerformanceAnomaly();
            anomaly.setAnomalyId(UUID.randomUUID().toString());
            anomaly.setDetectedAt(LocalDateTime.now());
            anomaly.setType(PerformanceAnomaly.AnomalyType.APPLICATION_ERROR_RATE_HIGH);
            anomaly.setSeverity(PerformanceAnomaly.Severity.CRITICAL);
            anomaly.setName("错误率过高");
            anomaly.setDescription("应用错误率超过阈值");
            anomaly.setActualValue(metrics.getErrorRate() * 100);
            anomaly.setExpectedValue(thresholds.getErrorRatePercent());
            anomaly.setDeviationPercentage((metrics.getErrorRate() * 100 - thresholds.getErrorRatePercent())
                    / thresholds.getErrorRatePercent() * 100);
            anomaly.setAffectedComponent("应用服务");
            anomalies.add(anomaly);
        }

        // 可以添加更多异常检测逻辑...

        return anomalies;
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
    public List<OptimizationSuggestion> generateOptimizationSuggestions(int timeRangeHoursm, String service)
            throws Exception {
        LOG.info("开始为服务 {} 生成优化建议，时间范围: {}小时", service, timeRangeHoursm);
        try {
            // 1. 收集性能数据
            PerformanceMetrics metrics = collectPerformanceMetrics(timeRangeHoursm, service);
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
    private String generateSummary(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
        StringBuilder summary = new StringBuilder();

        summary.append("系统在过去").append(metrics.getTimeRangeHours()).append("小时内");
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
    private List<String> collectErrorStacks(int timeRangeHours) {
        List<String> errorStacks = new ArrayList<>();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(timeRangeHours);
        String sql = "SELECT log_stack FROM events WHERE is_error=1 AND start_time >= toDateTime(?) AND start_time <= toDateTime(?) AND log_stack IS NOT NULL LIMIT 40";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            String startTimeStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String endTimeStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, startTimeStr);
            stmt.setString(2, endTimeStr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String stack = rs.getString("log_stack");
                    if (stack != null && !stack.isEmpty()) {
                        if (stack.length() > 2000) {
                            stack = stack.substring(0, 3000) + "... [truncated]";
                        }
                        errorStacks.add(stack);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("收集 log_stack 失败", e);
        }
        return errorStacks;
    }
}
