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
     * 获取最近链路列表（最近20条）
     */
    @GetMapping("/traces/recent")
    public Map<String, Object> getRecentTraces(
            @RequestParam(defaultValue = "20") int limit,
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
                "service_instance, " +
                "operation_name, " +
                "span_id, " +
                "parent_span_id, " +
                "start_time, " +
                "end_time, " +
                "toUInt64((end_time - start_time) * 1000) as duration_ms, " +
                "is_error, " +
                "span_type, " +
                "span_layer, " +
                "component_id, " +
                "tag_status_code, " +
                "tag_http_method, " +
                "tag_http_url, " +
                "tag_db_type, " +
                "tag_db_statement, " +
                "tag_error_message, " +
                "log_error_kind, " +
                "log_message, " +
                "log_stack, " +
                // SegmentReference 相关字段 - 服务间关系的关键
                "refs_ref_type, " +
                "refs_trace_id, " +
                "refs_parent_trace_segment_id, " +
                "refs_parent_span_id, " +
                "refs_parent_service, " +
                "refs_parent_service_instance, " +
                "refs_parent_endpoint, " +
                "refs_network_address_used_at_peer " +
                "FROM events " +
                "WHERE trace_id = ? " +
                "AND start_time >= now() - INTERVAL 1 DAY " +
                "ORDER BY start_time, span_id";

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
                .map(s -> Map.of(
                        "service", s.get("service"),
                        "operation", s.get("operation_name"),
                        "error_message", s.get("tag_error_message"),
                        "log_message", s.get("log_message")))
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
                    "WHERE start_time >= now() - INTERVAL 1 HOUR";

            List<Map<String, Object>> result = clickHouseJdbcTemplate.queryForList(sql);
            Map<String, Object> stats = result.isEmpty() ? Map.of() : result.get(0);

            return Map.of(
                    "status", "success",
                    "timeRange", "最近1天",
                    "stats", stats,
                    "timestamp", LocalDateTime.now());

        } catch (Exception e) {
            LOG.error("获取最近一天数据统计失败", e);
            return createErrorResponse("获取数据统计失败: " + e.getMessage());
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
                    width = totalDurationMs > 0 ? (int) Math.max(100, 800 * duration / totalDurationMs) : 100;

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
            // 创建局部节点ID映射表
            Map<String, String> nodeIdMap = createNodeIdMap();

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<mxfile host=\"app.diagrams.net\">\n");
            xml.append("  <diagram name=\"Flame Graph\" id=\"flame\">\n");
            xml.append("    <mxGraphModel dx=\"1200\" dy=\"800\" grid=\"1\" gridSize=\"10\" guides=\"1\">\n");
            xml.append("      <root>\n");
            xml.append("        <mxCell id=\"0\" />\n");
            xml.append("        <mxCell id=\"1\" parent=\"0\" />\n");

            // 构建调用树
            Map<String, Map<String, Object>> spanMap = new HashMap<>();
            Map<String, String> parentMap = new HashMap<>();
            for (Map<String, Object> span : spans) {
                // 将数字类型的ID转换为字符串
                String spanId = String.valueOf(span.get("span_id"));
                Object parentSpanIdObj = span.get("parent_span_id");
                String parentSpanId = parentSpanIdObj != null ? String.valueOf(parentSpanIdObj) : null;

                spanMap.put(spanId, span);
                if (parentSpanId != null && !"-1".equals(parentSpanId)) {
                    parentMap.put(spanId, parentSpanId);
                }
            }

            // 找到根节点
            List<String> rootIds = new ArrayList<>();
            for (Map<String, Object> span : spans) {
                String spanId = String.valueOf(span.get("span_id"));
                if (!parentMap.containsKey(spanId)) {
                    rootIds.add(spanId);
                }
            }

            // 递归生成火焰图
            int y = 600; // 从底部开始
            int baseX = 50;
            int maxWidth = 1000;

            for (String rootId : rootIds) {
                drawFlameNode(xml, spanMap, parentMap, rootId, baseX, y, maxWidth, nodeIdMap);
            }

            xml.append("      </root>\n");
            xml.append("    </mxGraphModel>\n");
            xml.append("  </diagram>\n");
            xml.append("</mxfile>");

            return xml.toString();

        } catch (Exception e) {
            LOG.error("生成火焰图时发生错误", e);
            return generateEmptyDAG("生成火焰图时发生错误：" + e.getMessage());
        }
    }

    private void drawFlameNode(StringBuilder xml, Map<String, Map<String, Object>> spanMap,
            Map<String, String> parentMap, String spanId, int x, int y, int width, Map<String, String> nodeIdMap) {
        Map<String, Object> span = spanMap.get(spanId);
        if (span == null)
            return;

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

        // 使用全局唯一ID添加火焰图节点
        String uniqueId = generateUniqueId("flame");
        nodeIdMap.put(spanId, uniqueId);

        xml.append(String.format(
                "        <mxCell id=\"%s\" value=\"%s\" style=\"rounded=1;whiteSpace=wrap;html=1;" +
                        "fillColor=%s;strokeColor=%s;strokeWidth=2;fontSize=12;fontStyle=1\" vertex=\"1\" parent=\"1\">\n",
                uniqueId, label, fillColor, strokeColor));
        xml.append(String.format(
                "          <mxGeometry x=\"%d\" y=\"%d\" width=\"%d\" height=\"40\" as=\"geometry\" />\n",
                x, y, width));
        xml.append("        </mxCell>\n");

        // 查找子节点
        List<String> childIds = new ArrayList<>();
        for (Map.Entry<String, String> entry : parentMap.entrySet()) {
            if (entry.getValue().equals(spanId)) {
                childIds.add(entry.getKey());
            }
        }

        // 如果有子节点，继续递归
        if (!childIds.isEmpty()) {
            int childWidth = width / childIds.size();
            int childX = x;
            for (String childId : childIds) {
                drawFlameNode(xml, spanMap, parentMap, childId, childX, y - 60, childWidth, nodeIdMap);
                childX += childWidth;
            }
        }
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
}
