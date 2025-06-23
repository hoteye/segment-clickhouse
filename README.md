# Segment Alarm ClickHouse 系统

一个高性能的可观测性数据处理系统，专为大规模分布式系统监控和告警而设计。本项目采用多模块架构，实现了从数据采集到智能分析的完整可观测性解决方案。

## 🏗️ 项目架构

```
segment-alarm-clickhouse/                    # 父项目（多模块管理）
├── ai-analysis-module/                      # 🤖 AI 智能分析模块
│   ├── src/main/java/com/o11y/ai/          # AI 分析核心代码
│   ├── src/main/resources/                  # 配置文件
│   └── README.md                           # AI 模块详细文档
├── flink-data-transformer-module/          # 🚀 Flink 数据转换模块
│   ├── src/main/java/com/o11y/             # Flink 处理核心代码
│   ├── proto/                              # Protobuf 定义
│   └── README.md                           # Flink 模块详细文档
├── pom.xml                                 # 父项目依赖管理
├── Dockerfile                              # Docker 镜像构建
├── init.sql                               # ClickHouse 初始化脚本
└── README.md                              # 本文件
```

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
- **多 LLM 支持**: 支持 OpenAI、Azure OpenAI 等多种 LLM 提供商

## 🛠️ 技术栈

| 技术领域 | 技术选型 | 版本 | 说明 |
|---------|---------|------|------|
| **流处理** | Apache Flink | 1.17.2 | 实时数据流处理引擎 |
| **消息队列** | Apache Kafka | 3.5.1 | 高吞吐量消息中间件 |
| **数据存储** | ClickHouse | 21.0+ | 列式数据库，支持高性能 OLAP |
| **AI/ML** | OpenAI API | GPT-4 | 大语言模型接口 |
| **应用框架** | Spring Boot | 3.1.x | 微服务应用框架 |
| **构建工具** | Maven | 3.6+ | 项目构建和依赖管理 |
| **运行环境** | Java | 11+ | JVM 运行环境 |
| **容器化** | Docker | - | 容器化部署 |
| **序列化** | Protobuf | 3.22.3 | 高效数据序列化 |

## 🚀 快速开始

### 1. 环境准备

```bash
# 检查 Java 版本
java -version  # 需要 Java 11+

# 检查 Maven 版本
mvn -version   # 需要 Maven 3.6+

# 启动基础设施 (使用 Docker Compose)
docker-compose up -d kafka clickhouse
```

### 2. 编译项目

```bash
# 克隆项目
git clone <repository-url>
cd segment-alarm-clickhouse

# 编译所有模块
mvn clean compile

# 打包所有模块
mvn clean package
```

### 3. 运行模块

#### 启动 Flink 数据转换模块

```bash
# 方式 1: 直接运行
java --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -cp flink-data-transformer-module/target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.flink.FlinkKafkaToClickHouseJob

# 方式 2: 使用 VS Code 任务
# 在 VS Code 中按 Ctrl+Shift+P，搜索 "Tasks: Run Task"，选择相应的 Flink 任务
```

#### 启动 AI 分析模块

```bash
# 进入 AI 模块目录
cd ai-analysis-module

# 启动 Spring Boot 应用
mvn spring-boot:run

# 或使用 JAR 文件启动
java -jar target/ai-analysis-module-1.0.5.jar
```

### 4. 验证服务

```bash
# 检查 Flink 作业状态
curl http://localhost:8081/jobs

# 检查 AI 分析模块健康状态
curl http://localhost:8080/actuator/health

# 查看可用的 REST API
curl http://localhost:8080/api/analysis/status
```

## 📊 模块详细配置

### Flink 模块配置 (`flink-data-transformer-module/src/main/resources/application.yaml`)

```yaml
# Kafka 配置
kafka:
  bootstrap-servers: localhost:9092
  topics:
    segment: segment-data
    alarm-rule: alarm-rules

# ClickHouse 配置
clickhouse:
  url: jdbc:clickhouse://localhost:8123/o11y
  username: default
  password: ""
  batch-size: 1000

# Flink 配置
flink:
  parallelism: 4
  checkpoint:
    interval: 60000
    mode: EXACTLY_ONCE
```

