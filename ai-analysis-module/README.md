# AI 智能分析模块

## 概述

这是一个独立的 AI 智能化应用性能分析模块，基于 Spring Boot 构建，集成了多种 LLM 提供商，能够对可观测性数据进行智能分析并生成优化建议。

## 主要功能

### 🔍 智能性能分析
- 自动收集系统性能指标（JVM、应用、数据库、系统资源）
- 基于阈值的异常检测
- LLM 驱动的智能分析和洞察

### 🤖 多 LLM 支持
- **OpenAI GPT**：支持 GPT-3.5/GPT-4
- **Azure OpenAI**：企业级 OpenAI 服务
- **本地 LLM**：支持 Ollama 等本地部署模型
- **降级方案**：LLM 不可用时的基础分析

### 📊 报告生成
- 智能化分析报告
- 优化建议生成
- 报告存储和检索
- 定时自动分析

### 🔧 REST API
- 报告生成 API
- 报告查询 API
- 分析任务触发
- 健康检查

## 快速开始

### 1. 环境要求
- Java 11+
- Maven 3.6+
- ClickHouse 数据库
- （可选）OpenAI API Key 或本地 LLM

### 2. 配置

编辑 `src/main/resources/application.yml`：

```yaml
# 数据源配置
spring:
  datasource:
    url: jdbc:clickhouse://localhost:8123/default
    username: default
    password: 

# AI 配置
ai:
  llm:
    provider: openai  # openai, azure, ollama
    openai:
      api-key: ${AI_OPENAI_API_KEY}
      model: gpt-3.5-turbo
```

### 3. 环境变量

```bash
# OpenAI 配置
export AI_OPENAI_API_KEY="your-openai-api-key"
export AI_OPENAI_BASE_URL="https://api.openai.com/v1"

# 或者 Azure OpenAI 配置
export AI_AZURE_API_KEY="your-azure-api-key"
export AI_AZURE_ENDPOINT="https://your-resource.openai.azure.com"
export AI_AZURE_DEPLOYMENT="your-deployment-name"

# 或者本地 LLM 配置
export AI_LOCAL_LLM_URL="http://localhost:11434"
export AI_LOCAL_LLM_MODEL="llama2"
```

### 4. 构建和运行

```bash
# 进入 AI 模块目录
cd ai-analysis-module

# 构建项目
mvn clean package

# 运行应用
java -jar target/ai-analysis-module-1.0.5.jar

# 或者直接运行
mvn spring-boot:run
```

应用将在 `http://localhost:8081` 启动

## API 使用

### 生成分析报告

```bash
# 生成过去1小时的报告
curl -X POST "http://localhost:8081/ai-analysis/api/ai-analysis/reports/generate?timeRangeHours=1"

# 生成过去24小时的报告
curl -X POST "http://localhost:8081/ai-analysis/api/ai-analysis/reports/generate?timeRangeHours=24"
```

### 获取报告列表

```bash
# 获取最近10个报告
curl "http://localhost:8081/ai-analysis/api/ai-analysis/reports?limit=10"
```

### 获取特定报告

```bash
# 根据报告ID获取详细信息
curl "http://localhost:8081/ai-analysis/api/ai-analysis/reports/{reportId}"
```

### 健康检查

```bash
curl "http://localhost:8081/ai-analysis/api/ai-analysis/health"
```

## 配置说明

### LLM 提供商配置

#### OpenAI
```yaml
ai:
  llm:
    provider: openai
    openai:
      api-key: ${AI_OPENAI_API_KEY}
      base-url: https://api.openai.com/v1
      model: gpt-3.5-turbo
      timeout: 30000
      max-tokens: 2000
      temperature: 0.7
```

#### Azure OpenAI
```yaml
ai:
  llm:
    provider: azure
    azure:
      api-key: ${AI_AZURE_API_KEY}
      endpoint: ${AI_AZURE_ENDPOINT}
      deployment-name: ${AI_AZURE_DEPLOYMENT}
      api-version: 2023-05-15
```

