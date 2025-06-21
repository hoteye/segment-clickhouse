@echo off
REM Always package the project before deployment
call mvn clean package -DskipTests

REM Copy the shaded jar to the jobmanager container
call docker cp target\segment-alarm-clickhouse-1.0.5-shaded.jar jobmanager:/opt/flink/segment-alarm-clickhouse-1.0.5-shaded.jar

REM Submit the Flink job (adjust main class as needed)
call docker exec jobmanager flink run -d -c com.o11y.application.launcher.FlinkServiceLauncher /opt/flink/segment-alarm-clickhouse-1.0.5-shaded.jar

