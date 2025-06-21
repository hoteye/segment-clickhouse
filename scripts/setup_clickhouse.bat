@echo off
REM ClickHouse 自动安装和初始化脚本
echo "正在设置 ClickHouse..."

REM 停止并删除现有的 ClickHouse 容器（如果存在）
echo "清理现有容器..."
docker stop clickhouse-server 2>nul
docker rm clickhouse-server 2>nul

REM 创建新的 ClickHouse 容器
echo "创建 ClickHouse 容器..."
docker run -d --name clickhouse-server ^
  -p 8123:8123 ^
  -p 9000:9000 ^
  -e CLICKHOUSE_USER=root ^
  -e CLICKHOUSE_PASSWORD=123456 ^
  -e TZ=Asia/Shanghai ^
  --ulimit nofile=262144:262144 ^
  clickhouse/clickhouse-server:latest

REM 等待容器启动
echo "等待 ClickHouse 启动..."
timeout /t 15 /nobreak

REM 测试连接
echo "测试 ClickHouse 连接..."
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SELECT 1"
if %errorlevel% neq 0 (
    echo "ClickHouse 连接失败，请检查容器状态"
    exit /b 1
)

REM 初始化数据库表
echo "初始化数据库表..."
powershell -Command "Get-Content init.sql | docker exec -i clickhouse-server clickhouse-client --user root --password 123456"

REM 验证表创建
echo "验证表创建..."
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SHOW TABLES"

echo "ClickHouse 设置完成！"
echo "访问地址: http://localhost:8123"
echo "用户名: root"
echo "密码: 123456"
