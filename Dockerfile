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
# Set container startup command
CMD ["java", "-jar", "app.jar"]