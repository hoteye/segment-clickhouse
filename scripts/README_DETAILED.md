# Segment ClickHouse Integration

## 🚀 快速开始

### 🎯 主要入口（推荐）
```bash
# 交互式管理界面 - 包含所有功能的菜单
manage.bat

# 快速启动 - 自动检测并启动必要服务
quick-start.bat
```

### 📁 脚本目录管理
所有管理脚本都位于 `scripts/` 目录中：

```bash
# 一键启动完整开发环境
scripts\start_dev_env.bat

# 检查环境状态
scripts\check_env_status.bat

# 清理环境
scripts\cleanup_dev_env.bat

# ClickHouse 管理
scripts\setup_clickhouse_full.bat         # Docker 方式
scripts\setup_clickhouse_compose_full.bat # Docker Compose 方式
scripts\reset_clickhouse.bat              # 重置数据库

# Flink 作业管理
scripts\flink_deploy.bat                  # 部署到集群
```

### 🛠️ 开发调试
```bash
# 构建项目
mvn clean package

# VS Code 调试
# 使用 F5 或运行配置："Run FlinkServiceLauncher with JVM opens"

# 直接运行
java --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -cp target\segment-alarm-clickhouse-1.0.5-shaded.jar \
     com.o11y.stream.FlinkServiceLauncher
```

## 📋 项目介绍

Segment ClickHouse Integration 是一个高效的数据处理服务，基于 **领域驱动设计（DDD）** 架构，专门用于：

- 从 Kafka 消息队列消费 Skywalking Agent 上报的 Segment 消息
- 基于 Span 中的标签和日志信息动态管理 ClickHouse 数据库表结构
- 将解析后的数据批量插入到 ClickHouse 中

## ✨ 核心特性

- **🔄 Kafka 消息消费**: 通过 `KafkaService` 从 Kafka 消息队列消费数据
- **🛠️ 动态表结构管理**: 自动检测缺失字段并动态添加到 ClickHouse 表中，确保数据完整性
- **📦 批量数据插入**: 基于批次大小或时间间隔高效地批量插入数据到 ClickHouse
- **⚡ 高可扩展性**: 通过配置文件灵活调整批量插入参数和动态字段添加间隔
- **🔧 错误处理与重试**: 失败时自动重新初始化数据库连接，确保服务稳定性
- **🏗️ DDD 架构**: 采用领域驱动设计，代码结构清晰，易于维护和扩展

## 📁 项目结构（DDD 架构）

