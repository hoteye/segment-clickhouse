#!/bin/bash

echo "========================================"
echo "SkyWalking 网络连接诊断工具"
echo "========================================"
echo

# 默认配置
DEFAULT_OAP_HOST="skywalking-oap-server:11800"
DEFAULT_OAP_IP="192.168.100.6:11800"

# 从环境变量获取配置
OAP_HOST=${SW_GRPC_LOG_SERVER_HOST:-$DEFAULT_OAP_HOST}
OAP_IP=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-$DEFAULT_OAP_IP}

echo "当前配置:"
echo "  OAP Host: $OAP_HOST"
echo "  OAP IP: $OAP_IP"
echo

# 解析主机名和端口
HOST_NAME=$(echo $OAP_HOST | cut -d: -f1)
HOST_PORT=$(echo $OAP_HOST | cut -d: -f2)

IP_ADDR=$(echo $OAP_IP | cut -d: -f1)
IP_PORT=$(echo $OAP_IP | cut -d: -f2)

echo "诊断结果:"
echo "----------------------------------------"

# 1. 检查DNS解析
echo "1. DNS解析检查:"
if nslookup $HOST_NAME >/dev/null 2>&1; then
    echo "   ✓ $HOST_NAME DNS解析成功"
    RESOLVED_IP=$(nslookup $HOST_NAME | grep -A1 "Name:" | tail -1 | awk '{print $2}')
    echo "   → 解析到IP: $RESOLVED_IP"
else
    echo "   ✗ $HOST_NAME DNS解析失败"
fi
echo

# 2. 检查端口连通性
echo "2. 端口连通性检查:"

# 检查主机名端口
if command -v nc >/dev/null 2>&1; then
    if nc -z -w5 $HOST_NAME $HOST_PORT 2>/dev/null; then
        echo "   ✓ $HOST_NAME:$HOST_PORT 连接正常"
    else
        echo "   ✗ $HOST_NAME:$HOST_PORT 连接失败"
    fi
else
    echo "   - nc命令不可用，跳过端口检查"
fi

# 检查IP端口
if command -v nc >/dev/null 2>&1; then
    if nc -z -w5 $IP_ADDR $IP_PORT 2>/dev/null; then
        echo "   ✓ $IP_ADDR:$IP_PORT 连接正常"
    else
        echo "   ✗ $IP_ADDR:$IP_PORT 连接失败"
    fi
fi
echo

# 3. 检查Docker网络（如果在容器中）
echo "3. Docker网络检查:"
if [ -f /.dockerenv ]; then
    echo "   ✓ 当前在Docker容器中运行"
    
    # 检查容器网络
    if command -v ping >/dev/null 2>&1; then
        if ping -c 1 $HOST_NAME >/dev/null 2>&1; then
            echo "   ✓ 可以ping通 $HOST_NAME"
        else
            echo "   ✗ 无法ping通 $HOST_NAME"
        fi
    fi
else
    echo "   - 当前不在Docker容器中"
fi
echo

# 4. 检查环境变量
echo "4. 环境变量检查:"
echo "   SW_GRPC_LOG_SERVER_HOST: ${SW_GRPC_LOG_SERVER_HOST:-未设置}"
echo "   SW_AGENT_COLLECTOR_BACKEND_SERVICES: ${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-未设置}"
echo

# 5. 提供建议
echo "5. 建议配置:"
echo "----------------------------------------"

if [ -f /.dockerenv ]; then
    echo "在Docker容器中，建议使用:"
    echo "  SW_GRPC_LOG_SERVER_HOST=skywalking-oap-server:11800"
    echo "  SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap-server:11800"
else
    echo "在宿主机中，建议使用:"
    echo "  SW_GRPC_LOG_SERVER_HOST=192.168.100.6:11800"
    echo "  SW_AGENT_COLLECTOR_BACKEND_SERVICES=192.168.100.6:11800"
fi

echo
echo "如果使用Docker Compose，确保:"
echo "1. 所有服务在同一网络中"
echo "2. 使用服务名而不是IP地址"
echo "3. 端口映射正确配置"
echo 