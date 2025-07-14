# Segment Alarm ClickHouse

一个高性能的可观测性数据处理系统，专为大规模分布式系统监控和告警而设计。本项目采用多模块架构，实现了从数据采集到智能分析的完整可观测性解决方案。

## 项目概述

**版本**: 1.0.5  
**架构**: 多模块架构，模块化隔离，依赖统一管理  
**性能目标**: 10K+ records/sec，端到端延迟 < 100ms，99.9%可用性  
**AI 能力**: 集成多种 LLM，支持 DeepSeek、OpenAI、Azure OpenAI 等

## 🏗️ 架构设计原则

- **模块化隔离**: 每个模块独立开发、测试和部署
- **依赖管理**: 父项目统一管理版本，子模块继承配置
- **脚本归属**: 运维脚本归属到相关功能模块，便于维护
- **清晰边界**: Flink 相关的所有资源（代码、脚本、配置）集中管理

## 技术栈

### 核心技术

- **Java**: 11+ (支持最新 JVM 特性)
- **Apache Flink**: 1.17.2 (实时流处理引擎)
- **ClickHouse**: 21.0+ (列式数据库，高性能 OLAP)
- **Apache Kafka**: 3.5.1 (高吞吐量消息中间件)
- **Spring Boot**: 2.7.12 (微服务应用框架)

### AI/ML 技术栈

- **DeepSeek AI**: 国产大模型，中文支持优秀，价格实惠
- **OpenAI GPT**: GPT-3.5/GPT-4 模型支持
- **Azure OpenAI**: 企业级 OpenAI 服务集成
- **本地 LLM**: 支持 Ollama 等本地部署模型

### 数据序列化

- **Protobuf**: 3.22.3 (高性能序列化)
- **Jackson**: 2.15.2 (JSON 处理)

### 测试框架

- **JUnit Jupiter**: 5.9.3
- **Mockito**: 5.5.0
- **TestContainers**: 1.17.6
- **Awaitility**: 4.2.0

## 📋 功能模块

### 🚀 Flink Data Transformer Module

**职责**: 实时数据流处理和转换

- **数据源处理**: 从 Kafka 消费 Segment 数据流
- **实时转换**: 支持复杂的数据转换、聚合和清洗
- **告警检测**: 实时监控数据异常，动态告警规则配置
- **数据存储**: 高效将处理结果写入 ClickHouse
- **容错机制**: 基于 Flink 检查点的数据一致性保障

### 🤖 AI Analysis Module

**职责**: 基于 LLM 的智能性能分析

- **性能分析**: 自动分析系统性能指标和趋势
- **异常检测**: 基于机器学习的智能异常识别
- **优化建议**: 提供针对性的系统优化建议
- **报告生成**: 生成详细的分析报告和可视化图表
- **多 LLM 支持**: 支持 OpenAI、Azure OpenAI、DeepSeek 等多种 LLM 提供商

## 项目结构

```
segment-alarm-clickhouse/                    # 父项目（多模块管理）
├── ai-analysis-module/                      # 🤖 AI 智能分析模块
│   ├── src/main/java/com/o11y/ai/          # AI 分析核心代码
│   ├── src/main/resources/                  # 配置文件
│   └── README.md                           # AI 模块详细文档
├── flink-data-transformer-module/          # 🚀 Flink 数据转换模块
│   ├── src/main/java/com/o11y/             # Flink 处理核心代码
│   ├── scripts/                            # 运维脚本和工具
│   │   ├── manage.sh                       # 完整管理界面
│   │   ├── quick-start.sh                  # 快速启动脚本
│   │   ├── flink_deploy.sh                # Flink 作业部署
│   │   └── ...                            # 其他运维脚本
│   ├── proto/                              # Protobuf 定义
│   └── README.md                           # Flink 模块详细文档
├── docs/                                   # 项目文档和配置
├── manage.sh                               # 根目录管理入口（调用子模块脚本）
├── quick-start.sh                          # 根目录快速启动入口
├── pom.xml                                 # 父项目依赖管理
├── Dockerfile                              # Docker 镜像构建
├── init.sql                               # ClickHouse 初始化脚本
└── README.md                              # 项目说明文档
```

## 🚀 快速开始

### 1. 环境准备

```bash
# 检查 Java 版本
java -version  # 需要 Java 11+

# 检查 Maven 版本
mvn -version   # 需要 Maven 3.6+

# 快速启动所有必要服务（推荐）
./quick-start.sh

# 或手动启动基础设施 (使用 Docker Compose)
docker-compose up -d kafka clickhouse
```

### 2. 便捷脚本使用（推荐）

```bash
# 根目录快速启动 - 自动检测环境并启动必要服务
./quick-start.sh

# 完整管理界面 - 提供所有功能的菜单式访问
./manage.sh

# 直接访问子模块脚本
./flink-data-transformer-module/scripts/flink_deploy.sh
```

### 3. 手动构建与部署

