#!/bin/bash

# Linux版本的Flink部署脚本
# 用于在Linux环境下部署Flink作业到Docker容器

set -e  # 遇到错误时退出

echo "=== 开始Flink作业部署 ==="

# 获取脚本所在目录的上级目录（项目根目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "项目目录: $PROJECT_DIR"
cd "$PROJECT_DIR"

# 1. 清理并打包项目
echo "=== 步骤1: 清理并打包项目 ==="
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 项目打包失败"
    exit 1
fi
echo "✅ 项目打包成功"

# 2. 检查JAR文件是否存在
JAR_FILE="target/flink-data-transformer-module-1.0.5-shaded.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR文件不存在: $JAR_FILE"
    exit 1
fi
echo "✅ JAR文件检查通过: $JAR_FILE"

# 3. 检查Docker容器是否运行
echo "=== 步骤2: 检查Docker容器状态 ==="
if ! docker ps | grep -q "jobmanager"; then
    echo "❌ jobmanager容器未运行，请先启动Flink集群"
    echo "提示: 可以使用 docker-compose up -d 启动Flink集群"
    exit 1
fi
echo "✅ jobmanager容器运行正常"

# 4. 复制JAR文件到容器
echo "=== 步骤3: 复制JAR文件到容器 ==="
docker cp "$JAR_FILE" jobmanager:/opt/flink/flink-data-transformer-module-1.0.5-shaded.jar
if [ $? -ne 0 ]; then
    echo "❌ JAR文件复制失败"
    exit 1
fi
echo "✅ JAR文件复制成功"

# 5. 提交Flink作业
echo "=== 步骤4: 提交Flink作业 ==="
docker exec jobmanager flink run -d -c com.o11y.application.launcher.FlinkServiceLauncher /opt/flink/flink-data-transformer-module-1.0.5-shaded.jar
if [ $? -ne 0 ]; then
    echo "❌ Flink作业提交失败"
    exit 1
fi
echo "✅ Flink作业提交成功"

echo "=== 部署完成 ==="
echo "📊 可以通过以下命令查看作业状态:"
echo "   docker exec jobmanager flink list"
echo ""
echo "📋 查看作业日志:"
echo "   docker logs jobmanager"
echo ""
echo "🌐 访问Flink Web UI:"
echo "   http://localhost:8081" 