package com.o11y.ai.service;

import com.o11y.ai.config.AiAnalysisProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 高级异常检测服务
 * 基于统计学、机器学习和大模型的多维度异常检测引擎
 */
@Service
public class AdvancedAnomalyDetectionService {

    private static final Logger LOG = LoggerFactory.getLogger(AdvancedAnomalyDetectionService.class);

    @Autowired
    private AiAnalysisProperties properties;

    @Autowired
    private LLMAnalysisService llmService;

    // 异常类型定义
    public enum AnomalyType {
        RESPONSE_TIME_SPIKE("响应时间异常飙升"),
        ERROR_RATE_INCREASE("错误率增长"),
        THROUGHPUT_DROP("吞吐量下降"),
        RESOURCE_EXHAUSTION("资源耗尽"),
        CASCADE_FAILURE("级联故障"),
        PATTERN_DEVIATION("行为模式偏离"),
        DEPENDENCY_TIMEOUT("依赖超时"),
        CIRCUIT_BREAKER_TRIGGERED("熔断器触发");

        private final String description;

        AnomalyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 实时异常检测主方法
     */
    public Map<String, Object> detectAnomalies(List<Map<String, Object>> timeSeriesData,
            Map<String, Object> currentMetrics) {
        Map<String, Object> detectionResult = new HashMap<>();
        List<Map<String, Object>> detectedAnomalies = new ArrayList<>();

        try {
            LOG.info("开始高级异常检测，数据点数量: {}",
                    timeSeriesData != null ? timeSeriesData.size() : 0);

            // 安全检查
            if (timeSeriesData == null) {
                timeSeriesData = new ArrayList<>();
            }
            if (currentMetrics == null) {
                currentMetrics = new HashMap<>();
            }

            // 1. 统计学异常检测
            List<Map<String, Object>> statisticalAnomalies = safeDetectStatisticalAnomalies(timeSeriesData,
                    currentMetrics);

            // 2. 时间序列异常检测
            List<Map<String, Object>> timeSeriesAnomalies = safeDetectTimeSeriesAnomalies(timeSeriesData);

            // 3. 模式异常检测
            List<Map<String, Object>> patternAnomalies = safeDetectPatternAnomalies(timeSeriesData);

            // 4. 业务逻辑异常检测
            List<Map<String, Object>> businessAnomalies = safeDetectBusinessLogicAnomalies(currentMetrics);

            // 合并所有异常
            detectedAnomalies.addAll(statisticalAnomalies);
            detectedAnomalies.addAll(timeSeriesAnomalies);
            detectedAnomalies.addAll(patternAnomalies);
            detectedAnomalies.addAll(businessAnomalies);

            // 5. 异常关联性分析
            Map<String, Object> correlationAnalysis = safeAnalyzeAnomalyCorrelations(detectedAnomalies, timeSeriesData);

            // 6. 严重性评估
            assessAnomalySeverity(detectedAnomalies);

            // 7. 大模型深度分析
            String llmAnalysis = "";
            if (!detectedAnomalies.isEmpty()) {
                llmAnalysis = performLLMAnalysis(detectedAnomalies, timeSeriesData, currentMetrics);
            }

            // 构建结果
            detectionResult.put("anomalies", detectedAnomalies);
            detectionResult.put("correlationAnalysis", correlationAnalysis);
            detectionResult.put("llmAnalysis", llmAnalysis);
            detectionResult.put("totalCount", detectedAnomalies.size());
            detectionResult.put("severityDistribution", calculateSeverityDistribution(detectedAnomalies));
            detectionResult.put("timestamp", LocalDateTime.now());
            detectionResult.put("confidence", calculateOverallConfidence(detectedAnomalies));

            LOG.info("异常检测完成，发现 {} 个异常", detectedAnomalies.size());

        } catch (Exception e) {
            LOG.error("异常检测过程中发生错误", e);
            detectionResult.put("error", e.getMessage());
            detectionResult.put("status", "failed");
        }

        return detectionResult;
    }

