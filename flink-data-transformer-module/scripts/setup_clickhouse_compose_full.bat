@echo off
chcp 65001 > nul
echo ==========================================
echo   ClickHouse Docker Compose 完整部署脚本
echo ==========================================

echo.
echo [1/3] 停止现有的 Docker Compose 服务...
docker-compose -f docker-compose.yml down

echo.
echo [2/3] 启动 ClickHouse 服务...
docker-compose -f scripts\docker-compose.yml up -d

if %errorlevel% neq 0 (
    echo ❌ Docker Compose 启动失败！
    exit /b 1
)

echo ✅ ClickHouse 服务启动成功！

echo.
echo [3/3] 等待服务就绪并执行初始化...
:wait_loop
docker-compose -f scripts\docker-compose.yml exec -T clickhouse clickhouse-client --user root --password 123456 --query "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    echo 正在等待 ClickHouse 服务启动...
    timeout /t 3 /nobreak >nul
    goto wait_loop
)

echo ✅ ClickHouse 服务已就绪！

echo.
echo 执行数据库初始化脚本...
if not exist "scripts\init.sql" (
    echo ❌ 找不到 scripts\init.sql 文件！
    exit /b 1
)

powershell -Command "Get-Content scripts\init.sql | docker-compose -f scripts\docker-compose.yml exec -T clickhouse clickhouse-client --user root --password 123456"

if %errorlevel% neq 0 (
    echo ❌ 数据库初始化失败！
    exit /b 1
)

echo ✅ 数据库初始化成功！

echo.
echo ==========================================
echo           部署完成！
echo ==========================================
echo.
echo ClickHouse 连接信息：
echo   HTTP 端口: 8123
echo   Native 端口: 9000
echo   用户名: root
echo   密码: 123456
echo   时区: Asia/Shanghai
echo.
echo 管理命令：
echo   查看状态: docker-compose ps
echo   查看日志: docker-compose logs -f clickhouse
echo   进入客户端: docker-compose exec clickhouse clickhouse-client --user root --password 123456
echo   停止服务: docker-compose down
echo.
echo Web UI 访问:
echo   http://localhost:8123/play
echo.

pause
