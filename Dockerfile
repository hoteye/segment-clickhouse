# Use official OpenJDK runtime image as base image
FROM openjdk:17-jdk-slim AS base

# Set working directory
WORKDIR /app

# Set timezone to Asia/Shanghai
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Copy built JAR file into container
ARG JAR_FILE
COPY target/${JAR_FILE} app.jar

COPY application.yaml application.yaml
# Set container startup command with JVM memory limits
CMD ["java", "-Xms512m", "-Xmx512m", "-XX:MetaspaceSize=128m", "-XX:MaxMetaspaceSize=256m", "-jar", "app.jar"]