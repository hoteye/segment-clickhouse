# 使用官方 OpenJDK 运行时镜像作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 将构建好的 JAR 文件复制到容器中
COPY target/protobuf-java-1.0-SNAPSHOT.jar app.jar

COPY application.yaml application.yaml
# 设置容器启动时的命令
CMD ["java", "-jar", "app.jar"]