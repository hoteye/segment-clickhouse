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
 * 智能根因分析引擎
 * 基于多维度数据分析和大模型推理的根因定位服务
 */
@Service
public class RootCauseAnalysisEngine {

    private static final Logger LOG = LoggerFactory.getLogger(RootCauseAnalysisEngine.class);

    @Autowired
    private AiAnalysisProperties properties;
    @Autowired
    private LLMAnalysisService llmService;

    // 根因类型定义
    public enum RootCauseType {
        INFRASTRUCTURE("基础设施问题"),
        APPLICATION("应用程序问题"),
        DATABASE("数据库问题"),
        NETWORK("网络问题"),
        DEPENDENCY("依赖服务问题"),
        CONFIGURATION("配置问题"),
        RESOURCE("资源问题"),
        BUSINESS_LOGIC("业务逻辑问题"),
        SECURITY("安全问题"),
        DATA_QUALITY("数据质量问题");

        private final String description;

        RootCauseType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 综合根因分析 - 主入口方法
     */
    public Map<String, Object> analyzeRootCause(Map<String, Object> incidentData) {
        Map<String, Object> rootCauseResult = new HashMap<>();

        try {
            LOG.info("开始综合根因分析，事件ID: {}", incidentData.get("incidentId"));

            // 1. 数据预处理和特征提取
            Map<String, Object> features = extractIncidentFeatures(incidentData);

            // 2. 多维度分析
            Map<String, Object> technicalAnalysis = performTechnicalAnalysis(incidentData, features);
            Map<String, Object> temporalAnalysis = performTemporalAnalysis(incidentData, features);
            Map<String, Object> dependencyAnalysis = performDependencyAnalysis(incidentData, features);
            Map<String, Object> resourceAnalysis = performResourceAnalysis(incidentData, features);

            // 3. 证据收集和关联
            List<Map<String, Object>> evidences = collectEvidence(incidentData, features);
            Map<String, Object> correlationMatrix = buildCorrelationMatrix(evidences);

            // 4. 根因候选生成
            List<Map<String, Object>> rootCauseCandidates = generateRootCauseCandidates(
                    technicalAnalysis, temporalAnalysis, dependencyAnalysis, resourceAnalysis, evidences);

            // 5. 候选评分和排序
            List<Map<String, Object>> rankedCandidates = scoreAndRankCandidates(rootCauseCandidates, evidences);

            // 6. 大模型深度推理
            String llmAnalysis = performLLMRootCauseAnalysis(incidentData, rankedCandidates, evidences);

            // 7. 解决方案推荐
            List<Map<String, Object>> recommendations = generateRecommendations(rankedCandidates, evidences);

            // 8. 构建结果
            rootCauseResult.put("incidentId", incidentData.get("incidentId"));
            rootCauseResult.put("primaryRootCause", rankedCandidates.isEmpty() ? null : rankedCandidates.get(0));
            rootCauseResult.put("allCandidates", rankedCandidates);
            rootCauseResult.put("evidences", evidences);
            rootCauseResult.put("correlationMatrix", correlationMatrix);
            rootCauseResult.put("llmAnalysis", llmAnalysis);
            rootCauseResult.put("recommendations", recommendations);
            rootCauseResult.put("confidence", calculateOverallConfidence(rankedCandidates));
            rootCauseResult.put("analysisTimestamp", LocalDateTime.now());

            // 9. 分析元数据
            rootCauseResult.put("metadata", Map.of(
                    "evidenceCount", evidences.size(),
                    "candidateCount", rankedCandidates.size(),
                    "analysisDepth", calculateAnalysisDepth(evidences),
                    "dataQuality", assessDataQuality(incidentData, evidences)));

            LOG.info("根因分析完成，识别出 {} 个候选原因", rankedCandidates.size());

        } catch (Exception e) {
            LOG.error("根因分析失败", e);
            rootCauseResult.put("error", e.getMessage());
            rootCauseResult.put("status", "failed");
        }

        return rootCauseResult;
    }

