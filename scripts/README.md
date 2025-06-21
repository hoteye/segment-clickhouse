# 🚀 Scripts 目录说明

本目录包含了 Segment ClickHouse 项目的所有管理脚本。

> 📋 **完整项目文档**: [README_DETAILED.md](README_DETAILED.md) - 包含技术栈、架构、故障排除等详细信息

## 📁 脚本分类

### 🎯 主要入口脚本（位于根目录）
- `manage.bat` - **主管理界面**，提供菜单式的所有功能访问
- `quick-start.bat` - **快速启动**，自动检测环境并启动必要服务

### 🌟 环境管理脚本
| 脚本名称 | 功能描述 | 使用场景 |
|---------|---------|----------|
| `start_dev_env.bat` | 🚀 一键启动完整开发环境 | 首次启动或完整重置后启动 |
| `check_env_status.bat` | 📊 检查所有环境组件状态 | 排查问题、确认环境状态 |
| `cleanup_dev_env.bat` | 🧹 清理所有环境组件 | 彻底清理环境、释放资源 |

### 🗄️ ClickHouse 管理脚本
| 脚本名称 | 功能描述 | 使用场景 |
|---------|---------|----------|
| `setup_clickhouse_full.bat` | 🐳 Docker 方式启动 ClickHouse | 使用 Docker 命令直接管理 |
| `setup_clickhouse_compose_full.bat` | 🐳 Docker Compose 方式启动 | 使用 Docker Compose 管理 |
| `reset_clickhouse.bat` | 🔄 重置 ClickHouse 数据库 | 数据库损坏或需要重新初始化 |
| `setup_clickhouse.bat` | 🔧 基础 ClickHouse 启动 | 简单启动（不含初始化） |
| `setup_clickhouse_compose.bat` | 🔧 基础 Compose 启动 | 简单 Compose 启动 |

### 🌊 Flink 作业管理脚本
| 脚本名称 | 功能描述 | 使用场景 |
|---------|---------|----------|
| `flink_deploy.bat` | 🚀 部署 Flink 作业到集群 | 集群环境部署 |

### 🛠️ 开发辅助脚本
| 脚本名称 | 功能描述 | 使用场景 |
|---------|---------|----------|
| `commit_changes.bat` | 💾 Git 提交更改 | 代码提交 |

## 🎮 使用指南

### 新手入门
1. **首次使用**：运行根目录的 `manage.bat`，选择选项 1（一键启动完整开发环境）
2. **日常开发**：运行根目录的 `quick-start.bat` 快速启动
3. **环境检查**：使用 `check_env_status.bat` 检查所有组件状态
4. **环境清理**：使用 `cleanup_dev_env.bat` 彻底清理环境

### 高级用户
- 直接运行 scripts 目录中的具体脚本
- 根据需要组合使用不同的脚本
- 修改脚本以适应特定的部署需求

## 🔧 脚本特性

### 🛡️ 错误处理
- 所有脚本都包含错误检查和处理
- 失败时会显示明确的错误信息
- 支持自动重试和恢复

### 📝 日志输出
- 使用 emoji 和颜色区分不同类型的消息
- 详细的进度提示和状态反馈
- 支持中文字符显示（UTF-8 编码）

### ⚡ 智能检测
- 自动检测环境状态，避免重复操作
- 智能等待服务就绪
- 自动处理端口冲突和依赖问题

### 🔄 幂等性
- 多次运行同一脚本是安全的
- 自动清理之前的状态
- 支持增量更新和恢复

## 🚨 注意事项

### 权限要求
- 需要管理员权限运行 Docker 相关操作
- 确保防火墙允许相关端口通信

### 依赖检查
运行脚本前请确保已安装：
- ✅ Docker Desktop
- ✅ Java 11+
- ✅ Maven 3.6+
- ✅ Flink（可选，用于集群部署）

### 端口占用
脚本使用的默认端口：
- `8123` - ClickHouse HTTP 接口
- `9000` - ClickHouse Native 接口
- `8081` - Flink Web UI
- `9092` - Kafka（如果使用）

### 故障排除
1. **脚本执行失败**：检查是否有足够的权限和磁盘空间
2. **Docker 相关错误**：确保 Docker Desktop 正在运行
3. **端口冲突**：使用 `netstat -ano | findstr :端口号` 检查端口占用
4. **编码问题**：确保控制台支持 UTF-8 编码

## 📞 获取帮助

### 调试模式
在脚本中添加 `echo on` 可以看到详细的执行过程。

### 日志查看
- ClickHouse 日志：`docker logs -f clickhouse-server`
- Flink 日志：通过 Flink Web UI 查看
- 系统日志：Windows 事件查看器

### 社区支持
- 查看项目根目录的 `README.md` 获取详细文档
- 在 GitHub Issues 中提交问题
- 参考官方文档：Flink、ClickHouse、Docker

---

💡 **提示**：建议将本目录添加到系统 PATH 中，这样可以在任何位置直接运行这些脚本。
