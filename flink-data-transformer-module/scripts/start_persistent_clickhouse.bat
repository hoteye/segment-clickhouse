@echo off
echo 正在创建持久化ClickHouse容器...

REM 创建Docker卷（如果不存在）
docker volume create clickhouse-data 2>nul
docker volume create clickhouse-logs 2>nul

REM 停止并删除现有容器（如果存在）
docker stop clickhouse-server 2>nul
docker rm clickhouse-server 2>nul

REM 启动带持久化的ClickHouse容器
docker run -d --name clickhouse-server ^
  -p 8123:8123 -p 9000:9000 ^
  -e CLICKHOUSE_USER=root ^
  -e CLICKHOUSE_PASSWORD=123456 ^
  -e TZ=Asia/Shanghai ^
  --ulimit nofile=262144:262144 ^
  -v clickhouse-data:/var/lib/clickhouse ^
  -v clickhouse-logs:/var/log/clickhouse-server ^
  --restart=unless-stopped ^
  clickhouse/clickhouse-server:latest

echo 等待ClickHouse启动...
timeout /t 15 /nobreak > nul

echo 初始化数据库...
type ..\init.sql | docker exec -i clickhouse-server clickhouse-client --user root --password 123456

echo 验证表结构...
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SHOW TABLES"

echo.
echo ✅ 持久化ClickHouse容器启动完成！
echo 📊 Web UI: http://localhost:8123
echo 🔗 JDBC: jdbc:clickhouse://localhost:9000/default
echo 👤 用户名: root
echo 🔑 密码: 123456
echo.
echo 💾 数据存储在Docker卷中，重启容器数据不会丢失
pause 