package com.o11y.ai.service;

import com.o11y.ai.config.AiAnalysisProperties;
import com.o11y.ai.model.PerformanceMetrics;
import com.o11y.ai.model.PerformanceReport;
import com.o11y.ai.model.OptimizationSuggestion;
import com.o11y.ai.repository.ClickHouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PerformanceAnalysisService 单元测试 (重构后)
 */
@ExtendWith(MockitoExtension.class)
class PerformanceAnalysisServiceTest {

    @Mock
    private AiAnalysisProperties properties;

    @Mock
    private LLMAnalysisService llmService;

    @Mock
    private ReportStorageService reportService;

    @Mock
    private ClickHouseRepository clickHouseRepository;

    @InjectMocks
    private PerformanceAnalysisService performanceAnalysisService;

    @BeforeEach
    void setUp() {
        // Mock AiAnalysisProperties and LLM properties
        AiAnalysisProperties.Analysis analysis = new AiAnalysisProperties.Analysis();
        analysis.setEnabled(true);
        AiAnalysisProperties.Analysis.Window window = new AiAnalysisProperties.Analysis.Window();
        window.setHours(1);
        analysis.setWindow(window);
        AiAnalysisProperties.Analysis.Thresholds thresholds = new AiAnalysisProperties.Analysis.Thresholds();
        analysis.setThresholds(thresholds);
        when(properties.getAnalysis()).thenReturn(analysis);

        AiAnalysisProperties.Llm llm = new AiAnalysisProperties.Llm();
        llm.setEnabled(false); // 禁用LLM以简化测试
        when(properties.getLlm()).thenReturn(llm);
    }

    private PerformanceMetrics createMockMetrics(boolean withData, boolean withBaseline) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        if (withData) {
            metrics.setTotalRequests(1000L);
            metrics.setAvgResponseTime(150.0);
            metrics.setErrorRate(0.05);
        } else {
            metrics.setTotalRequests(0L);
        }

        if (withBaseline) {
            PerformanceMetrics baseline = new PerformanceMetrics();
            baseline.setTotalRequests(800L);
            baseline.setAvgResponseTime(100.0);
            baseline.setErrorRate(0.02);
            metrics.setBaselineMetrics(baseline);
        }
        return metrics;
    }

    @Test
    void testGenerateAnalysisReport_withDataAndBaseline() {
        // Given
        int timeRangeHours = 1;
        String service = "test-service";
        PerformanceMetrics mockMetrics = createMockMetrics(true, true);
        when(clickHouseRepository.getAggregatedPerformanceMetrics(any(), any(), eq(service))).thenReturn(mockMetrics);
        when(llmService.analyzePerformanceData(any(), any(), any())).thenReturn("Mocked AI Analysis");

        // When
        CompletableFuture<PerformanceReport> futureReport = performanceAnalysisService.generateAnalysisReport(timeRangeHours, service);
        PerformanceReport report = futureReport.join();

        // Then
        assertNotNull(report);
        assertEquals(1, report.getAnomalies().size()); // Should detect response time anomaly
        verify(clickHouseRepository, times(1)).getAggregatedPerformanceMetrics(any(), any(), eq(service));
        verify(reportService, times(1)).saveReport(any(PerformanceReport.class));
        verify(clickHouseRepository, times(1)).savePerformanceReport(anyString(), anyString(), any());
    }

    @Test
    void testGenerateAnalysisReport_withNoData() {
        // Given
        int timeRangeHours = 1;
        String service = "test-service";
        PerformanceMetrics mockMetrics = createMockMetrics(false, false);
        when(clickHouseRepository.getAggregatedPerformanceMetrics(any(), any(), eq(service))).thenReturn(mockMetrics);

        // When
        CompletableFuture<PerformanceReport> futureReport = performanceAnalysisService.generateAnalysisReport(timeRangeHours, service);
        PerformanceReport report = futureReport.join();

        // Then
        assertNull(report, "Report should be null when there is no data");
        verify(clickHouseRepository, times(1)).getAggregatedPerformanceMetrics(any(), any(), eq(service));
        verify(reportService, never()).saveReport(any());
    }

    @Test
    void testGenerateAnalysisReport_withNullMetrics() {
        // Given
        int timeRangeHours = 1;
        String service = "test-service";
        when(clickHouseRepository.getAggregatedPerformanceMetrics(any(), any(), eq(service))).thenReturn(null);

        // When
        CompletableFuture<PerformanceReport> futureReport = performanceAnalysisService.generateAnalysisReport(timeRangeHours, service);
        PerformanceReport report = futureReport.join();

        // Then
        assertNull(report, "Report should be null when metrics are null");
    }

    @Test
    void testGenerateOptimizationSuggestions_usesNewMetricsCollection() throws Exception {
        // Given
        int timeRangeHours = 1;
        String service = "test-service";
        PerformanceMetrics mockMetrics = createMockMetrics(true, true);
        when(clickHouseRepository.getAggregatedPerformanceMetrics(any(), any(), eq(service))).thenReturn(mockMetrics);
        when(llmService.generateOptimizationSuggestions(any(), any())).thenReturn(Collections.singletonList(new OptimizationSuggestion()));

        // When
        List<OptimizationSuggestion> suggestions = performanceAnalysisService.generateOptimizationSuggestions(timeRangeHours, service);

        // Then
        assertNotNull(suggestions);
        verify(clickHouseRepository, times(1)).getAggregatedPerformanceMetrics(any(), any(), eq(service));
    }
}
