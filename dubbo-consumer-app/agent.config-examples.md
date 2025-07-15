# SkyWalking Agent 配置示例

## 1. Docker Compose 环境配置

如果OAP服务器和应用都在Docker Compose中：

```properties
# OAP服务器地址（使用容器名）
collector.backend_service=skywalking-oap-server:11800

# 日志上报配置
plugin.toolkit.log.grpc.reporter.server_host=skywalking-oap-server:11800
plugin.toolkit.log.grpc.reporter.server_port=11800
plugin.toolkit.log.grpc.reporter.max_message_size=10485760
plugin.toolkit.log.grpc.reporter.upstream_timeout=30
```

## 2. 独立容器环境配置

如果OAP服务器是独立容器，应用在宿主机上：

```properties
# 使用宿主机IP和端口映射
collector.backend_service=192.168.1.100:11800

# 日志上报配置
plugin.toolkit.log.grpc.reporter.server_host=192.168.1.100:11800
plugin.toolkit.log.grpc.reporter.server_port=11800
plugin.toolkit.log.grpc.reporter.max_message_size=10485760
plugin.toolkit.log.grpc.reporter.upstream_timeout=30
```

## 3. Kubernetes 环境配置

如果使用Kubernetes部署：

```properties
# 使用Kubernetes服务名
collector.backend_service=skywalking-oap.skywalking-system.svc.cluster.local:11800

# 日志上报配置
plugin.toolkit.log.grpc.reporter.server_host=skywalking-oap.skywalking-system.svc.cluster.local:11800
plugin.toolkit.log.grpc.reporter.server_port=11800
plugin.toolkit.log.grpc.reporter.max_message_size=10485760
plugin.toolkit.log.grpc.reporter.upstream_timeout=30
```

## 4. 环境变量配置

使用环境变量动态配置：

```properties
# 使用环境变量
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:skywalking-oap-server:11800}

# 日志上报配置
plugin.toolkit.log.grpc.reporter.server_host=${SW_GRPC_LOG_SERVER_HOST:skywalking-oap-server:11800}
plugin.toolkit.log.grpc.reporter.server_port=${SW_GRPC_LOG_SERVER_PORT:11800}
plugin.toolkit.log.grpc.reporter.max_message_size=${SW_GRPC_LOG_MAX_MESSAGE_SIZE:10485760}
plugin.toolkit.log.grpc.reporter.upstream_timeout=${SW_GRPC_LOG_GRPC_UPSTREAM_TIMEOUT:30}
```

## 5. 网络配置检查

### Docker Compose 网络检查

```bash
# 查看网络
docker network ls

# 查看容器网络配置
docker inspect skywalking-oap | grep -A 10 "NetworkSettings"

# 测试网络连通性
docker exec dubbo-consumer-app ping oap
```

### 端口映射检查

```bash
# 检查端口映射
docker port skywalking-oap

# 测试端口连通性
telnet 192.168.1.100 11800
```

## 6. 常见问题解决

### 问题1：连接超时

**症状**: `DEADLINE_EXCEEDED: ClientCall started after deadline exceeded`

**解决方案**:
```properties
# 增加超时时间
plugin.toolkit.log.grpc.reporter.upstream_timeout=60
```

### 问题2：网络不通

**症状**: `UNAVAILABLE: io exception`

**解决方案**:
1. 检查容器网络配置
2. 确认端口映射正确
3. 验证防火墙设置

### 问题3：DNS解析失败

**症状**: `UNAVAILABLE: Name or service not known`

**解决方案**:
1. 使用IP地址替代容器名
2. 检查DNS配置
3. 使用完整的服务名

## 7. 配置验证脚本

创建配置验证脚本：

```bash
#!/bin/bash
# verify_skywalking_config.sh

echo "验证SkyWalking配置..."

# 检查OAP服务器连通性
OAP_HOST=${SW_GRPC_LOG_SERVER_HOST:-oap:11800}
echo "测试连接到: $OAP_HOST"

# 使用nc测试端口连通性
if nc -z -w5 $(echo $OAP_HOST | cut -d: -f1) $(echo $OAP_HOST | cut -d: -f2); then
    echo "✓ OAP服务器连接正常"
else
    echo "✗ OAP服务器连接失败"
    exit 1
fi

echo "配置验证完成"
``` 