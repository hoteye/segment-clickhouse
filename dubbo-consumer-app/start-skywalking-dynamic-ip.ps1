# SkyWalking 动态IP启动脚本 (PowerShell)
Write-Host "SkyWalking 动态IP启动脚本" -ForegroundColor Green
Write-Host "===============================" -ForegroundColor Green

# 方法1：使用Docker网络（推荐）
Write-Host "使用方法1：Docker自定义网络" -ForegroundColor Yellow

# 创建网络（如果不存在）
Write-Host "创建Docker网络..." -ForegroundColor Cyan
docker network create skywalking-network 2>$null

# 停止现有容器
Write-Host "停止现有容器..." -ForegroundColor Cyan
docker stop skywalking-oap-server es7 skywalking-ui 2>$null
docker rm skywalking-oap-server es7 skywalking-ui 2>$null

# 设置系统参数
Write-Host "设置系统参数..." -ForegroundColor Cyan
try {
    wsl --exec sudo sysctl -w vm.max_map_count=262144
    Write-Host "✓ 系统参数设置成功" -ForegroundColor Green
} catch {
    Write-Host "⚠️  无法设置系统参数，请手动设置或使用Docker Desktop" -ForegroundColor Yellow
}

# 启动ES7
Write-Host "启动Elasticsearch..." -ForegroundColor Cyan
docker run -d `
  --name es7 `
  --network skywalking-network `
  -p 9200:9200 `
  -p 9300:9300 `
  -e "discovery.type=single-node" `
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" `
  -e "xpack.security.enabled=false" `
  -v es7_data:/usr/share/elasticsearch/data `
  docker.elastic.co/elasticsearch/elasticsearch:7.10.2

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Elasticsearch 启动成功" -ForegroundColor Green
} else {
    Write-Host "✗ Elasticsearch 启动失败" -ForegroundColor Red
    exit 1
}

# 等待ES启动
Write-Host "等待Elasticsearch就绪..." -ForegroundColor Cyan
$maxRetries = 12
$retryCount = 0

do {
    Start-Sleep -Seconds 10
    $retryCount++
    Write-Host "检查ES健康状态 ($retryCount/$maxRetries)..." -ForegroundColor Gray
    
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9200/_cluster/health" -TimeoutSec 5
        $health = $response.Content | ConvertFrom-Json
        if ($health.status -eq "green" -or $health.status -eq "yellow") {
            Write-Host "✓ Elasticsearch 就绪" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "等待中..." -ForegroundColor Gray
    }
} while ($retryCount -lt $maxRetries)

if ($retryCount -ge $maxRetries) {
    Write-Host "✗ Elasticsearch 启动超时" -ForegroundColor Red
    docker logs es7
    exit 1
}

# 启动SkyWalking OAP
Write-Host "启动SkyWalking OAP..." -ForegroundColor Cyan
docker run -d `
  --name skywalking-oap-server `
  --network skywalking-network `
  --restart=always `
  -p 11800:11800 `
  -p 12800:12800 `
  -e SW_STORAGE=elasticsearch `
  -e SW_STORAGE_ES_CLUSTER_NODES=es7:9200 `
  -e SW_HEALTH_CHECKER=default `
  -e TZ=Asia/Shanghai `
  apache/skywalking-oap-server:8.7.0-es7

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ SkyWalking OAP 启动成功" -ForegroundColor Green
} else {
    Write-Host "✗ SkyWalking OAP 启动失败" -ForegroundColor Red
    exit 1
}

# 启动SkyWalking UI
Write-Host "启动SkyWalking UI..." -ForegroundColor Cyan
docker run -d `
  --name skywalking-ui `
  --network skywalking-network `
  --restart=always `
  -p 8080:8080 `
  -e SW_OAP_ADDRESS=http://skywalking-oap-server:12800 `
  apache/skywalking-ui:8.7.0

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ SkyWalking UI 启动成功" -ForegroundColor Green
} else {
    Write-Host "✗ SkyWalking UI 启动失败" -ForegroundColor Red
}

# 等待OAP启动
Write-Host "等待SkyWalking OAP启动..." -ForegroundColor Cyan
Start-Sleep -Seconds 30

# 显示状态
Write-Host "`n服务状态:" -ForegroundColor Green
Write-Host "===============================" -ForegroundColor Green
docker ps --filter "name=es7" --filter "name=skywalking" --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"

Write-Host "`n访问地址:" -ForegroundColor Green
Write-Host "===============================" -ForegroundColor Green
Write-Host "Elasticsearch: http://localhost:9200" -ForegroundColor White
Write-Host "SkyWalking UI: http://localhost:8080" -ForegroundColor White
Write-Host "SkyWalking OAP gRPC: localhost:11800" -ForegroundColor White
Write-Host "SkyWalking OAP HTTP: http://localhost:12800" -ForegroundColor White

Write-Host "`n检查命令:" -ForegroundColor Green
Write-Host "===============================" -ForegroundColor Green
Write-Host "检查ES: curl http://localhost:9200/_cluster/health" -ForegroundColor White
Write-Host "检查OAP: docker logs skywalking-oap-server" -ForegroundColor White
Write-Host "检查UI: docker logs skywalking-ui" -ForegroundColor White 