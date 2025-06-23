@echo off
chcp 65001 > nul
echo ==========================================
echo        完整开发环境一键启动脚本
echo ==========================================

echo.
echo 这个脚本将会：
echo   1. 启动 ClickHouse 数据库
echo   2. 初始化数据库表结构
echo   3. 构建 Flink 项目
echo   4. 部署 Flink 作业
echo.

set /p choice="是否继续？(Y/N): "
if /i not "%choice%"=="Y" (
    echo 操作已取消。
    exit /b 0
)

echo.
echo ==========================================
echo [阶段 1/4] ClickHouse 数据库启动
echo ==========================================

echo.
echo 停止现有 ClickHouse 容器...
docker stop clickhouse-server 2>nul
docker rm clickhouse-server 2>nul

echo.
echo 启动新的 ClickHouse 容器...
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
echo 等待 ClickHouse 服务就绪...
:wait_clickhouse
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    echo 正在等待 ClickHouse 服务启动...
    timeout /t 3 /nobreak >nul
    goto wait_clickhouse
)

echo ✅ ClickHouse 服务已就绪！

echo.
echo ==========================================
echo [阶段 2/4] 数据库初始化
echo ==========================================

echo 执行数据库初始化脚本...
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
echo [阶段 3/4] Flink 项目构建
echo ==========================================

echo.
echo 清理并重新构建项目...
call mvn clean package -DskipTests

if %errorlevel% neq 0 (
    echo ❌ 项目构建失败！
    exit /b 1
)

echo ✅ 项目构建成功！

echo.
echo ==========================================
echo [阶段 4/4] Flink 作业部署
echo ==========================================

echo.
echo 检查 Flink 集群状态...
call flink list 2>nul
if %errorlevel% neq 0 (
    echo ⚠️  Flink 集群未运行，请先启动 Flink 集群：
    echo    start-cluster.bat  (Windows)
    echo    或 ./bin/start-cluster.sh  (Linux/Mac)
    echo.
    set /p deploy_choice="是否跳过 Flink 部署，仅启动本地作业？(Y/N): "
    if /i "%deploy_choice%"=="Y" (
        echo.
        echo 启动本地 Flink 作业...
        java --add-opens=java.base/java.util=ALL-UNNAMED ^
             --add-opens=java.base/java.lang=ALL-UNNAMED ^
             -cp target\segment-alarm-clickhouse-1.0.5-shaded.jar ^
             com.o11y.stream.FlinkServiceLauncher
        goto end
    ) else (
        echo 请先启动 Flink 集群后重新运行此脚本。
        exit /b 1
    )
)

echo.
echo 部署 Flink 作业到集群...
call flink_deploy.bat

if %errorlevel% neq 0 (
    echo ❌ Flink 作业部署失败！
    exit /b 1
)

echo ✅ Flink 作业部署成功！

:end
echo.
echo ==========================================
echo           🎉 环境启动完成！
echo ==========================================
echo.
echo ClickHouse 信息：
echo   HTTP 端口: 8123
echo   Native 端口: 9000
echo   用户名: root / 密码: 123456
echo   Web UI: http://localhost:8123/play
echo.
echo Flink 信息：
echo   Web UI: http://localhost:8081
echo   作业状态: flink list
echo.
echo 有用的命令：
echo   查看 ClickHouse 日志: docker logs -f clickhouse-server
echo   进入 ClickHouse 客户端: docker exec -it clickhouse-server clickhouse-client --user root --password 123456
echo   重置 ClickHouse: reset_clickhouse.bat
echo   重启 Flink 作业: flink_deploy.bat
echo.

pause