```plaintext
segment-alarm-clickhouse/
├── 📋 部署和管理脚本
│   ├── start_dev_env.bat                   # 🚀 一键启动完整开发环境
│   ├── cleanup_dev_env.bat                 # 🧹 一键清理环境
│   ├── check_env_status.bat                # 📊 环境状态检查
│   ├── setup_clickhouse_full.bat           # 🐳 ClickHouse Docker 完整部署
│   ├── setup_clickhouse_compose_full.bat   # 🐳 ClickHouse Compose 完整部署
│   ├── reset_clickhouse.bat                # 🔄 重置 ClickHouse 数据库
│   └── flink_deploy.bat                    # 🚀 Flink 作业部署
```plaintext
segment-alarm-clickhouse/
├── 🎯 主入口脚本
│   ├── manage.bat                          # 🎮 交互式管理界面（推荐）
│   └── quick-start.bat                     # 🚀 快速启动脚本
├── 📁 脚本目录
│   └── scripts/
│       ├── README.md                       # 📖 脚本说明文档
│       ├── start_dev_env.bat              # 🚀 一键启动完整开发环境
│       ├── check_env_status.bat           # 📊 环境状态检查
│       ├── cleanup_dev_env.bat            # 🧹 一键清理环境
│       ├── setup_clickhouse_full.bat      # 🐳 ClickHouse Docker 完整部署
│       ├── setup_clickhouse_compose_full.bat # � ClickHouse Compose 完整部署
│       ├── reset_clickhouse.bat           # 🔄 重置 ClickHouse 数据库
│       └── flink_deploy.bat               # 🚀 Flink 作业部署
├── �📄 配置文件
│   ├── Dockerfile                          # 🐳 Docker 构建文件
│   ├── docker-compose.yml                 # 🐳 Docker Compose 配置
│   ├── init.sql                           # 🗄️ ClickHouse 初始化建表脚本
│   └── pom.xml                            # 📦 Maven 配置文件
├── 📡 协议定义
│   └── proto/
│       └── segment.proto                   # 📋 Skywalking Segment Protobuf 定义
├── 💻 源代码（DDD 架构）
│   ├── main/java/com/o11y/
│   │   ├── application/                    # 🎯 应用层
│   │   │   └── launcher/                   # 应用启动器
│   │   ├── domain/                         # 🏗️ 领域层
│   │   │   ├── model/                      # 领域模型
│   │   │   ├── service/                    # 领域服务
│   │   │   └── repository/                 # 仓储接口
│   │   ├── infrastructure/                 # 🔧 基础设施层
│   │   │   ├── clickhouse/                 # ClickHouse 基础设施
│   │   │   ├── kafka/                      # Kafka 基础设施
│   │   │   ├── config/                     # 配置管理
│   │   │   └── flink/                      # Flink 基础设施
│   │   ├── shared/                         # 🔗 共享组件
│   │   │   └── utils/                      # 工具类
│   │   └── stream/                         # 🌊 流处理层
│   │       ├── FlinkServiceLauncher.java   # Flink 服务启动器
│   │       ├── sink/                       # Flink Sink 实现
│   │       └── task/                       # 后台任务
│   └── resources/
│       ├── application.yaml                # 🔧 应用配置
│       ├── logback.xml                     # 📝 日志配置
│       └── *.xml                          # 映射配置文件
└── 🧪 测试代码
    └── test/java/com/o11y/                # 单元测试和集成测试
```
```

## 🛠️ 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| **Java** | 11+ | 核心开发语言 |
| **Apache Flink** | 1.17+ | 流处理引擎 |
| **ClickHouse** | 23.0+ | 时序数据库 |
| **Apache Kafka** | 2.8+ | 消息队列 |
| **Maven** | 3.6+ | 项目构建 |
| **Docker** | 20.0+ | 容器化部署 |
| **Protobuf** | 3.0+ | 数据序列化 |

## 📖 详细使用说明

### 环境准备

1. **安装 Java 11+**
   ```bash
   java -version  # 确认 Java 版本
   ```

2. **安装 Maven 3.6+**
   ```bash
   mvn -version  # 确认 Maven 版本
   ```

3. **安装 Docker**
   ```bash
   docker --version  # 确认 Docker 版本
   ```

4. **安装 Flink（可选，用于集群部署）**
   - 下载 Flink 1.17+ 并配置环境变量
   - 或使用本地模式运行

### 第一次启动

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd segment-alarm-clickhouse
   ```

2. **一键启动环境**
   ```bash
   start_dev_env.bat
   ```
   
   这个脚本会：
   - ✅ 启动 ClickHouse 数据库容器
   - ✅ 初始化数据库表结构
   - ✅ 构建 Java 项目
   - ✅ 部署 Flink 作业

3. **验证启动成功**
   ```bash
   check_env_status.bat
   ```

### 配置文件说明

#### `application.yaml` - 主配置文件
```yaml
# ClickHouse 连接配置
clickhouse:
  url: jdbc:clickhouse://localhost:8123/default
  username: root
  password: 123456

# Kafka 连接配置
kafka:
  bootstrap.servers: localhost:9092
  group.id: segment-consumer-group
  
# Flink 配置
flink:
  parallelism: 1
  checkpoint.interval: 60000
```

#### `init.sql` - 数据库初始化脚本
- 自动创建必需的 ClickHouse 表
- 可根据需求修改表结构
- 支持动态字段扩展

### 开发调试

#### VS Code 调试
1. 打开项目文件夹
2. 按 `F5` 或选择运行配置："Run FlinkServiceLauncher with JVM opens"
3. 设置断点进行调试

#### 本地运行
```bash
# 方式1：使用 Maven
mvn exec:java -Dexec.mainClass="com.o11y.stream.FlinkServiceLauncher"

