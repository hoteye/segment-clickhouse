@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo ======================================
echo      集成测试执行脚本
echo ======================================
echo.

set "BASE_DIR=%~dp0.."
cd /d "%BASE_DIR%"

echo [1/4] 检查Docker环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker未安装或未启动，集成测试需要Docker支持
    exit /b 1
)
echo ✅ Docker环境检查通过

echo.
echo [2/4] 清理旧的测试容器...
docker stop integration-test-clickhouse integration-test-kafka >nul 2>&1
docker rm integration-test-clickhouse integration-test-kafka >nul 2>&1

echo.
echo [3/4] 编译项目...
call mvn clean compile test-compile -q
if errorlevel 1 (
    echo ❌ 项目编译失败
    exit /b 1
)
echo ✅ 项目编译完成

echo.
echo [4/4] 执行集成测试...
echo 📋 集成测试包含：
echo    - BaseIntegrationTest           : 测试基础设施
echo    - RuleGenerationIntegrationTest : 业务规则生成测试
echo    - RulePublishIntegrationTest    : 规则下发测试
echo    - EndToEndIntegrationTest       : 端到端测试
echo.

call mvn test -Dtest="com.o11y.integration.*" -Dmaven.test.failure.ignore=false
if errorlevel 1 (
    echo.
    echo ❌ 集成测试失败，请查看详细日志
    exit /b 1
)

echo.
echo ✅ 集成测试全部通过
echo.
echo 📊 测试报告位置：target/surefire-reports/
echo 📋 测试覆盖范围：
echo    - 业务规则生成正确性
echo    - 规则下发机制
echo    - Kafka消息格式验证
echo    - 异常场景处理
echo    - 端到端业务流程
echo.
echo ======================================

pause 