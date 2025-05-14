# Segment Clickhouse Integration

## 项目简介
Segment Clickhouse Integration 是一个高效的数据处理服务，旨在从 Kafka 消息队列中消费 Skywalking agent 上报的 segment 消息，根据 span 中的 tag、log 信息动态管理 Clickhouse 数据库表结构，并将解析后的数据批量插入到 Clickhouse 中。

## 功能特性
- **Kafka 消息消费**：通过 `KafkaService` 消费 Kafka 消息队列中的数据。
- **动态表结构管理**：自动检测缺失字段并动态添加到 Clickhouse 表中，确保数据完整性。
- **批量数据插入**：支持按批量大小或时间间隔将数据高效插入到 Clickhouse。
- **高扩展性**：通过配置文件灵活调整批量插入参数和动态字段添加间隔。
- **错误处理与重试**：在数据库连接失败时自动重新初始化连接，确保服务稳定性。

## 项目结构
```plaintext
segment-clickhouse/
├── Dockerfile                     # Docker 构建文件
├── [README.md](http://_vscodecontentref_/2)                      # 项目说明文档
├── [pom.xml](http://_vscodecontentref_/3)                        # Maven 项目配置文件
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── psbc/
│   │   │           ├── DatabaseService.java       # Clickhouse 数据库服务
│   │   │           ├── KafkaService.java          # Kafka 消息消费服务
│   │   │           ├── TransformerService.java    # 核心服务逻辑
│   │   │           ├── TransformerUtils.java      # 工具类
│   │   │           └── BackgroundTaskManager.java # 后台任务管理
│   │   └── resources/
│   │       ├── [application.yaml](http://_vscodecontentref_/4)                   # 应用程序配置文件
│   │       └── [segmentOnEvent.yaml](http://_vscodecontentref_/5)                # 动态字段映射配置
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── psbc/
│       │           └── TransformerTest.java       # 单元测试类
│       └── resources/
│           └── test-application.yaml              # 测试配置文件
└── target/                         # Maven 构建输出目录（生成的 JAR 文件等）