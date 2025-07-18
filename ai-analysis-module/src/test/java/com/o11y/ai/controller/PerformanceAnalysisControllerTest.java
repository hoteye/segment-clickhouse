package com.o11y.ai.controller;

import com.o11y.ai.service.PerformanceAnalysisService;
import com.o11y.ai.service.ReportStorageService;
import com.o11y.ai.service.LLMAnalysisService;
import com.o11y.ai.repository.ClickHouseRepository;
import com.o11y.ai.model.PerformanceReport;
import com.o11y.ai.model.OptimizationSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PerformanceAnalysisController的Spring Boot测试
 */
@WebMvcTest(PerformanceAnalysisController.class)
class PerformanceAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PerformanceAnalysisService analysisService;

    @MockBean
    private ReportStorageService reportService;

    @MockBean
    private LLMAnalysisService llmAnalysisService;

    @MockBean
    private ClickHouseRepository clickHouseRepository;

    @Test
    void testGenerateReport_withValidParameters() throws Exception {
        // Given
        PerformanceReport mockReport = PerformanceReport.builder()
                .reportId("test-report-123")
                .generatedAt(LocalDateTime.now())
                .timeRange(24)
                .summary("Test summary")
                .intelligentAnalysis("Test analysis")
                .build();

        when(analysisService.generateAnalysisReport(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(mockReport));

        // When & Then
        mockMvc.perform(post("/api/reports/generate")
                .param("timeRangeMinutes", "1440")
                .param("service", "test-service"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportId").exists())
                .andExpect(jsonPath("$.message").exists());

        verify(analysisService).generateAnalysisReport(1440, "test-service");
    }

    @Test
    void testGenerateReport_withDefaultParameters() throws Exception {
        // Given
        PerformanceReport mockReport = PerformanceReport.builder()
                .reportId("default-report")
                .generatedAt(LocalDateTime.now())
                .timeRange(1)
                .build();

        when(analysisService.generateAnalysisReport(anyInt(), any())).thenReturn(CompletableFuture.completedFuture(mockReport));

        // When & Then
        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportId").exists());

        verify(analysisService).generateAnalysisReport(eq(60), any());
    }

    @Test
    void testGenerateReport_whenServiceReturnsNull() throws Exception {
        // Given
        when(analysisService.generateAnalysisReport(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(post("/api/reports/generate")
                .param("timeRangeMinutes", "1440")
                .param("service", "test-service"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportId").exists());

        verify(analysisService).generateAnalysisReport(1440, "test-service");
    }

    @Test
    void testGenerateOptimizationSuggestions() throws Exception {
        // Given
        OptimizationSuggestion suggestion = new OptimizationSuggestion();
        suggestion.setTitle("Optimize database queries");
        suggestion.setDescription("Add database indexes");
        suggestion.setPriority("HIGH");
        suggestion.setCategory("Database");
        suggestion.setImpactLevel("HIGH");
        suggestion.setImplementationComplexity("MEDIUM");
        
        List<OptimizationSuggestion> mockSuggestions = Arrays.asList(suggestion);

        when(analysisService.generateOptimizationSuggestions(anyInt(), anyString()))
                .thenReturn(mockSuggestions);

        // When & Then
        mockMvc.perform(post("/api/suggestions")
                .param("timeRangeMinutes", "12")
                .param("service", "test-service"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.suggestions[0].title").value("Optimize database queries"))
                .andExpect(jsonPath("$.total").value(1));

        verify(analysisService).generateOptimizationSuggestions(12, "test-service");
    }

    @Test
    void testHealthCheck() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testLLMHealthCheck() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/llm/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetReports() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetReportById() throws Exception {
        // Given
        String reportId = "test-report-123";
        PerformanceReport mockReport = PerformanceReport.builder()
                .reportId(reportId)
                .generatedAt(LocalDateTime.now())
                .timeRange(24)
                .summary("Mock report")
                .build();

        when(reportService.getReportById(reportId)).thenReturn(mockReport);

        // When & Then
        mockMvc.perform(get("/api/reports/{reportId}", reportId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reportId").value(reportId));
    }

    @Test
    void testTriggerAnalysis() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/analysis/trigger"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetErrorTraces() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/traces/errors")
                .param("timeRangeMinutes", "24")
                .param("service", "test-service"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetSlowTraces() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/traces/slow")
                .param("timeRangeMinutes", "24")
                .param("service", "test-service")
                .param("thresholdMs", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetTopologyServices() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/topology/services")
                .param("timeRangeMinutes", "24"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetSampleEvents() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/data/events/sample"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void testGetEventsSchema() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/data/events/schema"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}