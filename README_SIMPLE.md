# Segment ClickHouse Integration

## 🚀 快速开始

### 一键启动（推荐）
```bash
# Windows 用户
quick-start.bat

# 完整管理界面
manage.bat
```

### 手动管理
```bash
# 进入脚本目录查看所有可用脚本
cd scripts
dir

# 查看脚本说明
type README.md
```

## 📁 项目结构

```
segment-alarm-clickhouse/
├── 🚀 快速启动脚本
│   ├── quick-start.bat           # 一键启动开发环境
│   └── manage.bat               # 完整管理界面
├── 🤖 ai-analysis-module/       # AI 智能分析模块
├── � flink-data-transformer-module/  # Flink 数据转换模块
│   ├── scripts/               # 所有脚本和配置文件
│   │   ├── README.md         # 脚本详细说明
│   │   ├── init.sql         # ClickHouse 初始化脚本
│   │   ├── docker-compose.yml # Docker Compose 配置
│   │   └── ...              # 其他管理脚本
│   ├── proto/               # Protobuf 定义文件
│   └── src/                 # Flink 源代码
├── 📄 pom.xml                  # Maven 父项目配置
└── 📋 README.md               # 项目说明

```

## 🛠️ 技术栈

- **Java 11+** - 核心开发语言
- **Apache Flink** - 流处理引擎  
- **ClickHouse** - 时序数据库
- **Apache Kafka** - 消息队列
- **Maven** - 项目构建
- **Docker** - 容器化部署

## 📖 详细文档

完整的使用说明、API 文档和故障排除指南请查看：
- [详细 README](flink-data-transformer-module/scripts/README.md) - 完整的项目文档
- [脚本说明](flink-data-transformer-module/scripts/) - 所有管理脚本的使用方法

## 🔧 快速操作命令

```bash
# 🚀 一键启动
quick-start.bat

# 📊 检查状态  
scripts\check_env_status.bat

# 🧹 清理环境
scripts\cleanup_dev_env.bat

# 🔄 重置数据库
scripts\reset_clickhouse.bat
```

## 📞 获取帮助

1. 运行 `manage.bat` 查看所有可用操作
2. 查看 `scripts\README.md` 了解详细说明
3. 使用 `scripts\check_env_status.bat` 诊断问题

---

**领域驱动设计 (DDD) 架构** | **生产就绪** | **一键部署**
