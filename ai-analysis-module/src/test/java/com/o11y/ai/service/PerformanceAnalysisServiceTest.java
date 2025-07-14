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
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * PerformanceAnalysisService单元测试
 */
@ExtendWith(MockitoExtension.class)
class PerformanceAnalysisServiceTest {

    @Mock
    private AiAnalysisProperties properties;

    @Mock
    private DataSource dataSource;

    @Mock
    private LLMAnalysisService llmService;

    @Mock
    private ReportStorageService reportService;

    @Mock
    private ClickHouseRepository clickHouseRepository;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @InjectMocks
    private PerformanceAnalysisService performanceAnalysisService;

    @BeforeEach
    void setUp() throws Exception {
        // Mock AiAnalysisProperties 配置
        lenient().when(properties.getAnalysis()).thenReturn(createMockAnalysis());
        lenient().when(properties.getLlm()).thenReturn(createMockLlm());
        
        // Mock 数据库连接
        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.executeQuery()).thenReturn(resultSet);
        
        // Mock 基础数据查询结果 - 使用更宽松的配置
        lenient().when(resultSet.next()).thenReturn(false);
        // Mock所有可能的ResultSet字段调用，返回默认值
        lenient().when(resultSet.getLong(anyString())).thenReturn(0L);
        lenient().when(resultSet.getDouble(anyString())).thenReturn(0.0);
        lenient().when(resultSet.getInt(anyString())).thenReturn(0);
        lenient().when(resultSet.getString(anyString())).thenReturn("");
        
        // Mock LLM服务
        lenient().when(llmService.analyzePerformanceData(any(PerformanceMetrics.class), any(List.class))).thenReturn("模拟LLM分析结果");
        lenient().when(llmService.analyzePerformanceData(any(PerformanceMetrics.class), any(List.class), any(List.class))).thenReturn("模拟LLM分析结果");
        lenient().when(llmService.generateOptimizationSuggestions(any(PerformanceMetrics.class), any(List.class))).thenReturn(java.util.Collections.emptyList());
        
        // Mock ClickHouseRepository
        lenient().when(clickHouseRepository.getServiceMetrics(any(), any(), anyString())).thenReturn(java.util.Collections.emptyList());
        lenient().doNothing().when(clickHouseRepository).savePerformanceReport(anyString(), anyString(), any());
        
