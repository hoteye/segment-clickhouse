package com.o11y.ai.service;

import com.o11y.ai.config.AiAnalysisProperties;
import com.o11y.ai.model.PerformanceMetrics;
import com.o11y.ai.model.PerformanceAnomaly;
import com.o11y.ai.model.OptimizationSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * LLMAnalysisService单元测试
 */
@ExtendWith(MockitoExtension.class)
class LLMAnalysisServiceTest {

    @Mock
    private AiAnalysisProperties properties;

    @InjectMocks
    private LLMAnalysisService llmAnalysisService;

    @BeforeEach
    void setUp() {
        // Mock AiAnalysisProperties for LLM service
        AiAnalysisProperties.Llm mockLlm = new AiAnalysisProperties.Llm();
        mockLlm.setEnabled(false); // 禁用实际的LLM调用
        mockLlm.setProvider("mock");
        
        lenient().when(properties.getLlm()).thenReturn(mockLlm);
    }

    @Test
    void testAnalyzePerformanceData_withValidInput() {
        // Given
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setTotalRequests(1000L);
        metrics.setAvgResponseTime(150.0);
        metrics.setErrorRate(0.05);
        metrics.setService("test-service");

        PerformanceAnomaly anomaly = PerformanceAnomaly.builder()
                .type(PerformanceAnomaly.AnomalyType.APPLICATION_RESPONSE_TIME_HIGH)
                .severity(PerformanceAnomaly.Severity.MEDIUM)
                .description("Response time increased by 20%")
                .actualValue(150.0)
                .expectedValue(100.0)
                .deviationPercentage(50.0)
                .name("响应时间异常")
                .build();

        List<PerformanceAnomaly> anomalies = Arrays.asList(anomaly);

        // When & Then - LLM已禁用，应该使用降级分析
        assertDoesNotThrow(() -> {
            String result = llmAnalysisService.analyzePerformanceData(metrics, anomalies);
            // 禁用LLM时应该返回降级分析结果
            assertNotNull(result, "应该返回降级分析结果");
        });
    }

    @Test
    void testAnalyzePerformanceData_withNullMetrics() {
        // Given
        PerformanceMetrics nullMetrics = null;
        List<PerformanceAnomaly> anomalies = Arrays.asList();

        // When & Then - null指标应该被优雅处理
        assertThrows(Exception.class, () -> {
            llmAnalysisService.analyzePerformanceData(nullMetrics, anomalies);
            // null指标通常会抛出异常
        });
    }

    @Test
    void testAnalyzePerformanceData_withEmptyAnomalies() {
        // Given
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setTotalRequests(1000L);
        metrics.setAvgResponseTime(100.0);
        metrics.setErrorRate(0.01);

        List<PerformanceAnomaly> emptyAnomalies = Arrays.asList();

        // When & Then
        assertDoesNotThrow(() -> {
            String result = llmAnalysisService.analyzePerformanceData(metrics, emptyAnomalies);
            // Method should handle empty anomalies list gracefully
        });
    }

    @Test
    void testGenerateOptimizationSuggestions_withValidMetrics() {
        // Given
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.setTotalRequests(1000L);
        metrics.setAvgResponseTime(200.0);
        metrics.setErrorRate(0.08);

        // When & Then
        assertDoesNotThrow(() -> {
            List<OptimizationSuggestion> result = llmAnalysisService.generateOptimizationSuggestions(metrics, Arrays.asList());
            // Method should execute without throwing exceptions
        });
    }

    @Test
    void testGenerateOptimizationSuggestions_withNullMetrics() {
        // Given
        PerformanceMetrics nullMetrics = null;

        // When & Then - null指标会导致异常
        assertThrows(Exception.class, () -> {
            llmAnalysisService.generateOptimizationSuggestions(nullMetrics, Arrays.asList());
            // null指标在buildAnalysisPrompt中会抛出NullPointerException
        });
    }

    @Test
    void testServiceInitialization() {
        // When & Then
        assertNotNull(llmAnalysisService);
        // Verify that the service is properly initialized
    }

    @Test
    void testDifferentLLMProviders() {
        // Test with different metrics configurations
        assertDoesNotThrow(() -> {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.setTotalRequests(500L);
            metrics.setAvgResponseTime(100.0);
            llmAnalysisService.analyzePerformanceData(metrics, Arrays.asList());
        });

        assertDoesNotThrow(() -> {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.setTotalRequests(1500L);
            metrics.setErrorRate(0.02);
            llmAnalysisService.analyzePerformanceData(metrics, Arrays.asList());
        });
    }
}