# 多Kafka源配置和使用指南

## 概述

本系统支持从多个生产环境Kafka源消费segmentObject数据，并将它们合并成一个完整的数据流进行处理。

## 架构设计

### 双Kafka源架构

1. **主Kafka源**：用于系统控制消息
   - 阈值热更新
   - 告警规则发布
   - 参数更新
   - 系统控制流

2. **多数据源Kafka**：用于业务数据
   - 不同生产环境的segmentObject数据
   - 支持动态启用/禁用
   - 独立配置和监控

## 配置文件

### application.yaml 配置示例

```yaml
kafka:
  # 主Kafka源配置 - 用于阈值热更新、告警规则等控制消息
  bootstrap_servers: "192.168.100.6:9092"
  group_id: "skywalking-segments-consumer-group-myself"
  topic: "topic-dics-long-skywalking-segments"
  param_update_topic: "flink-operator-param-update"
  alarm_rule_topic: "alarm_rule_topic"
  alarm_rule_group_id: "alarm-rule-consumer-group"
  auto_offset_reset: "earliest"
  poll_interval_ms: 200
  
  # 多Kafka源配置 - 用于消费不同生产环境的segmentObject数据
  data_sources:
    # 生产环境Kafka源1
    prod_source_1:
      bootstrap_servers: "192.168.100.6:9092"
      topic: "prod-segments-topic-1"
      group_id: "prod-segments-consumer-group-1"
      auto_offset_reset: "earliest"
      poll_interval_ms: 200
      source_name: "prod_source_1"
      description: "生产环境数据源1"
      enabled: true
    
    # 生产环境Kafka源2
    prod_source_2:
      bootstrap_servers: "192.168.100.6:9092"
      topic: "prod-segments-topic-2"
      group_id: "prod-segments-consumer-group-2"
      auto_offset_reset: "earliest"
      poll_interval_ms: 200
      source_name: "prod_source_2"
      description: "生产环境数据源2"
      enabled: true
    
    # 生产环境Kafka源3
    prod_source_3:
      bootstrap_servers: "192.168.100.6:9092"
      topic: "prod-segments-topic-3"
      group_id: "prod-segments-consumer-group-3"
      auto_offset_reset: "earliest"
      poll_interval_ms: 200
      source_name: "prod_source_3"
      description: "生产环境数据源3"
      enabled: false  # 可以动态禁用某个源
```

## 配置说明

### 主Kafka源配置

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `bootstrap_servers` | Kafka集群地址 | "192.168.100.6:9092" |
| `group_id` | 消费组ID | "skywalking-segments-consumer-group-myself" |
| `topic` | 主数据Topic | "topic-dics-long-skywalking-segments" |
| `param_update_topic` | 参数更新Topic | "flink-operator-param-update" |
| `alarm_rule_topic` | 告警规则Topic | "alarm_rule_topic" |
| `alarm_rule_group_id` | 告警规则消费组 | "alarm-rule-consumer-group" |

### 多数据源配置

每个数据源包含以下配置项：

| 配置项 | 说明 | 必需 | 示例 |
|--------|------|------|------|
| `bootstrap_servers` | Kafka集群地址 | 是 | "192.168.100.6:9092" |
| `topic` | 数据Topic | 是 | "prod-segments-topic-1" |
| `group_id` | 消费组ID | 是 | "prod-segments-consumer-group-1" |
| `auto_offset_reset` | 偏移量重置策略 | 否 | "earliest" |
| `poll_interval_ms` | 轮询间隔 | 否 | 200 |
| `source_name` | 源名称 | 否 | "prod_source_1" |
| `description` | 源描述 | 否 | "生产环境数据源1" |
| `enabled` | 是否启用 | 否 | true |

## 使用方法

### 1. 启动多Kafka源Flink作业

```bash
# 编译项目
mvn clean package

# 启动Flink作业
java -jar target/flink-data-transformer-module-1.0.0.jar
```

### 2. 动态管理数据源

#### 启用/禁用数据源

修改 `application.yaml` 中的 `enabled` 配置：

```yaml
data_sources:
  prod_source_1:
    # ... 其他配置
    enabled: true   # 启用
  prod_source_2:
    # ... 其他配置
    enabled: false  # 禁用
```

