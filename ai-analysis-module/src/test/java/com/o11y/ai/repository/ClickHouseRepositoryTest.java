package com.o11y.ai.repository;

import com.o11y.ai.model.PerformanceMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * ClickHouseRepository单元测试
 */
@ExtendWith(MockitoExtension.class)
class ClickHouseRepositoryTest {

    @Mock
    private JdbcTemplate clickHouseJdbcTemplate;

    @InjectMocks
    private ClickHouseRepository clickHouseRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Mock JdbcTemplate的查询方法
        lenient().when(clickHouseJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(Arrays.asList());
        lenient().when(clickHouseJdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(Arrays.asList());
        lenient().when(clickHouseJdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @Test
    void testGetServiceMetrics_withValidTimeRange() throws Exception {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();
        String service = "test-service";

        // When & Then
        assertDoesNotThrow(() -> {
            List<PerformanceMetrics> result = clickHouseRepository.getServiceMetrics(startTime, endTime, service);
            // 应该返回空列表（由mock配置决定）
            assertNotNull(result, "结果不应为null");
        });

        // 验证JdbcTemplate被调用
        verify(clickHouseJdbcTemplate).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
    }

    @Test
    void testGetServiceMetrics_withNullService() throws Exception {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();
        String nullService = null;

        // When & Then
        assertDoesNotThrow(() -> {
            List<PerformanceMetrics> result = clickHouseRepository.getServiceMetrics(startTime, endTime, nullService);
            assertNotNull(result, "结果不应为null");
        });
    }

    @Test
    void testGetErrorTraces_withValidParameters() throws Exception {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();
        String service = "test-service";

        // When & Then
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> result = clickHouseRepository.getErrorTraces(startTime, endTime, service);
            assertNotNull(result, "结果不应为null");
        });
    }

    @Test
    void testGetSlowRequests_withValidParameters() throws Exception {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();
        String service = "test-service";
        int thresholdMs = 1000;

        // When & Then
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> result = clickHouseRepository.getSlowRequests(startTime, endTime, (long) thresholdMs, service);
            assertNotNull(result, "结果不应为null");
        });
    }

    @Test
    void testGetServiceTopology_withValidTimeRange() throws Exception {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();

        // When & Then
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> result = clickHouseRepository.getServiceTopology(startTime, endTime);
            assertNotNull(result, "结果不应为null");
        });
    }

    @Test
    void testGetEventsSample_withDefaultLimit() throws Exception {
        // Given
        int defaultLimit = 10;

        // When & Then
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> result = clickHouseRepository.getEventsSample(LocalDateTime.now().minusHours(1), LocalDateTime.now(), defaultLimit);
            assertNotNull(result, "结果不应为null");
        });
    }

    @Test
    void testGetEventsTableSchema() throws Exception {
        // When & Then
        assertDoesNotThrow(() -> {
            List<Map<String, Object>> result = clickHouseRepository.getEventsTableSchema();
            assertNotNull(result, "结果不应为null");
        });
    }

    @Test
    void testConnectionHandling_whenExceptionOccurs() throws Exception {
        // Given - 模拟JdbcTemplate抛出异常
        when(clickHouseJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenThrow(new RuntimeException("Connection failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            clickHouseRepository.getServiceMetrics(
                LocalDateTime.now().minusHours(1), 
                LocalDateTime.now(), 
                "test-service"
            );
            // 异常应该被正确抛出
        });
    }

    @Test
    void testServiceInitialization() {
        // When & Then
        assertNotNull(clickHouseRepository);
        // Verify that the repository is properly initialized
    }
}