# 方式2：直接运行 JAR
java --add-opens=java.base/java.util=ALL-UNNAMED \
     --add-opens=java.base/java.lang=ALL-UNNAMED \
     -cp target/segment-alarm-clickhouse-1.0.5-shaded.jar \
     com.o11y.stream.FlinkServiceLauncher
```

### 生产部署

#### Docker 部署
```bash
# 构建镜像
docker build -t segment-alarm-clickhouse:latest .

# 运行容器
docker run -d --name segment-alarm \
  -e CLICKHOUSE_URL="jdbc:clickhouse://clickhouse:8123/default" \
  -e KAFKA_SERVERS="kafka:9092" \
  segment-alarm-clickhouse:latest
```

#### Flink 集群部署
```bash
# 启动 Flink 集群
start-cluster.bat  # Windows
# 或 ./bin/start-cluster.sh  # Linux/Mac

# 部署作业
flink_deploy.bat
```

## 🔧 常用操作

### 数据库管理
```bash
# 进入 ClickHouse 客户端
docker exec -it clickhouse-server clickhouse-client --user root --password 123456

# 查看所有表
SHOW TABLES;

# 查看表结构
DESCRIBE TABLE events;

# 查询数据
SELECT * FROM events LIMIT 10;
```

### 日志查看
```bash
# ClickHouse 日志
docker logs -f clickhouse-server

# Flink 作业日志
# 通过 Flink Web UI: http://localhost:8081
```

### 性能监控
- **ClickHouse Web UI**: http://localhost:8123/play
- **Flink Web UI**: http://localhost:8081
- **Kafka 监控**: 使用 Kafka Manager 或其他监控工具

## 🚨 故障排除

### 常见问题

1. **ClickHouse 连接失败**
   ```bash
   # 检查容器状态
   docker ps | grep clickhouse
   
   # 重启容器
   reset_clickhouse.bat
   ```

2. **Maven 构建失败**
   ```bash
   # 清理并重新构建
   mvn clean package -DskipTests
   
   # 如果依赖下载失败，可以尝试
   mvn dependency:resolve
   ```

3. **Flink 作业启动失败**
   ```bash
   # 检查 Flink 集群状态
   flink list
   
   # 查看作业详细错误
   # 通过 Flink Web UI 查看异常堆栈
   ```

4. **端口冲突**
   ```bash
   # 检查端口占用
   netstat -ano | findstr :8123  # ClickHouse HTTP
   netstat -ano | findstr :9000  # ClickHouse Native
   netstat -ano | findstr :8081  # Flink Web UI
   ```

5. **Docker 相关问题**
   ```bash
   # 清理所有容器和镜像
   cleanup_dev_env.bat
   
   # 重新拉取最新镜像
   docker pull clickhouse/clickhouse-server:latest
   ```

### 日志级别调整

编辑 `src/main/resources/logback.xml`：
```xml
<!-- 调试模式 -->
<logger name="com.o11y" level="DEBUG"/>

<!-- 生产模式 -->
<logger name="com.o11y" level="INFO"/>
```

### 性能优化建议

1. **ClickHouse 优化**
   - 调整 `max_memory_usage` 和 `max_threads`
   - 合理设置分区字段和排序键
   - 使用物化视图加速查询

2. **Flink 优化**
   - 调整并行度 `parallelism`
   - 设置合适的检查点间隔
   - 优化批次大小和缓冲时间

3. **JVM 优化**
   ```bash
   # 增加堆内存
   -Xmx4g -Xms2g
   
   # G1 垃圾收集器
   -XX:+UseG1GC
   ```

## 📞 支持与贡献

### 获取帮助
- 查看日志文件定位问题
- 使用 `check_env_status.bat` 检查环境状态
- 参考 Flink 和 ClickHouse 官方文档

### 贡献指南
1. Fork 项目
2. 创建功能分支
3. 提交代码并写好注释
4. 创建 Pull Request

---

## 📜 许可证

MIT License - 详见 LICENSE 文件