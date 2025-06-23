@echo off
chcp 65001 > nul
echo ==========================================
echo         ClickHouse 重置脚本
echo ==========================================
echo.
echo ⚠️  这将删除所有 ClickHouse 数据！
set /p confirm="确认重置？(Y/N): "
if /i not "%confirm%"=="Y" (
    echo 操作已取消。
    exit /b 0
)

echo.
echo [1/4] 停止现有容器...
docker-compose -f scripts\docker-compose.yml down 2>nul
docker stop clickhouse-server 2>nul
docker rm clickhouse-server 2>nul
echo ✅ 容器已停止并删除

echo.
echo [2/4] 清理数据卷...
docker volume rm segment-alarm-clickhouse_clickhouse_data 2>nul
echo ✅ 数据卷已清理

echo.
echo [3/4] 重新启动 ClickHouse...
docker run -d --name clickhouse-server ^
    -p 8123:8123 ^
    -p 9000:9000 ^
    -e CLICKHOUSE_USER=root ^
    -e CLICKHOUSE_PASSWORD=123456 ^
    -e TZ=Asia/Shanghai ^
    --ulimit nofile=262144:262144 ^
    clickhouse/clickhouse-server:latest

echo.
echo 等待服务就绪...
:wait_loop
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    timeout /t 2 /nobreak >nul
    goto wait_loop
)
echo ✅ ClickHouse 服务已就绪

echo.
echo [4/4] 重新初始化数据库...
if not exist "scripts\init.sql" (
    echo ❌ 找不到 scripts\init.sql 文件！
    exit /b 1
)

powershell -Command "Get-Content scripts\init.sql | docker exec -i clickhouse-server clickhouse-client --user root --password 123456"
if %errorlevel% neq 0 (
    echo ❌ 数据库初始化失败！
    exit /b 1
)

echo ✅ 数据库初始化完成！

echo.
echo ==========================================
echo          🎉 重置完成！
echo ==========================================
echo.
echo ClickHouse 已完全重置并重新初始化。
echo   Web UI: http://localhost:8123/play
echo   连接测试: docker exec -it clickhouse-server clickhouse-client --user root --password 123456
echo.

pause
