# 使用 OpenJDK 11 作为基础镜像
FROM openjdk:11-jdk-slim

# 设置工作目录
WORKDIR /app
# 设置时区为 Asia/Shanghai
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 将项目的 JAR 文件复制到容器中
COPY target/dubbo-provider-b-1.1-SNAPSHOT.jar /app/demo.jar

# 复制 SkyWalking Java Agent 目录到容器中
COPY apache-skywalking-apm-bin-es7 /app/skywalking-agent

# 暴露 Spring Boot 应用端口（8746）
EXPOSE 8746

# 启动应用，并添加 SkyWalking 的 Java Agent 配置
CMD ["java", "-javaagent:/app/skywalking-agent/agent/skywalking-agent.jar", "-jar", "/app/demo.jar"]