# 使用本地 OpenJDK 11 作为基础镜像
FROM openjdk:11-jdk-slim

# 设置工作目录
WORKDIR /app

# 设置时区为 Asia/Shanghai
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone


# 传递版本号参数并写入环境变量
ARG JAR_VERSION
ENV APP_VERSION=${JAR_VERSION}

# 将构建好的 JAR 文件复制到容器中
ARG JAR_FILE
COPY target/${JAR_FILE} app.jar

# 复制 SkyWalking Java Agent 目录到容器中
COPY apache-skywalking-apm-bin-es7 /app/skywalking-agent

EXPOSE 33333 8087

# 启动应用，并添加 SkyWalking 的 Java Agent 配置
CMD ["java", "-Xmx250m", "-javaagent:/app/skywalking-agent/agent/skywalking-agent.jar", "-jar", "/app/app.jar"]
