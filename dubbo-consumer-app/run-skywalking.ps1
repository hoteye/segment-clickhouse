# SkyWalking Docker Compose 启动脚本
param(
    [string]$Action = "up"  # up, down, restart, status
)

$ComposeFile = "docker-compose-dynamic-ip.yml"

Write-Host "SkyWalking Docker Compose 管理脚本" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Green

# 检查Docker Compose文件是否存在
if (-not (Test-Path $ComposeFile)) {
    Write-Host "错误: 找不到 $ComposeFile 文件" -ForegroundColor Red
    exit 1
}

# 检查Docker是否运行
try {
    docker ps | Out-Null
    Write-Host "✓ Docker 运行正常" -ForegroundColor Green
} catch {
    Write-Host "✗ Docker 未运行，请启动 Docker Desktop" -ForegroundColor Red
    exit 1
}

# 设置系统参数
Write-Host "`n设置系统参数..." -ForegroundColor Cyan
try {
    wsl --exec sudo sysctl -w vm.max_map_count=262144 | Out-Null
    Write-Host "✓ vm.max_map_count 设置成功" -ForegroundColor Green
} catch {
    Write-Host "⚠️  无法设置 vm.max_map_count，可能需要手动设置" -ForegroundColor Yellow
}

switch ($Action.ToLower()) {
    "up" {
        Write-Host "`n启动 SkyWalking 服务..." -ForegroundColor Cyan
        
        # 启动服务
        docker-compose -f $ComposeFile up -d
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n✓ 服务启动成功" -ForegroundColor Green
            
            # 等待服务就绪
            Write-Host "`n等待服务就绪..." -ForegroundColor Cyan
            Start-Sleep -Seconds 30
            
            # 检查ES健康状态
            Write-Host "检查 Elasticsearch 状态..." -ForegroundColor Gray
            $maxRetries = 12
            $retryCount = 0
            
            do {
                try {
                    $response = Invoke-WebRequest -Uri "http://localhost:9200/_cluster/health" -TimeoutSec 5
                    $health = $response.Content | ConvertFrom-Json
                    if ($health.status -eq "green" -or $health.status -eq "yellow") {
                        Write-Host "✓ Elasticsearch 就绪 (状态: $($health.status))" -ForegroundColor Green
                        break
                    }
                } catch {
                    $retryCount++
                    Write-Host "等待 Elasticsearch... ($retryCount/$maxRetries)" -ForegroundColor Gray
                    Start-Sleep -Seconds 10
                }
            } while ($retryCount -lt $maxRetries)
            
            # 显示服务状态
            Write-Host "`n当前服务状态:" -ForegroundColor Green
            docker-compose -f $ComposeFile ps
            
            # 显示访问地址
            Write-Host "`n访问地址:" -ForegroundColor Green
            Write-Host "===============================" -ForegroundColor Green
            Write-Host "🔍 Elasticsearch: http://localhost:9200" -ForegroundColor White
            Write-Host "📊 SkyWalking UI: http://localhost:8080" -ForegroundColor White
            Write-Host "🔧 SkyWalking OAP: http://localhost:12800" -ForegroundColor White
            Write-Host "📡 gRPC端口: localhost:11800" -ForegroundColor White
            
        } else {
            Write-Host "`n✗ 服务启动失败" -ForegroundColor Red
            docker-compose -f $ComposeFile logs --tail=20
        }
    }
    
    "down" {
        Write-Host "`n停止 SkyWalking 服务..." -ForegroundColor Cyan
        docker-compose -f $ComposeFile down
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ 服务已停止" -ForegroundColor Green
        } else {
            Write-Host "✗ 停止服务时出错" -ForegroundColor Red
        }
    }
    
    "restart" {
        Write-Host "`n重启 SkyWalking 服务..." -ForegroundColor Cyan
        docker-compose -f $ComposeFile restart
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ 服务已重启" -ForegroundColor Green
            Start-Sleep -Seconds 15
            docker-compose -f $ComposeFile ps
        } else {
            Write-Host "✗ 重启服务时出错" -ForegroundColor Red
        }
    }
    
    "status" {
        Write-Host "`n服务状态:" -ForegroundColor Cyan
        docker-compose -f $ComposeFile ps
        
        Write-Host "`n详细状态检查:" -ForegroundColor Cyan
        
        # 检查容器状态
        $containers = @("es7", "skywalking-oap-server", "skywalking-ui")
        foreach ($container in $containers) {
            $status = docker inspect $container --format='{{.State.Status}}' 2>$null
            if ($status) {
                $color = if ($status -eq "running") { "Green" } else { "Red" }
                Write-Host "  $container : $status" -ForegroundColor $color
            } else {
                Write-Host "  $container : 未找到" -ForegroundColor Gray
            }
        }
        
        # 检查端口
        Write-Host "`n端口检查:" -ForegroundColor Cyan
        $ports = @(
            @{Port=9200; Service="Elasticsearch"},
            @{Port=8080; Service="SkyWalking UI"},
            @{Port=12800; Service="SkyWalking OAP HTTP"},
            @{Port=11800; Service="SkyWalking OAP gRPC"}
        )
        
        foreach ($portInfo in $ports) {
            try {
                $connection = Test-NetConnection -ComputerName localhost -Port $portInfo.Port -InformationLevel Quiet
                $status = if ($connection) { "开放" } else { "关闭" }
                $color = if ($connection) { "Green" } else { "Red" }
                Write-Host "  端口 $($portInfo.Port) ($($portInfo.Service)): $status" -ForegroundColor $color
            } catch {
                Write-Host "  端口 $($portInfo.Port) ($($portInfo.Service)): 检查失败" -ForegroundColor Red
            }
        }
    }
    
    "logs" {
        Write-Host "`n查看服务日志..." -ForegroundColor Cyan
        docker-compose -f $ComposeFile logs -f
    }
    
    default {
        Write-Host "`n使用方法:" -ForegroundColor Yellow
        Write-Host "  .\run-skywalking.ps1 up      # 启动服务" -ForegroundColor White
        Write-Host "  .\run-skywalking.ps1 down    # 停止服务" -ForegroundColor White
        Write-Host "  .\run-skywalking.ps1 restart # 重启服务" -ForegroundColor White
        Write-Host "  .\run-skywalking.ps1 status  # 查看状态" -ForegroundColor White
        Write-Host "  .\run-skywalking.ps1 logs    # 查看日志" -ForegroundColor White
    }
} 