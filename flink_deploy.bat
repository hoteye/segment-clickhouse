@echo off
REM 打包项目
call mvn clean package -DskipTests

REM 拷贝 jar 包到 jobmanager 容器
call docker cp target\segment-alarm-clickhouse-1.0.5.jar jobmanager:/opt/flink/segment-alarm-clickhouse-1.0.5.jar

REM 提交 Flink 任务（主类名可根据实际调整）
call docker exec jobmanager flink run -d -c com.o11y.flink.FlinkServiceLauncher /opt/flink/segment-alarm-clickhouse-1.0.5.jar

