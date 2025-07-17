package com.o11y.ai.controller;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 链路可视化控制器
 * 基于SkyWalking链路数据生成多种可视化图表（Draw.io格式）
 * 支持火焰图、瀑布图、有向无环图、调用树等多种展现形式
 */
@RestController
@RequestMapping("/api/trace-visualization")
public class TraceVisualizationController {

    private static final Logger LOG = LoggerFactory.getLogger(TraceVisualizationController.class);

    @Autowired
    private JdbcTemplate clickHouseJdbcTemplate;

    /**
     * 生成全局唯一ID（使用时间戳+随机数确保唯一性）
     */
    private String generateUniqueId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }

    /**
     * 为每个图表生成创建一个新的节点ID映射表
     */
    private Map<String, String> createNodeIdMap() {
        return new HashMap<>();
    }

    /**
     * 获取链路的Draw.io DAG图
     */
    @GetMapping("/trace/{traceId}/dag")
    public Map<String, Object> generateTraceDAG(@PathVariable String traceId) {
        try {
            LOG.info("生成链路DAG图: {}", traceId);

            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            String drawioXml = generateDAGDrawio(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "visualizationType", "DAG",
                    "spanCount", spans.size(),
                    "drawioXml", drawioXml,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("生成DAG图失败: {}", traceId, e);
            return createErrorResponse("生成DAG图失败: " + e.getMessage());
        }
    }

    /**
     * 获取链路的瀑布图
     */
    @GetMapping("/trace/{traceId}/waterfall")
    public Map<String, Object> generateTraceWaterfall(@PathVariable String traceId) {
        try {
            LOG.info("生成链路瀑布图: {}", traceId);

            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            String drawioXml = generateWaterfallDrawio(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "visualizationType", "Waterfall",
                    "spanCount", spans.size(),
                    "drawioXml", drawioXml,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("生成瀑布图失败: {}", traceId, e);
            return createErrorResponse("生成瀑布图失败: " + e.getMessage());
        }
    }

    /**
     * 获取链路的火焰图
     */
    @GetMapping("/trace/{traceId}/flame")
    public Map<String, Object> generateTraceFlameGraph(@PathVariable String traceId) {
        try {
            LOG.info("生成链路火焰图: {}", traceId);

            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            String drawioXml = generateFlameGraphDrawio(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "visualizationType", "FlameGraph",
                    "spanCount", spans.size(),
                    "drawioXml", drawioXml,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("生成火焰图失败: {}", traceId, e);
            return createErrorResponse("生成火焰图失败: " + e.getMessage());
        }
    }

    /**
     * 获取链路的调用树
     */
    @GetMapping("/trace/{traceId}/tree")
    public Map<String, Object> generateTraceTree(@PathVariable String traceId) {
        try {
            LOG.info("生成链路调用树: {}", traceId);

            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            String drawioXml = generateTreeDrawio(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "visualizationType", "CallTree",
                    "spanCount", spans.size(),
                    "drawioXml", drawioXml,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("生成调用树失败: {}", traceId, e);
            return createErrorResponse("生成调用树失败: " + e.getMessage());
        }
    }

    /**
     * 获取类似SkyWalking原生的链路视图
     * 特点：水平时间轴、服务分层、Span嵌套关系、详细信息展示
     */
    @GetMapping("/trace/{traceId}/skywalking-style")
    public Map<String, Object> generateSkyWalkingStyleTrace(@PathVariable String traceId) {
        try {
            LOG.info("生成SkyWalking风格链路视图: {}", traceId);

            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            String drawioXml = generateSkyWalkingStyleDrawio(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "visualizationType", "SkyWalkingStyle",
                    "spanCount", spans.size(),
                    "drawioXml", drawioXml,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("生成SkyWalking风格链路视图失败: {}", traceId, e);
            return createErrorResponse("生成SkyWalking风格链路视图失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近链路列表（最近20条）
     */
    @GetMapping("/traces/recent")
    public Map<String, Object> getRecentTraces(
            @RequestParam(defaultValue = "18") int limit,
            @RequestParam(defaultValue = "1") int hours) {
        LOG.info("开始查询最近链路列表: limit={}, hours={}", limit, hours);
        long startTime = System.currentTimeMillis();

        try {
            // 优化 SQL: 限定分区，添加 PREWHERE，使用 FINAL 修饰符
            String sql = "SELECT " +
                    "trace_id, " +
                    "COUNT(*) as span_count, " +
                    "MIN(start_time) as trace_start, " +
                    "MAX(end_time) as trace_end, " +
                    "toUInt64((MAX(end_time) - MIN(start_time)) * 1000) as duration_ms, " +
                    "SUM(is_error) as error_count, " +
                    "groupArray(DISTINCT service) as services " +
                    "FROM events " +
                    "PREWHERE toDate(start_time) >= toDate(now() - INTERVAL ? HOUR) " + // 添加日期分区过滤
                    "WHERE start_time >= now() - INTERVAL ? HOUR " + // 精确时间过滤
                    "GROUP BY trace_id " +
                    "HAVING span_count > 1 " +
                    "ORDER BY trace_start DESC " +
                    "LIMIT ? " +
                    "SETTINGS max_execution_time=10, max_bytes_before_external_group_by=2147483648";

            List<Map<String, Object>> traces = clickHouseJdbcTemplate.queryForList(sql, hours, hours, limit);

            long endTime = System.currentTimeMillis();
            LOG.info("完成查询最近链路列表: 耗时={}ms, 结果数={}", endTime - startTime, traces.size());

            return Map.of(
                    "status", "success",
                    "traces", traces,
                    "count", traces.size());

        } catch (Exception e) {
            LOG.error("获取最近链路失败", e);
            return createErrorResponse("获取最近链路失败: " + e.getMessage());
        }
    }

    /**
     * 获取链路详细信息
     */
    @GetMapping("/trace/{traceId}/details")
    public Map<String, Object> getTraceDetails(@PathVariable String traceId) {
        try {
            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            Map<String, Object> metadata = extractTraceMetadata(spans);
            Map<String, Object> analysis = analyzeTracePerformance(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "spans", spans,
                    "metadata", metadata,
                    "analysis", analysis,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("获取链路详情失败: {}", traceId, e);
            return createErrorResponse("获取链路详情失败: " + e.getMessage());
        }
    }

    /**
     * 验证链路是否在最近一天内
     */
    @GetMapping("/api/traces/validate/{traceId}")
    public Map<String, Object> validateTraceInRecentDay(@PathVariable String traceId) {
        if (StringUtils.isBlank(traceId)) {
            return createErrorResponse("traceId不能为空");
        }

        try {
            // 验证链路是否在最近一天内
            String sql = "SELECT COUNT(*) AS count FROM events " +
                    "WHERE trace_id = ? AND start_time >= now() - INTERVAL 1 DAY";

            Map<String, Object> result = clickHouseJdbcTemplate.queryForMap(sql, traceId);
            long count = ((Number) result.get("count")).longValue();

            Map<String, Object> response = new HashMap<>();
            response.put("exists", count > 0);
            return response;

        } catch (Exception e) {
            LOG.error("验证链路时间范围失败: {}", e.getMessage());
            return createErrorResponse("验证链路时间范围失败: " + e.getMessage());
        }
    }

    /**
     * 从ClickHouse获取链路span数据
     */
    private List<Map<String, Object>> getTraceSpans(String traceId) {
        String sql = "SELECT " +
                "trace_id, " +
                "trace_segment_id, " +
                "service, " +
                "operation_name, " +
                "span_id, " +
                "parent_span_id, " +
                "start_time, " +
                "end_time, " +
                "toUInt64((end_time - start_time) * 1000) as duration_ms, " +
                "is_error, " +
                "span_type, " +
                "span_layer, " +
                "refs_parent_trace_segment_id, " +
                "refs_parent_span_id, " +
                "refs_parent_service " +
                "FROM events " +
                "PREWHERE trace_id = ? " +
                "WHERE start_time >= now() - INTERVAL 1 DAY " +
                "ORDER BY start_time, span_id " +
                "SETTINGS max_execution_time=3, max_bytes_before_external_group_by=2147483648";

        return clickHouseJdbcTemplate.queryForList(sql, traceId);
    }

    /**
     * 提取链路元数据
     */
    private Map<String, Object> extractTraceMetadata(List<Map<String, Object>> spans) {
        if (spans.isEmpty())
            return Map.of();

        LocalDateTime startTime = spans.stream()
                .map(s -> (LocalDateTime) s.get("start_time"))
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        LocalDateTime endTime = spans.stream()
                .map(s -> (LocalDateTime) s.get("end_time"))
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        long totalDuration = spans.stream()
                .mapToLong(s -> ((Number) s.get("duration_ms")).longValue())
                .sum();

        Set<String> services = spans.stream()
                .map(s -> (String) s.get("service"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        long errorCount = spans.stream()
                .mapToLong(s -> {
                    Object isError = s.get("is_error");
                    return (isError != null && ((Number) isError).intValue() > 0) ? 1 : 0;
                })
                .sum();

        return Map.of(
                "startTime", startTime,
                "endTime", endTime,
                "totalDuration", totalDuration,
                "spanCount", spans.size(),
                "serviceCount", services.size(),
                "services", services,
                "errorCount", errorCount,
                "hasErrors", errorCount > 0);
    }

    /**
     * 分析链路性能
     */
    private Map<String, Object> analyzeTracePerformance(List<Map<String, Object>> spans) {
        if (spans.isEmpty())
            return Map.of();

        // 找出最慢的span
        Map<String, Object> slowestSpan = spans.stream()
                .max(Comparator.comparing(s -> ((Number) s.get("duration_ms")).longValue()))
                .orElse(Map.of());

        // 按服务统计耗时
        Map<String, Long> serviceTimings = spans.stream()
                .filter(s -> s.get("service") != null)
                .collect(Collectors.groupingBy(
                        s -> (String) s.get("service"),
                        Collectors.summingLong(s -> ((Number) s.get("duration_ms")).longValue())));

        // 统计错误信息
        List<Map<String, Object>> errors = spans.stream()
                .filter(s -> {
                    Object isError = s.get("is_error");
                    return isError != null && ((Number) isError).intValue() > 0;
                })
                .map(s -> {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("service", s.get("service") != null ? s.get("service") : "Unknown");
                    errorMap.put("operation", s.get("operation_name") != null ? s.get("operation_name") : "Unknown");
                    errorMap.put("error_message", s.get("tag_error_message") != null ? s.get("tag_error_message") : "");
                    errorMap.put("log_message", s.get("log_message") != null ? s.get("log_message") : "");
                    return errorMap;
                })
                .collect(Collectors.toList());

        return Map.of(
                "slowestSpan", slowestSpan,
                "serviceTimings", serviceTimings,
                "errors", errors,
                "criticalPath", calculateCriticalPath(spans));
    }

    /**
     * 计算关键路径
     */
    private List<Map<String, Object>> calculateCriticalPath(List<Map<String, Object>> spans) {
        // 简化的关键路径计算 - 找出从根span到叶子span的最长路径
        Map<String, List<Map<String, Object>>> spansByParent = spans.stream()
                .collect(Collectors.groupingBy(s -> {
                    Object parentSpanId = s.get("parent_span_id");
                    return parentSpanId != null ? parentSpanId.toString() : "-1";
                }));

        // 找到根span（parent_span_id = -1）
        List<Map<String, Object>> roots = spansByParent.getOrDefault("-1", new ArrayList<>());
        if (roots.isEmpty())
            return new ArrayList<>();

        // 递归找到最长路径
        return findLongestPath(roots.get(0), spansByParent);
    }

    private List<Map<String, Object>> findLongestPath(Map<String, Object> span,
            Map<String, List<Map<String, Object>>> spansByParent) {
        List<Map<String, Object>> path = new ArrayList<>();
        path.add(span);

        String spanId = span.get("span_id").toString();
        List<Map<String, Object>> children = spansByParent.getOrDefault(spanId, new ArrayList<>());

        if (!children.isEmpty()) {
            // 找到耗时最长的子span
            Map<String, Object> longestChild = children.stream()
                    .max(Comparator.comparing(s -> ((Number) s.get("duration_ms")).longValue()))
                    .orElse(null);

            if (longestChild != null) {
                path.addAll(findLongestPath(longestChild, spansByParent));
            }
        }

        return path;
    }

    /**
     * 生成DAG Draw.io XML - 基于SegmentReference绘制服务间关系
     */
    private String generateDAGDrawio(List<Map<String, Object>> spans) {
        if (spans == null || spans.isEmpty()) {
            LOG.warn("生成DAG图失败：spans为空");
            return generateEmptyDAG("暂无服务关系数据");
        }

        try {
            // 创建局部节点ID映射表
            Map<String, String> nodeIdMap = createNodeIdMap();

            Map<String, List<Map<String, Object>>> serviceSpans = new HashMap<>();
            Map<String, Set<String>> serviceDependencies = new HashMap<>();

            // 按服务分组
            for (Map<String, Object> span : spans) {
                String service = (String) span.get("service");
                if (service != null) {
                    serviceSpans.computeIfAbsent(service, k -> new ArrayList<>()).add(span);
                }

                // 记录服务依赖关系
                String parentService = (String) span.get("refs_parent_service");
                if (parentService != null && !"\\N".equals(parentService) && !parentService.equals(service)) {
                    serviceDependencies.computeIfAbsent(parentService, k -> new HashSet<>()).add(service);
                }
            }

            if (serviceSpans.isEmpty()) {
                return generateEmptyDAG("未找到有效的服务数据");
            }

            // 计算服务层级
            Set<String> allServices = serviceSpans.keySet();
            Map<String, Integer> serviceLevels = calculateServiceHierarchy(allServices, serviceDependencies);

            // 按层级分组服务
            Map<Integer, List<String>> servicesByLevel = new HashMap<>();
            for (Map.Entry<String, Integer> entry : serviceLevels.entrySet()) {
                servicesByLevel.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<mxfile host=\"app.diagrams.net\">\n");
            xml.append("  <diagram name=\"Service Dependency DAG\" id=\"dag\">\n");
            xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
            xml.append("      <root>\n");
            xml.append("        <mxCell id=\"0\" />\n");
            xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");

            // 按层级生成服务节点
            int levelHeight = 120; // 每层高度
            int nodeWidth = 200;
            int nodeHeight = 80;

            for (int level = 0; level <= serviceLevels.values().stream().mapToInt(Integer::intValue).max()
                    .orElse(0); level++) {
                List<String> servicesInLevel = servicesByLevel.getOrDefault(level, new ArrayList<>());
                int y = 50 + level * levelHeight;

                // 计算该层服务的水平分布
                int totalWidth = servicesInLevel.size() * (nodeWidth + 50); // 节点宽度 + 间距
                int startX = Math.max(100, (1200 - totalWidth) / 2); // 居中显示

                for (int i = 0; i < servicesInLevel.size(); i++) {
                    String service = servicesInLevel.get(i);
                    List<Map<String, Object>> serviceSpanList = serviceSpans.get(service);

                    // 计算服务指标
                    long errorCount = serviceSpanList.stream()
                            .filter(s -> s.get("is_error") != null && ((Number) s.get("is_error")).intValue() > 0)
                            .count();
                    long totalDuration = serviceSpanList.stream()
                            .mapToLong(s -> ((Number) s.get("duration_ms")).longValue())
                            .sum();
                    double avgDuration = serviceSpanList.size() > 0 ? (double) totalDuration / serviceSpanList.size()
                            : 0;

                    // 生成唯一节点ID
                    String nodeId = generateUniqueId("service");
                    nodeIdMap.put(service, nodeId);

                    // 选择节点颜色
                    String fillColor = errorCount > 0 ? "#ffcdd2" : avgDuration > 1000 ? "#fff3e0" : "#e8f5e9";
                    String strokeColor = errorCount > 0 ? "#d32f2f" : "#2e7d32";

                    // 生成节点标签
                    String label = String.format("%s&#xa;调用次数: %d&#xa;平均耗时: %.1fms&#xa;错误数: %d",
                            service, serviceSpanList.size(), avgDuration, errorCount);

                    // 计算X坐标
                    int x = startX + i * (nodeWidth + 50);

                    // 添加节点
                    xml.append(String.format(
                            "        <mxCell id=\"%s\" value=\"%s\" style=\"rounded=1;whiteSpace=wrap;html=1;" +
                                    "fillColor=%s;strokeColor=%s;strokeWidth=2;fontSize=12;fontStyle=1\" vertex=\"1\" parent=\"1\">\n",
                            nodeId, escapeXml(label), fillColor, strokeColor));
                    xml.append(String.format(
                            "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" as=\"geometry\" />\n",
                            x, y, nodeWidth, nodeHeight));
                    xml.append("        </mxCell>\n");
                }
            }

            // 生成服务依赖关系边
            for (Map.Entry<String, Set<String>> entry : serviceDependencies.entrySet()) {
                String sourceService = entry.getKey();
                String sourceId = nodeIdMap.get(sourceService);

                for (String targetService : entry.getValue()) {
                    String targetId = nodeIdMap.get(targetService);
                    if (sourceId != null && targetId != null) {
                        String edgeId = generateUniqueId("edge");
                        xml.append(String.format(
                                "        <mxCell id=\"%s\" value=\"\" style=\"endArrow=classic;html=1;" +
                                        "rounded=1;edgeStyle=orthogonalEdgeStyle;curved=1;strokeWidth=2;fontSize=10\" "
                                        +
                                        "edge=\"1\" parent=\"1\" source=\"%s\" target=\"%s\">\n",
                                edgeId, sourceId, targetId));
                        xml.append("          <mxGeometry relative=\"1\" as=\"geometry\"/>\n");
                        xml.append("        </mxCell>\n");
                    }
                }
            }

            xml.append("      </root>\n");
            xml.append("    </mxGraphModel>\n");
            xml.append("  </diagram>\n");
            xml.append("</mxfile>");

            return xml.toString();

        } catch (Exception e) {
            LOG.error("生成DAG图时发生错误", e);
            return generateEmptyDAG("生成服务关系图时发生错误：" + e.getMessage());
        }
    }

    /**
     * 获取链路的服务调用关系分析
     */
    @GetMapping("/trace/{traceId}/service-relations")
    public Map<String, Object> analyzeServiceRelations(@PathVariable String traceId) {
        try {
            LOG.info("分析链路服务调用关系: {}", traceId);

            List<Map<String, Object>> spans = getTraceSpans(traceId);
            if (spans.isEmpty()) {
                return createErrorResponse("链路数据不存在: " + traceId);
            }

            Map<String, Object> relationAnalysis = analyzeSegmentReferences(spans);

            return Map.of(
                    "status", "success",
                    "traceId", traceId,
                    "serviceRelationAnalysis", relationAnalysis,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("分析服务调用关系失败: {}", traceId, e);
            return createErrorResponse("分析服务调用关系失败: " + e.getMessage());
        }
    }

    /**
     * 基于SegmentReference分析服务间调用关系
     */
    private Map<String, Object> analyzeSegmentReferences(List<Map<String, Object>> spans) {
        Map<String, Object> analysis = new HashMap<>();

        // 收集所有服务
        Set<String> allServices = spans.stream()
                .map(span -> (String) span.get("service"))
                .collect(Collectors.toSet());

        // 分析跨服务调用
        List<Map<String, Object>> crossServiceCalls = new ArrayList<>();
        Map<String, Integer> serviceCallCounts = new HashMap<>();
        Map<String, Set<String>> serviceDependencies = new HashMap<>();

        for (Map<String, Object> span : spans) {
            String service = (String) span.get("service");
            String refsParentService = (String) span.get("refs_parent_service");
            Object refsRefType = span.get("refs_ref_type");
            String refsParentEndpoint = (String) span.get("refs_parent_endpoint");
            String refsNetworkAddress = (String) span.get("refs_network_address_used_at_peer");

            if (refsParentService != null && !"\\N".equals(refsParentService) && !refsParentService.equals(service)
                    && refsRefType != null) {
                // 记录跨服务调用
                Map<String, Object> crossCall = new HashMap<>();
                crossCall.put("fromService", refsParentService);
                crossCall.put("toService", service);
                crossCall.put("refType", refsRefType.toString());
                crossCall.put("parentEndpoint", refsParentEndpoint);
                crossCall.put("networkAddress", refsNetworkAddress);
                crossCall.put("spanOperation", span.get("operation_name"));
                crossCall.put("duration", span.get("duration_ms"));
                crossCall.put("hasError",
                        span.get("is_error") != null && ((Number) span.get("is_error")).intValue() > 0);

                crossServiceCalls.add(crossCall);

                // 统计调用次数
                String callKey = refsParentService + "->" + service;
                serviceCallCounts.put(callKey, serviceCallCounts.getOrDefault(callKey, 0) + 1);

                // 构建依赖关系
                serviceDependencies.computeIfAbsent(refsParentService, k -> new HashSet<>()).add(service);
            }
        }

        // 计算服务层级
        Map<String, Integer> serviceLevels = calculateServiceHierarchy(allServices, serviceDependencies);

        // 识别关键路径
        List<String> criticalPath = findCriticalServicePath(spans, serviceDependencies);

        // 计算服务性能指标
        Map<String, Map<String, Object>> serviceMetrics = calculateServiceMetrics(spans);

        analysis.put("allServices", allServices);
        analysis.put("crossServiceCalls", crossServiceCalls);
        analysis.put("serviceCallCounts", serviceCallCounts);
        analysis.put("serviceDependencies", serviceDependencies);
        analysis.put("serviceLevels", serviceLevels);
        analysis.put("criticalPath", criticalPath);
        analysis.put("serviceMetrics", serviceMetrics);
        analysis.put("summary", Map.of(
                "totalServices", allServices.size(),
                "crossServiceCallsCount", crossServiceCalls.size(),
                "maxServiceLevel", serviceLevels.values().stream().mapToInt(Integer::intValue).max().orElse(0)));

        return analysis;
    }

    private Map<String, Integer> calculateServiceHierarchy(Set<String> services,
            Map<String, Set<String>> dependencies) {
        Map<String, Integer> levels = new HashMap<>();
        Set<String> visited = new HashSet<>();

        // 找到根服务
        Set<String> targetServices = dependencies.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        Set<String> rootServices = services.stream()
                .filter(s -> !targetServices.contains(s))
                .collect(Collectors.toSet());

        if (rootServices.isEmpty() && !services.isEmpty()) {
            rootServices.add(services.iterator().next());
        }

        // 递归分配层级
        for (String root : rootServices) {
            assignHierarchyLevel(root, 0, levels, visited, dependencies);
        }

        // 为未分配的服务设置默认层级
        for (String service : services) {
            levels.putIfAbsent(service, 0);
        }

        return levels;
    }

    private void assignHierarchyLevel(String service, int level, Map<String, Integer> levels,
            Set<String> visited, Map<String, Set<String>> dependencies) {
        if (visited.contains(service))
            return;

        visited.add(service);
        levels.put(service, Math.max(levels.getOrDefault(service, 0), level));

        Set<String> dependentServices = dependencies.getOrDefault(service, new HashSet<>());
        for (String dependent : dependentServices) {
            assignHierarchyLevel(dependent, level + 1, levels, visited, dependencies);
        }
    }

    private List<String> findCriticalServicePath(List<Map<String, Object>> spans,
            Map<String, Set<String>> dependencies) {
        // 基于实际调用关系构建关键路径
        List<String> criticalPath = new ArrayList<>();

        // 找到入口服务（没有被其他服务调用的服务）
        Set<String> allServices = dependencies.keySet();
        Set<String> targetServices = dependencies.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        Set<String> entryServices = allServices.stream()
                .filter(service -> !targetServices.contains(service))
                .collect(Collectors.toSet());

        // 如果没有明确的入口服务，找到最早开始的服务
        if (entryServices.isEmpty()) {
            Map<String, LocalDateTime> serviceStartTimes = spans.stream()
                    .collect(Collectors.groupingBy(
                            span -> (String) span.get("service"),
                            Collectors.mapping(
                                    span -> (LocalDateTime) span.get("start_time"),
                                    Collectors.minBy(LocalDateTime::compareTo))))
                    .entrySet().stream()
                    .filter(entry -> entry.getValue().isPresent())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().get()));

            String earliestService = serviceStartTimes.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (earliestService != null) {
                entryServices.add(earliestService);
            }
        }

        // 从入口服务开始，按照调用关系构建路径
        if (!entryServices.isEmpty()) {
            String startService = entryServices.iterator().next();
            buildPathFromService(startService, dependencies, criticalPath, new HashSet<>());
        }

        return criticalPath;
    }

    /**
     * 从指定服务开始递归构建调用路径
     */
    private void buildPathFromService(String service, Map<String, Set<String>> dependencies,
            List<String> path, Set<String> visited) {
        if (visited.contains(service))
            return;

        visited.add(service);
        path.add(service);

        // 找到该服务调用的下游服务
        Set<String> downstreamServices = dependencies.getOrDefault(service, new HashSet<>());

        // 如果有多个下游服务，选择调用链最长的那个
        if (!downstreamServices.isEmpty()) {
            // 这里可以进一步优化，选择最关键的下游服务
            String nextService = downstreamServices.iterator().next();
            buildPathFromService(nextService, dependencies, path, visited);
        }
    }

    private Map<String, Map<String, Object>> calculateServiceMetrics(List<Map<String, Object>> spans) {
        return spans.stream()
                .collect(Collectors.groupingBy(span -> (String) span.get("service")))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<Map<String, Object>> serviceSpans = entry.getValue();
                            long totalDuration = serviceSpans.stream()
                                    .mapToLong(span -> ((Number) span.get("duration_ms")).longValue())
                                    .sum();
                            long errorCount = serviceSpans.stream()
                                    .mapToLong(span -> span.get("is_error") != null &&
                                            ((Number) span.get("is_error")).intValue() > 0 ? 1 : 0)
                                    .sum();

                            return Map.of(
                                    "spanCount", serviceSpans.size(),
                                    "totalDuration", totalDuration,
                                    "avgDuration", serviceSpans.size() > 0 ? totalDuration / serviceSpans.size() : 0,
                                    "errorCount", errorCount,
                                    "errorRate",
                                    serviceSpans.size() > 0 ? (double) errorCount / serviceSpans.size() : 0.0);
                        }));
    }

    /**
     * 获取最近一天的数据统计
     */
    @GetMapping("/stats/recent-hour")
    public Map<String, Object> getRecentHourStats() {
        try {
            // 系统概览：统计最近1小时内的所有数据
            String sql = "SELECT " +
                    "COUNT(DISTINCT trace_id) as trace_count, " +
                    "COUNT(*) as span_count, " +
                    "COUNT(DISTINCT service) as service_count, " +
                    "COUNT(DISTINCT service_instance) as instance_count, " +
                    "SUM(is_error) as error_count, " +
                    // Avoid DECIMAL_OVERFLOW: cast duration to Float64 before multiply
                    "AVG(toFloat64(end_time - start_time) * 1000) as avg_duration_ms, " +
                    "MIN(start_time) as earliest_time, " +
                    "MAX(start_time) as latest_time " +
                    "FROM events " +
                    "WHERE start_time >= now() - INTERVAL 1 HOUR " +
                    "SETTINGS max_execution_time=3";

            List<Map<String, Object>> result = clickHouseJdbcTemplate.queryForList(sql);
            Map<String, Object> stats = result.isEmpty() ? Map.of() : result.get(0);

            return Map.of(
                    "status", "success",
                    "timeRange", "最近1小时",
                    "stats", stats,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("获取最近一小时数据统计失败", e);
            return createErrorResponse("获取数据统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取最近18笔交易的链路列表
     */
    @GetMapping("/traces/recent-18")
    public Map<String, Object> getRecent18Traces() {
        LOG.info("开始查询最近18笔交易链路列表");
        long startTime = System.currentTimeMillis();

        try {
            String sql = "SELECT " +
                    "trace_id, " +
                    "COUNT(*) as span_count, " +
                    "MIN(start_time) as trace_start, " +
                    "MAX(end_time) as trace_end, " +
                    "toUInt64((MAX(end_time) - MIN(start_time)) * 1000) as duration_ms, " +
                    "SUM(is_error) as error_count, " +
                    "groupArray(DISTINCT service) as services " +
                    "FROM events " +
                    "PREWHERE toDate(start_time) >= toDate(now() - INTERVAL 1 HOUR) " +
                    "AND service IN ('python-sample', 'dubbo-consumer', 'dubbo-provider-a', 'dubbo-provider-b') " +
                    "WHERE start_time >= now() - INTERVAL 1 HOUR " +
                    "GROUP BY trace_id " +
                    "HAVING span_count > 1 " +
                    "ORDER BY trace_start DESC " +
                    "LIMIT 18 " +
                    "SETTINGS max_execution_time=3, " +
                    "max_bytes_before_external_group_by=2147483648, " +
                    "max_memory_usage=10000000000";

            List<Map<String, Object>> traces = clickHouseJdbcTemplate.queryForList(sql);

            long endTime = System.currentTimeMillis();
            LOG.info("完成查询最近18笔交易链路列表: 耗时={}ms, 结果数={}", endTime - startTime, traces.size());

            return Map.of(
                    "status", "success",
                    "traces", traces,
                    "count", traces.size(),
                    "timeRange", "最近1小时");

        } catch (Exception e) {
            LOG.error("获取最近18笔交易链路失败", e);
            return createErrorResponse("获取最近18笔交易链路失败: " + e.getMessage());
        }
    }

    // 工具方法: 生成错误响应
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }

    // 工具方法: XML特殊字符转义
    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&") // 保留&，不转义
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // 工具方法: 生成空的DAG图
    private String generateEmptyDAG(String message) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<mxfile host=\"app.diagrams.net\">\n");
        xml.append("  <diagram name=\"Empty DAG\" id=\"dag\">\n");
        xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
        xml.append("      <root>\n");
        xml.append("        <mxCell id=\"0\" />\n");
        xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");
        xml.append("        <mxCell id=\"empty\" value=\"" + escapeXml(message)
                + "\" style=\"text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=14;fontStyle=1\" vertex=\"1\" parent=\"1\">\n");
        xml.append("          <mxGeometry x=\"400\" y=\"360\" width=\"400\" height=\"80\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");
        xml.append("      </root>\n");
        xml.append("    </mxGraphModel>\n");
        xml.append("  </diagram>\n");
        xml.append("</mxfile>");
        return xml.toString();
    }

    private String generateWaterfallDrawio(List<Map<String, Object>> spans) {
        // 防空保护
        if (spans == null || spans.isEmpty()) {
            return generateEmptyDAG("暂无瀑布图数据");
        }

        try {
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<mxfile host=\"app.diagrams.net\">\n");
            xml.append("  <diagram name=\"Waterfall\" id=\"waterfall\">\n");
            xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
            xml.append("      <root>\n");
            xml.append("        <mxCell id=\"0\" />\n");
            xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");

            // 按开始时间排序
            spans.sort((a, b) -> {
                LocalDateTime timeA = (LocalDateTime) a.get("start_time");
                LocalDateTime timeB = (LocalDateTime) b.get("start_time");
                return timeA.compareTo(timeB);
            });

            // 找到最早和最晚的时间
            LocalDateTime minTime = spans.stream()
                    .map(s -> (LocalDateTime) s.get("start_time"))
                    .min(LocalDateTime::compareTo)
                    .orElseThrow(() -> new IllegalStateException("No valid start time found"));

            LocalDateTime maxTime = spans.stream()
                    .map(s -> (LocalDateTime) s.get("end_time"))
                    .max(LocalDateTime::compareTo)
                    .orElse(minTime);

            // 计算时间轴
            long totalDurationMs = java.time.Duration.between(minTime, maxTime).toMillis();

            // 按服务分组
            Map<String, List<Map<String, Object>>> serviceSpans = spans.stream()
                    .collect(Collectors.groupingBy(span -> (String) span.get("service")));

            // 计算服务层级（基于refs_parent_service）
            Map<String, Integer> serviceLevels = calculateServiceLevelsForWaterfall(spans);

            // 按层级排序服务
            List<String> sortedServices = serviceSpans.keySet().stream()
                    .sorted(Comparator.comparing(service -> serviceLevels.getOrDefault(service, 0)))
                    .collect(Collectors.toList());

            int y = 50;
            int width;
            String fillColor;
            String strokeColor;

            for (String service : sortedServices) {
                List<Map<String, Object>> serviceSpanList = serviceSpans.get(service);

                // 为每个服务添加服务标签
                xml.append(String.format(
                        "        <mxCell id=\"service_%s\" value=\"%s\" style=\"text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;whiteSpace=wrap;rounded=0;fontSize=14;fontStyle=1\" vertex=\"1\" parent=\"1\">\n",
                        service.hashCode(), escapeXml(service)));
                xml.append(String.format(
                        "          <mxGeometry x=\"10\" y=\"%d\" width=\"150\" height=\"30\" as=\"geometry\" />\n",
                        y - 10));
                xml.append("        </mxCell>\n");

                // 按时间排序该服务的所有span
                serviceSpanList.sort((a, b) -> {
                    LocalDateTime timeA = (LocalDateTime) a.get("start_time");
                    LocalDateTime timeB = (LocalDateTime) b.get("start_time");
                    return timeA.compareTo(timeB);
                });

                for (Map<String, Object> span : serviceSpanList) {
                    String operation = (String) span.get("operation_name");
                    String spanType = (String) span.get("span_type");
                    if (operation == null)
                        operation = "unknown";

                    LocalDateTime startTime = (LocalDateTime) span.get("start_time");
                    long duration = ((Number) span.get("duration_ms")).longValue();
                    boolean isError = span.get("is_error") != null && ((Number) span.get("is_error")).intValue() > 0;

                    // 计算位置和宽度
                    long offsetMs = java.time.Duration.between(minTime, startTime).toMillis();
                    int x = totalDurationMs > 0 ? (int) (800 * offsetMs / totalDurationMs) + 200 : 200;
                    // 修复0ms span的宽度显示问题
                    if (duration == 0) {
                        width = 20; // 0ms的span使用最小宽度
                    } else {
                        width = totalDurationMs > 0 ? (int) Math.max(50, 800 * duration / totalDurationMs) : 50;
                    }

                    // 根据错误状态和span_type选择颜色
                    if ("Exit".equalsIgnoreCase(spanType)) {
                        fillColor = "#ede7f6"; // 淡紫色
                        strokeColor = "#7e57c2";
                    } else if (isError) {
                        fillColor = "#ffcdd2";
                        strokeColor = "#d32f2f";
                    } else if (duration > 1000) {
                        fillColor = "#fff3e0";
                        strokeColor = "#f57c00";
                    } else {
                        fillColor = "#e8f5e9";
                        strokeColor = "#2e7d32";
                    }

                    // 生成节点标签
                    String label = String.format("%s&#xa;%dms",
                            escapeXml(operation), duration);

                    // 添加瀑布图节点
                    xml.append(String.format(
                            "        <mxCell id=\"span_%d\" value=\"%s\" style=\"rounded=1;whiteSpace=wrap;html=1;" +
                                    "fillColor=%s;strokeColor=%s;strokeWidth=2;fontSize=10;fontStyle=1\" vertex=\"1\" parent=\"1\">\n",
                            span.hashCode(), label, fillColor, strokeColor));
                    xml.append(String.format(
                            "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"30\" as=\"geometry\" />\n",
                            x, y, width));
                    xml.append("        </mxCell>\n");
                }

                y += 80; // 服务间的间距
            }

            xml.append("      </root>\n");
            xml.append("    </mxGraphModel>\n");
            xml.append("  </diagram>\n");
            xml.append("</mxfile>");

            return xml.toString();

        } catch (Exception e) {
            LOG.error("生成瀑布图时发生错误", e);
            return generateEmptyDAG("生成瀑布图时发生错误：" + e.getMessage());
        }
    }

    /**
     * 为瀑布图计算服务层级
     */
    private Map<String, Integer> calculateServiceLevelsForWaterfall(List<Map<String, Object>> spans) {
        Map<String, Integer> levels = new HashMap<>();
        Map<String, Set<String>> dependencies = new HashMap<>();

        // 构建服务依赖关系
        for (Map<String, Object> span : spans) {
            String service = (String) span.get("service");
            String refsParentService = (String) span.get("refs_parent_service");

            if (refsParentService != null && !"\\N".equals(refsParentService) && !refsParentService.equals(service)) {
                dependencies.computeIfAbsent(refsParentService, k -> new HashSet<>()).add(service);
            }
        }

        // 计算层级
        Set<String> allServices = spans.stream()
                .map(span -> (String) span.get("service"))
                .collect(Collectors.toSet());

        // 找到根服务（没有被其他服务调用的服务）
        Set<String> targetServices = dependencies.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        Set<String> rootServices = allServices.stream()
                .filter(s -> !targetServices.contains(s))
                .collect(Collectors.toSet());

        // 递归分配层级
        for (String root : rootServices) {
            assignHierarchyLevel(root, 0, levels, new HashSet<>(), dependencies);
        }

        // 为未分配的服务设置默认层级
        for (String service : allServices) {
            levels.putIfAbsent(service, 0);
        }

        return levels;
    }

    private String generateFlameGraphDrawio(List<Map<String, Object>> spans) {
        if (spans == null || spans.isEmpty()) {
            return generateEmptyDAG("暂无火焰图数据");
        }

        try {
            // 1. 初始化XML和ID映射
            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<mxfile host=\"app.diagrams.net\">\n");
            xml.append("  <diagram name=\"Flame Graph\" id=\"flame\">\n");
            xml.append("    <mxGraphModel dx=\"1400\" dy=\"900\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
            xml.append("      <root>\n");
            xml.append("        <mxCell id=\"0\" />\n");
            xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");
            Map<String, String> nodeIdMap = createNodeIdMap();

            // 2. 构建完整的调用树（包括跨服务）
            Map<String, Map<String, Object>> spanMap = new HashMap<>();
            Map<String, List<String>> childrenMap = new HashMap<>();
            for (Map<String, Object> span : spans) {
                String uniqueId = getUniqueSpanId(span);
                spanMap.put(uniqueId, span);
                String parentUniqueId = getParentUniqueId(span);
                if (parentUniqueId != null) {
                    childrenMap.computeIfAbsent(parentUniqueId, k -> new ArrayList<>()).add(uniqueId);
                }
            }

            // 3. 找到所有根节点
            Set<String> allChildren = childrenMap.values().stream().flatMap(List::stream).collect(Collectors.toSet());
            List<Map<String, Object>> rootSpans = spanMap.values().stream()
                    .filter(s -> !allChildren.contains(getUniqueSpanId(s)))
                    .sorted(Comparator.comparing(s -> (LocalDateTime) s.get("start_time")))
                    .collect(Collectors.toList());

            if (rootSpans.isEmpty()) {
                 return generateEmptyDAG("未找到根节点，无法生成火焰图");
            }

            // 4. 计算全局时间范围和缩放比例（火焰图核心：X轴代表时间线）
            LocalDateTime globalStartTime = spans.stream()
                .map(s -> (LocalDateTime) s.get("start_time"))
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
            LocalDateTime globalEndTime = spans.stream()
                .map(s -> (LocalDateTime) s.get("end_time"))
                .max(LocalDateTime::compareTo)
                .orElse(globalStartTime);
            long totalDurationMs = java.time.Duration.between(globalStartTime, globalEndTime).toMillis();
            if (totalDurationMs == 0) totalDurationMs = 1; // 防止除以零

            double graphWidth = 1200.0;
            double timeScale = graphWidth / totalDurationMs;
            int levelHeight = 35;
            int startY = 50; // 火焰图从顶部开始

            // 5. 递归绘制火焰图：根节点在顶部，调用栈向下增长
            for (Map<String, Object> rootSpan : rootSpans) {
                drawFlameNode(xml, rootSpan, spanMap, childrenMap, globalStartTime, timeScale, startY, 0, nodeIdMap, levelHeight);
            }

            xml.append("      </root>\n");
            xml.append("    </mxGraphModel>\n");
            xml.append("  </diagram>\n");
            xml.append("</mxfile>");

            return xml.toString();

        } catch (Exception e) {
            LOG.error("生成火焰图时发生错误", e);
            return generateEmptyDAG("生成火焰图时发生错误: " + e.getMessage());
        }
    }

    private void drawFlameNode(StringBuilder xml, Map<String, Object> span,
                               Map<String, Map<String, Object>> spanMap,
                               Map<String, List<String>> childrenMap,
                               LocalDateTime globalStartTime, double timeScale,
                               int baseY, int depth,
                               Map<String, String> nodeIdMap, int levelHeight) {
        if (span == null) return;

        // 1. 基于start_time计算X轴位置（时间线）
        LocalDateTime startTime = (LocalDateTime) span.get("start_time");
        long duration = ((Number) span.get("duration_ms")).longValue();
        long offsetMs = java.time.Duration.between(globalStartTime, startTime).toMillis();

        int x = (int) (offsetMs * timeScale) + 50; // 50是左侧边距
        
        // 2. 基于duration_ms计算宽度（火焰图核心：宽度正比于耗时）
        int width = Math.max(1, (int) (duration * timeScale));
        
        // 3. 基于调用栈深度计算Y轴位置（火焰图核心：Y轴代表调用栈深度）
        int y = baseY + (depth * levelHeight);

        // 4. 绘制当前Span的矩形
        String service = (String) span.get("service");
        String operation = (String) span.get("operation_name");
        boolean isError = span.get("is_error") != null && ((Number) span.get("is_error")).intValue() > 0;
        
        // 使用SkyWalking标准识别span类型
        boolean isEntry = isEntrySpan(span);
        boolean isExit = isExitSpan(span);
        
        String fillColor = isError ? "#ffcdd2" : (isExit ? "#ede7f6" : (isEntry ? "#e3f2fd" : "#e8f5e9"));
        String strokeColor = isError ? "#d32f2f" : (isExit ? "#7e57c2" : (isEntry ? "#1976d2" : "#2e7d32"));

        String label = String.format("%s&#xa;%s (%dms)",
                escapeXml(service != null ? service : "unknown"),
                escapeXml(operation != null ? operation : "unknown"),
                duration);

        String uniqueId = generateUniqueId("flame");
        nodeIdMap.put(getUniqueSpanId(span), uniqueId);

        xml.append(String.format(
                "        <mxCell id=\"%s\" value=\"%s\" style=\"rounded=0;whiteSpace=wrap;html=1;" +
                        "fillColor=%s;strokeColor=%s;strokeWidth=1;fontSize=10;fontStyle=1;align=left;spacingLeft=4;\" vertex=\"1\" parent=\"1\">\n",
                uniqueId, label, fillColor, strokeColor));
        xml.append(String.format(
                "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" as=\"geometry\" />\n",
                x, y, width, levelHeight - 2)); // 留出层级间的间隙
        xml.append("        </mxCell>\n");

        // 5. 递归绘制子节点（调用栈向下增长）
        String parentUniqueId = getUniqueSpanId(span);
        List<String> childIds = childrenMap.getOrDefault(parentUniqueId, Collections.emptyList());
        
        // 保证子节点按时间顺序绘制
        childIds.sort(Comparator.comparing(id -> (LocalDateTime) spanMap.get(id).get("start_time")));

        for (String childId : childIds) {
            Map<String, Object> childSpan = spanMap.get(childId);
            drawFlameNode(xml, childSpan, spanMap, childrenMap, globalStartTime, timeScale, baseY, depth + 1, nodeIdMap, levelHeight);
        }
    }

    /**
     * 为Span生成一个在Trace内唯一的ID
     */
    private String getUniqueSpanId(Map<String, Object> span) {
        return span.get("trace_segment_id") + "_" + span.get("span_id");
    }

    /**
     * 获取Span的父级唯一ID，能处理跨服务和同服务两种情况
     */
    private String getParentUniqueId(Map<String, Object> span) {
        // 优先处理跨服务的父级 (refs_*)
        String parentSegmentId = (String) span.get("refs_parent_trace_segment_id");
        if (parentSegmentId != null && !parentSegmentId.isEmpty() && !"\\N".equals(parentSegmentId)) {
            Object parentSpanIdObj = span.get("refs_parent_span_id");
            if (parentSpanIdObj != null) {
                return parentSegmentId + "_" + parentSpanIdObj;
            }
        }

        // 处理同一服务内的父级 (parent_span_id)
        Object parentSpanIdObj = span.get("parent_span_id");
        if (parentSpanIdObj != null) {
            String parentSpanId = String.valueOf(parentSpanIdObj);
            if (!"-1".equals(parentSpanId)) {
                return span.get("trace_segment_id") + "_" + parentSpanId;
            }
        }
        return null;
    }

    /**
     * 识别EntrySpan: CrossProcess引用 + parent_span_id=-1
     */
    private boolean isEntrySpan(Map<String, Object> span) {
        Object refsRefType = span.get("refs_ref_type");
        Object parentSpanId = span.get("parent_span_id");
        return "CrossProcess".equals(refsRefType) && 
               parentSpanId != null && "-1".equals(String.valueOf(parentSpanId));
    }

    /**
     * 识别ExitSpan: 被其他span的refs_*字段引用
     */
    private boolean isExitSpan(Map<String, Object> span) {
        // 简化版本：检查span_type字段或operation_name是否包含客户端特征
        String spanType = (String) span.get("span_type");
        String operationName = (String) span.get("operation_name");
        
        return "Exit".equalsIgnoreCase(spanType) || 
               (operationName != null && (operationName.contains("HTTP") || operationName.contains("RPC")));
    }

    private String generateTreeDrawio(List<Map<String, Object>> spans) {
        if (spans == null || spans.isEmpty()) {
            return generateEmptyDAG("暂无调用树数据");
        }

        try {
            // 创建局部节点ID映射表
            Map<String, String> nodeIdMap = createNodeIdMap();

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<mxfile host=\"app.diagrams.net\">\n");
            xml.append("  <diagram name=\"Call Tree\" id=\"tree\">\n");
            xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
            xml.append("      <root>\n");
            xml.append("        <mxCell id=\"0\" />\n");
            xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");

            // 构建调用树 - 基于SegmentReference建立跨服务调用关系
            Map<String, Map<String, Object>> spanMap = new HashMap<>();
            Map<String, List<String>> childrenMap = new HashMap<>();
            Set<String> rootSpanIds = new HashSet<>();

            for (Map<String, Object> span : spans) {
                // 使用trace_segment_id + span_id作为唯一标识
                String traceSegmentId = String.valueOf(span.get("trace_segment_id"));
                String spanId = String.valueOf(span.get("span_id"));
                String uniqueSpanId = traceSegmentId + "_" + spanId;

                spanMap.put(uniqueSpanId, span);

                // 1. 处理同一segment内的父子关系
                Object parentSpanIdObj = span.get("parent_span_id");
                String parentSpanId = parentSpanIdObj != null ? String.valueOf(parentSpanIdObj) : null;

                if (parentSpanId != null && !"-1".equals(parentSpanId)) {
                    String uniqueParentId = traceSegmentId + "_" + parentSpanId;
                    childrenMap.computeIfAbsent(uniqueParentId, k -> new ArrayList<>()).add(uniqueSpanId);
                }

                // 2. 处理跨服务的调用关系（基于SegmentReference）
                String refsParentTraceSegmentId = (String) span.get("refs_parent_trace_segment_id");
                Object refsParentSpanIdObj = span.get("refs_parent_span_id");

                if (refsParentTraceSegmentId != null && refsParentSpanIdObj != null) {
                    String refsParentSpanId = String.valueOf(refsParentSpanIdObj);
                    String uniqueRefsParentId = refsParentTraceSegmentId + "_" + refsParentSpanId;

                    // 建立跨服务的父子关系
                    childrenMap.computeIfAbsent(uniqueRefsParentId, k -> new ArrayList<>()).add(uniqueSpanId);

                    LOG.debug("建立跨服务调用关系: {} -> {}", uniqueRefsParentId, uniqueSpanId);
                }
            }

            // 3. 找到真正的根节点（没有任何父节点的节点）
            Set<String> allChildren = childrenMap.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());

            for (String spanId : spanMap.keySet()) {
                if (!allChildren.contains(spanId)) {
                    rootSpanIds.add(spanId);
                }
            }

            // 如果没有找到根节点，使用parent_span_id为-1的span作为根节点
            if (rootSpanIds.isEmpty()) {
                for (Map<String, Object> span : spans) {
                    Object parentSpanIdObj = span.get("parent_span_id");
                    String parentSpanId = parentSpanIdObj != null ? String.valueOf(parentSpanIdObj) : null;
                    if ("-1".equals(parentSpanId)) {
                        String traceSegmentId = String.valueOf(span.get("trace_segment_id"));
                        String spanId = String.valueOf(span.get("span_id"));
                        rootSpanIds.add(traceSegmentId + "_" + spanId);
                    }
                }
            }

            LOG.info("构建调用树：总span数={}, 根节点数={}, 根节点={}", spans.size(), rootSpanIds.size(), rootSpanIds);

            // 递归生成调用树
            int startY = 50;
            int startX = 50;
            int currentY = startY;

            for (String rootSpanId : rootSpanIds) {
                currentY = drawTreeNode(xml, spanMap, childrenMap, rootSpanId, startX, currentY, 0, nodeIdMap);
                currentY += 50; // 多个根节点之间的间距
            }

            xml.append("      </root>\n");
            xml.append("    </mxGraphModel>\n");
            xml.append("  </diagram>\n");
            xml.append("</mxfile>");

            return xml.toString();

        } catch (Exception e) {
            LOG.error("生成调用树时发生错误", e);
            return generateEmptyDAG("生成调用树时发生错误：" + e.getMessage());
        }
    }

    private int drawTreeNode(StringBuilder xml, Map<String, Map<String, Object>> spanMap,
            Map<String, List<String>> childrenMap, String spanId, int x, int y, int level,
            Map<String, String> nodeIdMap) {
        Map<String, Object> span = spanMap.get(spanId);
        if (span == null)
            return y;

        // 计算节点属性
        String service = (String) span.get("service");
        String operation = (String) span.get("operation_name");
        long duration = ((Number) span.get("duration_ms")).longValue();
        boolean isError = span.get("is_error") != null && ((Number) span.get("is_error")).intValue() > 0;
        String spanType = (String) span.get("span_type");

        // 选择颜色
        String fillColor;
        String strokeColor;
        if ("Exit".equalsIgnoreCase(spanType)) {
            fillColor = "#ede7f6";
            strokeColor = "#7e57c2";
        } else if (isError) {
            fillColor = "#ffcdd2";
            strokeColor = "#d32f2f";
        } else if (duration > 1000) {
            fillColor = "#fff3e0";
            strokeColor = "#f57c00";
        } else {
            fillColor = "#e8f5e9";
            strokeColor = "#2e7d32";
        }

        // 生成节点标签
        String label = String.format("%s&#xa;%s&#xa;%dms",
                escapeXml(service != null ? service : "unknown"),
                escapeXml(operation != null ? operation : "unknown"),
                duration);

        // 使用全局唯一ID添加树节点
        String uniqueId = generateUniqueId("tree");
        nodeIdMap.put(spanId, uniqueId);

        // 根据层级调整x坐标
        int nodeX = x + level * 250;

        xml.append(String.format(
                "        <mxCell id=\"%s\" value=\"%s\" style=\"rounded=1;whiteSpace=wrap;html=1;" +
                        "fillColor=%s;strokeColor=%s;strokeWidth=2;fontSize=12;fontStyle=1\" vertex=\"1\" parent=\"1\">\n",
                uniqueId, label, fillColor, strokeColor));
        xml.append(String.format(
                "          <mxGeometry x=\"%d\" y=\"%d\" width=\"200\" height=\"60\" as=\"geometry\" />\n",
                nodeX, y));
        xml.append("        </mxCell>\n");

        int currentY = y + 100;

        // 查找并绘制子节点
        List<String> children = childrenMap.get(spanId);
        if (children != null && !children.isEmpty()) {
            for (String childId : children) {
                // 递归绘制子节点
                currentY = drawTreeNode(xml, spanMap, childrenMap, childId, x, currentY, level + 1, nodeIdMap);

                // 添加连接线，使用映射的唯一ID
                String childUniqueId = nodeIdMap.get(childId);
                if (childUniqueId != null) {
                    String edgeId = generateUniqueId("edge");
                    xml.append(String.format(
                            "        <mxCell id=\"%s\" value=\"\" style=\"endArrow=classic;html=1;" +
                                    "rounded=1;edgeStyle=orthogonalEdgeStyle;curved=1;strokeWidth=2\" " +
                                    "edge=\"1\" parent=\"1\" source=\"%s\" target=\"%s\">\n",
                            edgeId, uniqueId, childUniqueId));
                    xml.append("          <mxGeometry relative=\"1\" as=\"geometry\"/>\n");
                    xml.append("        </mxCell>\n");
                }

                currentY += 20; // 子节点间的间距
            }
        }

        return currentY;
    }

    /**
     * 生成类似SkyWalking原生的链路视图Draw.io XML
     * 特点：水平时间轴布局、服务分层显示、Span类型区分、嵌套关系展示
     */
    private String generateSkyWalkingStyleDrawio(List<Map<String, Object>> spans) {
        if (spans == null || spans.isEmpty()) {
            return generateEmptyDAG("暂无链路数据");
        }

        try {
            // 创建局部节点ID映射表
            Map<String, String> nodeIdMap = createNodeIdMap();

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<mxfile host=\"app.diagrams.net\">\n");
            xml.append("  <diagram name=\"SkyWalking Style Trace\" id=\"skywalking\">\n");
            xml.append("    <mxGraphModel dx=\"1400\" dy=\"900\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
            xml.append("      <root>\n");
            xml.append("        <mxCell id=\"0\" />\n");
            xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");

            // 按开始时间排序所有spans
            spans.sort((a, b) -> {
                LocalDateTime timeA = (LocalDateTime) a.get("start_time");
                LocalDateTime timeB = (LocalDateTime) b.get("start_time");
                return timeA.compareTo(timeB);
            });

            // 计算时间范围
            LocalDateTime minTime = spans.stream()
                    .map(s -> (LocalDateTime) s.get("start_time"))
                    .min(LocalDateTime::compareTo)
                    .orElseThrow(() -> new IllegalStateException("No valid start time found"));

            LocalDateTime maxTime = spans.stream()
                    .map(s -> (LocalDateTime) s.get("end_time"))
                    .max(LocalDateTime::compareTo)
                    .orElse(minTime);

            long totalDurationMs = java.time.Duration.between(minTime, maxTime).toMillis();
            LOG.info("链路总耗时: {}ms", totalDurationMs);

            // 按服务分组spans
            Map<String, List<Map<String, Object>>> serviceSpans = spans.stream()
                    .collect(Collectors.groupingBy(span -> (String) span.get("service")));

            // 构建span层次结构（基于同segment内的parent-child关系）
            Map<String, List<Map<String, Object>>> spanHierarchy = buildSpanHierarchy(spans);

            // 按服务层级排序（基于refs_parent_service）
            List<String> sortedServices = sortServicesByDependency(spans, serviceSpans.keySet());

            // 绘制时间轴
            drawTimeAxis(xml, minTime, maxTime, totalDurationMs);

            // 绘制服务层和spans
            int currentY = 120;
            for (String service : sortedServices) {
                List<Map<String, Object>> serviceSpanList = serviceSpans.get(service);

                // 绘制服务标题行
                drawServiceHeader(xml, service, currentY);

                // 绘制该服务的所有spans（按层次结构）
                currentY = drawServiceSpans(xml, serviceSpanList, spanHierarchy, minTime, totalDurationMs, currentY,
                        nodeIdMap);
                currentY += 40; // 服务间间距
            }

            xml.append("      </root>\n");
            xml.append("    </mxGraphModel>\n");
            xml.append("  </diagram>\n");
            xml.append("</mxfile>");

            return xml.toString();

        } catch (Exception e) {
            LOG.error("生成SkyWalking风格链路视图时发生错误", e);
            return generateEmptyDAG("生成SkyWalking风格链路视图时发生错误：" + e.getMessage());
        }
    }

    /**
     * 构建span层次结构
     */
    private Map<String, List<Map<String, Object>>> buildSpanHierarchy(List<Map<String, Object>> spans) {
        Map<String, List<Map<String, Object>>> hierarchy = new HashMap<>();

        // 按segment分组
        Map<String, List<Map<String, Object>>> segmentSpans = spans.stream()
                .collect(Collectors.groupingBy(span -> (String) span.get("trace_segment_id")));

        for (List<Map<String, Object>> segmentSpanList : segmentSpans.values()) {
            // 在每个segment内部构建父子关系
            Map<String, List<Map<String, Object>>> childrenMap = new HashMap<>();

            for (Map<String, Object> span : segmentSpanList) {
                String spanId = String.valueOf(span.get("span_id"));
                Object parentSpanIdObj = span.get("parent_span_id");
                String parentSpanId = parentSpanIdObj != null ? String.valueOf(parentSpanIdObj) : null;

                if (parentSpanId != null && !"-1".equals(parentSpanId)) {
                    String parentKey = span.get("trace_segment_id") + "_" + parentSpanId;
                    childrenMap.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(span);
                } else {
                    // 根span
                    String rootKey = span.get("trace_segment_id") + "_root";
                    hierarchy.computeIfAbsent(rootKey, k -> new ArrayList<>()).add(span);
                }
            }

            // 将子span关系加入hierarchy
            hierarchy.putAll(childrenMap);
        }

        return hierarchy;
    }

    /**
     * 按服务依赖关系排序服务
     */
    private List<String> sortServicesByDependency(List<Map<String, Object>> spans, Set<String> allServices) {
        Map<String, Set<String>> dependencies = new HashMap<>();

        // 构建服务依赖关系
        for (Map<String, Object> span : spans) {
            String service = (String) span.get("service");
            String refsParentService = (String) span.get("refs_parent_service");

            if (refsParentService != null && !"\\N".equals(refsParentService) && !refsParentService.equals(service)) {
                dependencies.computeIfAbsent(refsParentService, k -> new HashSet<>()).add(service);
            }
        }

        // 拓扑排序
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String service : allServices) {
            topologicalSort(service, dependencies, sorted, visited, visiting);
        }

        return sorted;
    }

    private void topologicalSort(String service, Map<String, Set<String>> dependencies,
            List<String> sorted, Set<String> visited, Set<String> visiting) {
        if (visiting.contains(service)) {
            return; // 检测到循环依赖
        }
        if (visited.contains(service)) {
            return;
        }

        visiting.add(service);

        Set<String> dependents = dependencies.getOrDefault(service, new HashSet<>());
        for (String dependent : dependents) {
            topologicalSort(dependent, dependencies, sorted, visited, visiting);
        }

        visiting.remove(service);
        visited.add(service);
        sorted.add(0, service); // 添加到前面
    }

    /**
     * 绘制时间轴
     */
    private void drawTimeAxis(StringBuilder xml, LocalDateTime minTime, LocalDateTime maxTime, long totalDurationMs) {
        // 时间轴背景
        xml.append("        <mxCell id=\"timeaxis_bg\" value=\"\" style=\"rounded=0;whiteSpace=wrap;html=1;" +
                "fillColor=#f5f5f5;strokeColor=#d5d5d5;strokeWidth=1\" vertex=\"1\" parent=\"1\">\n");
        xml.append("          <mxGeometry x=\"200\" y=\"50\" width=\"1000\" height=\"40\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");

        // 时间标签
        xml.append("        <mxCell id=\"timeaxis_label\" value=\"时间轴 (总耗时: " + totalDurationMs + "ms)\" " +
                "style=\"text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;" +
                "whiteSpace=wrap;rounded=0;fontSize=14;fontStyle=1\" vertex=\"1\" parent=\"1\">\n");
        xml.append("          <mxGeometry x=\"200\" y=\"55\" width=\"1000\" height=\"30\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");

        // 绘制时间刻度
        if (totalDurationMs > 0) {
            int tickCount = 10;
            for (int i = 0; i <= tickCount; i++) {
                long tickTimeMs = (totalDurationMs * i) / tickCount;
                int x = 200 + (1000 * i) / tickCount;

                xml.append("        <mxCell id=\"tick_" + i + "\" value=\"" + tickTimeMs + "ms\" " +
                        "style=\"text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=top;" +
                        "whiteSpace=wrap;rounded=0;fontSize=10\" vertex=\"1\" parent=\"1\">\n");
                xml.append("          <mxGeometry x=\"" + (x - 25)
                        + "\" y=\"90\" width=\"50\" height=\"20\" as=\"geometry\" />\n");
                xml.append("        </mxCell>\n");

                // 垂直刻度线
                xml.append("        <mxCell id=\"tickline_" + i + "\" value=\"\" style=\"endArrow=none;html=1;" +
                        "strokeColor=#cccccc;strokeWidth=1\" edge=\"1\" parent=\"1\">\n");
                xml.append("          <mxGeometry relative=\"1\" as=\"geometry\">\n");
                xml.append("            <mxPoint x=\"" + x + "\" y=\"90\" as=\"sourcePoint\" />\n");
                xml.append("            <mxPoint x=\"" + x + "\" y=\"800\" as=\"targetPoint\" />\n");
                xml.append("          </mxGeometry>\n");
                xml.append("        </mxCell>\n");
            }
        }
    }

    /**
     * 绘制服务标题行
     */
    private void drawServiceHeader(StringBuilder xml, String service, int y) {
        String serviceId = generateUniqueId("service_header");
        xml.append("        <mxCell id=\"" + serviceId + "\" value=\"" + escapeXml(service) + "\" " +
                "style=\"rounded=0;whiteSpace=wrap;html=1;fillColor=#e3f2fd;strokeColor=#1976d2;" +
                "strokeWidth=2;fontSize=14;fontStyle=1;align=left;spacingLeft=10\" vertex=\"1\" parent=\"1\">\n");
        xml.append("          <mxGeometry x=\"20\" y=\"" + y + "\" width=\"170\" height=\"30\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");
    }

    /**
     * 绘制服务的所有spans
     */
    private int drawServiceSpans(StringBuilder xml, List<Map<String, Object>> serviceSpanList,
            Map<String, List<Map<String, Object>>> spanHierarchy,
            LocalDateTime minTime, long totalDurationMs, int startY,
            Map<String, String> nodeIdMap) {

        int currentY = startY + 35; // 从服务标题下方开始

        // 按span类型分组：Entry、Exit、Local
        Map<String, List<Map<String, Object>>> spansByType = serviceSpanList.stream()
                .collect(Collectors.groupingBy(span -> (String) span.get("span_type")));

        List<Map<String, Object>> entrySpans = spansByType.getOrDefault("Entry", new ArrayList<>());
        List<Map<String, Object>> exitSpans = spansByType.getOrDefault("Exit", new ArrayList<>());
        List<Map<String, Object>> localSpans = spansByType.getOrDefault("Local", new ArrayList<>());

        // 如果该服务有Entry span，则需要特殊嵌套布局（Exit和Local都内嵌到Entry中）
        if (!entrySpans.isEmpty()) {
            // 绘制Entry spans（作为容器），Exit spans和Local spans内嵌其中
            for (Map<String, Object> entrySpan : entrySpans) {
                List<Map<String, Object>> relatedExitSpans = findRelatedExitSpans(entrySpan, exitSpans);
                List<Map<String, Object>> relatedLocalSpans = findRelatedLocalSpans(entrySpan, localSpans);

                // 计算内嵌span总数（Exit + Local）
                int embeddedSpanCount = relatedExitSpans.size() + relatedLocalSpans.size();

                // 绘制Entry span容器（根据内部span数量调整高度）
                int entrySpanHeight = Math.max(35, 35 + embeddedSpanCount * 30); // 基础高度35 + 每个内嵌span 30
                drawEntrySpanWithEmbeddedSpans(xml, entrySpan, relatedExitSpans, relatedLocalSpans, minTime,
                        totalDurationMs, currentY, entrySpanHeight, nodeIdMap);
                currentY += entrySpanHeight + 10; // Entry span高度 + 间距
            }

            // 绘制没有关联Entry span的独立Exit spans
            List<Map<String, Object>> independentExitSpans = findIndependentExitSpans(entrySpans, exitSpans);
            for (Map<String, Object> exitSpan : independentExitSpans) {
                drawSingleSpan(xml, exitSpan, minTime, totalDurationMs, currentY, 0, nodeIdMap);
                currentY += 35;
            }

            // 绘制没有关联Entry span的独立Local spans
            List<Map<String, Object>> independentLocalSpans = findIndependentLocalSpans(entrySpans, localSpans);
            for (Map<String, Object> localSpan : independentLocalSpans) {
                drawSingleSpan(xml, localSpan, minTime, totalDurationMs, currentY, 0, nodeIdMap);
                currentY += 35;
            }
        } else {
            // 如果只有一种类型的span，按原有的层次结构绘制
            List<Map<String, Object>> rootSpans = serviceSpanList.stream()
                    .filter(span -> {
                        Object parentSpanId = span.get("parent_span_id");
                        return parentSpanId == null || "-1".equals(String.valueOf(parentSpanId));
                    })
                    .collect(Collectors.toList());

            for (Map<String, Object> rootSpan : rootSpans) {
                currentY = drawSpanWithChildren(xml, rootSpan, spanHierarchy, minTime, totalDurationMs,
                        currentY, 0, nodeIdMap);
            }
        }

        return currentY;
    }

    /**
     * 绘制Entry span并在其内部嵌入Exit spans和Local spans
     */
    private void drawEntrySpanWithEmbeddedSpans(StringBuilder xml, Map<String, Object> entrySpan,
            List<Map<String, Object>> exitSpans, List<Map<String, Object>> localSpans,
            LocalDateTime minTime, long totalDurationMs, int y, int height,
            Map<String, String> nodeIdMap) {

        // 绘制Entry span作为容器（更大的矩形）
        drawEntrySpanContainer(xml, entrySpan, minTime, totalDurationMs, y, height, nodeIdMap);

        // 在Entry span内部绘制Exit spans和Local spans
        int embeddedY = y + 25; // Entry span顶部下方留一些空间

        // 先绘制Exit spans
        for (Map<String, Object> exitSpan : exitSpans) {
            drawEmbeddedExitSpan(xml, exitSpan, minTime, totalDurationMs, embeddedY, nodeIdMap);
            embeddedY += 30; // span之间的间距
        }

        // 再绘制Local spans
        for (Map<String, Object> localSpan : localSpans) {
            drawEmbeddedLocalSpan(xml, localSpan, minTime, totalDurationMs, embeddedY, nodeIdMap);
            embeddedY += 30; // span之间的间距
        }
    }

    /**
     * 绘制Entry span容器
     */
    private void drawEntrySpanContainer(StringBuilder xml, Map<String, Object> entrySpan, LocalDateTime minTime,
            long totalDurationMs, int y, int height, Map<String, String> nodeIdMap) {

        String operation = (String) entrySpan.get("operation_name");
        long duration = ((Number) entrySpan.get("duration_ms")).longValue();
        boolean isError = entrySpan.get("is_error") != null && ((Number) entrySpan.get("is_error")).intValue() > 0;
        LocalDateTime startTime = (LocalDateTime) entrySpan.get("start_time");

        // 计算时间轴位置
        long offsetMs = java.time.Duration.between(minTime, startTime).toMillis();
        int spanX = totalDurationMs > 0 ? (int) (1000 * offsetMs / totalDurationMs) + 200 : 200;
        int spanWidth = totalDurationMs > 0 ? (int) Math.max(100, 1000 * duration / totalDurationMs) : 150;

        // Entry span颜色
        String fillColor = isError ? "#ffcdd2" : "#c8e6c9"; // 绿色系
        String strokeColor = isError ? "#d32f2f" : "#4caf50";

        String spanId = generateUniqueId("entry_container");
        nodeIdMap.put(entrySpan.get("trace_segment_id") + "_" + entrySpan.get("span_id"), spanId);

        // 绘制Entry span容器矩形
        xml.append(String.format(
                "        <mxCell id=\"%s\" value=\"%s\\n🟢 Entry | %dms%s\" " +
                        "style=\"rounded=1;whiteSpace=wrap;html=1;fillColor=%s;strokeColor=%s;strokeWidth=2;fontSize=10;fontStyle=1;\" "
                        +
                        "vertex=\"1\" parent=\"1\">\n",
                spanId,
                operation != null ? operation.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        : "Unknown",
                duration,
                isError ? " ❌" : "",
                fillColor,
                strokeColor));

        xml.append(String.format(
                "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" as=\"geometry\" />\n",
                spanX, y, spanWidth, height));

        xml.append("        </mxCell>\n");
    }

    /**
     * 绘制内嵌的Exit span
     */
    private void drawEmbeddedExitSpan(StringBuilder xml, Map<String, Object> exitSpan, LocalDateTime minTime,
            long totalDurationMs, int y, Map<String, String> nodeIdMap) {

        String operation = (String) exitSpan.get("operation_name");
        long duration = ((Number) exitSpan.get("duration_ms")).longValue();
        boolean isError = exitSpan.get("is_error") != null && ((Number) exitSpan.get("is_error")).intValue() > 0;
        LocalDateTime startTime = (LocalDateTime) exitSpan.get("start_time");

        // 计算时间轴位置（内嵌的Exit span稍微缩进）
        long offsetMs = java.time.Duration.between(minTime, startTime).toMillis();
        int spanX = totalDurationMs > 0 ? (int) (1000 * offsetMs / totalDurationMs) + 220 : 220; // 向右缩进20px
        int spanWidth = totalDurationMs > 0 ? (int) Math.max(80, 1000 * duration / totalDurationMs - 40) : 120; // 宽度稍小

        // Exit span颜色
        String fillColor = isError ? "#ffcdd2" : "#e1bee7"; // 紫色系
        String strokeColor = isError ? "#d32f2f" : "#9c27b0";

        String spanId = generateUniqueId("embedded_exit");
        nodeIdMap.put(exitSpan.get("trace_segment_id") + "_" + exitSpan.get("span_id"), spanId);

        // 绘制内嵌Exit span矩形
        xml.append(String.format(
                "        <mxCell id=\"%s\" value=\"%s\\n🟣 Exit | %dms%s\" " +
                        "style=\"rounded=1;whiteSpace=wrap;html=1;fillColor=%s;strokeColor=%s;strokeWidth=1;fontSize=9;\" "
                        +
                        "vertex=\"1\" parent=\"1\">\n",
                spanId,
                operation != null ? operation.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        : "Unknown",
                duration,
                isError ? " ❌" : "",
                fillColor,
                strokeColor));

        xml.append(String.format(
                "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" as=\"geometry\" />\n",
                spanX, y, spanWidth, 25 // 内嵌Exit span高度固定为25
        ));

        xml.append("        </mxCell>\n");
    }

    /**
     * 绘制内嵌的Local span
     */
    private void drawEmbeddedLocalSpan(StringBuilder xml, Map<String, Object> localSpan, LocalDateTime minTime,
            long totalDurationMs, int y, Map<String, String> nodeIdMap) {

        String operation = (String) localSpan.get("operation_name");
        long duration = ((Number) localSpan.get("duration_ms")).longValue();
        boolean isError = localSpan.get("is_error") != null && ((Number) localSpan.get("is_error")).intValue() > 0;
        LocalDateTime startTime = (LocalDateTime) localSpan.get("start_time");

        // 计算时间轴位置（内嵌的Local span稍微缩进）
        long offsetMs = java.time.Duration.between(minTime, startTime).toMillis();
        int spanX = totalDurationMs > 0 ? (int) (1000 * offsetMs / totalDurationMs) + 220 : 220; // 向右缩进20px
        int spanWidth = totalDurationMs > 0 ? (int) Math.max(80, 1000 * duration / totalDurationMs - 40) : 120; // 宽度稍小

        // Local span颜色
        String fillColor = isError ? "#ffcdd2" : "#fff3e0"; // 橙色系
        String strokeColor = isError ? "#d32f2f" : "#ff9800";

        String spanId = generateUniqueId("embedded_local");
        nodeIdMap.put(localSpan.get("trace_segment_id") + "_" + localSpan.get("span_id"), spanId);

        // 绘制内嵌Local span矩形
        xml.append(String.format(
                "        <mxCell id=\"%s\" value=\"%s\\n🟠 Local | %dms%s\" " +
                        "style=\"rounded=1;whiteSpace=wrap;html=1;fillColor=%s;strokeColor=%s;strokeWidth=1;fontSize=9;\" "
                        +
                        "vertex=\"1\" parent=\"1\">\n",
                spanId,
                operation != null ? operation.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        : "Unknown",
                duration,
                isError ? " ❌" : "",
                fillColor,
                strokeColor));

        xml.append(String.format(
                "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" as=\"geometry\" />\n",
                spanX, y, spanWidth, 25 // 内嵌Local span高度固定为25
        ));

        xml.append("        </mxCell>\n");
    }

    /**
     * 找到与Entry span相关的Exit spans（基于父子关系）
     */
    private List<Map<String, Object>> findRelatedExitSpans(Map<String, Object> entrySpan,
            List<Map<String, Object>> exitSpans) {
        String entrySegmentId = (String) entrySpan.get("trace_segment_id");
        String entrySpanId = String.valueOf(entrySpan.get("span_id"));

        return exitSpans.stream()
                .filter(exitSpan -> {
                    String exitSegmentId = (String) exitSpan.get("trace_segment_id");
                    Object exitParentSpanId = exitSpan.get("parent_span_id");

                    // 同segment内且Exit span的parent_span_id等于Entry span的span_id
                    return entrySegmentId.equals(exitSegmentId) &&
                            exitParentSpanId != null &&
                            entrySpanId.equals(String.valueOf(exitParentSpanId));
                })
                .collect(Collectors.toList());
    }

    /**
     * 找到与Entry span相关的Local spans（基于父子关系）
     */
    private List<Map<String, Object>> findRelatedLocalSpans(Map<String, Object> entrySpan,
            List<Map<String, Object>> localSpans) {
        String entrySegmentId = (String) entrySpan.get("trace_segment_id");
        String entrySpanId = String.valueOf(entrySpan.get("span_id"));

        return localSpans.stream()
                .filter(localSpan -> {
                    String localSegmentId = (String) localSpan.get("trace_segment_id");
                    Object localParentSpanId = localSpan.get("parent_span_id");

                    // 同segment内且Local span的parent_span_id等于Entry span的span_id
                    return entrySegmentId.equals(localSegmentId) &&
                            localParentSpanId != null &&
                            entrySpanId.equals(String.valueOf(localParentSpanId));
                })
                .collect(Collectors.toList());
    }

    /**
     * 找到没有关联Entry span的独立Exit spans
     */
    private List<Map<String, Object>> findIndependentExitSpans(List<Map<String, Object>> entrySpans,
            List<Map<String, Object>> exitSpans) {
        Set<Map<String, Object>> relatedExitSpans = new HashSet<>();

        // 收集所有已经关联的Exit spans
        for (Map<String, Object> entrySpan : entrySpans) {
            relatedExitSpans.addAll(findRelatedExitSpans(entrySpan, exitSpans));
        }

        // 返回未关联的Exit spans
        return exitSpans.stream()
                .filter(exitSpan -> !relatedExitSpans.contains(exitSpan))
                .collect(Collectors.toList());
    }

    /**
     * 找到没有关联Entry span的独立Local spans
     */
    private List<Map<String, Object>> findIndependentLocalSpans(List<Map<String, Object>> entrySpans,
            List<Map<String, Object>> localSpans) {
        Set<Map<String, Object>> relatedLocalSpans = new HashSet<>();

        // 收集所有已经关联的Local spans
        for (Map<String, Object> entrySpan : entrySpans) {
            relatedLocalSpans.addAll(findRelatedLocalSpans(entrySpan, localSpans));
        }

        // 返回未关联的Local spans
        return localSpans.stream()
                .filter(localSpan -> !relatedLocalSpans.contains(localSpan))
                .collect(Collectors.toList());
    }

    /**
     * 递归绘制span及其子span
     */
    private int drawSpanWithChildren(StringBuilder xml, Map<String, Object> span,
            Map<String, List<Map<String, Object>>> spanHierarchy,
            LocalDateTime minTime, long totalDurationMs, int y, int indentLevel,
            Map<String, String> nodeIdMap) {

        // 绘制当前span
        drawSingleSpan(xml, span, minTime, totalDurationMs, y, indentLevel, nodeIdMap);
        int currentY = y + 35;

        // 绘制子spans
        String segmentId = (String) span.get("trace_segment_id");
        String spanId = String.valueOf(span.get("span_id"));
        String spanKey = segmentId + "_" + spanId;

        List<Map<String, Object>> children = spanHierarchy.getOrDefault(spanKey, new ArrayList<>());
        for (Map<String, Object> child : children) {
            currentY = drawSpanWithChildren(xml, child, spanHierarchy, minTime, totalDurationMs,
                    currentY, indentLevel + 1, nodeIdMap);
        }

        return currentY;
    }

    /**
     * 绘制单个span
     */
    private void drawSingleSpan(StringBuilder xml, Map<String, Object> span, LocalDateTime minTime,
            long totalDurationMs, int y, int indentLevel, Map<String, String> nodeIdMap) {

        String operation = (String) span.get("operation_name");
        String spanType = (String) span.get("span_type");
        long duration = ((Number) span.get("duration_ms")).longValue();
        boolean isError = span.get("is_error") != null && ((Number) span.get("is_error")).intValue() > 0;
        LocalDateTime startTime = (LocalDateTime) span.get("start_time");
        String statusCode = (String) span.get("tag_status_code");
        String httpMethod = (String) span.get("tag_http_method");
        String httpUrl = (String) span.get("tag_http_url");

        // 计算时间轴位置
        long offsetMs = java.time.Duration.between(minTime, startTime).toMillis();
        int spanX = totalDurationMs > 0 ? (int) (1000 * offsetMs / totalDurationMs) + 200 : 200;
        int spanWidth = totalDurationMs > 0 ? (int) Math.max(20, 1000 * duration / totalDurationMs) : 100;

        // 根据缩进调整Y位置（子span略微缩进）
        int spanY = y + (indentLevel * 2);

        // 根据span类型选择颜色
        String fillColor, strokeColor, textColor = "#000000";
        switch (spanType != null ? spanType.toLowerCase() : "unknown") {
            case "entry":
                fillColor = isError ? "#ffcdd2" : "#c8e6c9"; // 绿色系 - 服务入口
                strokeColor = isError ? "#d32f2f" : "#4caf50";
                break;
            case "exit":
                fillColor = isError ? "#ffcdd2" : "#e1bee7"; // 紫色系 - 服务出口
                strokeColor = isError ? "#d32f2f" : "#9c27b0";
                break;
            case "local":
                fillColor = isError ? "#ffcdd2" : "#fff3e0"; // 橙色系 - 本地调用
                strokeColor = isError ? "#d32f2f" : "#ff9800";
                break;
            default:
                fillColor = isError ? "#ffcdd2" : "#f5f5f5"; // 灰色系 - 未知类型
                strokeColor = isError ? "#d32f2f" : "#9e9e9e";
        }

        // 构建标签文本
        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append(escapeXml(operation != null ? operation : "unknown"));
        labelBuilder.append("&#xa;").append(duration).append("ms");

        if (spanType != null) {
            labelBuilder.append(" [").append(spanType.toUpperCase()).append("]");
        }
        if (statusCode != null) {
            labelBuilder.append("&#xa;状态: ").append(statusCode);
        }
        if (httpMethod != null) {
            labelBuilder.append("&#xa;").append(httpMethod);
        }

        String spanUniqueId = generateUniqueId("span");
        nodeIdMap.put(span.get("trace_segment_id") + "_" + span.get("span_id"), spanUniqueId);

        // 绘制span矩形
        xml.append("        <mxCell id=\"").append(spanUniqueId).append("\" value=\"").append(labelBuilder.toString())
                .append("\" style=\"rounded=1;whiteSpace=wrap;html=1;fillColor=").append(fillColor)
                .append(";strokeColor=").append(strokeColor).append(";strokeWidth=2;fontSize=11;fontStyle=")
                .append(isError ? "1" : "0")
                .append(";align=left;spacingLeft=5;verticalAlign=top;spacingTop=3\" vertex=\"1\" parent=\"1\">\n");
        xml.append("          <mxGeometry x=\"").append(spanX).append("\" y=\"").append(spanY)
                .append("\" width=\"").append(spanWidth).append("\" height=\"30\" as=\"geometry\" />\n");
        xml.append("        </mxCell>\n");

        // 如果有详细信息，添加tooltip样式的小图标
        if (httpUrl != null || isError) {
            String infoId = generateUniqueId("info");
            xml.append("        <mxCell id=\"").append(infoId).append("\" value=\"ⓘ\" ")
                    .append("style=\"text;html=1;strokeColor=none;fillColor=none;align=center;verticalAlign=middle;")
                    .append("whiteSpace=wrap;rounded=0;fontSize=12;fontColor=#1976d2\" vertex=\"1\" parent=\"1\">\n");
            xml.append("          <mxGeometry x=\"").append(spanX + spanWidth - 15).append("\" y=\"").append(spanY)
                    .append("\" width=\"15\" height=\"15\" as=\"geometry\" />\n");
            xml.append("        </mxCell>\n");
        }
    }
}
