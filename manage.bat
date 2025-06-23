@echo off
chcp 65001 > nul
echo ==========================================
echo        项目管理主界面
echo ==========================================
echo.
echo 请选择要执行的操作：
echo.
echo 🚀 快速操作：
echo   1. 快速启动开发环境 (quick-start.bat)
echo   2. 环境状态检查
echo   3. 清理开发环境
echo.
echo 🐳 ClickHouse 管理：
echo   4. 启动 ClickHouse (Docker)
echo   5. 启动 ClickHouse (Docker Compose)
echo   6. 重置 ClickHouse 数据库
echo.
echo 🌊 Flink 作业管理：
echo   7. 部署 Flink 作业到集群
echo   8. 本地运行 Flink 作业
echo.
echo 🛠️ 项目构建：
echo   9. Maven 构建项目
echo   10. Maven 清理项目
echo.
echo 0. 退出
echo.

set /p choice="请输入选项编号 (0-10): "

if "%choice%"=="1" (
    echo 正在快速启动开发环境...
    call flink-data-transformer-module\scripts\start_dev_env.bat
) else if "%choice%"=="2" (
    echo 正在检查环境状态...
    call flink-data-transformer-module\scripts\check_env_status.bat
) else if "%choice%"=="3" (
    echo 正在清理开发环境...
    call flink-data-transformer-module\scripts\cleanup_dev_env.bat
) else if "%choice%"=="4" (
    echo 正在启动 ClickHouse (Docker)...
    call flink-data-transformer-module\scripts\setup_clickhouse_full.bat
) else if "%choice%"=="5" (
    echo 正在启动 ClickHouse (Docker Compose)...
    call flink-data-transformer-module\scripts\setup_clickhouse_compose_full.bat
) else if "%choice%"=="6" (
    echo 正在重置 ClickHouse 数据库...
    call flink-data-transformer-module\scripts\reset_clickhouse.bat
) else if "%choice%"=="7" (
    echo 正在部署 Flink 作业到集群...
    call flink-data-transformer-module\scripts\flink_deploy.bat
) else if "%choice%"=="8" (
    echo 正在本地运行 Flink 作业...
    java --add-opens=java.base/java.util=ALL-UNNAMED ^
         --add-opens=java.base/java.lang=ALL-UNNAMED ^
         -cp target\segment-alarm-clickhouse-1.0.5-shaded.jar ^
         com.o11y.stream.FlinkServiceLauncher
) else if "%choice%"=="9" (
    echo 正在构建项目...
    mvn clean package
) else if "%choice%"=="10" (
    echo 正在清理项目...
    mvn clean
) else if "%choice%"=="0" (
    echo 再见！
    exit /b 0
) else (
    echo 无效的选项，请重新选择。
    pause
    goto :eof
)

echo.
echo ==========================================
echo 操作完成！按任意键返回主菜单...
pause >nul
call manage.bat