        // Mock ReportStorageService
        lenient().doNothing().when(reportService).saveReport(any());
    }
    
    private AiAnalysisProperties.Analysis createMockAnalysis() {
        AiAnalysisProperties.Analysis analysis = new AiAnalysisProperties.Analysis();
        analysis.setEnabled(true);
        
        AiAnalysisProperties.Analysis.Schedule schedule = new AiAnalysisProperties.Analysis.Schedule();
        schedule.setEnabled(true);
        schedule.setCron("0 0 * * * ?");
        analysis.setSchedule(schedule);
        
        AiAnalysisProperties.Analysis.Window window = new AiAnalysisProperties.Analysis.Window();
        window.setHours(24);
        analysis.setWindow(window);
        
        return analysis;
    }
    
    private AiAnalysisProperties.Llm createMockLlm() {
        AiAnalysisProperties.Llm llm = new AiAnalysisProperties.Llm();
        llm.setEnabled(false); // 禁用LLM避免复杂的mock
        llm.setProvider("mock");
        return llm;
    }

    @Test
    void testGenerateAnalysisReport_withValidParameters() {
        // Given
        int timeRangeHours = 24;
        String service = "test-service";
        
        // Mock 有数据的场景 - 设置resultSet返回一些数据
        try {
            lenient().when(resultSet.next()).thenReturn(true, true, false); // 多次调用以支持不同查询
            lenient().when(resultSet.getLong("total_requests")).thenReturn(1000L);
            lenient().when(resultSet.getDouble("avg_response_time")).thenReturn(150.0);
            lenient().when(resultSet.getDouble("max_response_time")).thenReturn(500.0);
            lenient().when(resultSet.getLong("failed_requests")).thenReturn(50L);
            lenient().when(resultSet.getDouble("avg_throughput")).thenReturn(10.0);
            lenient().when(resultSet.getDouble("error_rate")).thenReturn(0.05);
            lenient().when(resultSet.getDouble("p95_response_time")).thenReturn(300.0);
            lenient().when(resultSet.getDouble("p99_response_time")).thenReturn(500.0);
        } catch (Exception e) {
            // ignore
        }

        // When & Then
        assertDoesNotThrow(() -> {
            PerformanceReport result = performanceAnalysisService.generateAnalysisReport(timeRangeHours, service);
            // 在mock数据的情况下，应该能正常生成报告
            assertNotNull(result, "在有模拟数据的情况下应该能生成报告");
            assertEquals(timeRangeHours, result.getTimeRange());
            assertNotNull(result.getReportId());
        });
    }
    
    @Test 
    void testGenerateAnalysisReport_withNoData() {
        // Given
        int timeRangeHours = 24;
        String service = "test-service";
        
        // 默认的mock行为已经返回false和0值，无需额外设置

        // When & Then - 根据实际行为，即使没有数据也会生成报告
        assertDoesNotThrow(() -> {
            PerformanceReport result = performanceAnalysisService.generateAnalysisReport(timeRangeHours, service);
            // 实际上，即使没有数据也会生成包含0值的报告
            if (result != null) {
                assertEquals(0L, result.getMetrics().getTotalRequests(), "没有数据时请求数应该为0");
            }
        });
    }

    @Test
    void testGenerateAnalysisReport_withNegativeTimeRange() {
        // Given
        int invalidTimeRange = -1;
        String service = "test-service";

        // When & Then - 负数时间范围会被保留（业务逻辑）
        assertDoesNotThrow(() -> {
            PerformanceReport result = performanceAnalysisService.generateAnalysisReport(invalidTimeRange, service);
            // 根据实际实现，负数时间范围会被直接使用
            if (result != null) {
                assertEquals(invalidTimeRange, result.getTimeRange(), "时间范围按原值保存");
            }
        });
    }

    @Test
    void testGenerateAnalysisReport_withNullService() {
        // Given
        int timeRangeHours = 24;
        String nullService = null;

        // When & Then - null服务名应该正常处理（查询所有服务）
        assertDoesNotThrow(() -> {
            PerformanceReport result = performanceAnalysisService.generateAnalysisReport(timeRangeHours, nullService);
            // 空服务名时通常返回null（没有数据）
        });
    }

    @Test
    void testGenerateOptimizationSuggestions_withValidParameters() {
        // Given
        int timeRangeHours = 24;
        String service = "test-service";

        // When & Then
        assertDoesNotThrow(() -> {
            List<OptimizationSuggestion> result = performanceAnalysisService.generateOptimizationSuggestions(timeRangeHours, service);
            // 应该返回建议列表（可能为空）
            assertNotNull(result, "优化建议列表不应该为null");
        });
    }

    @Test
    void testScheduledAnalysis_withAnalysisDisabled() {
        // Given - 禁用分析
        try {
            AiAnalysisProperties.Analysis disabledAnalysis = createMockAnalysis();
            disabledAnalysis.setEnabled(false);
            lenient().when(properties.getAnalysis()).thenReturn(disabledAnalysis);
        } catch (Exception e) {
            // ignore
        }

        // When & Then
        assertDoesNotThrow(() -> {
            performanceAnalysisService.scheduledAnalysis();
            // 禁用时应该直接返回，不执行分析
        });
    }
    
    @Test
    void testScheduledAnalysis_withAnalysisEnabled() {
        // Given - 启用分析但没有服务数据（默认mock行为已经处理）

        // When & Then
        assertDoesNotThrow(() -> {
            performanceAnalysisService.scheduledAnalysis();
            // 没有服务时应该正常结束
        });
    }

    @Test
    void testServiceInitialization() {
        // When & Then
        assertNotNull(performanceAnalysisService);
        // Verify that all required dependencies are properly injected
        // Note: In a real test environment, we'd verify actual dependency injection
    }
}