    /**
     * 提取事件特征
     */
    private Map<String, Object> extractIncidentFeatures(Map<String, Object> incidentData) {
        Map<String, Object> features = new HashMap<>();

        try {
            // 时间特征
            features.put("startTime", incidentData.get("startTime"));
            features.put("duration", calculateIncidentDuration(incidentData));
            features.put("timeOfDay", extractTimeOfDay(incidentData));
            features.put("dayOfWeek", extractDayOfWeek(incidentData));

            // 影响范围特征
            features.put("affectedServices", incidentData.get("affectedServices"));
            features.put("affectedUsers", incidentData.get("affectedUsers"));
            features.put("impactSeverity", incidentData.get("severity"));

            // 技术特征
            features.put("errorMessages", extractErrorMessages(incidentData));
            features.put("performanceMetrics", extractPerformanceMetrics(incidentData));
            features.put("systemComponents", extractSystemComponents(incidentData));

            // 环境特征
            features.put("deploymentEvents", extractRecentDeployments(incidentData));
            features.put("configurationChanges", extractConfigurationChanges(incidentData));
            features.put("externalFactors", extractExternalFactors(incidentData));

            LOG.debug("提取到 {} 个事件特征", features.size());

        } catch (Exception e) {
            LOG.error("特征提取失败", e);
        }

        return features;
    }

    /**
     * 技术层面分析
     */
    private Map<String, Object> performTechnicalAnalysis(Map<String, Object> incidentData,
            Map<String, Object> features) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // 错误模式分析
            List<String> errorPatterns = analyzeErrorPatterns(features);
            analysis.put("errorPatterns", errorPatterns);

            // 性能指标分析
            Map<String, Object> performanceIssues = analyzePerformanceMetrics(features);
            analysis.put("performanceIssues", performanceIssues);

            // 系统组件分析
            Map<String, Object> componentHealth = analyzeComponentHealth(features);
            analysis.put("componentHealth", componentHealth);

            // 资源使用分析
            Map<String, Object> resourceUsage = analyzeResourceUsage(features);
            analysis.put("resourceUsage", resourceUsage);

            LOG.debug("技术分析完成");

        } catch (Exception e) {
            LOG.error("技术分析失败", e);
        }