#### 本地 LLM (Ollama)
```yaml
ai:
  llm:
    provider: ollama
    local:
      url: http://localhost:11434
      model: llama2
      timeout: 60000
```

### 分析配置

```yaml
ai:
  analysis:
    enabled: true
    window:
      hours: 1  # 默认分析时间窗口
    schedule:
      enabled: true
      cron: "0 0 */1 * * ?"  # 每小时执行
    thresholds:
      response-time-ms: 1000
      error-rate-percent: 5.0
      cpu-usage-percent: 80.0
      memory-usage-percent: 85.0
```

## 项目结构

```
ai-analysis-module/
├── src/main/java/com/o11y/ai/
│   ├── AiAnalysisApplication.java          # 启动类
│   ├── config/
│   │   └── AiAnalysisProperties.java       # 配置属性
│   ├── controller/
│   │   └── PerformanceAnalysisController.java  # REST API
│   ├── service/
│   │   ├── PerformanceAnalysisService.java     # 核心分析服务
│   │   ├── LLMAnalysisService.java             # LLM 分析服务
│   │   └── ReportStorageService.java           # 报告存储服务
│   └── model/
│       ├── PerformanceMetrics.java             # 性能指标模型
│       ├── PerformanceAnomaly.java             # 异常模型
│       └── PerformanceReport.java              # 报告模型
├── src/main/resources/
│   └── application.yml                     # 配置文件
└── pom.xml                                 # Maven 配置
```

## 扩展和定制

### 添加新的指标收集器

实现 `PerformanceAnalysisService` 中的指标收集方法：

```java
private void collectCustomMetrics(Connection conn, PerformanceMetrics metrics, 
                                 LocalDateTime startTime, LocalDateTime endTime) {
    // 自定义指标收集逻辑
}
```

### 添加新的异常检测器

在 `detectAnomalies` 方法中添加自定义异常检测逻辑：

```java
// 自定义异常检测
if (customCondition) {
    PerformanceAnomaly anomaly = new PerformanceAnomaly();
    // 设置异常属性
    anomalies.add(anomaly);
}
```

### 集成新的 LLM 提供商

在 `LLMAnalysisService` 中添加新的调用方法：

```java
private String callCustomLLM(String prompt) throws Exception {
    // 自定义 LLM 调用逻辑
}
```

## 监控和运维

### 日志

日志文件位置：`logs/ai-analysis.log`

关键日志级别：
- `INFO`：正常操作日志
- `WARN`：警告信息（如 LLM 调用失败）
- `ERROR`：错误信息（需要关注）

### 监控端点

- `/actuator/health` - 健康检查
- `/actuator/metrics` - 指标监控
- `/actuator/prometheus` - Prometheus 格式指标

### 报告存储

报告默认存储在 `./reports` 目录下，文件格式：
- `report_yyyyMMdd_HHmmss_reportId.json`

建议定期清理过期报告：

```bash
# 清理30天前的报告
find ./reports -name "report_*.json" -mtime +30 -delete
```

## 故障排除

### 常见问题

1. **LLM API 调用失败**
   - 检查 API Key 配置
   - 验证网络连接
   - 查看 API 限制和余额

2. **数据库连接失败**
   - 检查 ClickHouse 服务状态
   - 验证连接配置
   - 确认数据库权限

3. **报告生成失败**
   - 检查数据表是否存在
   - 验证数据格式
   - 查看错误日志

### 调试模式

启用调试日志：

```yaml
logging:
  level:
    com.o11y.ai: DEBUG
```

## 性能优化

### 数据库查询优化

- 为常用查询字段添加索引
- 使用合适的时间分区策略
- 限制查询时间范围

### LLM 调用优化

- 使用连接池
- 实现重试机制
- 缓存相似查询结果
- 异步处理长时间分析

### 内存管理

```bash
# 调整 JVM 参数
java -Xmx2g -Xms1g -XX:+UseG1GC -jar ai-analysis-module.jar
```

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 发起 Pull Request

## 许可证

本项目采用 MIT 许可证，详见 LICENSE 文件。
