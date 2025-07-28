# Segment Alarm ClickHouse

高性能可观测性数据处理系统，多模块架构，实时数据处理。

## 技术栈

Java 11+、Apache Flink 1.17.2、ClickHouse 21.0+、Kafka 3.5.1、Spring Boot 2.7.12
AI 支持：DeepSeek、OpenAI、Azure OpenAI、本地 LLM

## 项目结构

```
segment-alarm-clickhouse/
├── ai-analysis-module/              # AI智能分析模块
│   ├── src/main/java/com/o11y/ai/   # AI分析核心代码
│   └── src/main/resources/          # 配置文件
├── flink-data-transformer-module/  # Flink数据转换模块
│   ├── src/main/java/com/o11y/     # Flink处理核心代码
│   └── scripts/                    # 运维脚本
├── manage.sh                       # 管理脚本
├── quick-start.sh                  # 快速启动
└── pom.xml                        # 依赖管理
```

## 快速启动

```bash
# 构建
mvn clean package

# Flink部署
./flink-data-transformer-module/scripts/flink_deploy.sh

# 服务启动
java --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -cp flink-data-transformer-module/target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.application.launcher.FlinkServiceLauncher

# AI模块
cd ai-analysis-module && mvn spring-boot:run
```

## 核心配置

### Flink 配置

```yaml
kafka:
  bootstrap-servers: localhost:9092
  topics:
    segment: segment-data
    alarm-rule: alarm-rules
clickhouse:
  url: jdbc:clickhouse://localhost:8123/o11y
  username: default
  password: ""
```

### AI 配置

```yaml
ai-analysis:
  llm:
    provider: deepseek
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY}
      model: deepseek-chat
```

## 数据库表

- `events` - SkyWalking 链路追踪数据
- `flink_operator_agg_result` - 聚合性能指标
- `hourly_alarm_rules` - 小时级动态阈值规则(24 条)
- `ai_performance_reports` - AI 分析报告

## 核心组件

### Flink 模块

- `AggregateOperator.java` - 实时数据聚合
- `AlarmRule.java` - 多级阈值告警规则
- `HourlyDynamicThresholdGenerator.java` - 小时级规则生成
- `HourlyRulePublishProcessFunction.java` - 规则定时下发
- `SimpleClickHouseSink.java` - ClickHouse 写入

### AI 模块

- `PerformanceAnalysisService.java` - 核心分析引擎
- `LLMAnalysisService.java` - 多 LLM 提供商支持
- `ClickHouseRepository.java` - 数据访问 (已修复请求统计逻辑)
- `PerformanceAnalysisController.java` - REST API

**重要修复**: ClickHouseRepository 已修复请求统计逻辑，使用 `uniqIf(trace_segment_id)` 替代错误的 Span 计数方式，确保性能指标的准确性。

## 小时级动态阈值系统

一次性分析历史数据生成 24 小时规则，每小时整点从`hourly_alarm_rules`表读取当前小时规则下发到 Kafka。

```bash
# 生成规则
java -jar flink-data-transformer-module/target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator generate-all 7
```

## 监控端点

- Flink Web UI: http://localhost:8081
- AI 健康检查: http://localhost:8082/ai-analysis/api/ai-analysis/health
- ClickHouse: http://localhost:8123/play

## ClickHouse 数据库查询

### 容器内查询方法

```bash
# 查询 ClickHouse 容器内数据
docker exec clickhouse-server clickhouse-client --query "YOUR_SQL_QUERY"

# 常用查询示例
# 查看最新数据时间
docker exec clickhouse-server clickhouse-client --query "SELECT MAX(window_start) FROM default.flink_operator_agg_result"

# 查看所有数据库
docker exec clickhouse-server clickhouse-client --query "SHOW DATABASES"

# 查看表结构
docker exec clickhouse-server clickhouse-client --query "DESCRIBE default.flink_operator_agg_result"
```

**注意**: 实际数据库为 `default`，不是配置中的 `o11y`

## AI 助手指令

### 文件操作