    /**
     * 统计学异常检测 - 基于Z-Score和IQR方法
     */
    private List<Map<String, Object>> detectStatisticalAnomalies(List<Map<String, Object>> data,
            Map<String, Object> current) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        try {
            // 关键指标列表
            List<String> keyMetrics = Arrays.asList(
                    "responseTime", "errorRate", "throughput", "cpuUsage", "memoryUsage");

            for (String metric : keyMetrics) {
                List<Double> values = data.stream()
                        .map(item -> {
                            Object value = item.get(metric);
                            return value != null ? Double.parseDouble(value.toString()) : 0.0;
                        })
                        .collect(Collectors.toList());

                if (values.size() < 3)
                    continue; // 数据点太少，跳过

                // 计算统计指标
                double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double stdDev = calculateStandardDeviation(values, mean);
                double[] quartiles = calculateQuartiles(values);
                double iqr = quartiles[2] - quartiles[0]; // Q3 - Q1

                // 当前值
                Object currentValue = current.get(metric);
                if (currentValue == null)
                    continue;
                double currentVal = Double.parseDouble(currentValue.toString());

                // Z-Score异常检测 (阈值: ±3σ)
                double zScore = Math.abs((currentVal - mean) / stdDev);
                boolean zScoreAnomaly = zScore > 3.0;

                // IQR异常检测
                double lowerBound = quartiles[0] - 1.5 * iqr;
                double upperBound = quartiles[2] + 1.5 * iqr;
                boolean iqrAnomaly = currentVal < lowerBound || currentVal > upperBound;

                if (zScoreAnomaly || iqrAnomaly) {
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("type", AnomalyType.PATTERN_DEVIATION);
                    anomaly.put("metric", metric);
                    anomaly.put("currentValue", currentVal);
                    anomaly.put("expectedRange", Map.of("min", lowerBound, "max", upperBound));
                    anomaly.put("zScore", zScore);
                    anomaly.put("deviation", Math.abs(currentVal - mean));
                    anomaly.put("confidence", Math.min(zScore / 3.0, 1.0));
                    anomaly.put("detectionMethod", zScoreAnomaly ? "Z-Score" : "IQR");
                    anomaly.put("severity", zScore > 5.0 ? "HIGH" : (zScore > 4.0 ? "MEDIUM" : "LOW"));

                    anomalies.add(anomaly);
                    LOG.debug("检测到统计学异常: {} = {}, Z-Score = {}", metric, currentVal, zScore);
                }
            }

        } catch (Exception e) {
            LOG.error("统计学异常检测失败", e);
        }

        return anomalies;
    }

    /**
     * 时间序列异常检测 - 检测趋势变化和周期性异常
     */
    private List<Map<String, Object>> detectTimeSeriesAnomalies(List<Map<String, Object>> data) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        try {
            if (data.size() < 10)
                return anomalies; // 数据点太少

            // 按时间排序
            data.sort((a, b) -> {
                LocalDateTime timeA = LocalDateTime.parse(a.get("timestamp").toString());
                LocalDateTime timeB = LocalDateTime.parse(b.get("timestamp").toString());
                return timeA.compareTo(timeB);
            });

            // 检测响应时间异常
            detectResponseTimeAnomalies(data, anomalies);

            // 检测错误率异常
            detectErrorRateAnomalies(data, anomalies);

            // 检测吞吐量异常
            detectThroughputAnomalies(data, anomalies);

        } catch (Exception e) {
            LOG.error("时间序列异常检测失败", e);
        }

