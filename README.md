# Segment Clickhouse Integration

## 项目简介
Segment Clickhouse Integration 是一个高效的数据处理服务，旨在从 Kafka 消息队列中消费 Skywalking agent 上报的segment消息，根据span中的tag、log信息动态管理 Clickhouse 数据库表结构，并将解析后的数据批量插入到 Clickhouse 中。

## 功能特性
- **Kafka 消息消费**：通过 `KafkaService` 消费 Kafka 消息队列中的数据。
- **动态表结构管理**：自动检测缺失字段并动态添加到 Clickhouse 表中，确保数据完整性。
- **批量数据插入**：支持按批量大小或时间间隔将数据高效插入到 Clickhouse。
- **高扩展性**：通过配置文件灵活调整批量插入参数和动态字段添加间隔。
- **错误处理与重试**：在数据库连接失败时自动重新初始化连接，确保服务稳定性。

## 项目结构