- 优先编辑现有文件，避免创建新文件
- 不要主动创建文档文件(\*.md)
- 遵循现有代码风格

### 开发规范

- Java 11+语法，Google Style Guide
- 包名：`com.o11y.{module}.{layer}`
- 单元测试覆盖率 > 80%
- 提交格式：`feat(module): 描述`

### 关键路径

- Flink 核心：`flink-data-transformer-module/src/main/java/com/o11y/`
- AI 分析：`ai-analysis-module/src/main/java/com/o11y/ai/`
- 配置文件：`src/main/resources/application.yaml`

## 故障排查

1. 服务启动失败：检查端口占用、数据库连接
2. 规则下发异常：检查 Kafka 连接和主题配置
3. LLM 调用失败：验证 API 密钥、网络连接
4. 数据查询问题：检查表结构、时间范围

## SkyWalking 链路追踪分析

### SkyWalking 架构与数据流

**整体架构：**

```
Application (应用)
├── SkyWalking Agent (数据采集)
│   ├── 字节码增强
│   ├── Trace数据生成
│   └── 上报到OAP
├── OAP Server (数据处理)
│   ├── 接收Agent数据
│   ├── 数据聚合分析
│   └── 存储到Storage
└── Storage (数据存储)
    ├── ClickHouse (我们的存储)
    └── 查询接口
```

**数据模型原理：**

```
Trace (分布式事务全链路)
├── TraceSegment (单JVM进程内操作)
│   ├── EntrySpan (服务接收请求入口)
│   ├── LocalSpan (进程内方法调用)
│   └── ExitSpan (对外调用出口)
└── ContextCarrier (跨进程上下文传播)
    ├── TraceId (全局唯一标识)
    ├── TraceSegmentId (片段标识)
    ├── SpanId (span标识)
    └── Parent Service Info (父服务信息)
```

**⭐ 重要概念：TraceSegmentId 与服务请求统计**

- **1 个 TraceSegmentId = 1 次对服务的完整请求**
- 在性能分析中，应使用 `COUNT(DISTINCT trace_segment_id)` 统计真实请求数
- 不应使用 Span 数量统计，因为一个 TraceSegment 可能包含多个 Span
- 这是准确计算服务吞吐量、请求量等关键指标的基础

**Span 类型详解：**

- **EntrySpan**：服务接收外部请求的入口点（如 Web 请求、RPC 调用接收端）
- **ExitSpan**：服务向外部发起调用的出口点（如数据库查询、RPC 调用发起端）
- **LocalSpan**：服务内部方法调用（不涉及进程间通信）

**上下文传播机制：**

- 单线程内：使用 ThreadLocal 管理 TraceContext
- 跨线程：通过 ContextSnapshot 实现
- 跨进程：通过 ContextCarrier 在 HTTP Header 或 RPC 参数中传递

### events 表结构关键信息

- 数据库：`default.events`（不是 o11y 数据库）
- 总数据量：920 万+ spans，165 万+ traces
- CrossProcess spans：418 万+（约 45%为跨服务调用）

### ref\_字段分析（跨进程关系核心字段）

```sql
-- 关键字段说明（基于SkyWalking ContextCarrier机制）
refs_ref_type          -- 关系类型：CrossProcess（跨服务调用）
refs_trace_id          -- 父trace ID（保持链路连续性）
refs_parent_trace_segment_id  -- 父segment ID（上游服务片段）
refs_parent_span_id    -- 父span ID（上游Exit Span）
refs_parent_service    -- 父服务名称（调用方）
refs_parent_service_instance -- 父服务实例
refs_parent_endpoint   -- 父端点（上游接口）
```

### 链路调用模式（基于最近 2 小时数据分析）

**典型调用链路：**

```
crm-simulate → esb-c → esb-p → ism-server
```

**服务清单：**

- `crm-simulate` - CRM 模拟器（入口服务）
- `esb-c` - 企业服务总线-Consumer
- `esb-p` - 企业服务总线-Provider
- `iam-server` - 身份认证服务