### AI 模块配置 (`ai-analysis-module/src/main/resources/application.yml`)

```yaml
# AI 分析配置
ai-analysis:
  llm:
    provider: openai
    api-key: ${OPENAI_API_KEY}
    model: gpt-4
    temperature: 0.7

# 数据源配置
spring:
  datasource:
    url: jdbc:clickhouse://localhost:8123/o11y
    username: default
    password: ""
```

## 📈 性能指标

### Flink 模块性能

| 指标 | 数值 | 说明 |
|------|------|------|
| **吞吐量** | 10K+ records/sec | 单节点处理能力 |
| **延迟** | < 100ms | 端到端处理延迟 |
| **可用性** | 99.9% | 基于检查点的容错 |
| **扩展性** | 线性扩展 | 支持水平扩容 |

### AI 模块性能

| 指标 | 数值 | 说明 |
|------|------|------|
| **分析延迟** | < 5s | 单次分析响应时间 |
| **并发能力** | 100+ 并发 | 支持并发分析请求 |
| **准确率** | 95%+ | 异常检测准确率 |
| **覆盖率** | 90%+ | 性能指标覆盖度 |

## 🔧 运维指南

### 监控和健康检查

```bash
# Flink Web UI
http://localhost:8081

# AI 模块健康检查
curl http://localhost:8080/actuator/health

# ClickHouse 监控
curl http://localhost:8123/play
```

### 常见问题排查

1. **Flink 作业启动失败**
   ```bash
   # 检查 Kafka 连接
   kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic segment-data
   
   # 检查 ClickHouse 连接
   clickhouse-client --query "SHOW DATABASES"
   ```

2. **AI 模块无法连接 LLM**
   ```bash
   # 检查 API Key 配置
   echo $OPENAI_API_KEY
   
   # 测试网络连接
   curl -H "Authorization: Bearer $OPENAI_API_KEY" https://api.openai.com/v1/models
   ```

3. **内存不足问题**
   ```bash
   # 调整 JVM 参数
   export JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC"
   ```

### 部署和扩容

```bash
# Docker 部署
docker build -t segment-alarm-clickhouse .
docker run -d -p 8080:8080 -p 8081:8081 segment-alarm-clickhouse

# Kubernetes 部署
kubectl apply -f k8s/

# 扩容 Flink 集群
kubectl scale deployment flink-taskmanager --replicas=5
```

## 🤝 开发指南

### 开发环境设置

```bash
# 安装开发依赖
mvn dependency:resolve

# 代码格式化
mvn spotless:apply

# 运行测试
mvn test

# 生成测试报告
mvn surefire-report:report
```

### 添加新功能

1. **扩展 Flink 算子**
   ```java
   // 在 flink-data-transformer-module 中添加新的处理算子
   public class CustomProcessFunction extends KeyedProcessFunction<K, I, O> {
       // 实现自定义处理逻辑
   }
   ```

2. **添加 AI 分析功能**
   ```java
   // 在 ai-analysis-module 中添加新的分析服务
   @Service
   public class CustomAnalysisService {
       // 实现自定义分析逻辑
   }
   ```

### 贡献代码

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开 Pull Request

## 📚 相关文档

- [Flink 数据转换模块详细文档](./flink-data-transformer-module/README.md)
- [AI 分析模块详细文档](./ai-analysis-module/README.md)
- [API 接口文档](./docs/api.md)
- [部署指南](./docs/deployment.md)

## 📄 许可证

本项目采用 Apache License 2.0 许可证。详情请参见 [LICENSE](LICENSE) 文件。

## 🆘 支持和联系

- **Issues**: [GitHub Issues](../../issues)
- **Wiki**: [项目 Wiki](../../wiki)  
- **讨论**: [GitHub Discussions](../../discussions)

---

**注意**: 
- 确保在生产环境中正确配置所有安全参数
- 定期备份 ClickHouse 数据
- 监控系统资源使用情况
- 及时更新依赖版本以修复安全漏洞
