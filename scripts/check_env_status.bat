@echo off
chcp 65001 > nul
echo ==========================================
echo          开发环境状态检查
echo ==========================================

echo.
echo [1/5] Docker 状态检查
echo ==========================================
docker --version >nul 2>&1
if %errorlevel% equ 0 (
    docker --version
    echo ✅ Docker 可用
) else (
    echo ❌ Docker 未安装或不可用
)

echo.
echo [2/5] ClickHouse 容器状态
echo ==========================================
docker ps --filter "name=clickhouse-server" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | findstr "clickhouse-server" >nul 2>&1
if %errorlevel% equ 0 (
    docker ps --filter "name=clickhouse-server" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo.
    echo 测试 ClickHouse 连接...
    docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SELECT 'ClickHouse连接正常' as status" 2>nul
    if %errorlevel% equ 0 (
        echo ✅ ClickHouse 服务正常
    ) else (
        echo ⚠️  ClickHouse 容器运行中，但服务未就绪
    )
) else (
    echo ❌ ClickHouse 容器未运行
)

echo.
echo [3/5] ClickHouse 表结构检查
echo ==========================================
docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SHOW TABLES" 2>nul
if %errorlevel% equ 0 (
    echo "数据库表列表："
    docker exec clickhouse-server clickhouse-client --user root --password 123456 --query "SHOW TABLES FORMAT Pretty" 2>nul
    echo ✅ 数据库表结构正常
) else (
    echo ❌ 无法访问数据库表或数据库未初始化
)

echo.
echo [4/5] Maven 和项目状态
echo ==========================================
mvn --version >nul 2>&1
if %errorlevel% equ 0 (
    mvn --version | findstr "Apache Maven"
    echo ✅ Maven 可用
    
    if exist "target\segment-alarm-clickhouse-1.0.5-shaded.jar" (
        echo ✅ Shaded JAR 文件存在
        echo    文件路径: target\segment-alarm-clickhouse-1.0.5-shaded.jar
        dir "target\segment-alarm-clickhouse-1.0.5-shaded.jar" | findstr "segment-alarm-clickhouse"
    ) else (
        echo ⚠️  Shaded JAR 文件不存在，需要运行 mvn clean package
    )
) else (
    echo ❌ Maven 未安装或不可用
)

echo.
echo [5/5] Flink 环境检查
echo ==========================================
flink --version >nul 2>&1
if %errorlevel% equ 0 (
    flink --version
    echo ✅ Flink CLI 可用
    
    echo.
    echo "检查 Flink 集群状态..."
    flink list >nul 2>&1
    if %errorlevel% equ 0 (
        echo ✅ Flink 集群运行中
        echo.
        echo "运行中的作业："
        flink list
    ) else (
        echo ⚠️  Flink 集群未运行或不可访问
        echo    请启动 Flink 集群: start-cluster.bat
    )
) else (
    echo ❌ Flink CLI 未安装或不在 PATH 中
)

echo.
echo ==========================================
echo            状态检查完成
echo ==========================================
echo.
echo 快速操作命令：
echo   启动完整环境: start_dev_env.bat
echo   清理环境: cleanup_dev_env.bat
echo   重置 ClickHouse: reset_clickhouse.bat
echo   部署 Flink 作业: flink_deploy.bat
echo.
echo 管理界面：
echo   ClickHouse Web UI: http://localhost:8123/play
echo   Flink Web UI: http://localhost:8081
echo.

pause
