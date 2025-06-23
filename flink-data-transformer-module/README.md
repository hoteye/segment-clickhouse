# Flink Data Transformer Module

基于 Apache Flink 的高性能数据转换和流处理模块，专门用于处理从 Kafka 消费的数据并将结果写入 ClickHouse。

## 功能特性

### 🚀 核心功能
- **实时数据流处理**: 从 Kafka 消费 Segment 数据流，支持高吞吐量和低延迟处理
- **智能数据转换**: 支持复杂的数据转换、聚合和清洗操作
- **告警检测**: 实时监控数据异常，支持动态告警规则和阈值配置
- **数据存储**: 高效将处理结果写入 ClickHouse，支持批量写入和数据压缩
- **容错恢复**: 基于 Flink 的检查点机制，确保数据处理的一致性和可靠性

### 📊 处理能力
- **高并发处理**: 支持多并行度处理，可根据数据量动态调整
- **内存管理**: 优化内存使用，支持大数据量处理
- **背压控制**: 自动调节处理速度，防止内存溢出
- **动态扩缩容**: 支持 Flink 集群动态扩缩容

## 架构设计

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│                 │    │                  │    │                 │
│  Kafka Topics   │───▶│  Flink Streaming │───▶│   ClickHouse    │
│                 │    │    Processing    │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │   Alarm Gateway  │
                       │   (告警通知)      │
                       └──────────────────┘
```

### 主要组件

1. **数据源 (Source)**
   - `SegmentDeserializationSchema`: Protobuf 数据反序列化
   - `AlarmRuleDeserializationSchema`: 告警规则反序列化
   - Kafka 连接器配置和管理

2. **流处理算子 (Operators)**
   - `AggregateOperator`: 数据聚合处理
   - `FlinkOperator`: 基础算子抽象
   - `NewKeyTableSyncProcessFunction`: 新键表同步处理

3. **数据输出 (Sinks)**
   - `SimpleClickHouseSink`: ClickHouse 基础写入
   - `AggResultClickHouseSink`: 聚合结果写入
   - `AlarmGatewaySink`: 告警信息输出

4. **支持服务**
   - `FlinkService`: 核心流处理服务
   - `DatabaseService`: 数据库操作服务
   - `KafkaService`: Kafka 连接服务

## 快速开始

### 1. 环境要求

- **Java**: 11+
- **Apache Flink**: 1.17.2+
- **Apache Kafka**: 2.8+  
- **ClickHouse**: 21.0+
- **Maven**: 3.6+

### 2. 配置文件

编辑 `src/main/resources/application.yaml`:

```yaml
# Kafka 配置
kafka:
  bootstrap-servers: localhost:9092
  topics:
    segment: segment-data
    alarm-rule: alarm-rules
  consumer:
    group-id: flink-transformer-group
    auto-offset-reset: latest

# ClickHouse 配置  
clickhouse:
  url: jdbc:clickhouse://localhost:8123/o11y
  username: default
  password: ""
  batch-size: 1000
  flush-interval: 5000

# Flink 配置
flink:
  parallelism: 4
  checkpoint:
    interval: 60000
    mode: EXACTLY_ONCE
  restart-strategy:
    type: fixed-delay
    attempts: 3
    delay: 10000
```

### 3. 构建和运行

#### 本地开发环境

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包应用
mvn clean package

# 运行 Flink 作业
java --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -cp target/flink-data-transformer-module-1.0.5-shaded.jar \
     com.o11y.flink.FlinkKafkaToClickHouseJob
```

#### 生产环境部署

```bash
# 提交到 Flink 集群
flink run -p 8 target/flink-data-transformer-module-1.0.5-shaded.jar

# 查看作业状态
flink list

# 停止作业
flink cancel <job-id>
```

## 配置详解

### Kafka 配置

```yaml
kafka:
  bootstrap-servers: "localhost:9092"
  topics:
    segment: "segment-data"        # 主数据流 Topic
    alarm-rule: "alarm-rules"      # 告警规则 Topic
  consumer:
    group-id: "flink-transformer-group"
    auto-offset-reset: "latest"    # earliest | latest | none
    enable-auto-commit: false
    max-poll-records: 1000
  producer:
    acks: "all"                    # 确保数据可靠性
    retries: 3
    batch-size: 16384
```

