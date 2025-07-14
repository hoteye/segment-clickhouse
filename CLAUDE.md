# Segment Alarm ClickHouse

高性能可观测性数据处理系统，多模块架构，实时数据处理。

## 技术栈

Java 11+、Apache Flink 1.17.2、ClickHouse 21.0+、Kafka 3.5.1、Spring Boot 2.7.12
AI支持：DeepSeek、OpenAI、Azure OpenAI、本地LLM

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
# 快速启动
./quick-start.sh

# 管理界面
./manage.sh

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

### Flink配置
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

### AI配置
```yaml
ai-analysis:
  llm:
    provider: deepseek
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY}
      model: deepseek-chat
```

## 数据库表

- `events` - SkyWalking链路追踪数据
- `flink_operator_agg_result` - 聚合性能指标
- `hourly_alarm_rules` - 小时级动态阈值规则(24条)
- `ai_performance_reports` - AI分析报告

## 核心组件

### Flink模块
- `AggregateOperator.java` - 实时数据聚合
- `AlarmRule.java` - 多级阈值告警规则
- `HourlyDynamicThresholdGenerator.java` - 小时级规则生成
- `HourlyRulePublishProcessFunction.java` - 规则定时下发
- `SimpleClickHouseSink.java` - ClickHouse写入

### AI模块
- `PerformanceAnalysisService.java` - 核心分析引擎
- `LLMAnalysisService.java` - 多LLM提供商支持
- `ClickHouseRepository.java` - 数据访问
- `PerformanceAnalysisController.java` - REST API

## 小时级动态阈值系统

一次性分析历史数据生成24小时规则，每小时整点从`hourly_alarm_rules`表读取当前小时规则下发到Kafka。

```bash
# 生成规则
java -jar flink-data-transformer-module/target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.domain.model.alarm.HourlyDynamicThresholdGenerator generate-all 7
```

## 监控端点

- Flink Web UI: http://localhost:8081
- AI健康检查: http://localhost:8082/ai-analysis/api/ai-analysis/health
- ClickHouse: http://localhost:8123/play

## AI助手指令

### 文件操作
- 优先编辑现有文件，避免创建新文件
- 不要主动创建文档文件(*.md)
- 遵循现有代码风格

### 开发规范
- Java 11+语法，Google Style Guide
- 包名：`com.o11y.{module}.{layer}`
- 单元测试覆盖率 > 80%
- 提交格式：`feat(module): 描述`

### 关键路径
- Flink核心：`flink-data-transformer-module/src/main/java/com/o11y/`
- AI分析：`ai-analysis-module/src/main/java/com/o11y/ai/`
- 配置文件：`src/main/resources/application.yaml`

## 故障排查

1. 服务启动失败：检查端口占用、数据库连接
2. 规则下发异常：检查Kafka连接和主题配置
3. LLM调用失败：验证API密钥、网络连接
4. 数据查询问题：检查表结构、时间范围

## 相关文档

- [Flink模块文档](./flink-data-transformer-module/README.md)
- [AI模块文档](./ai-analysis-module/README.md)
- [小时级规则系统文档](./flink-data-transformer-module/README-HOURLY-RULES.md)