@echo off
chcp 65001 > nul
echo ==========================================
echo        完整开发环境清理脚本
echo ==========================================

echo.
echo 这个脚本将会：
echo   1. 停止所有运行中的 Flink 作业
echo   2. 停止并删除 ClickHouse 容器
echo   3. 清理项目构建文件
echo   4. 清理 Docker 相关资源
echo.

set /p choice="是否继续？这将清理所有环境(Y/N): "
if /i not "%choice%"=="Y" (
    echo 操作已取消。
    exit /b 0
)

echo.
echo ==========================================
echo [1/4] 停止 Flink 作业
echo ==========================================

echo.
echo 检查运行中的 Flink 作业...
call flink list 2>nul
if %errorlevel% equ 0 (
    echo 找到运行中的 Flink 作业，正在停止...
    for /f "tokens=4" %%i in ('flink list ^| findstr "RUNNING"') do (
        echo 停止作业: %%i
        call flink cancel %%i
    )
    echo ✅ Flink 作业已停止！
) else (
    echo ℹ️  没有运行中的 Flink 作业或 Flink 集群未启动
)

echo.
echo ==========================================
echo [2/4] 清理 ClickHouse 容器
echo ==========================================

echo.
echo 停止 ClickHouse 容器...
docker stop clickhouse-server 2>nul
if %errorlevel% equ 0 (
    echo ✅ ClickHouse 容器已停止！
) else (
    echo ℹ️  ClickHouse 容器未运行
)

echo.
echo 删除 ClickHouse 容器...
docker rm clickhouse-server 2>nul
if %errorlevel% equ 0 (
    echo ✅ ClickHouse 容器已删除！
) else (
    echo ℹ️  ClickHouse 容器不存在
)

echo.
echo 停止 Docker Compose 服务...
docker-compose -f scripts\docker-compose.yml down 2>nul
if %errorlevel% equ 0 (
    echo ✅ Docker Compose 服务已停止！
) else (
    echo ℹ️  Docker Compose 服务未运行
)

echo.
echo ==========================================
echo [3/4] 清理项目构建文件
echo ==========================================

echo.
echo 清理 Maven 构建文件...
if exist "target" (
    rmdir /s /q target
    echo ✅ target 目录已清理！
) else (
    echo ℹ️  target 目录不存在
)

echo.
echo 清理 Maven 缓存...
call mvn clean 2>nul
if %errorlevel% equ 0 (
    echo ✅ Maven 缓存已清理！
) else (
    echo ℹ️  Maven 清理跳过
)

echo.
echo ==========================================
echo [4/4] 清理 Docker 资源
echo ==========================================

echo.
echo 清理未使用的 Docker 镜像...
docker image prune -f >nul 2>&1
echo ✅ Docker 镜像已清理！

echo.
echo 清理未使用的 Docker 容器...
docker container prune -f >nul 2>&1
echo ✅ Docker 容器已清理！

echo.
echo 清理未使用的 Docker 网络...
docker network prune -f >nul 2>&1
echo ✅ Docker 网络已清理！

echo.
echo ==========================================
echo           🧹 清理完成！
echo ==========================================
echo.
echo 环境已完全清理，包括：
echo   ✅ Flink 作业已停止
echo   ✅ ClickHouse 容器已删除
echo   ✅ 项目构建文件已清理
echo   ✅ Docker 资源已清理
echo.
echo 要重新启动开发环境，请运行：
echo   start_dev_env.bat
echo.

pause
