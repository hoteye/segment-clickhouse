# Segment Clickhouse Integration

## Project Introduction
Segment Clickhouse Integration is an efficient data processing service designed to consume segment messages reported by Skywalking agents from Kafka message queues, dynamically manage Clickhouse database table structures based on tag and log information in spans, and batch insert the parsed data into Clickhouse.

## Features
- **Kafka Message Consumption**: Consume data from Kafka message queues via `KafkaService`.
- **Dynamic Table Structure Management**: Automatically detect missing fields and dynamically add them to Clickhouse tables to ensure data integrity.
- **Batch Data Insertion**: Efficiently insert data into Clickhouse in batches based on batch size or time interval.
- **High Scalability**: Flexibly adjust batch insertion parameters and dynamic field addition intervals via configuration files.
- **Error Handling and Retry**: Automatically reinitialize the database connection upon failure to ensure service stability.

## Project Structure
```plaintext
segment-alarm-clickhouse/
├── Dockerfile                      # Docker 构建文件
├── README.md                       # 项目说明文档
├── pom.xml                         # Maven 配置文件
├── init.sql                        # ClickHouse 初始化建表脚本
├── proto/
│   └── segment.proto               # Skywalking Segment Protobuf 定义
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── o11y/
│   │   │           ├── DatabaseService.java           # ClickHouse数据库服务
│   │   │           ├── KafkaService.java              # Kafka消费服务
│   │   │           ├── TransformerService.java        # 业务核心逻辑
│   │   │           ├── TransformerUtils.java          # 工具类
│   │   │           ├── BackgroundTaskManager.java     # 后台任务管理
│   │   │           └── flink/
│   │   │               ├── FlinkKafkaToClickHouseJob.java   # Flink主作业入口
│   │   │               ├── sink/
│   │   │               │   └── SimpleClickHouseSink.java    # Flink Sink，写入ClickHouse
│   │   │               └── task/
│   │   │                   └── NewKeyTableSyncTask.java     # 定时同步新字段到表结构
│   │   └── resources/
│   │       ├── application.yaml                  # 应用配置
│   │       ├── logback.xml                       # 日志配置
│   │       └── segmentOnEvent.yaml               # 动态字段映射配置
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── o11y/
│       │           ├── TransformerTest.java              # 业务单元测试
│       │           ├── DatabaseServiceTest.java          # 数据库服务测试
│       │           └── flink/
│       │               └── sink/
│       │                   └── SimpleClickHouseSinkTest.java # Sink单元测试
│       └── resources/
│           └── test-application.yaml              # 测试配置
└── target/                          # Maven构建输出目录
```