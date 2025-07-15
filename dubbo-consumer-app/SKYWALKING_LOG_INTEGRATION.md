# SkyWalking 错误日志关联指南

## 概述

本文档说明如何将错误日志关联到SkyWalking的span中，实现分布式追踪和日志的统一管理。

## 配置说明

### 1. 依赖配置

在 `pom.xml` 中添加以下依赖：

```xml
<!-- SkyWalking 日志工具包 -->
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>8.7.0</version>
</dependency>
```

### 2. Logback配置

创建 `src/main/resources/logback-spring.xml` 文件：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout">
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%tid] - %msg%n</pattern>
            </layout>
        </encoder>
    </appender>

    <!-- SkyWalking gRPC日志上报 -->
    <appender name="GRPC_LOG" class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.log.GRPCLogClientAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout">
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%tid] - %msg%n</pattern>
            </layout>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="GRPC_LOG"/>
    </root>
</configuration>
```

### 3. SkyWalking Agent配置

确保在启动应用时添加以下JVM参数：

```bash
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=your-service-name
-Dskywalking.collector.backend_service=your-oap-server:11800
```

## 使用方法

### 1. 使用ErrorLogUtil工具类

```java
import com.example.consumer.util.ErrorLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YourService {
    private static final Logger logger = LoggerFactory.getLogger(YourService.class);
    
    public void someMethod() {
        try {
            // 业务逻辑
        } catch (Exception e) {
            // 记录错误日志并关联到SkyWalking span
            ErrorLogUtil.logError(logger, "业务处理失败", e, "上下文信息");
            throw e;
        }
    }
}
```

### 2. 使用ActiveSpan直接记录

```java
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;

public void someMethod() {
    try {
        // 业务逻辑
    } catch (Exception e) {
        // 记录到SkyWalking span
        ActiveSpan.error(e);
        ActiveSpan.tag("error.type", e.getClass().getSimpleName());
        ActiveSpan.tag("error.message", e.getMessage());
        throw e;
    }
}
```

### 3. 使用AOP切面

```java
@Aspect
@Component
public class LoggingAspect {
    
    @AfterThrowing(pointcut = "execution(* com.example..*.*(..))", throwing = "exception")
    public void afterThrowing(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        
        // 记录到SkyWalking span
        ActiveSpan.error(exception);
        ActiveSpan.tag("error.method", methodName);
        ActiveSpan.tag("error.type", exception.getClass().getSimpleName());
    }
}
```

## 功能特性

### 1. 自动Trace ID关联

- 日志中自动包含Trace ID (`[%tid]`)
- 可以通过Trace ID在SkyWalking UI中查看完整的调用链路
- 支持日志和追踪数据的关联查询

### 2. 错误信息记录

- 异常类型和消息
- 异常堆栈信息（调试模式）
- 业务上下文信息
- 请求参数信息

### 3. 标签管理

- 错误类型标签
- 错误消息标签
- 业务代码标签
- 上下文信息标签

## 最佳实践

### 1. 日志级别使用

- `ERROR`: 系统错误、业务异常
- `WARN`: 警告信息、潜在问题
- `INFO`: 重要业务操作
- `DEBUG`: 调试信息

### 2. 异常处理

```java
// 推荐：使用工具类统一处理
ErrorLogUtil.logError(logger, "操作失败", exception, "用户ID: " + userId);

// 不推荐：直接打印异常
logger.error("操作失败", exception);
```

### 3. 上下文信息

```java
// 添加丰富的上下文信息
Map<String, String> tags = new HashMap<>();
tags.put("user.id", userId);
tags.put("operation.type", "CREATE");
tags.put("resource.id", resourceId);

ErrorLogUtil.logError(logger, "创建资源失败", exception, "创建用户资源", tags);
```

## 监控和查询

### 1. SkyWalking UI

- 在SkyWalking UI中可以查看带有错误信息的span
- 支持按错误类型、错误消息等条件筛选
- 可以查看完整的调用链路和日志

### 2. 日志查询

```bash
# 查看包含特定Trace ID的日志
grep "TID:xxx" application.log

# 查看错误日志
grep "ERROR" application.log
```

### 3. 告警配置

在SkyWalking中配置告警规则：

```yaml
rules:
  - name: "Error Rate Alert"
    expression: "endpoint_sla < 0.95"
    message: "服务错误率过高"
```

## 故障排查

### 1. 日志未关联到span

- 检查SkyWalking Agent是否正确启动
- 确认logback配置中的GRPC_LOG appender
- 验证网络连接到OAP服务器

### 2. Trace ID未显示

- 检查logback配置中的`[%tid]`模式
- 确认使用了正确的Layout类
- 验证SkyWalking Agent配置

### 3. 错误信息不完整

- 检查异常处理代码
- 确认ErrorLogUtil的使用方式
- 验证日志级别配置

## 总结

通过以上配置和使用方法，可以实现：

1. **自动关联**: 日志自动关联到SkyWalking span
2. **统一管理**: 错误日志和追踪数据的统一管理
3. **快速定位**: 通过Trace ID快速定位问题
4. **完整上下文**: 提供完整的错误上下文信息
5. **监控告警**: 支持基于错误的监控和告警 