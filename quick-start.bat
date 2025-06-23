@echo off
chcp 65001 > nul

REM 快速启动脚本 - 用于日常开发的最常用操作

echo ==========================================
echo         快速开发环境启动
echo ==========================================
echo.

REM 检查是否有现有的 ClickHouse 容器运行
docker ps --filter "name=clickhouse-server" --format "{{.Names}}" | findstr "clickhouse-server" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ ClickHouse 容器已运行
    
    REM 测试连接
    docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SELECT 1" >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ ClickHouse 服务正常
    ) else (
        echo ⚠️  ClickHouse 服务异常，正在重启...
        call flink-data-transformer-module\scripts\reset_clickhouse.bat
    )
) else (
    echo 🚀 启动 ClickHouse 容器...
    call flink-data-transformer-module\scripts\setup_clickhouse_full.bat
)

echo.
echo 📦 检查项目构建状态...
if exist "target\segment-alarm-clickhouse-1.0.5-shaded.jar" (
    echo ✅ 项目已构建
) else (
    echo 🔨 构建项目...
    mvn clean package -DskipTests
    if %errorlevel% neq 0 (
        echo ❌ 项目构建失败！
        pause
        exit /b 1
    )
    echo ✅ 项目构建完成！
)

echo.
echo 🌊 检查 Flink 作业状态...
flink list >nul 2>&1
if %errorlevel% equ 0 (
    echo ✅ Flink 集群运行中
    
    REM 检查是否有运行中的作业
    flink list | findstr "RUNNING" >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ 已有 Flink 作业运行中
        echo.
        flink list
    ) else (
        echo 🚀 部署 Flink 作业...
        call flink-data-transformer-module\scripts\flink_deploy.bat
    )
) else (
    echo ⚠️  Flink 集群未运行，启动本地作业...
    echo.
    echo 启动本地 Flink 作业（按 Ctrl+C 停止）...
    java --add-opens=java.base/java.util=ALL-UNNAMED ^
         --add-opens=java.base/java.lang=ALL-UNNAMED ^
         -cp target\segment-alarm-clickhouse-1.0.5-shaded.jar ^
         com.o11y.stream.FlinkServiceLauncher
)

echo.
echo ==========================================
echo          🎉 快速启动完成！
echo ==========================================
echo.
echo 📊 环境信息：
echo   ClickHouse Web UI: http://localhost:8123/play
echo   Flink Web UI: http://localhost:8081
echo.
echo 💡 有用的命令：
echo   完整管理界面: flink-data-transformer-module\scripts\manage.bat
echo   环境状态检查: flink-data-transformer-module\scripts\check_env_status.bat
echo   清理环境: flink-data-transformer-module\scripts\cleanup_dev_env.bat
echo.

pause
