@echo off
chcp 65001 > nul
echo ==========================================
echo      ClickHouse 完整部署和初始化脚本
echo ==========================================

echo.
echo [1/4] 停止并删除现有的 ClickHouse 容器...
docker stop clickhouse-server 2>nul
docker rm clickhouse-server 2>nul

echo.
echo [2/4] 启动新的 ClickHouse 容器...
docker run -d --name clickhouse-server ^
    -p 8123:8123 ^
    -p 9000:9000 ^
    -e CLICKHOUSE_USER=root ^
    -e CLICKHOUSE_PASSWORD=123456 ^
    -e TZ=Asia/Shanghai ^
    --ulimit nofile=262144:262144 ^
    clickhouse/clickhouse-server:latest

if %errorlevel% neq 0 (
    echo ❌ ClickHouse 容器启动失败！
    exit /b 1
)

echo ✅ ClickHouse 容器启动成功！

echo.
echo [3/4] 等待 ClickHouse 服务就绪...
:wait_loop
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    echo 正在等待 ClickHouse 服务启动...
    timeout /t 3 /nobreak >nul
    goto wait_loop
)

echo ✅ ClickHouse 服务已就绪！

echo.
echo [4/4] 执行数据库初始化脚本...
if not exist "scripts\init.sql" (
    echo ❌ 找不到 scripts\init.sql 文件！
    exit /b 1
)

powershell -Command "Get-Content scripts\init.sql | docker exec -i clickhouse-server clickhouse-client --user root --password 123456"

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
echo 验证连接：
echo   docker exec -it clickhouse-server clickhouse-client --user root --password 123456
echo.
echo Web UI 访问:
echo   http://localhost:8123/play
echo.

pause