```bash
# 编译所有模块
mvn clean compile

# 打包所有模块
mvn clean package

# Flink模块独立构建
cd flink-data-transformer-module && mvn clean package -DskipTests

# AI分析模块独立构建
cd ai-analysis-module && mvn clean package

# 小时级规则生成（定期执行）
java -jar flink-data-transformer-module/target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator generate-all 7
```

### 4. 服务启动

```bash
# 启动Flink数据转换模块
java --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -cp flink-data-transformer-module/target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.application.launcher.FlinkServiceLauncher

# 启动AI分析模块
cd ai-analysis-module
mvn spring-boot:run
# 或使用JAR: java -jar target/ai-analysis-module-1.0.5.jar
```

### 5. 服务验证

```bash
# 检查Flink作业状态
curl http://localhost:8081/jobs

# 检查AI分析模块健康状态
curl http://localhost:8082/ai-analysis/api/ai-analysis/health

# 查看可用的REST API
curl http://localhost:8082/ai-analysis/api/ai-analysis/reports
```

## 📊 配置详解

### Flink 模块配置 (flink-data-transformer-module/src/main/resources/application.yaml)

```yaml
# Kafka配置
kafka:
  bootstrap-servers: localhost:9092
  topics:
    segment: segment-data
    alarm-rule: alarm-rules

# ClickHouse配置
clickhouse:
  url: jdbc:clickhouse://localhost:8123/o11y
  username: default
  password: ""
  batch-size: 1000

# Flink配置
flink:
  parallelism: 4
  checkpoint:
    interval: 60000
    mode: EXACTLY_ONCE
```

### AI 模块配置 (ai-analysis-module/src/main/resources/application.yml)

```yaml
# AI分析配置
ai-analysis:
  llm:
    provider: deepseek # deepseek, openai, azure, ollama
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com/v1
      model: deepseek-chat
    openai:
      api-key: ${AI_OPENAI_API_KEY}
      model: gpt-3.5-turbo

# 数据源配置
spring:
  datasource:
    url: jdbc:clickhouse://localhost:8123/o11y
    username: default
    password: ""
```

### 阈值配置 (threshold-config.yaml)

```yaml
threshold:
  success_rate:
    low: 0.995 # 99.5%
    mid: 0.99 # 99.0%
    high: 0.985 # 98.5%
  response_time:
    avg_duration_multiplier:
      low: 1.2 # 平均响应时间的1.2倍
      mid: 1.5 # 平均响应时间的1.5倍
      high: 2.0 # 平均响应时间的2.0倍
```

## 🔧 核心组件

### 1. Flink 数据处理 (flink-data-transformer-module)

- **聚合算子**: `AggregateOperator.java:1-400` - 实时数据聚合
- **告警规则**: `AlarmRule.java:1-64` - 多级阈值告警规则
- **动态阈值系统**: 小时级规则下发系统
  - `HourlyDynamicThresholdGenerator.java` - 一次性生成 24 小时规则
  - `HourlyRulePublishProcessFunction.java` - 基于 Flink Timer 的定时下发
- **数据源**: `SegmentDeserializationSchema.java` - Protobuf 数据反序列化
- **数据输出**: `SimpleClickHouseSink.java` - ClickHouse 高效写入

### 2. AI 智能分析 (ai-analysis-module)

- **性能分析**: `PerformanceAnalysisService.java` - 核心分析引擎
- **LLM 服务**: `LLMAnalysisService.java` - 多 LLM 提供商支持
- **报告存储**: `ReportStorageService.java` - 双重存储架构
- **数据访问**: `ClickHouseRepository.java` - ClickHouse 数据操作
- **REST API**: `PerformanceAnalysisController.java` - 统一 API 入口

### 3. 数据存储架构

- **ClickHouse 主存储**:
  - `events` - 原始链路追踪数据
  - `flink_operator_agg_result` - 聚合性能指标
  - `hourly_alarm_rules` - 小时级动态阈值规则（24 条记录）
  - `ai_performance_reports` - AI 分析报告
- **Kafka 消息流**: 实时数据传输和告警下发
- **文件系统备份**: 本地 JSON 报告存储

## 🎯 小时级动态阈值规则系统

### 核心设计理念

- **一次性分析**: 分析前 N 天的`flink_operator_agg_result`数据，生成所有 24 小时规则
- **定时下发**: 每小时整点从`hourly_alarm_rules`表中取当前小时规则并下发到 Kafka
- **Flink 原生**: 基于 Flink Timer 机制，可靠性高，支持故障恢复
- **简洁存储**: 24 条记录（每小时一条），主键查询即可

### 系统架构

```
flink_operator_agg_result (历史数据)
    ↓ (一次性分析)
hourly_alarm_rules (24条记录)
    ↓ (每小时读取)
Kafka alarm_rule_topic (规则下发)
```

### 数据库表结构

```sql
CREATE TABLE hourly_alarm_rules (
    hour_of_day UInt8,          -- 主键：小时序号 (0-23)
    rule_map_json String,       -- 该小时的所有规则JSON
    rule_count UInt32,          -- 规则数量
    total_services UInt32,      -- 服务数量
    total_operators UInt32,     -- 操作员数量
    PRIMARY KEY (hour_of_day)
) ENGINE = MergeTree() ORDER BY hour_of_day;
```

