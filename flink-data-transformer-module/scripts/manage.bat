@echo off
chcp 65001 > nul
echo ==========================================
echo      Segment ClickHouse 项目管理工具
echo ==========================================

echo.
echo 请选择要执行的操作：
echo.
echo 📋 环境管理：
echo   [1] 🚀 一键启动完整开发环境
echo   [2] 📊 检查环境状态
echo   [3] 🧹 清理开发环境
echo.
echo 🗄️ ClickHouse 管理：
echo   [4] 🐳 启动 ClickHouse (Docker)
echo   [5] 🐳 启动 ClickHouse (Docker Compose)
echo   [6] 🔄 重置 ClickHouse 数据库
echo.
echo 🌊 Flink 作业管理：
echo   [7] 🚀 部署 Flink 作业
echo   [8] 📦 构建项目
echo.
echo 📁 文件管理：
echo   [9] 📂 打开脚本目录
echo   [0] ❌ 退出
echo.

set /p choice="请输入选项 (0-9): "

if "%choice%"=="1" (
    echo.
    echo 🚀 启动完整开发环境...
    call scripts\start_dev_env.bat
) else if "%choice%"=="2" (
    echo.
    echo 📊 检查环境状态...
    call scripts\check_env_status.bat
) else if "%choice%"=="3" (
    echo.
    echo 🧹 清理开发环境...
    call scripts\cleanup_dev_env.bat
) else if "%choice%"=="4" (
    echo.
    echo 🐳 启动 ClickHouse (Docker)...
    call scripts\setup_clickhouse_full.bat
) else if "%choice%"=="5" (
    echo.
    echo 🐳 启动 ClickHouse (Docker Compose)...
    call scripts\setup_clickhouse_compose_full.bat
) else if "%choice%"=="6" (
    echo.
    echo 🔄 重置 ClickHouse 数据库...
    call scripts\reset_clickhouse.bat
) else if "%choice%"=="7" (
    echo.
    echo 🚀 部署 Flink 作业...
    call scripts\flink_deploy.bat
) else if "%choice%"=="8" (
    echo.
    echo 📦 构建项目...
    mvn clean package -DskipTests
    if %errorlevel% equ 0 (
        echo ✅ 项目构建成功！
    ) else (
        echo ❌ 项目构建失败！
    )
    pause
) else if "%choice%"=="9" (
    echo.
    echo 📂 打开脚本目录...
    start explorer scripts
) else if "%choice%"=="0" (
    echo.
    echo 👋 再见！
    exit /b 0
) else (
    echo.
    echo ❌ 无效选项，请重新选择
    pause
    goto :eof
)

echo.
echo 操作完成，按任意键返回主菜单...
pause >nul
goto :eof