### ClickHouse 配置

```yaml
clickhouse:
  url: "jdbc:clickhouse://localhost:8123/o11y"
  username: "default"
  password: ""
  connection-pool:
    max-connections: 20
    min-connections: 5
    max-wait-time: 30000
  batch-processing:
    batch-size: 1000              # 批量写入大小
    flush-interval: 5000          # 刷新间隔(ms)
    max-retries: 3
```

### Flink 配置

```yaml
flink:
  parallelism: 4                  # 并行度
  max-parallelism: 128           # 最大并行度
  checkpoint:
    interval: 60000               # 检查点间隔(ms)
    timeout: 600000              # 检查点超时(ms)
    mode: "EXACTLY_ONCE"         # 一致性模式
    storage: "file:///tmp/checkpoints"
  restart-strategy:
    type: "fixed-delay"          # 重启策略
    attempts: 3                  # 重启次数
    delay: 10000                 # 重启延迟(ms)
  state:
    backend: "rocksdb"           # 状态后端
    incremental: true            # 增量检查点
```

## 监控和运维

### 1. Flink Web UI

访问 `http://localhost:8081` 查看作业状态、监控指标和日志。

### 2. 关键监控指标

- **吞吐量**: Records/s, Bytes/s
- **延迟**: 端到端延迟、处理延迟
- **背压**: 算子背压状态
- **检查点**: 检查点成功率、持续时间
- **异常**: 重启次数、异常率

### 3. 日志配置

编辑 `src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="com.o11y" level="INFO"/>
    <logger name="org.apache.flink" level="WARN"/>
    <logger name="org.apache.kafka" level="WARN"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

## 性能优化

### 1. 并行度调优

```yaml
flink:
  parallelism: 8                 # 根据 CPU 核数调整
  max-parallelism: 256          # 支持更大规模扩容
```

### 2. 内存配置

```bash
# 启动时设置 JVM 参数
export FLINK_ENV_JAVA_OPTS="-Xmx4g -Xms4g -XX:+UseG1GC"
```

### 3. 批量处理优化

```yaml
clickhouse:
  batch-processing:
    batch-size: 5000             # 增加批量大小
    flush-interval: 10000        # 增加刷新间隔
```

## 故障排查

### 常见问题

1. **连接超时**
   - 检查 Kafka/ClickHouse 连接配置
   - 确认网络可达性和端口开放

2. **内存溢出**
   - 增加 JVM 堆内存
   - 调整批量处理大小
   - 启用增量检查点

3. **数据丢失**
   - 检查检查点配置
   - 确认 Kafka 分区分配
   - 验证 ClickHouse 写入状态

4. **性能问题**
   - 调整并行度
   - 优化数据序列化
   - 调整批量写入参数

### 日志排查

```bash
# 查看 Flink 作业日志
tail -f /opt/flink/log/flink-*-taskexecutor-*.log

# 查看特定日志级别
grep -i "error\|exception" /opt/flink/log/*.log
```

## 扩展开发

### 1. 自定义算子

```java
public class CustomProcessFunction extends KeyedProcessFunction<String, InputType, OutputType> {
    @Override
    public void processElement(InputType value, Context ctx, Collector<OutputType> out) {
        // 自定义处理逻辑
    }
}
```

### 2. 自定义 Sink

```java
public class CustomSink extends RichSinkFunction<DataType> {
    @Override
    public void invoke(DataType value, Context context) {
        // 自定义输出逻辑
    }
}
```

### 3. 新增数据源

```java
public class CustomDeserializationSchema implements DeserializationSchema<CustomType> {
    @Override
    public CustomType deserialize(byte[] message) {
        // 自定义反序列化逻辑
    }
}
```

## 许可证

本项目采用 Apache License 2.0 许可证。详情请参见 LICENSE 文件。

## 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进项目。

---

**注意**: 本模块专注于 Flink 数据处理功能，AI 分析功能请参考 `ai-analysis-module` 模块。
