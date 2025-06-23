package com.o11y.ai.service;

import com.o11y.ai.model.PerformanceReport;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 报告存储服务
 */
@Service
public class ReportStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportStorageService.class);

    private final ObjectMapper objectMapper;
    private final String reportsPath = "./reports";

    public ReportStorageService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // 创建报告目录
        try {
            Files.createDirectories(Paths.get(reportsPath));
        } catch (IOException e) {
            LOG.error("创建报告目录失败", e);
        }
    }

    /**
     * 保存报告
     */
    public void saveReport(PerformanceReport report) {
        try {
            String fileName = String.format("report_%s_%s.json",
                    report.getGeneratedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                    report.getReportId().substring(0, 8));

            Path filePath = Paths.get(reportsPath, fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), report);

            LOG.info("报告已保存: {}", filePath);

        } catch (IOException e) {
            LOG.error("保存报告失败: {}", report.getReportId(), e);
        }
    }

    /**
     * 获取最近的报告列表
     */
    public List<PerformanceReport> getRecentReports(int limit) {
        try {
            return Files.list(Paths.get(reportsPath))
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .limit(limit)
                    .map(this::loadReport)
                    .filter(report -> report != null)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            LOG.error("获取报告列表失败", e);
            return List.of();
        }
    }

    /**
     * 根据ID获取报告
     */
    public PerformanceReport getReportById(String reportId) {
        try {
            return Files.list(Paths.get(reportsPath))
                    .filter(path -> path.toString().contains(reportId))
                    .findFirst()
                    .map(this::loadReport)
                    .orElse(null);

        } catch (IOException e) {
            LOG.error("获取报告失败: {}", reportId, e);
            return null;
        }
    }

    /**
     * 加载报告文件
     */
    private PerformanceReport loadReport(Path filePath) {
        try {
            return objectMapper.readValue(filePath.toFile(), PerformanceReport.class);
        } catch (IOException e) {
            LOG.error("加载报告文件失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 清理过期报告
     */
    public void cleanupOldReports(int retentionDays) {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);

            Files.list(Paths.get(reportsPath))
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant()
                                    .isBefore(cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant());
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            LOG.info("已删除过期报告: {}", path);
                        } catch (IOException e) {
                            LOG.error("删除过期报告失败: {}", path, e);
                        }
                    });

        } catch (IOException e) {
            LOG.error("清理过期报告失败", e);
        }
    }
}