### 工作流程

1. **初始化**: 运行`HourlyDynamicThresholdGenerator.generateAllHourlyRulesOnce(7)`
2. **启动 Flink**: FlinkService 自动启动`HourlyRulePublishProcessFunction`
3. **定时下发**: 每小时整点(00:00, 01:00, ..., 23:00)自动下发规则

### 性能优势

| 维度           | 传统设计     | 新设计       | 改进          |
| -------------- | ------------ | ------------ | ------------- |
| **计算频率**   | 每小时重计算 | 一次性计算   | 24x 性能提升  |
| **数据库查询** | 复杂聚合查询 | 简单主键查询 | 100x 查询速度 |
| **存储记录数** | 规则数量 ×24 | 固定 24 条   | 大幅减少      |
| **资源消耗**   | 高           | 低           | 显著降低      |

## 🔧 监控与运维

### 监控端点

```bash
# Flink Web UI
http://localhost:8081

# AI分析模块健康检查
curl http://localhost:8082/ai-analysis/api/ai-analysis/health

# ClickHouse监控
curl http://localhost:8123/play
```

### 关键监控指标

- **Flink**: 作业状态、吞吐量、背压、检查点成功率
- **小时级规则**: 规则生成耗时、下发成功率、hourly_alarm_rules 表数据更新频率
- **AI 模块**: 报告生成时间、LLM 调用成功率、存储状态
- **ClickHouse**: 写入 TPS、查询延迟、连接池状态
- **Kafka**: 消费延迟、分区状态、消息积压、alarm_rule_topic 消息发送成功率

### 日志管理

```bash
# 主要日志路径
flink-data-transformer-module/logs/flink-debug.log
ai-analysis-module/logs/ai-analysis.log

# 日志监控
tail -f ai-analysis-module/logs/ai-analysis.log | grep ERROR
```

### 故障排查指南

1. **服务启动失败**: 检查端口占用、数据库连接
2. **小时级规则问题**:
   - 规则生成失败：检查 ClickHouse 连接和历史数据
   - 规则下发失败：检查 Kafka 连接和主题配置
   - Flink 任务异常：依赖 Flink checkpoint 自动恢复
3. **LLM 调用失败**: 验证 API 密钥、网络连接、编码配置
4. **数据查询问题**: 检查表结构、时间范围、查询性能
5. **报告生成超时**: 查看数据量、LLM 响应时间
6. **存储问题**: 检查磁盘空间、文件权限、数据库写入权限

### 运维脚本

```bash
# 使用便捷管理脚本
./manage.sh                     # 完整管理界面
./quick-start.sh               # 快速启动
./flink-data-transformer-module/scripts/flink_deploy.sh   # Flink部署
```

## 🎯 项目特色

### 技术亮点

- 🚀 **高性能**: Flink + ClickHouse 组合，支持 10K+ records/sec
- 🧠 **多 LLM 智能**: 支持 DeepSeek、OpenAI、Azure OpenAI 等多种 LLM
- ⚡ **实时性**: 端到端延迟 < 100ms，99.9%可用性
- 📊 **全链路**: 完整的数据采集到智能分析解决方案
- 🔧 **模块化**: 独立开发、测试和部署的模块化架构

### 核心创新

- **小时级动态阈值**: 一次性生成+定时下发的高效架构
  - 性能提升 24 倍（避免每小时重复计算）
  - 查询速度提升 100 倍（简单主键查询）
  - 基于 Flink Timer 机制，支持故障自动恢复
- **双重存储**: ClickHouse 主存储+文件系统备份的可靠架构
- **智能降级**: LLM 不可用时自动使用基础分析算法
- **中文优化**: 针对中文场景优化的编码和分析能力
- **便捷运维**: 完整的管理脚本和快速启动工具

### 应用价值

- **企业级可观测性**: 适用于大规模分布式系统监控
- **智能运维**: AI 驱动的性能分析和优化建议
- **成本控制**: 国产 LLM 支持，降低 AI 分析成本
- **开箱即用**: 丰富的脚本和文档，快速部署上线

## 📚 相关文档

- [Flink 数据转换模块详细文档](./flink-data-transformer-module/README.md)
- [AI 分析模块详细文档](./ai-analysis-module/README.md)
- [小时级动态阈值规则系统详细文档](./flink-data-transformer-module/README-HOURLY-RULES.md)
- [小时级规则系统实现总结](./flink-data-transformer-module/IMPLEMENTATION-SUMMARY.md)
- [运维脚本使用指南](./flink-data-transformer-module/scripts/README.md)

## 🤝 开发规范

### 代码风格

- 使用 Java 11+语法特性，遵循 Google Java Style Guide
- 包名约定：`com.o11y.{module}.{layer}`
- 类和方法需要完整的 Javadoc 注释
- 单元测试覆盖率 > 80%

### Git 提交规范

```bash
feat(analysis): 添加慢请求检测功能
fix(clickhouse): 修复时间格式兼容性问题
docs(readme): 更新API使用文档
```
