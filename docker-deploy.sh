#!/bin/bash

# Docker 部署脚本 - 构建并部署3个Dubbo模块
# 用法: ./docker-deploy.sh

set -e

echo "=========================================="
echo "开始 Docker 部署流程"
echo "=========================================="

# 定义模块列表和端口映射
declare -A MODULES=(
    ["dubbo-consumer-app"]="8087"
    ["dubbo-provider-app"]="8745" 
    ["dubbo-provider-b"]="8746"
)

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

function log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

function log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

function log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 清理函数
function cleanup_module() {
    local module=$1
    local port=${MODULES[$module]}
    
    log_info "清理模块: $module"
    
    # 停止并删除容器
    if docker ps -a --format "table {{.Names}}" | grep -q "^${module}$"; then
        log_info "停止并删除容器: $module"
        docker rm -f $module || log_warn "删除容器 $module 失败"
    else
        log_info "容器 $module 不存在，跳过删除"
    fi
    
    # 删除镜像 (保留最新版本，删除旧版本)
    if docker images --format "table {{.Repository}}:{{.Tag}}" | grep -q "^${module}:"; then
        log_info "删除旧镜像: $module"
        # 删除除了latest和当前版本之外的所有镜像
        docker images $module --format "table {{.Repository}}:{{.Tag}}" | tail -n +2 | grep -v "latest" | grep -v "1.1.10" | xargs -r docker rmi || log_warn "删除旧镜像失败"
    fi
}

# 构建模块
function build_module() {
    local module=$1
    
    log_info "========== 构建模块: $module =========="
    
    cd $module
    
    # Maven 打包 (跳过Docker插件)
    log_info "执行 Maven 打包..."
    mvn clean package -DskipTests -Ddockerfile.skip=true
    
    # 检查JAR文件是否存在
    jar_file=$(find target -name "*.jar" | grep -v original | head -1)
    if [ -z "$jar_file" ]; then
        log_error "未找到打包后的JAR文件"
        cd ..
        return 1
    fi
    
    jar_name=$(basename $jar_file)
    log_info "找到JAR文件: $jar_name"
    
    # Docker 构建 (使用本地 docker build 替代 maven plugin)
    log_info "构建 Docker 镜像..."
    docker build --build-arg JAR_FILE=$jar_name -t $module:1.1.10 .
    
    cd ..
    
    log_info "模块 $module 构建完成"
}

# 启动模块
function start_module() {
    local module=$1
    local port=${MODULES[$module]}
    
    log_info "========== 启动模块: $module =========="
    
    # 启动新容器
    log_info "启动容器: $module (端口: $port)"
    docker run --name $module \
        --publish $port:$port \
        --detach \
        --restart unless-stopped \
        $module:1.1.10
    
    # 等待容器启动
    sleep 3
    
    # 检查容器状态
    if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "^${module}"; then
        log_info "✓ 容器 $module 启动成功"
        # 显示容器日志 (最后10行)
        log_info "容器日志 (最后10行):"
        docker logs --tail 10 $module
    else
        log_error "✗ 容器 $module 启动失败"
        docker logs $module
        return 1
    fi
}

# 健康检查
function health_check() {
    log_info "========== 健康检查 =========="
    
    for module in "${!MODULES[@]}"; do
        local port=${MODULES[$module]}
        log_info "检查 $module (端口: $port)"
        
        if docker ps --format "table {{.Names}}\t{{.Status}}" | grep -q "^${module}.*Up"; then
            log_info "✓ $module 运行正常"
        else
            log_error "✗ $module 运行异常"
        fi
    done
    
    # 显示运行中的容器
    log_info "当前运行的容器:"
    docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}"
}

# 主流程
function main() {
    log_info "开始部署 ${#MODULES[@]} 个模块..."
    
    # 1. 清理阶段
    log_info "========== 阶段 1: 清理旧容器和镜像 =========="
    for module in "${!MODULES[@]}"; do
        cleanup_module $module
    done
    
    # 2. 构建阶段  
    log_info "========== 阶段 2: 构建模块 =========="
    for module in "${!MODULES[@]}"; do
        if [ -d "$module" ]; then
            build_module $module
        else
            log_error "模块目录 $module 不存在"
            exit 1
        fi
    done
    
    # 3. 启动阶段
    log_info "========== 阶段 3: 启动容器 =========="
    for module in "${!MODULES[@]}"; do
        start_module $module
    done
    
    # 4. 健康检查
    health_check
    
    log_info "=========================================="
    log_info "✓ 所有模块部署完成!"
    log_info "=========================================="
    
    # 显示访问信息
    echo ""
    log_info "服务访问信息:"
    for module in "${!MODULES[@]}"; do
        local port=${MODULES[$module]}
        echo "  - $module: http://localhost:$port"
    done
}

# 脚本入口
if [ "$1" = "help" ] || [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  help, --help, -h    显示帮助信息"
    echo ""
    echo "功能:"
    echo "  1. 停止并删除旧容器"
    echo "  2. 删除旧Docker镜像"
    echo "  3. Maven打包并构建新镜像"
    echo "  4. 启动新版本容器"
    echo "  5. 健康检查"
    echo ""
    echo "部署的模块:"
    for module in "${!MODULES[@]}"; do
        local port=${MODULES[$module]}
        echo "  - $module (端口: $port)"
    done
    exit 0
fi

# 检查Docker是否运行
if ! docker info >/dev/null 2>&1; then
    log_error "Docker 未运行或无权限访问，请检查 Docker 服务状态"
    exit 1
fi

# 检查Maven是否可用
if ! command -v mvn &> /dev/null; then
    log_error "Maven 未安装或不在PATH中"
    exit 1
fi

# 执行主流程
main "$@"