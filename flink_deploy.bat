@echo off
REM Check if there are code/config changes since last build using git
REM If there are staged/unstaged changes or new commits since last build, repackage

REM Get the last commit hash of the jar (if exists)
set JAR=target\segment-alarm-clickhouse-1.0.5.jar
set HASH_FILE=target\last_build_commit.txt
set NEED_BUILD=0

if exist %JAR% (
    if exist %HASH_FILE% (
        for /f %%i in (%HASH_FILE%) do set LAST_BUILD_COMMIT=%%i
        for /f %%i in ('git rev-parse HEAD') do set CUR_COMMIT=%%i
        if not "%LAST_BUILD_COMMIT%"=="%CUR_COMMIT%" (
            set NEED_BUILD=1
        )
    ) else (
        set NEED_BUILD=1
    )
    REM Also check for unstaged or staged changes
    git diff --quiet || set NEED_BUILD=1
    git diff --cached --quiet || set NEED_BUILD=1
) else (
    set NEED_BUILD=1
)

if %NEED_BUILD%==1 (
    echo Code/config changed, packaging...
    call mvn clean package -DskipTests
    for /f %%i in ('git rev-parse HEAD') do echo %%i > %HASH_FILE%
) else (
    echo No code/config changes since last build, skipping packaging
)

REM Copy the jar to the jobmanager container
call docker cp target\segment-alarm-clickhouse-1.0.5.jar jobmanager:/opt/flink/segment-alarm-clickhouse-1.0.5.jar

REM Submit the Flink job (adjust main class as needed)
call docker exec jobmanager flink run -d -c com.o11y.flink.FlinkServiceLauncher /opt/flink/segment-alarm-clickhouse-1.0.5.jar