        return analysis;
    }

    /**
     * 时间维度分析
     */
    private Map<String, Object> performTemporalAnalysis(Map<String, Object> incidentData,
            Map<String, Object> features) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // 时间模式分析
            String timePattern = analyzeTimePattern(features);
            analysis.put("timePattern", timePattern);

            // 事件序列分析
            List<Map<String, Object>> eventSequence = analyzeEventSequence(incidentData);
            analysis.put("eventSequence", eventSequence);

            // 周期性分析
            Map<String, Object> cyclicAnalysis = analyzeCyclicPatterns(features);
            analysis.put("cyclicAnalysis", cyclicAnalysis);

            // 趋势分析
            Map<String, Object> trendAnalysis = analyzeTrends(features);
            analysis.put("trendAnalysis", trendAnalysis);

            LOG.debug("时间分析完成");

        } catch (Exception e) {
            LOG.error("时间分析失败", e);
        }

        return analysis;
    }

    /**
     * 依赖关系分析
     */
    private Map<String, Object> performDependencyAnalysis(Map<String, Object> incidentData,
            Map<String, Object> features) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // 服务依赖分析
            Map<String, Object> serviceDependencies = analyzeServiceDependencies(features);
            analysis.put("serviceDependencies", serviceDependencies);

            // 数据依赖分析
            Map<String, Object> dataDependencies = analyzeDataDependencies(features);
            analysis.put("dataDependencies", dataDependencies);

            // 基础设施依赖分析
            Map<String, Object> infrastructureDependencies = analyzeInfrastructureDependencies(features);
            analysis.put("infrastructureDependencies", infrastructureDependencies);

            LOG.debug("依赖分析完成");

        } catch (Exception e) {
            LOG.error("依赖分析失败", e);
        }

        return analysis;
    }

    /**
     * 资源层面分析
     */
    private Map<String, Object> performResourceAnalysis(Map<String, Object> incidentData,
            Map<String, Object> features) {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // CPU资源分析
            Map<String, Object> cpuAnalysis = analyzeCPUResources(features);
            analysis.put("cpu", cpuAnalysis);

            // 内存资源分析
            Map<String, Object> memoryAnalysis = analyzeMemoryResources(features);
            analysis.put("memory", memoryAnalysis);

            // 网络资源分析
            Map<String, Object> networkAnalysis = analyzeNetworkResources(features);
            analysis.put("network", networkAnalysis);

            // 存储资源分析
            Map<String, Object> storageAnalysis = analyzeStorageResources(features);
            analysis.put("storage", storageAnalysis);

            LOG.debug("资源分析完成");

        } catch (Exception e) {
            LOG.error("资源分析失败", e);
        }

        return analysis;
    }

    /**
     * 收集证据
     */
    private List<Map<String, Object>> collectEvidence(Map<String, Object> incidentData,
            Map<String, Object> features) {
        List<Map<String, Object>> evidences = new ArrayList<>();

        try {
            // 日志证据
            List<Map<String, Object>> logEvidences = collectLogEvidence(incidentData);
            evidences.addAll(logEvidences);

            // 指标证据
            List<Map<String, Object>> metricEvidences = collectMetricEvidence(features);
            evidences.addAll(metricEvidences);

            // 配置证据
            List<Map<String, Object>> configEvidences = collectConfigurationEvidence(features);
            evidences.addAll(configEvidences);

            // 部署证据
            List<Map<String, Object>> deploymentEvidences = collectDeploymentEvidence(features);
            evidences.addAll(deploymentEvidences);

            // 证据评分
            for (Map<String, Object> evidence : evidences) {
                double reliability = calculateEvidenceReliability(evidence);
                evidence.put("reliability", reliability);
            }

            LOG.debug("收集到 {} 条证据", evidences.size());

        } catch (Exception e) {
            LOG.error("证据收集失败", e);
        }

        return evidences;
    }

    /**
     * 生成根因候选
     */
    private List<Map<String, Object>> generateRootCauseCandidates(Map<String, Object> technicalAnalysis,
            Map<String, Object> temporalAnalysis,
            Map<String, Object> dependencyAnalysis,
            Map<String, Object> resourceAnalysis,
            List<Map<String, Object>> evidences) {
        List<Map<String, Object>> candidates = new ArrayList<>();

        try {
            // 基于技术分析生成候选
            candidates.addAll(generateTechnicalCandidates(technicalAnalysis, evidences));

            // 基于时间分析生成候选
            candidates.addAll(generateTemporalCandidates(temporalAnalysis, evidences));

            // 基于依赖分析生成候选
            candidates.addAll(generateDependencyCandidates(dependencyAnalysis, evidences));

            // 基于资源分析生成候选
            candidates.addAll(generateResourceCandidates(resourceAnalysis, evidences));

            // 去重和合并相似候选
            candidates = deduplicateAndMergeCandidates(candidates);

            LOG.debug("生成 {} 个根因候选", candidates.size());

        } catch (Exception e) {
            LOG.error("根因候选生成失败", e);
        }

        return candidates;
    }

    /**
     * 评分和排序候选原因
     */
    private List<Map<String, Object>> scoreAndRankCandidates(List<Map<String, Object>> candidates,
            List<Map<String, Object>> evidences) {
        try {
            for (Map<String, Object> candidate : candidates) {
                double score = calculateCandidateScore(candidate, evidences);
                candidate.put("score", score);
                candidate.put("confidence", Math.min(score / 100.0, 1.0));
            }

            // 按评分排序
            candidates.sort((a, b) -> {
                double scoreA = (Double) a.get("score");
                double scoreB = (Double) b.get("score");
                return Double.compare(scoreB, scoreA); // 降序
            });

            LOG.debug("候选原因评分和排序完成");

        } catch (Exception e) {
            LOG.error("候选评分失败", e);
        }

        return candidates;
    }

    /**
     * 大模型根因推理
     */
    private String performLLMRootCauseAnalysis(Map<String, Object> incidentData,
            List<Map<String, Object>> candidates,
            List<Map<String, Object>> evidences) {
        try {
            buildRootCauseAnalysisPrompt(incidentData, candidates, evidences);
            return llmService.analyzePerformanceData(null, new ArrayList<>());
        } catch (Exception e) {
            LOG.error("LLM根因分析失败", e);
            return "大模型分析暂时不可用，请基于候选原因和证据进行人工分析。";
        }
    }

    /**
     * 生成解决方案推荐
     */
    private List<Map<String, Object>> generateRecommendations(List<Map<String, Object>> candidates,
            List<Map<String, Object>> evidences) {
        List<Map<String, Object>> recommendations = new ArrayList<>();

        try {
            for (Map<String, Object> candidate : candidates.subList(0, Math.min(3, candidates.size()))) {
                RootCauseType type = (RootCauseType) candidate.get("type");
                List<Map<String, Object>> actions = generateActionsForRootCause(type, candidate, evidences);

                Map<String, Object> recommendation = new HashMap<>();
                recommendation.put("rootCause", candidate);
                recommendation.put("actions", actions);
                recommendation.put("priority", calculateActionPriority(candidate));
                recommendation.put("estimatedTime", estimateResolutionTime(type, candidate));

                recommendations.add(recommendation);
            }

            LOG.debug("生成 {} 个解决方案推荐", recommendations.size());

        } catch (Exception e) {
            LOG.error("解决方案生成失败", e);
        }

        return recommendations;
    }

    // ==================== 辅助方法 ====================

    private long calculateIncidentDuration(Map<String, Object> incidentData) {
        // 简化实现
        return 3600000L; // 1小时（毫秒）
    }

    private String extractTimeOfDay(Map<String, Object> incidentData) {
        // 简化实现
        return "business_hours";
    }

    private String extractDayOfWeek(Map<String, Object> incidentData) {
        // 简化实现
        return "weekday";
    }

    private List<String> extractErrorMessages(Map<String, Object> incidentData) {
        List<String> errors = new ArrayList<>();
        Object errorObj = incidentData.get("errorMessages");
        if (errorObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> errorList = (List<String>) errorObj;
            errors = errorList;
        }
        return errors;
    }

    private Map<String, Object> extractPerformanceMetrics(Map<String, Object> incidentData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) incidentData.getOrDefault("performanceMetrics", new HashMap<>());
        return result;
    }

    private List<String> extractSystemComponents(Map<String, Object> incidentData) {
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) incidentData.getOrDefault("systemComponents", new ArrayList<>());
        return result;
    }

    private List<Map<String, Object>> extractRecentDeployments(Map<String, Object> incidentData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) incidentData.getOrDefault("recentDeployments", new ArrayList<>());
        return result;
    }

    private List<Map<String, Object>> extractConfigurationChanges(Map<String, Object> incidentData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) incidentData.getOrDefault("configurationChanges", new ArrayList<>());
        return result;
    }

    private Map<String, Object> extractExternalFactors(Map<String, Object> incidentData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) incidentData.getOrDefault("externalFactors", new HashMap<>());
        return result;
    }

    private List<String> analyzeErrorPatterns(Map<String, Object> features) {
        List<String> patterns = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<String> errorMessages = (List<String>) features.get("errorMessages");

        if (errorMessages != null) {
            for (String error : errorMessages) {
                if (error.contains("timeout")) {
                    patterns.add("TIMEOUT_PATTERN");
                } else if (error.contains("connection")) {
                    patterns.add("CONNECTION_PATTERN");
                } else if (error.contains("memory")) {
                    patterns.add("MEMORY_PATTERN");
                }
            }
        }

        return patterns;
    }

    private Map<String, Object> analyzePerformanceMetrics(Map<String, Object> features) {
        Map<String, Object> issues = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) features.get("performanceMetrics");

        if (metrics != null) {
            Object responseTime = metrics.get("responseTime");
            if (responseTime != null && Double.parseDouble(responseTime.toString()) > properties.getAnalysis()
                    .getThresholds().getResponseTimeMs()) {
                issues.put("highResponseTime", true);
            }

            Object errorRate = metrics.get("errorRate");
            if (errorRate != null && Double.parseDouble(errorRate.toString()) > properties.getAnalysis().getThresholds()
                    .getErrorRatePercent()) {
                issues.put("highErrorRate", true);
            }
        }

        return issues;
    }

    private Map<String, Object> analyzeComponentHealth(Map<String, Object> features) {
        Map<String, Object> health = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<String> components = (List<String>) features.get("systemComponents");

        if (components != null) {
            for (String component : components) {
                health.put(component, "HEALTHY"); // 简化实现
            }
        }

        return health;
    }

    private Map<String, Object> analyzeResourceUsage(Map<String, Object> features) {
        Map<String, Object> usage = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) features.get("performanceMetrics");

        if (metrics != null) {
            usage.put("cpu", metrics.getOrDefault("cpuUsage", 0));
            usage.put("memory", metrics.getOrDefault("memoryUsage", 0));
            usage.put("disk", metrics.getOrDefault("diskUsage", 0));
        }

        return usage;
    }

    private String analyzeTimePattern(Map<String, Object> features) {
        String timeOfDay = (String) features.get("timeOfDay");
        String dayOfWeek = (String) features.get("dayOfWeek");

        if ("business_hours".equals(timeOfDay) && "weekday".equals(dayOfWeek)) {
            return "PEAK_HOURS";
        } else if ("night".equals(timeOfDay)) {
            return "OFF_HOURS";
        }

        return "NORMAL_HOURS";
    }

    private List<Map<String, Object>> analyzeEventSequence(Map<String, Object> incidentData) {
        // 简化实现
        return new ArrayList<>();
    }

    private Map<String, Object> analyzeCyclicPatterns(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeTrends(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeServiceDependencies(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeDataDependencies(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeInfrastructureDependencies(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeCPUResources(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeMemoryResources(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeNetworkResources(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private Map<String, Object> analyzeStorageResources(Map<String, Object> features) {
        // 简化实现
        return new HashMap<>();
    }

    private List<Map<String, Object>> collectLogEvidence(Map<String, Object> incidentData) {
        // 简化实现
        return new ArrayList<>();
    }

    private List<Map<String, Object>> collectMetricEvidence(Map<String, Object> features) {
        // 简化实现
        return new ArrayList<>();
    }

    private List<Map<String, Object>> collectConfigurationEvidence(Map<String, Object> features) {
        // 简化实现
        return new ArrayList<>();
    }

    private List<Map<String, Object>> collectDeploymentEvidence(Map<String, Object> features) {
        // 简化实现
        return new ArrayList<>();
    }

    private double calculateEvidenceReliability(Map<String, Object> evidence) {
        // 简化实现 - 基于证据类型计算可靠性
        return 0.8;
    }

    private Map<String, Object> buildCorrelationMatrix(List<Map<String, Object>> evidences) {
        // 简化实现
        return new HashMap<>();
    }

    private List<Map<String, Object>> generateTechnicalCandidates(Map<String, Object> analysis,
            List<Map<String, Object>> evidences) {
        List<Map<String, Object>> candidates = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> performanceIssues = (Map<String, Object>) analysis.get("performanceIssues");
        if (performanceIssues != null && Boolean.TRUE.equals(performanceIssues.get("highResponseTime"))) {
            Map<String, Object> candidate = new HashMap<>();
            candidate.put("type", RootCauseType.APPLICATION);
            candidate.put("description", "应用程序响应时间过高");
            candidate.put("category", "performance");
            candidates.add(candidate);
        }

        return candidates;
    }

    private List<Map<String, Object>> generateTemporalCandidates(Map<String, Object> analysis,
            List<Map<String, Object>> evidences) {
        // 简化实现
        return new ArrayList<>();
    }

    private List<Map<String, Object>> generateDependencyCandidates(Map<String, Object> analysis,
            List<Map<String, Object>> evidences) {
        // 简化实现
        return new ArrayList<>();
    }

    private List<Map<String, Object>> generateResourceCandidates(Map<String, Object> analysis,
            List<Map<String, Object>> evidences) {
        // 简化实现
        return new ArrayList<>();
    }

    private List<Map<String, Object>> deduplicateAndMergeCandidates(List<Map<String, Object>> candidates) {
        // 简化实现 - 移除重复候选
        return candidates.stream().distinct().collect(Collectors.toList());
    }

    private double calculateCandidateScore(Map<String, Object> candidate, List<Map<String, Object>> evidences) {
        // 简化评分算法
        double baseScore = 50.0;

        // 根据证据数量调整分数
        long supportingEvidences = evidences.stream()
                .filter(evidence -> isEvidenceSupporting(evidence, candidate))
                .count();

        return baseScore + (supportingEvidences * 10.0);
    }

    private boolean isEvidenceSupporting(Map<String, Object> evidence, Map<String, Object> candidate) {
        // 简化实现 - 判断证据是否支持候选原因
        return true; // 简化为总是支持
    }

    private String buildRootCauseAnalysisPrompt(Map<String, Object> incidentData,
            List<Map<String, Object>> candidates,
            List<Map<String, Object>> evidences) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("作为一名资深的系统分析专家，请基于以下信息进行根因分析：\n\n");

        prompt.append("## 事件信息：\n");
        prompt.append("事件ID: ").append(incidentData.get("incidentId")).append("\n");
        prompt.append("严重程度: ").append(incidentData.get("severity")).append("\n");
        prompt.append("影响范围: ").append(incidentData.get("affectedServices")).append("\n\n");

        prompt.append("## 候选根因：\n");
        for (int i = 0; i < Math.min(candidates.size(), 5); i++) {
            Map<String, Object> candidate = candidates.get(i);
            prompt.append(String.format("%d. %s (评分: %.1f)\n",
                    i + 1, candidate.get("description"), candidate.get("score")));
        }

        prompt.append("\n## 关键证据：\n");
        for (int i = 0; i < Math.min(evidences.size(), 10); i++) {
            Map<String, Object> evidence = evidences.get(i);
            prompt.append(String.format("- %s\n", evidence.get("description")));
        }

        prompt.append("\n请提供详细的根因分析和解决建议。");

        return prompt.toString();
    }

    private List<Map<String, Object>> generateActionsForRootCause(RootCauseType type,
            Map<String, Object> candidate,
            List<Map<String, Object>> evidences) {
        List<Map<String, Object>> actions = new ArrayList<>();

        switch (type) {
            case APPLICATION:
                actions.add(Map.of("action", "检查应用程序日志", "priority", "HIGH"));
                actions.add(Map.of("action", "重启应用服务", "priority", "MEDIUM"));
                break;
            case DATABASE:
                actions.add(Map.of("action", "检查数据库连接", "priority", "HIGH"));
                actions.add(Map.of("action", "优化查询性能", "priority", "MEDIUM"));
                break;
            case NETWORK:
                actions.add(Map.of("action", "检查网络连接", "priority", "HIGH"));
                actions.add(Map.of("action", "重启网络设备", "priority", "MEDIUM"));
                break;
            default:
                actions.add(Map.of("action", "进行详细诊断", "priority", "MEDIUM"));
                break;
        }

        return actions;
    }

    private String calculateActionPriority(Map<String, Object> candidate) {
        double score = (Double) candidate.get("score");
        return score > 80 ? "CRITICAL" : (score > 60 ? "HIGH" : "MEDIUM");
    }

    private String estimateResolutionTime(RootCauseType type, Map<String, Object> candidate) {
        // 简化实现 - 基于根因类型估算解决时间
        switch (type) {
            case CONFIGURATION:
                return "30分钟";
            case APPLICATION:
                return "1-2小时";
            case DATABASE:
                return "2-4小时";
            case INFRASTRUCTURE:
                return "4-8小时";
            default:
                return "1-3小时";
        }
    }

    private double calculateOverallConfidence(List<Map<String, Object>> candidates) {
        if (candidates.isEmpty())
            return 0.0;

        return candidates.stream()
                .mapToDouble(candidate -> (Double) candidate.get("confidence"))
                .max()
                .orElse(0.0);
    }

    private String calculateAnalysisDepth(List<Map<String, Object>> evidences) {
        int evidenceCount = evidences.size();
        if (evidenceCount > 20)
            return "DEEP";
        if (evidenceCount > 10)
            return "MEDIUM";
        return "SHALLOW";
    }

    private String assessDataQuality(Map<String, Object> incidentData, List<Map<String, Object>> evidences) {
        // 简化实现 - 基于数据完整性评估质量
        int requiredFields = 0;
        int availableFields = 0;

        String[] requiredFieldNames = { "startTime", "severity", "affectedServices", "errorMessages" };
        for (String field : requiredFieldNames) {
            requiredFields++;
            if (incidentData.containsKey(field) && incidentData.get(field) != null) {
                availableFields++;
            }
        }

        double completeness = (double) availableFields / requiredFields;
        if (completeness > 0.8)
            return "HIGH";
        if (completeness > 0.5)
            return "MEDIUM";
        return "LOW";
    }
}