#### 添加新的数据源

在 `data_sources` 下添加新的配置：

```yaml
data_sources:
  # ... 现有配置
  prod_source_4:
    bootstrap_servers: "192.168.100.6:9092"
    topic: "prod-segments-topic-4"
    group_id: "prod-segments-consumer-group-4"
    auto_offset_reset: "earliest"
    poll_interval_ms: 200
    source_name: "prod_source_4"
    description: "生产环境数据源4"
    enabled: true
```

### 3. 监控数据源状态

#### 查看日志

```bash
# 查看Flink作业日志
tail -f logs/flink-job.log

# 查看数据源连接状态
grep "数据源" logs/flink-job.log
```

#### 监控指标

- 数据源连接状态
- 每个源的消费延迟
- 数据流合并状态
- 错误和异常信息

## 故障排查

### 常见问题

#### 1. 数据源连接失败

**症状**：日志显示 "创建数据源Kafka连接失败"

**排查步骤**：
1. 检查Kafka集群是否可访问
2. 验证Topic是否存在
3. 确认消费组权限
4. 检查网络连接

**解决方案**：
```bash
# 检查Kafka连接
kafka-topics.sh --bootstrap-server 192.168.100.6:9092 --list

# 检查Topic详情
kafka-topics.sh --bootstrap-server 192.168.100.6:9092 --describe --topic prod-segments-topic-1
```

#### 2. 数据源配置验证失败

**症状**：日志显示 "数据源配置验证失败"

**排查步骤**：
1. 检查必需配置项是否完整
2. 验证配置格式是否正确
3. 确认配置值不为空

**解决方案**：
```yaml
# 确保所有必需配置项都存在
prod_source_1:
  bootstrap_servers: "192.168.100.6:9092"  # 必需
  topic: "prod-segments-topic-1"           # 必需
  group_id: "prod-segments-consumer-group-1" # 必需
  # 其他配置项可选
```

#### 3. 数据流合并失败

**症状**：日志显示 "没有成功创建任何数据源Kafka连接"

**排查步骤**：
1. 检查所有数据源是否都被禁用
2. 验证配置格式
3. 查看详细错误日志

**解决方案**：
```yaml
# 确保至少有一个数据源启用
data_sources:
  prod_source_1:
    # ... 配置
    enabled: true  # 至少有一个为true
```

### 性能优化

#### 1. 调整并行度

```yaml
flink:
  parallelism: 6  # 根据数据源数量调整
```

#### 2. 优化批处理参数

```yaml
batch:
  size: 100       # 增加批处理大小
  interval: 100   # 减少批处理间隔
```

#### 3. 调整水位线策略

在 `MultiKafkaSourceManager` 中可以调整水位线配置：

```java
// 调整乱序容忍时间
WatermarkStrategy.<SegmentObject>forBoundedOutOfOrderness(Duration.ofSeconds(5))
```

## 最佳实践

### 1. 数据源命名规范

- 使用有意义的名称：`prod_source_1`, `test_source_1`
- 添加描述信息便于管理
- 保持命名一致性

### 2. 消费组管理

- 为每个数据源使用独立的消费组
- 避免消费组名称冲突
- 定期清理无用的消费组

### 3. 监控告警

- 监控每个数据源的消费延迟
- 设置数据源连接失败告警
- 监控数据流合并状态

### 4. 配置管理

- 使用版本控制管理配置文件
- 环境隔离：开发、测试、生产
- 定期备份配置

## 扩展功能

### 1. 数据源级别监控

可以扩展 `MultiKafkaSourceManager` 添加更多监控功能：

- 每个数据源的消息计数
- 数据源级别的延迟监控
- 数据源健康检查

### 2. 动态配置更新

可以实现配置热更新功能：

- 通过API动态启用/禁用数据源
- 运行时添加新的数据源
- 配置变更通知

### 3. 数据源负载均衡

可以实现更复杂的负载均衡策略：

- 基于数据量的负载均衡
- 数据源优先级设置
- 故障转移机制

## 总结

多Kafka源架构提供了灵活的数据源管理能力，支持从多个生产环境收集数据，同时保持系统控制流的独立性。通过合理的配置和监控，可以实现高效、稳定的数据处理流程。 