### 链路关系识别规则（基于 SkyWalking 模型）

1. **EntrySpan 识别**：

   - `refs_ref_type = 'CrossProcess'`且`parent_span_id = -1`
   - 对应 events 表中接收外部请求的 span（如 Web 接口、RPC 接收端）

2. **ExitSpan 识别**：

   - 发起对外调用的 span，下游 EntrySpan 会通过 refs 字段引用
   - 对应 events 表中数据库查询、RPC 调用发起端

3. **LocalSpan 识别**：

   - `refs_ref_type = NULL`且`parent_span_id != -1`
   - 同 TraceSegment 内的方法调用

4. **跨进程链路重构**：

   - **上游 ExitSpan** → **下游 EntrySpan**通过 ContextCarrier 关联
   - `refs_parent_trace_segment_id + refs_parent_span_id`精确定位父节点
   - `refs_parent_service`标识调用来源服务

5. **TraceSegment 边界**：
   - 每个服务实例的操作形成独立的 TraceSegment
   - 通过`trace_segment_id`字段识别

### 数据查询示例

```sql
-- 查看完整调用链路
SELECT trace_id, service, operation_name, refs_parent_service,
       refs_ref_type, start_time
FROM default.events
WHERE trace_id = 'YOUR_TRACE_ID'
ORDER BY start_time;

-- 正确统计服务真实请求数 (按 TraceSegmentId)
SELECT service, COUNT(DISTINCT trace_segment_id) as real_requests
FROM default.events
WHERE start_time >= now() - INTERVAL 1 HOUR
GROUP BY service
ORDER BY real_requests DESC;

-- 错误的统计方式 (会高估请求数)
SELECT service, COUNT(*) as span_count  -- ❌ 统计 Span 数量
FROM default.events
WHERE start_time >= now() - INTERVAL 1 HOUR
GROUP BY service;

-- 统计服务间调用关系
SELECT refs_parent_service, service, COUNT(*) as call_count
FROM default.events
WHERE refs_ref_type = 'CrossProcess'
GROUP BY refs_parent_service, service;
```

## TraceVisualizationController 改进记录

### 改进内容（已完成）

1. **完善 SQL 查询**：添加了缺失的 SkyWalking ref\_字段

   - `refs_trace_id` - 父 trace ID（链路连续性）
   - `refs_parent_service_instance` - 父服务实例
   - `refs_parent_endpoint` - 父端点
   - `refs_network_address_used_at_peer` - 网络地址

2. **新增 SkyWalking 标准 Span 识别方法**：

   - `isEntrySpan()` - 基于 `refs_ref_type='CrossProcess'` + `parent_span_id=-1`
   - `isExitSpan()` - 基于下游 EntrySpan 的 refs 字段引用关系
   - `isLocalSpan()` - 基于 `refs_ref_type=null` + `parent_span_id!=-1`
   - `isCrossProcessCall()` - 检查 `refs_ref_type='CrossProcess'`

3. **优化链路分析算法**：

   - `analyzeSegmentReferences()` 方法增加 span 类型统计
   - 只处理真正的跨进程调用（CrossProcess 类型）
   - 新增 span 类型分布统计

4. **新增 API 端点**：
   - `GET /trace/{traceId}/skywalking-analysis` - 基于 SkyWalking 标准的链路结构分析
   - 返回 EntrySpan、ExitSpan、LocalSpan 分类结果
   - 提供 TraceSegment 边界分析

### 符合性验证

- ✅ SQL 查询包含完整 ref\_字段
- ✅ EntrySpan 识别符合 SkyWalking 标准
- ✅ ExitSpan 识别基于 refs 关联而非 span_type
- ✅ 正确处理 CrossProcess 检查
- ✅ 链路重构算法符合 SkyWalking 模型

## 相关文档

- [Flink 模块文档](./flink-data-transformer-module/README.md)
- [AI 模块文档](./ai-analysis-module/README.md)
- [小时级规则系统文档](./flink-data-transformer-module/README-HOURLY-RULES.md)
