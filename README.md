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
segment-clickhouse/
├── Dockerfile                     # Docker build file
├── [README.md](http://_vscodecontentref_/2)                      # Project documentation
├── [pom.xml](http://_vscodecontentref_/3)                        # Maven project configuration file
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── psbc/
│   │   │           ├── DatabaseService.java       # Clickhouse database service
│   │   │           ├── KafkaService.java          # Kafka message consumer service
│   │   │           ├── TransformerService.java    # Core service logic
│   │   │           ├── TransformerUtils.java      # Utility class
│   │   │           └── BackgroundTaskManager.java # Background task manager
│   │   └── resources/
│   │       ├── [application.yaml](http://_vscodecontentref_/4)                   # Application config file
│   │       └── [segmentOnEvent.yaml](http://_vscodecontentref_/5)                # Dynamic field mapping config
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── psbc/
│       │           └── TransformerTest.java       # Unit test class
│       └── resources/
│           └── test-application.yaml              # Test config file
└── target/                         # Maven build output directory (generated JAR files, etc.)
```