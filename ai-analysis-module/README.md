# AI 智能分析模块

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/o11y/segment-alarm-clickhouse)
[![Java Version](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.12-green)](https://spring.io/projects/spring-boot)
[![ClickHouse](https://img.shields.io/badge/ClickHouse-25.4-orange)](https://clickhouse.com/)

## 概述

这是一个智能化的应用性能分析模块，基于 Spring Boot 构建，深度集成 ClickHouse 数据仓库和多种 LLM 服务。该模块能够自动分析 Flink 处理的可观测性数据，生成智能分析报告，并提供性能优化建议。

## 🎯 核心特性

### 📊 智能性能分析
- **实时数据分析**：基于 Flink 聚合结果和原始事件数据
- **多维度监控**：响应时间、吞吐量、错误率、资源使用率
- **异常检测**：智能识别性能瓶颈和异常模式
- **趋势预测**：基于历史数据进行性能趋势分析

### 🤖 多 LLM 智能引擎
- **DeepSeek AI**：国产 LLM，中文支持优秀，价格实惠
- **OpenAI GPT**：支持 GPT-3.5-turbo/GPT-4 模型
- **Azure OpenAI**：企业级 OpenAI 服务集成
- **本地 LLM**：支持 Ollama 等本地部署模型
- **智能降级**：LLM 不可用时自动使用基础分析算法

### 💾 双重存储架构
- **ClickHouse 主存储**：高性能分析报告存储和查询
- **文件系统备份**：本地 JSON 文件存储，便于调试和备份
- **数据源集成**：
  - `flink_operator_agg_result` - 聚合性能指标
  - `events` - 原始事件和链路追踪数据
  - `ai_performance_reports` - AI 分析报告存储

### 🔍 丰富的查询能力
- **错误链路分析**：识别和分析错误调用链
- **慢请求追踪**：定位性能瓶颈和慢查询
- **服务拓扑**：动态服务依赖关系分析
- **数据探索**：支持动态查询 events 表结构和样例数据

### 🚀 完整的 REST API
- **报告管理**：生成、存储、检索分析报告
- **数据查询**：多种维度的性能数据查询接口
- **实时监控**：健康检查和状态监控
- **双重检索**：支持从文件系统和 ClickHouse 检索报告

## 主要功能

### 🔍 智能性能分析
- 自动收集系统性能指标（JVM、应用、数据库、系统资源）
- 基于阈值的异常检测
- LLM 驱动的智能分析和洞察

### 🤖 多 LLM 支持
- **DeepSeek AI**：国产大模型，中文理解能力强，成本优势明显
- **OpenAI GPT**：支持 GPT-3.5/GPT-4
- **Azure OpenAI**：企业级 OpenAI 服务
- **本地 LLM**：支持 Ollama 等本地部署模型
- **降级方案**：LLM 不可用时的基础分析

### 📊 报告生成
- 智能化分析报告
- 优化建议生成
- 报告存储和检索
- 定时自动分析

### � 数据存储
- **ClickHouse 集成**：直接连接到与 Flink 模块共享的 ClickHouse 实例
- **性能数据查询**：从 `service_agg_result` 表查询实时性能指标
- **分析报告存储**：将 AI 分析结果存储到专用的报告表
- **数据保留策略**：自动清理过期数据，节省存储空间

### �🔧 REST API
- 报告生成 API
- 报告查询 API
- 分析任务触发
- 健康检查

## 快速开始

### 1. 环境要求
- **Java 17+** - 推荐使用 OpenJDK 17
- **Maven 3.6+** - 项目构建工具
- **ClickHouse 22.8+** - 数据存储和查询引擎
- **Docker** - 运行 ClickHouse（推荐）
- **LLM API 密钥**（可选）- OpenAI 或 Azure OpenAI

### 2. ClickHouse 数据库准备

如果使用 Docker：

```bash
# 启动 ClickHouse 容器
docker run -d --name clickhouse-server \
  -p 8123:8123 -p 9000:9000 \
  clickhouse/clickhouse-server:latest

# 创建 AI 分析相关表
docker exec -i clickhouse-server clickhouse-client < src/main/resources/clickhouse-schema.sql
```

### 3. 配置文件设置

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8082
  servlet:
    context-path: /ai-analysis

# ClickHouse 数据源配置
spring:
  datasource:
    clickhouse:
      url: jdbc:clickhouse://localhost:8123/default
      username: default
      password: ""
      driver-class-name: com.clickhouse.jdbc.ClickHouseDriver

# AI 分析配置
ai:
  analysis:
    enabled: true
    window:
      hours: 1  # 默认分析时间窗口
    schedule:
      enabled: true
      cron: "0 0 */1 * * ?"  # 每小时执行一次
    thresholds:
      response-time-ms: 1000
      error-rate-percent: 5.0
      cpu-usage-percent: 80.0
      memory-usage-percent: 85.0
  # LLM 配置
  llm:
    enabled: true
    provider: deepseek  # deepseek, openai, azure, ollama
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY:}
      base-url: https://api.deepseek.com/v1
      model: deepseek-chat
      timeout: 30000
      max-tokens: 2000
      temperature: 0.7
    openai:
      api-key: ${AI_OPENAI_API_KEY:}
      base-url: https://api.openai.com/v1
      model: gpt-3.5-turbo
      timeout: 30000
      max-tokens: 2000
      temperature: 0.7

  # 查询表配置
  query:
    tables:
      performance: flink_operator_agg_result
      events: events
```

### 4. 环境变量配置

```bash
# DeepSeek 配置（推荐）
export AI_DEEPSEEK_API_KEY="sk-your-deepseek-api-key"
export AI_DEEPSEEK_BASE_URL="https://api.deepseek.com/v1"
export AI_DEEPSEEK_MODEL="deepseek-chat"

# OpenAI 配置
export AI_OPENAI_API_KEY="sk-your-openai-api-key"

# 或者 Azure OpenAI 配置
export AI_AZURE_API_KEY="your-azure-api-key"
export AI_AZURE_ENDPOINT="https://your-resource.openai.azure.com"
export AI_AZURE_DEPLOYMENT="your-deployment-name"

# 或者本地 LLM 配置
export AI_LOCAL_LLM_URL="http://localhost:11434"
export AI_LOCAL_LLM_MODEL="llama2"
```

## 🤖 DeepSeek AI 详细配置

### DeepSeek 优势
- **💰 成本优势**：相比 OpenAI GPT，价格更具竞争力，新用户通常有免费额度
- **🇨🇳 中文支持**：原生中文理解，技术术语准确，符合中文表达习惯
- **🔌 API 兼容**：完全兼容 OpenAI API 格式，无需修改现有代码
- **🎯 多模型**：提供 deepseek-chat、deepseek-coder、deepseek-math 等专业模型

### 快速配置 DeepSeek

#### 1. 获取 API Key
1. 访问 [DeepSeek 官网](https://platform.deepseek.com/)
2. 注册账号并完成实名认证
3. 在控制台中创建 API Key
4. 确保账户有足够的余额

#### 2. 配置环境变量
```bash
# Windows
set AI_DEEPSEEK_API_KEY=sk-your-deepseek-api-key-here
set AI_DEEPSEEK_MODEL=deepseek-chat

# Linux/macOS
export AI_DEEPSEEK_API_KEY="sk-your-deepseek-api-key-here"
export AI_DEEPSEEK_MODEL="deepseek-chat"
```

#### 3. 应用配置
```yaml
ai:
  llm:
    enabled: true
    provider: deepseek  # 设置为 deepseek
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
      model: ${AI_DEEPSEEK_MODEL:deepseek-chat}
      timeout: 30000       # 30秒超时
      max-tokens: 2000     # 最大输出令牌数
      temperature: 0.7     # 创造性控制 (0-1)
```

### DeepSeek 模型选择

| 模型 | 适用场景 | 推荐用途 |
|------|----------|----------|
| **deepseek-chat** | 通用对话、性能分析 | 默认选择，适合大多数分析任务 |
| **deepseek-coder** | 代码分析、技术优化 | 代码性能优化、架构分析 |
| **deepseek-math** | 数学计算、统计分析 | 复杂性能指标计算和趋势分析 |

### DeepSeek 实际效果示例

使用 DeepSeek 生成的性能分析报告特点：

```json
{
  "intelligentAnalysis": "### 1. 系统性能总体评估\n- **吞吐量与响应时间**：系统整体吞吐量较低（8.33 req/s），但平均响应时间极短（0.21 ms），表明轻量级请求处理效率较高...\n- **错误率**：11.11%的错误率严重超标（期望值≤5%），是当前最突出的问题，需优先排查...",
  "optimizationSuggestions": [
    "降低错误率: 当前错误率11.11%远超5%的期望阈值，需立即排查错误来源。常见原因包括：输入验证不足、第三方服务调用失败、资源竞争或业务逻辑缺陷。",
    "优化线程池配置: 平均线程数100且CPU使用率50%，存在线程资源分配不合理可能。建议结合业务场景调整线程池参数。"
  ]
}
```

**DeepSeek 分析特点**：
- ✅ **详细分析**：提供深入的技术分析和数据解读
- ✅ **中文表达**：使用专业但易懂的中文技术术语
- ✅ **实用建议**：给出具体可执行的优化建议
- ✅ **结构化输出**：分析结果结构清晰，便于理解和执行

### DeepSeek 故障排除

#### 常见问题解决

1. **API Key 错误**
```bash
# 检查环境变量
echo $AI_DEEPSEEK_API_KEY
# 确认格式：应以 sk- 开头
```

2. **网络连接问题**
```bash
# 测试连接
curl -I https://api.deepseek.com
```

3. **中文乱码问题**
```yaml
# 确保 UTF-8 编码配置
server:
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true
```

4. **请求限流**
```yaml
# 增加重试配置
ai:
  llm:
    deepseek:
      retry-delay: 2000  # 2秒重试间隔
```

### 5. 构建和运行

```bash
# 克隆项目
git clone https://github.com/o11y/segment-alarm-clickhouse.git
cd segment-alarm-clickhouse/ai-analysis-module

# 构建项目
mvn clean compile

# 运行应用
mvn spring-boot:run

# 或者打包后运行
mvn package
java -jar target/ai-analysis-module-1.0.5.jar
```

应用将在 **http://localhost:8082/ai-analysis** 启动

### 6. 验证安装

```bash
# 健康检查
curl http://localhost:8082/ai-analysis/api/ai-analysis/health

# 查看 ClickHouse 连接状态
curl http://localhost:8082/ai-analysis/api/ai-analysis/data/events/schema

# 生成测试报告
curl -X POST "http://localhost:8082/ai-analysis/api/ai-analysis/reports/generate?timeRangeHours=1"
```

## 📚 API 使用指南

### 报告管理 API

#### 1. 生成智能分析报告

```bash
# 生成过去1小时的报告
curl -X POST "http://localhost:8082/ai-analysis/api/ai-analysis/reports/generate?timeRangeHours=1"

# 生成过去24小时的报告
curl -X POST "http://localhost:8082/ai-analysis/api/ai-analysis/reports/generate?timeRangeHours=24"

# 响应示例
{
  "reportId": "03523a46-6b85-4c58-b86e-84c520056043",
  "generatedAt": "2025-06-23T21:24:57.775770100",
  "timeRange": 1,
  "summary": "系统在过去1小时内处理了0个请求，平均响应时间NaNms，错误率0.00%。未检测到明显的性能异常。",
  "intelligentAnalysis": "## 系统性能分析报告\n\n### 基础性能评估\n...",
  "optimizationSuggestions": [],
  "metrics": {
    "avgResponseTime": "NaN",
    "avgThroughput": 0.0,
    "errorRate": 0.0,
    "avgCpuUsage": 0.5,
    "avgMemoryUsage": 0.7,
    "totalRequests": 0,
    "totalErrors": 0
  },
  "anomalies": []
}
```

#### 2. 报告检索 API

**从文件系统检索：**

```bash
# 获取最近10个报告
curl "http://localhost:8082/ai-analysis/api/ai-analysis/reports?limit=10"

# 根据报告ID获取详细信息（支持部分ID匹配）
curl "http://localhost:8082/ai-analysis/api/ai-analysis/reports/03523a46"
```

**从 ClickHouse 检索：**

```bash
# 获取 ClickHouse 中的报告列表
curl "http://localhost:8082/ai-analysis/api/ai-analysis/reports/clickhouse?limit=5"

# 根据完整ID从 ClickHouse 获取报告
curl "http://localhost:8082/ai-analysis/api/ai-analysis/reports/clickhouse/03523a46-6b85-4c58-b86e-84c520056043"
```

### 数据查询 API

#### 3. 错误链路追踪

```bash
# 获取过去1小时的错误调用链
curl "http://localhost:8082/ai-analysis/api/ai-analysis/traces/errors?hoursAgo=1"

# 按服务名过滤错误链路
curl "http://localhost:8082/ai-analysis/api/ai-analysis/traces/errors?hoursAgo=2&serviceName=user-service"
```

#### 4. 慢请求分析

```bash
# 获取响应时间超过1000ms的慢请求
curl "http://localhost:8082/ai-analysis/api/ai-analysis/traces/slow?hoursAgo=1&durationThreshold=1000"

# 按服务名过滤慢请求
curl "http://localhost:8082/ai-analysis/api/ai-analysis/traces/slow?hoursAgo=6&durationThreshold=500&serviceName=order-service"
```

#### 5. 服务拓扑分析

```bash
# 获取过去1小时的服务调用拓扑
curl "http://localhost:8082/ai-analysis/api/ai-analysis/topology/services?hoursAgo=1"
```

#### 6. 数据探索 API

```bash
# 获取 events 表结构
curl "http://localhost:8082/ai-analysis/api/ai-analysis/data/events/schema"

# 获取 events 表样例数据
curl "http://localhost:8082/ai-analysis/api/ai-analysis/data/events/sample?hoursAgo=1&limit=50"
```

### 系统管理 API

#### 7. 健康检查和监控

```bash
# 应用健康检查
curl "http://localhost:8082/ai-analysis/api/ai-analysis/health"

# LLM 服务状态检查
curl "http://localhost:8082/ai-analysis/api/ai-analysis/llm/health"

# 手动触发分析任务
curl -X POST "http://localhost:8082/ai-analysis/api/ai-analysis/analysis/trigger"
```

#### 8. 优化建议生成

```bash
# 获取性能优化建议
curl -X POST "http://localhost:8082/ai-analysis/api/ai-analysis/suggestions?timeRangeHours=2"
```

## 🏗️ 系统架构

### 数据流向图

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│   Kafka Topics  │───▶│  Flink Processor │───▶│   ClickHouse DB     │
│  (Trace Data)   │    │   (Real-time)    │    │                     │
└─────────────────┘    └──────────────────┘    │ ┌─────────────────┐ │
                                                │ │     events      │ │
                                                │ │   (raw data)    │ │
                                                │ └─────────────────┘ │
                                                │ ┌─────────────────┐ │
                                                │ │flink_operator_  │ │
                                                │ │  agg_result     │ │
                                                │ │ (aggregated)    │ │
                                                │ └─────────────────┘ │
                                                └─────────────────────┘
                                                          │
                                                          ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│  LLM Services   │◀───│  AI Analysis     │◀───│  Data Queries       │
│                 │    │    Module        │    │                     │
│ • OpenAI GPT    │    │                  │    │ • Performance       │
│ • Azure OpenAI  │    │ ┌──────────────┐ │    │ • Error Traces      │
│ • Local LLM     │    │ │ Analysis     │ │    │ • Slow Requests     │
└─────────────────┘    │ │ Engine       │ │    │ • Service Topology  │
                       │ └──────────────┘ │    └─────────────────────┘
                       │                  │
                       │ ┌──────────────┐ │    ┌─────────────────────┐
                       │ │ Report       │ │───▶│  Storage Backends   │
                       │ │ Generator    │ │    │                     │
                       │ └──────────────┘ │    │ • ClickHouse Tables │
                       └──────────────────┘    │ • JSON Files        │
                                               └─────────────────────┘
```

### 核心组件

#### 1. 数据访问层 (Repository)
- **ClickHouseRepository**: 负责所有 ClickHouse 数据查询和写入
- **数据源表**:
  - `events`: 原始链路追踪事件数据
  - `flink_operator_agg_result`: Flink 聚合后的性能指标
  - `ai_performance_reports`: AI 分析报告存储
  - `ai_anomaly_reports`: 异常分析记录
  - `ai_optimization_suggestions`: 优化建议存储

#### 2. 业务逻辑层 (Service)
- **PerformanceAnalysisService**: 核心分析引擎
  - 数据收集和预处理
  - 异常检测算法
  - 报告生成和存储
  - 定时分析任务调度

- **LLMAnalysisService**: 智能分析服务
  - 多 LLM 提供商支持
  - 智能分析和建议生成
  - 降级和容错机制

- **ReportStorageService**: 报告存储服务
  - 文件系统存储管理
  - 报告检索和清理
  - 存储路径管理

#### 3. 控制层 (Controller)
- **PerformanceAnalysisController**: REST API 统一入口
  - 报告管理接口
  - 数据查询接口
  - 系统管理接口

### 技术栈

| 组件类型 | 技术选型 | 版本 | 说明 |
|---------|---------|------|------|
| **框架** | Spring Boot | 2.7.12 | 主应用框架 |
| **数据库** | ClickHouse | 25.4+ | 高性能列式数据库 |
| **数据库驱动** | ClickHouse JDBC | Latest | ClickHouse Java 驱动 |
| **AI 引擎** | DeepSeek API | V3 | 国产大模型，中文支持优秀 |
| **AI 引擎** | OpenAI API | GPT-3.5/4 | OpenAI 大模型服务 |
| **序列化** | Jackson | 2.13+ | JSON 处理 |
| **HTTP 客户端** | Java HttpClient | 11+ | LLM API 调用 |
| **任务调度** | Spring Scheduler | Built-in | 定时分析任务 |
| **日志** | Logback | 1.2+ | 日志框架 |

## 🔧 详细配置说明

### ClickHouse 连接配置

```yaml
spring:
  datasource:
    clickhouse:
      url: jdbc:clickhouse://localhost:8123/default
      username: default
      password: ""
      driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
      # 连接池配置
      hikari:
        maximum-pool-size: 10
        minimum-idle: 2
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
```

### LLM 提供商详细配置

#### DeepSeek 配置 （⭐ 推荐）
```yaml
ai:
  llm:
    enabled: true
    provider: deepseek
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
      model: deepseek-chat    # 或 deepseek-coder（代码优化）
      timeout: 30000
      max-tokens: 2000
      temperature: 0.7
```

**DeepSeek 使用指南：**
- 🌐 **官网注册**: https://platform.deepseek.com/
- 💰 **价格优势**: 相比 OpenAI 更具价格优势
- 🇨🇳 **中文支持**: 对中文理解和生成效果良好
- 🔌 **API 兼容**: 完全兼容 OpenAI API 格式
- 🤖 **模型选择**:
  - `deepseek-chat`: 通用对话和分析任务
  - `deepseek-coder`: 代码分析和优化建议
  - `deepseek-math`: 数学和逻辑推理

**环境变量配置：**
```bash
export AI_DEEPSEEK_API_KEY="sk-your-deepseek-api-key"
export AI_DEEPSEEK_MODEL="deepseek-chat"
```

#### OpenAI 配置
```yaml
ai:
  llm:
    enabled: true
    provider: openai
    openai:
      api-key: ${AI_OPENAI_API_KEY}
      base-url: https://api.openai.com/v1
      model: gpt-3.5-turbo  # 或 gpt-4
      timeout: 30000
      max-tokens: 2000
      temperature: 0.7
      retry-attempts: 3
      retry-delay: 1000
```

#### Azure OpenAI 配置
```yaml
ai:
  llm:
    enabled: true
    provider: azure
    azure:
      api-key: ${AI_AZURE_API_KEY}
      endpoint: ${AI_AZURE_ENDPOINT}
      deployment-name: ${AI_AZURE_DEPLOYMENT}
      api-version: 2023-05-15
      timeout: 45000
      max-tokens: 2000
      temperature: 0.7
```

#### DeepSeek 配置 （推荐）
```yaml
ai:
  llm:
    enabled: true
    provider: deepseek
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
      model: deepseek-chat    # 或 deepseek-coder（代码优化）
      timeout: 30000
      max-tokens: 2000
      temperature: 0.7
```

**DeepSeek 使用指南：**
- 🌐 **官网注册**: https://platform.deepseek.com/
- 💰 **价格优势**: 相比 OpenAI 更具价格优势
- 🇨🇳 **中文支持**: 对中文理解和生成效果良好
- 🔌 **API 兼容**: 完全兼容 OpenAI API 格式
- 🤖 **模型选择**:
  - `deepseek-chat`: 通用对话和分析任务
  - `deepseek-coder`: 代码分析和优化建议
  - `deepseek-math`: 数学和逻辑推理

**环境变量配置：**
```bash
export AI_DEEPSEEK_API_KEY="sk-your-deepseek-api-key"
export AI_DEEPSEEK_MODEL="deepseek-chat"
```

#### 本地 LLM (Ollama) 配置
```yaml
ai:
  llm:
    enabled: true
    provider: ollama
    local:
      url: http://localhost:11434
      model: llama2  # 或其他本地模型
      timeout: 60000
      context-length: 4096
```

### 性能分析配置

```yaml
ai:
  analysis:
    enabled: true
    window:
      hours: 1  # 默认分析时间窗口
      max-hours: 72  # 最大允许的时间窗口
    
    # 定时任务配置
    schedule:
      enabled: true
      cron: "0 0 */1 * * ?"  # 每小时执行
      timezone: Asia/Shanghai
    
    # 异常检测阈值
    thresholds:
      response-time-ms: 1000      # 响应时间阈值
      error-rate-percent: 5.0     # 错误率阈值
      cpu-usage-percent: 80.0     # CPU 使用率阈值
      memory-usage-percent: 85.0  # 内存使用率阈值
      throughput-drop-percent: 30 # 吞吐量下降阈值
    
    # 数据表配置
    query:
      tables:
        performance: flink_operator_agg_result  # 性能数据表
        events: events                          # 事件数据表
      batch-size: 1000  # 批量查询大小
      timeout: 30000    # 查询超时时间

  # 报告存储配置
  reports:
    storage:
      filesystem:
        enabled: true
        path: ./reports
        retention-days: 30
      clickhouse:
        enabled: true
        table: ai_performance_reports
        retention-days: 90
```

### 日志配置

```yaml
logging:
  level:
    root: INFO
    com.o11y.ai: INFO
    com.clickhouse: WARN
    org.springframework.web: DEBUG  # API 调试
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  
  file:
    name: logs/ai-analysis.log
    max-size: 100MB
    max-history: 30
```

## 📁 项目结构详解

```
ai-analysis-module/
├── src/main/java/com/o11y/ai/
│   ├── AiAnalysisApplication.java              # 应用启动类
│   │
│   ├── config/                                 # 配置类
│   │   ├── AiAnalysisProperties.java           # 配置属性绑定
│   │   └── ClickHouseConfig.java               # ClickHouse 数据源配置
│   │
│   ├── controller/                             # REST API 控制器
│   │   └── PerformanceAnalysisController.java  # 主控制器
│   │
│   ├── service/                                # 业务逻辑层
│   │   ├── PerformanceAnalysisService.java     # 核心分析服务
│   │   ├── LLMAnalysisService.java             # LLM 智能分析服务
│   │   └── ReportStorageService.java           # 报告存储服务
│   │
│   ├── repository/                             # 数据访问层
│   │   └── ClickHouseRepository.java           # ClickHouse 数据访问
│   │
│   └── model/                                  # 数据模型
│       ├── PerformanceMetrics.java             # 性能指标模型
│       ├── PerformanceAnomaly.java             # 性能异常模型
│       ├── PerformanceReport.java              # 分析报告模型
│       └── OptimizationSuggestion.java         # 优化建议模型
│
├── src/main/resources/
│   ├── application.yml                         # 主配置文件
│   ├── clickhouse-schema.sql                   # ClickHouse 表结构定义
│   └── logback-spring.xml                      # 日志配置
│
├── src/test/java/                              # 测试代码
│
├── reports/                                    # 本地报告存储目录
│   ├── report_20250623_212457_03523a46.json   # 报告文件示例
│   └── ...
│
├── logs/                                       # 日志文件目录
│   └── ai-analysis.log
│
├── target/                                     # Maven 构建输出
├── pom.xml                                     # Maven 项目配置
└── README.md                                   # 项目文档
```

## 🚀 扩展开发指南

### 添加新的性能指标

1. **扩展 PerformanceMetrics 模型**：
```java
public class PerformanceMetrics {
    // 现有字段...
    
    private double diskUsage;      // 新增磁盘使用率
    private double networkLatency; // 新增网络延迟
    
    // getter/setter 方法
}
```

2. **更新数据收集逻辑**：
```java
// 在 PerformanceAnalysisService.collectPerformanceMetrics() 中添加
private void collectDiskMetrics(PerformanceMetrics metrics) {
    // 自定义磁盘指标收集逻辑
    String sql = "SELECT avg(disk_usage) FROM system_metrics WHERE timestamp >= ?";
    // ...
}
```

### 集成新的 LLM 提供商

1. **扩展配置类**：
```java
@ConfigurationProperties(prefix = "ai.llm")
public class LLMProperties {
    // 现有配置...
    
    private CustomLLMConfig custom = new CustomLLMConfig();
    
    public static class CustomLLMConfig {
        private String endpoint;
        private String apiKey;
        private String model;
        // ...
    }
}
```

2. **实现调用逻辑**：
```java
// 在 LLMAnalysisService 中添加
private String callCustomLLM(String prompt) throws Exception {
    // 自定义 LLM 调用实现
    return customLLMClient.generate(prompt);
}
```

### 添加新的异常检测算法

```java
// 在 PerformanceAnalysisService.detectAnomalies() 中添加
private void detectMemoryLeakAnomaly(PerformanceMetrics metrics, List<PerformanceAnomaly> anomalies) {
    // 内存泄漏检测逻辑
    if (isMemoryLeakDetected(metrics)) {
        PerformanceAnomaly anomaly = PerformanceAnomaly.builder()
            .type("MEMORY_LEAK")
            .severity("HIGH")
            .description("检测到可能的内存泄漏")
            .detectedAt(LocalDateTime.now())
            .build();
        anomalies.add(anomaly);
    }
}
```

## 📊 监控和运维

### 应用监控

#### 健康检查端点
```bash
# 应用整体健康状态
curl http://localhost:8082/ai-analysis/api/ai-analysis/health

# LLM 服务状态
curl http://localhost:8082/ai-analysis/api/ai-analysis/llm/health

# ClickHouse 连接状态（通过查询表结构验证）
curl http://localhost:8082/ai-analysis/api/ai-analysis/data/events/schema
```

#### 关键指标监控

| 指标类型 | 监控项 | 正常范围 | 异常处理 |
|---------|-------|----------|----------|
| **性能** | 报告生成时间 | < 30 秒 | 检查 LLM 服务状态 |
| **存储** | ClickHouse 连接 | 连接正常 | 重启应用或数据库 |
| **文件** | 磁盘空间使用 | < 80% | 清理过期报告 |
| **内存** | JVM 堆内存 | < 85% | 调整 JVM 参数 |

### 日志管理

#### 日志级别和路径
```yaml
# 主要日志文件
logs/ai-analysis.log          # 应用主日志
logs/ai-analysis-error.log    # 错误日志
logs/clickhouse-queries.log   # 数据库查询日志
```

#### 关键日志监控
```bash
# 监控错误日志
tail -f logs/ai-analysis.log | grep ERROR

# 监控 LLM 调用失败
tail -f logs/ai-analysis.log | grep "LLM.*失败"

# 监控 ClickHouse 连接问题
tail -f logs/ai-analysis.log | grep "ClickHouse"
```

### 报告存储管理

#### 文件系统清理
```bash
# 手动清理 30 天前的报告
find ./reports -name "report_*.json" -mtime +30 -delete

# 定期清理脚本 (crontab)
0 2 * * 0 find /path/to/ai-analysis-module/reports -name "report_*.json" -mtime +30 -delete
```

#### ClickHouse 数据管理
```sql
-- 查看报告存储情况
SELECT 
    count() as total_reports,
    min(created_time) as earliest,
    max(created_time) as latest,
    formatReadableSize(sum(length(content))) as total_size
FROM ai_performance_reports;

-- 清理过期报告 (超过 90 天)
DELETE FROM ai_performance_reports 
WHERE created_time < now() - INTERVAL 90 DAY;

-- 优化表存储
OPTIMIZE TABLE ai_performance_reports;
```

### 性能调优

#### JVM 参数优化
```bash
# 推荐的 JVM 启动参数
java -Xmx2g -Xms1g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+PrintGCDetails \
     -XX:+PrintGCTimeStamps \
     -Dfile.encoding=UTF-8 \
     -Dspring.profiles.active=prod \
     -jar ai-analysis-module-1.0.5.jar
```

#### ClickHouse 查询优化
```sql
-- 为常用查询字段创建索引
ALTER TABLE flink_operator_agg_result 
ADD INDEX idx_window_start window_start TYPE minmax GRANULARITY 1;

-- 为 events 表创建时间索引
ALTER TABLE events 
ADD INDEX idx_start_time start_time TYPE minmax GRANULARITY 1;
```

#### 应用级优化配置
```yaml
# 连接池优化
spring:
  datasource:
    clickhouse:
      hikari:
        maximum-pool-size: 20      # 增加连接池大小
        minimum-idle: 5
        connection-timeout: 10000
        validation-timeout: 5000

# HTTP 客户端优化（LLM 调用）
ai:
  llm:
    openai:
      timeout: 30000             # 增加超时时间
      retry-attempts: 3          # 启用重试
      connection-pool-size: 10   # 连接池大小
```

## 🚨 故障排除指南

### 常见问题诊断

#### 1. 应用启动失败

**症状**: 应用无法启动，端口冲突
```bash
# 检查端口占用
netstat -tlnp | grep 8082

# 杀死占用进程
kill -9 <PID>

# 或修改端口配置
server.port=8083
```

**症状**: ClickHouse 连接失败
```bash
# 检查 ClickHouse 服务状态
docker ps | grep clickhouse

# 测试连接
curl http://localhost:8123/ping

# 检查配置
grep -A 5 "clickhouse" src/main/resources/application.yml
```

#### 2. LLM 服务问题

**症状**: DeepSeek API 调用失败
```bash
# 检查 DeepSeek API 密钥配置
echo $AI_DEEPSEEK_API_KEY

# 测试 DeepSeek API 连接
curl -H "Authorization: Bearer $AI_DEEPSEEK_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"Hello"}]}' \
     https://api.deepseek.com/v1/chat/completions
```

**症状**: OpenAI API 调用失败
```bash
# 检查 API 密钥配置
echo $AI_OPENAI_API_KEY

# 测试 API 连接
curl -H "Authorization: Bearer $AI_OPENAI_API_KEY" \
     https://api.openai.com/v1/models
```

**症状**: 中文响应乱码
```yaml
# 确保应用配置了正确的编码
server:
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

# 启动时指定编码参数
java -Dfile.encoding=UTF-8 -jar ai-analysis-module.jar
```

**症状**: API 调用超时
```yaml
# 增加超时时间
ai:
  llm:
    deepseek:
      timeout: 60000  # 增加到 60 秒
    openai:
      timeout: 60000  # 增加到 60 秒
      retry-attempts: 5
```

#### 3. 数据查询问题

**症状**: 查询返回空结果
```sql
-- 检查表中是否有数据
SELECT count() FROM flink_operator_agg_result;
SELECT count() FROM events;

-- 检查时间范围
SELECT min(window_start), max(window_start) 
FROM flink_operator_agg_result;
```

**症状**: 查询性能慢
```sql
-- 检查查询执行计划
EXPLAIN SYNTAX 
SELECT * FROM events 
WHERE start_time >= now() - INTERVAL 1 HOUR;

-- 查看系统指标
SELECT * FROM system.query_log 
WHERE query LIKE '%events%' 
ORDER BY event_time DESC LIMIT 10;
```

#### 4. 报告生成问题

**症状**: 报告生成超时
```bash
# 检查分析服务日志
grep "generateAnalysisReport" logs/ai-analysis.log

# 检查数据量
curl "http://localhost:8082/ai-analysis/api/ai-analysis/data/events/sample?limit=1"
```

**症状**: 报告存储失败
```bash
# 检查文件权限
ls -la ./reports/

# 检查磁盘空间
df -h

# 检查 ClickHouse 写入权限
docker exec clickhouse-server clickhouse-client --query "SELECT 1"
```

### 调试技巧

#### 启用详细日志
```yaml
logging:
  level:
    com.o11y.ai: DEBUG
    com.clickhouse: DEBUG
    org.springframework.jdbc: DEBUG
```

#### 临时禁用 LLM 服务
```yaml
ai:
  llm:
    enabled: false  # 快速测试基础功能
```

#### 使用测试数据
```sql
-- 插入测试数据进行调试
INSERT INTO flink_operator_agg_result VALUES 
('test-service', 'test-instance', 'test-method', 'test-operator', 
 100.0, 200.0, 0.05, 1000, 50, 950, now() - INTERVAL 1 HOUR, now());
```

## 🔄 版本更新记录

### v1.0.5 (当前版本) - 2025-06-24

#### ✨ 新增功能
- **DeepSeek LLM 集成**: 新增国产大模型支持，中文分析能力优秀
- **双重存储架构**: 支持 ClickHouse 和文件系统双重报告存储
- **完整 REST API**: 新增多种数据查询和报告检索接口
- **错误链路追踪**: 支持从 events 表分析错误调用链
- **慢请求分析**: 可配置阈值的慢请求检测和分析
- **服务拓扑**: 动态服务依赖关系分析
- **数据探索**: 支持查询 events 表结构和样例数据
- **中文编码优化**: 解决 DeepSeek 返回内容的中文乱码问题

#### 🔧 技术改进
- **多 LLM 引擎**: 新增 DeepSeek 支持，提供更多 LLM 选择
- **编码问题修复**: 完善 UTF-8 编码处理，确保中文内容正确显示
- **ClickHouse 深度集成**: 替换 H2，使用 ClickHouse 作为主数据源
- **时间格式优化**: 修复 LocalDateTime 与 ClickHouse DateTime 兼容性问题
- **查询性能优化**: 优化聚合查询和批量数据处理
- **异常处理增强**: 完善 LLM 服务降级和错误处理机制

#### 📊 数据模型更新
- 支持从 `flink_operator_agg_result` 表查询聚合性能数据
- 支持从 `events` 表查询原始链路追踪数据
- 新增 `ai_performance_reports` 等 AI 分析相关表
- 完善数据映射和类型转换

#### 🚀 API 扩展
- 新增 `/reports/clickhouse` 端点支持从 ClickHouse 检索报告
- 新增 `/traces/errors` 端点支持错误链路查询
- 新增 `/traces/slow` 端点支持慢请求分析
- 新增 `/topology/services` 端点支持服务拓扑查询
- 新增 `/data/events/*` 端点支持数据探索
- 优化所有 API 的中文响应编码处理

#### 🛠️ 配置优化
- 新增 DeepSeek LLM 配置选项和环境变量支持
- 优化 UTF-8 编码配置，支持中文内容正确处理
- 简化 ClickHouse 数据源配置
- 优化 LLM 服务配置结构
- 增强日志和监控配置
- 完善错误处理和重试机制

### v1.0.4 - 2025-06-20
- 初始 LLM 集成
- 基础报告生成功能
- H2 数据库支持

### 路线图 (未来版本)

#### v1.1.0 (计划中)
- **实时流分析**: 集成 Kafka 消费者，支持实时数据分析
- **告警系统**: 基于异常检测的智能告警
- **仪表板**: Web UI 界面和可视化图表
- **多租户支持**: 支持多个业务域的数据隔离

#### v1.2.0 (计划中)
- **机器学习**: 集成 TensorFlow/PyTorch 进行预测分析
- **自动调优**: 基于分析结果的自动化性能优化建议
- **集群部署**: 支持分布式部署和负载均衡
- **API 网关**: 统一 API 管理和认证

## 🤝 贡献指南

### 开发环境设置

1. **克隆项目**:
```bash
git clone https://github.com/o11y/segment-alarm-clickhouse.git
cd segment-alarm-clickhouse/ai-analysis-module
```

2. **安装依赖**:
```bash
mvn clean install
```

3. **配置开发环境**:
```bash
# 复制配置模板
cp src/main/resources/application.yml.template src/main/resources/application.yml

# 配置测试数据库
docker-compose up -d clickhouse
```

4. **运行测试**:
```bash
mvn test
```

### 代码规范

#### Java 代码风格
- 使用 Google Java Style Guide
- 类和方法需要完整的 Javadoc 注释
- 单个方法不超过 50 行
- 优先使用 Builder 模式构建复杂对象

#### 提交规范
```bash
# 提交消息格式
<type>(<scope>): <description>

# 示例
feat(analysis): 添加慢请求检测功能
fix(clickhouse): 修复时间格式兼容性问题
docs(readme): 更新 API 使用文档
```

#### 分支策略
- `main`: 主分支，稳定版本
- `develop`: 开发分支，新功能集成
- `feature/*`: 功能分支
- `hotfix/*`: 紧急修复分支

### 贡献流程

1. **Fork 项目** 到你的 GitHub 账户
2. **创建功能分支**: `git checkout -b feature/new-feature`
3. **编写代码** 并添加测试用例
4. **运行测试**: `mvn test`
5. **提交更改**: `git commit -m "feat: 添加新功能"`
6. **推送分支**: `git push origin feature/new-feature`
7. **创建 Pull Request** 并描述更改内容

### 问题报告

#### Bug 报告模板
```markdown
**问题描述**
简要描述遇到的问题

**复现步骤**
1. 执行的操作
2. 使用的参数
3. 期望结果 vs 实际结果

**环境信息**
- Java 版本:
- ClickHouse 版本:
- 操作系统:
- 错误日志:

**附加信息**
其他可能有用的信息
```

#### 功能请求模板
```markdown
**功能描述**
详细描述希望添加的功能

**使用场景**
说明该功能的具体使用场景

**预期行为**
描述功能的预期行为和 API 设计

**替代方案**
是否考虑过其他实现方式
```

## 📜 许可证

本项目采用 **MIT 许可证**，详见 [LICENSE](../LICENSE) 文件。

### 许可证说明
- ✅ 商业使用
- ✅ 修改代码
- ✅ 分发代码
- ✅ 私人使用
- ❌ 责任免除
- ❌ 保证免除

## 🙏 致谢

感谢以下开源项目和贡献者：

- **[Spring Boot](https://spring.io/projects/spring-boot)** - 应用框架
- **[ClickHouse](https://clickhouse.com/)** - 高性能数据库
- **[Apache Flink](https://flink.apache.org/)** - 流处理引擎
- **[OpenAI](https://openai.com/)** - AI 服务提供商
- **[Jackson](https://github.com/FasterXML/jackson)** - JSON 处理库

特别感谢所有提交 Issue 和 Pull Request 的贡献者！

---

## 📞 联系我们

- **项目主页**: https://github.com/o11y/segment-alarm-clickhouse
- **问题反馈**: https://github.com/o11y/segment-alarm-clickhouse/issues
- **文档中心**: https://docs.o11y.com/ai-analysis
- **社区讨论**: https://discord.gg/o11y-community

---

<div align="center">

**[⬆ 回到顶部](#ai-智能分析模块)**

*如果这个项目对你有帮助，请给我们一个 ⭐ Star！*

</div>
