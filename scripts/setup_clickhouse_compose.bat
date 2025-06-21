@echo off
REM 使用 Docker Compose 设置 ClickHouse
echo "使用 Docker Compose 设置 ClickHouse..."

REM 停止现有服务
echo "停止现有服务..."
docker-compose -f scripts\docker-compose.yml down

REM 启动 ClickHouse 服务
echo "启动 ClickHouse 服务..."
docker-compose -f scripts\docker-compose.yml up -d

REM 等待服务启动
echo "等待服务启动..."
timeout /t 20 /nobreak

REM 等待健康检查通过
echo "等待健康检查..."
:healthcheck
docker-compose -f scripts\docker-compose.yml ps | findstr "healthy" >nul
if %errorlevel% neq 0 (
    echo "等待 ClickHouse 健康检查通过..."
    timeout /t 5 /nobreak
    goto healthcheck
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
echo ""
echo "使用以下命令管理服务:"
echo "  启动: docker-compose up -d"
echo "  停止: docker-compose down"
echo "  查看日志: docker-compose logs -f clickhouse-server"
echo "  查看状态: docker-compose ps"