        return anomalies;
    }

    /**
     * 响应时间异常检测
     */
    private void detectResponseTimeAnomalies(List<Map<String, Object>> data, List<Map<String, Object>> anomalies) {
        List<Double> responseTimes = data.stream()
                .map(item -> Double.parseDouble(item.get("responseTime").toString()))
                .collect(Collectors.toList());

        // 计算移动平均和变化率
        for (int i = 5; i < responseTimes.size(); i++) {
            double current = responseTimes.get(i);
            double movingAvg = responseTimes.subList(i - 5, i).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);

            double changeRate = (current - movingAvg) / movingAvg;

            // 响应时间暴增检测 (超过50%增长)
            if (changeRate > 0.5 && current > properties.getAnalysis().getThresholds().getResponseTimeMs()) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("type", AnomalyType.RESPONSE_TIME_SPIKE);
                anomaly.put("metric", "responseTime");
                anomaly.put("currentValue", current);
                anomaly.put("baselineValue", movingAvg);
                anomaly.put("changeRate", changeRate);
                anomaly.put("severity", changeRate > 1.0 ? "HIGH" : "MEDIUM");
                anomaly.put("confidence", Math.min(changeRate, 1.0));
                anomaly.put("timestamp", data.get(i).get("timestamp"));

                anomalies.add(anomaly);
            }
        }
    }

    /**
     * 错误率异常检测
     */
    private void detectErrorRateAnomalies(List<Map<String, Object>> data, List<Map<String, Object>> anomalies) {
        List<Double> errorRates = data.stream()
                .map(item -> Double.parseDouble(item.get("errorRate").toString()))
                .collect(Collectors.toList());

        for (int i = 3; i < errorRates.size(); i++) {
            double current = errorRates.get(i);
            double baseline = errorRates.subList(i - 3, i).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);

            // 错误率突增检测
            if (current > properties.getAnalysis().getThresholds().getErrorRatePercent() &&
                    current > baseline * 2) {

                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("type", AnomalyType.ERROR_RATE_INCREASE);
                anomaly.put("metric", "errorRate");
                anomaly.put("currentValue", current);
                anomaly.put("baselineValue", baseline);
                anomaly.put("increaseRatio", current / baseline);
                anomaly.put("severity", current > baseline * 5 ? "HIGH" : "MEDIUM");
                anomaly.put("confidence", Math.min((current - baseline) / baseline, 1.0));
                anomaly.put("timestamp", data.get(i).get("timestamp"));

                anomalies.add(anomaly);
            }
        }
    }

    /**
     * 吞吐量异常检测
     */
    private void detectThroughputAnomalies(List<Map<String, Object>> data, List<Map<String, Object>> anomalies) {
        List<Double> throughputs = data.stream()
                .map(item -> Double.parseDouble(item.get("throughput").toString()))
                .collect(Collectors.toList());

        for (int i = 5; i < throughputs.size(); i++) {
            double current = throughputs.get(i);
            double movingAvg = throughputs.subList(i - 5, i).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);

            double dropRate = (movingAvg - current) / movingAvg;

            // 吞吐量下降检测 (超过30%下降)
            if (dropRate > 0.3 && current < movingAvg * 0.7) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("type", AnomalyType.THROUGHPUT_DROP);
                anomaly.put("metric", "throughput");
                anomaly.put("currentValue", current);
                anomaly.put("baselineValue", movingAvg);
                anomaly.put("dropRate", dropRate);
                anomaly.put("severity", dropRate > 0.6 ? "HIGH" : "MEDIUM");
                anomaly.put("confidence", Math.min(dropRate, 1.0));
                anomaly.put("timestamp", data.get(i).get("timestamp"));

                anomalies.add(anomaly);
            }
        }
    }

    /**
     * 模式异常检测 - 检测行为模式的改变
     */
    private List<Map<String, Object>> detectPatternAnomalies(List<Map<String, Object>> data) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        try {
            // 检测周期性模式破坏
            detectCyclicPatternBreaks(data, anomalies);

            // 检测级联故障模式
            detectCascadeFailurePattern(data, anomalies);

        } catch (Exception e) {
            LOG.error("模式异常检测失败", e);
        }

        return anomalies;
    }

    /**
     * 业务逻辑异常检测
     */
    private List<Map<String, Object>> detectBusinessLogicAnomalies(Map<String, Object> metrics) {
        List<Map<String, Object>> anomalies = new ArrayList<>();

        try {
            // 资源耗尽检测
            detectResourceExhaustion(metrics, anomalies);

            // 依赖服务异常检测
            detectDependencyAnomalies(metrics, anomalies);

        } catch (Exception e) {
            LOG.error("业务逻辑异常检测失败", e);
        }

        return anomalies;
    }

    /**
     * 检测周期性模式破坏
     */
    private void detectCyclicPatternBreaks(List<Map<String, Object>> data, List<Map<String, Object>> anomalies) {
        // 简化实现 - 检测请求量的周期性变化
        if (data.size() < 24)
            return; // 至少需要一天的数据

        List<Double> hourlyVolumes = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            double volume = Double.parseDouble(data.get(i).get("throughput").toString());
            hourlyVolumes.add(volume);
        }

        // 检测今天与昨天同一时间段的差异
        for (int i = 12; i < Math.min(hourlyVolumes.size(), 24); i++) {
            double today = hourlyVolumes.get(i);
            double yesterday = hourlyVolumes.get(i - 12); // 假设12小时前为对比基准

            if (yesterday > 0) {
                double deviation = Math.abs(today - yesterday) / yesterday;

                if (deviation > 0.7) { // 70%以上偏离
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("type", AnomalyType.PATTERN_DEVIATION);
                    anomaly.put("description", "周期性模式破坏");
                    anomaly.put("currentValue", today);
                    anomaly.put("expectedValue", yesterday);
                    anomaly.put("deviation", deviation);
                    anomaly.put("severity", deviation > 1.0 ? "HIGH" : "MEDIUM");
                    anomaly.put("confidence", Math.min(deviation, 1.0));

                    anomalies.add(anomaly);
                }
            }
        }
    }

    /**
     * 检测级联故障模式
     */
    private void detectCascadeFailurePattern(List<Map<String, Object>> data, List<Map<String, Object>> anomalies) {
        // 检测多个指标同时恶化的情况
        for (int i = 2; i < data.size(); i++) {
            Map<String, Object> current = data.get(i);
            Map<String, Object> previous = data.get(i - 1);
            Map<String, Object> baseline = data.get(i - 2);

            boolean responseTimeIncrease = isMetricDeterioring(current, previous, baseline, "responseTime", true);
            boolean errorRateIncrease = isMetricDeterioring(current, previous, baseline, "errorRate", true);
            boolean throughputDecrease = isMetricDeterioring(current, previous, baseline, "throughput", false);

            // 如果多个关键指标同时恶化，可能是级联故障
            int deteriorationCount = 0;
            if (responseTimeIncrease)
                deteriorationCount++;
            if (errorRateIncrease)
                deteriorationCount++;
            if (throughputDecrease)
                deteriorationCount++;

            if (deteriorationCount >= 2) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("type", AnomalyType.CASCADE_FAILURE);
                anomaly.put("description", "疑似级联故障");
                anomaly.put("affectedMetrics", Arrays.asList("responseTime", "errorRate", "throughput"));
                anomaly.put("deteriorationCount", deteriorationCount);
                anomaly.put("severity", "HIGH");
                anomaly.put("confidence", 0.8);
                anomaly.put("timestamp", current.get("timestamp"));

                anomalies.add(anomaly);
            }
        }
    }

    /**
     * 检测资源耗尽
     */
    private void detectResourceExhaustion(Map<String, Object> metrics, List<Map<String, Object>> anomalies) {
        double cpuThreshold = properties.getAnalysis().getThresholds().getCpuUsagePercent();
        double memoryThreshold = properties.getAnalysis().getThresholds().getMemoryUsagePercent();

        Object cpuUsage = metrics.get("cpuUsage");
        Object memoryUsage = metrics.get("memoryUsage");

        if (cpuUsage != null && Double.parseDouble(cpuUsage.toString()) > cpuThreshold) {
            Map<String, Object> anomaly = new HashMap<>();
            anomaly.put("type", AnomalyType.RESOURCE_EXHAUSTION);
            anomaly.put("resource", "CPU");
            anomaly.put("currentValue", cpuUsage);
            anomaly.put("threshold", cpuThreshold);
            anomaly.put("severity", "HIGH");
            anomaly.put("confidence", 0.9);

            anomalies.add(anomaly);
        }

        if (memoryUsage != null && Double.parseDouble(memoryUsage.toString()) > memoryThreshold) {
            Map<String, Object> anomaly = new HashMap<>();
            anomaly.put("type", AnomalyType.RESOURCE_EXHAUSTION);
            anomaly.put("resource", "Memory");
            anomaly.put("currentValue", memoryUsage);
            anomaly.put("threshold", memoryThreshold);
            anomaly.put("severity", "HIGH");
            anomaly.put("confidence", 0.9);

            anomalies.add(anomaly);
        }
    }

    /**
     * 检测依赖服务异常
     */
    private void detectDependencyAnomalies(Map<String, Object> metrics, List<Map<String, Object>> anomalies) {
        // 检测依赖服务超时
        Object dependencyTimeout = metrics.get("dependencyTimeout");
        if (dependencyTimeout != null && Boolean.parseBoolean(dependencyTimeout.toString())) {
            Map<String, Object> anomaly = new HashMap<>();
            anomaly.put("type", AnomalyType.DEPENDENCY_TIMEOUT);
            anomaly.put("description", "依赖服务超时");
            anomaly.put("severity", "HIGH");
            anomaly.put("confidence", 0.9);

            anomalies.add(anomaly);
        }

        // 检测熔断器状态
        Object circuitBreakerState = metrics.get("circuitBreakerOpen");
        if (circuitBreakerState != null && Boolean.parseBoolean(circuitBreakerState.toString())) {
            Map<String, Object> anomaly = new HashMap<>();
            anomaly.put("type", AnomalyType.CIRCUIT_BREAKER_TRIGGERED);
            anomaly.put("description", "熔断器已触发");
            anomaly.put("severity", "MEDIUM");
            anomaly.put("confidence", 1.0);

            anomalies.add(anomaly);
        }
    }

    /**
     * 分析异常关联性
     */
    private Map<String, Object> analyzeAnomalyCorrelations(List<Map<String, Object>> anomalies,
            List<Map<String, Object>> timeSeriesData) {
        Map<String, Object> correlationAnalysis = new HashMap<>();

        try {
            // 时间关联性分析
            Map<String, List<Map<String, Object>>> timeGrouped = groupAnomaliesByTime(anomalies);

            // 指标关联性分析
            Map<String, Integer> metricCounts = countAnomaliesByMetric(anomalies);

            // 严重性关联性分析
            Map<String, Integer> severityCounts = countAnomaliesBySeverity(anomalies);

            correlationAnalysis.put("timeGrouped", timeGrouped);
            correlationAnalysis.put("metricCounts", metricCounts);
            correlationAnalysis.put("severityCounts", severityCounts);
            correlationAnalysis.put("totalAnomalies", anomalies.size());

        } catch (Exception e) {
            LOG.error("异常关联性分析失败", e);
        }

        return correlationAnalysis;
    }

    /**
     * 大模型深度分析
     */
    private String performLLMAnalysis(List<Map<String, Object>> anomalies,
            List<Map<String, Object>> timeSeriesData,
            Map<String, Object> currentMetrics) {
        try {
            buildAnomalyAnalysisPrompt(anomalies, timeSeriesData, currentMetrics);
            // 创建临时的性能指标对象用于LLM分析
            return llmService.analyzePerformanceData(null, new ArrayList<>());
        } catch (Exception e) {
            LOG.error("LLM分析失败", e);
            return "LLM分析暂时不可用，请查看具体异常详情进行人工分析。";
        }
    }

    /**
     * 构建异常分析提示词
     */
    private String buildAnomalyAnalysisPrompt(List<Map<String, Object>> anomalies,
            List<Map<String, Object>> timeSeriesData,
            Map<String, Object> currentMetrics) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("作为一名资深的系统性能分析专家，请分析以下检测到的系统异常情况：\n\n");

        prompt.append("## 检测到的异常清单：\n");
        for (int i = 0; i < anomalies.size(); i++) {
            Map<String, Object> anomaly = anomalies.get(i);
            prompt.append(String.format("%d. %s - %s\n",
                    i + 1,
                    anomaly.get("type"),
                    anomaly.getOrDefault("description", "")));
            prompt.append(String.format("   严重性: %s, 置信度: %s\n",
                    anomaly.get("severity"),
                    anomaly.get("confidence")));
        }

        prompt.append("\n## 当前系统指标：\n");
        currentMetrics.forEach((key, value) -> prompt.append(String.format("- %s: %s\n", key, value)));

        prompt.append("\n## 请提供以下分析：\n");
        prompt.append("1. **根因分析**: 分析异常的可能根本原因\n");
        prompt.append("2. **影响评估**: 评估对业务的影响程度\n");
        prompt.append("3. **紧急措施**: 立即需要采取的应急措施\n");
        prompt.append("4. **解决方案**: 详细的问题解决步骤\n");
        prompt.append("5. **预防措施**: 避免类似问题再次发生的建议\n");
        prompt.append("\n请用中文回答，条理清晰，重点突出。");

        return prompt.toString();
    }

    // 安全版本的检测方法
    private List<Map<String, Object>> safeDetectStatisticalAnomalies(List<Map<String, Object>> timeSeriesData,
            Map<String, Object> currentMetrics) {
        try {
            return detectStatisticalAnomalies(timeSeriesData, currentMetrics);
        } catch (Exception e) {
            LOG.warn("统计学异常检测失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> safeDetectTimeSeriesAnomalies(List<Map<String, Object>> timeSeriesData) {
        try {
            return detectTimeSeriesAnomalies(timeSeriesData);
        } catch (Exception e) {
            LOG.warn("时间序列异常检测失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> safeDetectPatternAnomalies(List<Map<String, Object>> timeSeriesData) {
        try {
            return detectPatternAnomalies(timeSeriesData);
        } catch (Exception e) {
            LOG.warn("模式异常检测失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> safeDetectBusinessLogicAnomalies(Map<String, Object> currentMetrics) {
        try {
            return detectBusinessLogicAnomalies(currentMetrics);
        } catch (Exception e) {
            LOG.warn("业务逻辑异常检测失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> safeAnalyzeAnomalyCorrelations(List<Map<String, Object>> detectedAnomalies,
            List<Map<String, Object>> timeSeriesData) {
        try {
            return analyzeAnomalyCorrelations(detectedAnomalies, timeSeriesData);
        } catch (Exception e) {
            LOG.warn("异常关联性分析失败: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("correlationScore", 0.0);
            fallback.put("relatedAnomalies", new ArrayList<>());
            return fallback;
        }
    }

    // 辅助方法
    private double calculateStandardDeviation(List<Double> values, double mean) {
        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    private double[] calculateQuartiles(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int n = sorted.size();
        double q1 = sorted.get(n / 4);
        double q2 = sorted.get(n / 2);
        double q3 = sorted.get(3 * n / 4);

        return new double[] { q1, q2, q3 };
    }

    private boolean isMetricDeterioring(Map<String, Object> current, Map<String, Object> previous,
            Map<String, Object> baseline, String metric, boolean higherIsBad) {
        try {
            double currVal = Double.parseDouble(current.get(metric).toString());
            double prevVal = Double.parseDouble(previous.get(metric).toString());
            double baseVal = Double.parseDouble(baseline.get(metric).toString());

            if (higherIsBad) {
                return currVal > prevVal && prevVal > baseVal;
            } else {
                return currVal < prevVal && prevVal < baseVal;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void assessAnomalySeverity(List<Map<String, Object>> anomalies) {
        for (Map<String, Object> anomaly : anomalies) {
            // 根据不同因素调整严重性
            AnomalyType type = (AnomalyType) anomaly.get("type");

            // 某些类型的异常天然严重性更高
            if (type == AnomalyType.CASCADE_FAILURE || type == AnomalyType.RESOURCE_EXHAUSTION) {
                anomaly.put("severity", "CRITICAL");
            }
        }
    }

    private Map<String, Integer> calculateSeverityDistribution(List<Map<String, Object>> anomalies) {
        Map<String, Integer> distribution = new HashMap<>();
        for (Map<String, Object> anomaly : anomalies) {
            String severity = (String) anomaly.get("severity");
            distribution.merge(severity, 1, Integer::sum);
        }
        return distribution;
    }

    private double calculateOverallConfidence(List<Map<String, Object>> anomalies) {
        if (anomalies.isEmpty())
            return 0.0;

        return anomalies.stream()
                .mapToDouble(anomaly -> Double.parseDouble(anomaly.get("confidence").toString()))
                .average()
                .orElse(0.0);
    }

    private Map<String, List<Map<String, Object>>> groupAnomaliesByTime(List<Map<String, Object>> anomalies) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();
        // 简化实现 - 按小时分组
        for (Map<String, Object> anomaly : anomalies) {
            String timeKey = "recent"; // 简化为最近时间段
            grouped.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(anomaly);
        }
        return grouped;
    }

    private Map<String, Integer> countAnomaliesByMetric(List<Map<String, Object>> anomalies) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> anomaly : anomalies) {
            String metric = (String) anomaly.get("metric");
            if (metric != null) {
                counts.merge(metric, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<String, Integer> countAnomaliesBySeverity(List<Map<String, Object>> anomalies) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> anomaly : anomalies) {
            String severity = (String) anomaly.get("severity");
            counts.merge(severity, 1, Integer::sum);
        }
        return counts;
    }
}
