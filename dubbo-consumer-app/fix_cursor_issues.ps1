# Cursor 问题修复脚本 (PowerShell)
param(
    [string]$ProjectPath = "",
    [switch]$AnalyzeOnly = $false
)

Write-Host "========================================" -ForegroundColor Green
Write-Host "Cursor 问题修复工具" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# 1. 清理Cursor缓存
Write-Host "步骤 1: 清理Cursor缓存" -ForegroundColor Cyan
Write-Host "------------------------" -ForegroundColor Cyan

$cursorWorkspaceStorage = "$env:APPDATA\Cursor\User\workspaceStorage"
$cursorGlobalStorage = "$env:APPDATA\Cursor\User\globalStorage"

if (Test-Path $cursorWorkspaceStorage) {
    try {
        Remove-Item -Path $cursorWorkspaceStorage -Recurse -Force
        Write-Host "✓ 已清理 workspaceStorage" -ForegroundColor Green
    }
    catch {
        Write-Host "✗ 清理 workspaceStorage 失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}

if (Test-Path $cursorGlobalStorage) {
    try {
        Remove-Item -Path $cursorGlobalStorage -Recurse -Force
        Write-Host "✓ 已清理 globalStorage" -ForegroundColor Green
    }
    catch {
        Write-Host "✗ 清理 globalStorage 失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""

# 2. 分析工程（如果提供了路径）
if ($ProjectPath -and (Test-Path $ProjectPath)) {
    Write-Host "步骤 2: 分析工程文件" -ForegroundColor Cyan
    Write-Host "------------------------" -ForegroundColor Cyan
    
    $totalFiles = 0
    $totalSize = 0
    $largeFiles = @()
    $fileTypes = @{}
    
    # 排除的目录
    $excludeDirs = @('target', 'build', 'node_modules', 'vendor', 'logs', 'tmp', 'temp', '.git', '.idea', '.vscode', 'dist', 'coverage', 'docs', 'javadoc', 'data', 'datasets')
    
    # 排除的文件扩展名
    $excludeExtensions = @('.jar', '.war', '.ear', '.class', '.zip', '.tar.gz', '.rar', '.db', '.sqlite', '.log', '.csv', '.json')
    
    Get-ChildItem -Path $ProjectPath -Recurse -File | ForEach-Object {
        $file = $_
        $relativePath = $file.FullName.Substring($ProjectPath.Length + 1)
        
        # 检查是否在排除目录中
        $isExcluded = $false
        foreach ($excludeDir in $excludeDirs) {
            if ($relativePath -like "*\$excludeDir\*") {
                $isExcluded = $true
                break
            }
        }
        
        if (-not $isExcluded) {
            $extension = $file.Extension.ToLower()
            if ($extension -notin $excludeExtensions) {
                $totalFiles++
                $totalSize += $file.Length
                
                # 统计文件类型
                if ($fileTypes.ContainsKey($extension)) {
                    $fileTypes[$extension].Count++
                    $fileTypes[$extension].Size += $file.Length
                } else {
                    $fileTypes[$extension] = @{Count = 1; Size = $file.Length}
                }
                
                # 记录大文件（>1MB）
                if ($file.Length -gt 1MB) {
                    $largeFiles += @{
                        Path = $relativePath
                        Size = $file.Length
                    }
                }
            }
        }
    }
    
    Write-Host "工程统计信息:" -ForegroundColor Yellow
    Write-Host "  总文件数: $totalFiles" -ForegroundColor White
    Write-Host "  总大小: $([math]::Round($totalSize / 1MB, 2)) MB" -ForegroundColor White
    Write-Host "  大文件数 (>1MB): $($largeFiles.Count)" -ForegroundColor White
    
    if ($largeFiles.Count -gt 0) {
        Write-Host ""
        Write-Host "大文件列表 (前10个):" -ForegroundColor Yellow
        $largeFiles | Sort-Object Size -Descending | Select-Object -First 10 | ForEach-Object {
            $sizeMB = [math]::Round($_.Size / 1MB, 2)
            Write-Host "  $($_.Path): $sizeMB MB" -ForegroundColor White
        }
    }
    
    Write-Host ""
    
    # 3. 创建配置文件
    Write-Host "步骤 3: 创建Cursor配置文件" -ForegroundColor Cyan
    Write-Host "------------------------" -ForegroundColor Cyan
    
    $cursorIgnorePath = Join-Path $ProjectPath ".cursorignore"
    $cursorRulesPath = Join-Path $ProjectPath ".cursorrules"
    
    # 创建 .cursorignore 文件
    if (-not (Test-Path $cursorIgnorePath)) {
        $cursorIgnoreContent = @"
# 构建输出目录
target/
build/
out/
dist/

# 依赖目录
node_modules/
vendor/

# 日志文件
logs/
*.log

# 临时文件
tmp/
temp/
.tmp/

# IDE配置文件
.idea/
.vscode/
*.iml

# 系统文件
.DS_Store
Thumbs.db

# 大数据文件
*.csv
*.json
*.xml
*.sql

# 压缩文件
*.zip
*.tar.gz
*.rar

# 二进制文件
*.jar
*.war
*.ear
*.class

# 数据库文件
*.db
*.sqlite

# 缓存目录
.cache/
.npm/
.yarn/

# 测试覆盖率报告
coverage/

# 文档生成目录
docs/
javadoc/

# 其他可能的大文件目录
data/
datasets/
models/
weights/
"@
        $cursorIgnoreContent | Out-File -FilePath $cursorIgnorePath -Encoding UTF8
        Write-Host "✓ 已创建 .cursorignore 文件" -ForegroundColor Green
    } else {
        Write-Host "- .cursorignore 文件已存在" -ForegroundColor Gray
    }
    
    # 创建 .cursorrules 文件
    if (-not (Test-Path $cursorRulesPath)) {
        $cursorRulesContent = @"
# Cursor配置文件 - 优化性能设置

# 禁用某些功能以提高性能
disable_telemetry: true
disable_analytics: true

# 限制文件索引大小
max_file_size_mb: 10

# 排除大文件类型
exclude_large_files: true

# 限制并发处理
max_concurrent_indexing: 2

# 禁用实时索引
disable_realtime_indexing: true

# 内存使用限制
max_memory_usage_mb: 2048

# 文件监控设置
file_watcher_enabled: false

# 代码补全设置
autocomplete_enabled: true
autocomplete_delay_ms: 500

# 语法高亮设置
syntax_highlighting_enabled: true

# 错误检查设置
error_checking_enabled: false

# 格式化设置
format_on_save: false
"@
        $cursorRulesContent | Out-File -FilePath $cursorRulesPath -Encoding UTF8
        Write-Host "✓ 已创建 .cursorrules 文件" -ForegroundColor Green
    } else {
        Write-Host "- .cursorrules 文件已存在" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "修复完成！" -ForegroundColor Green
Write-Host ""
Write-Host "下一步操作：" -ForegroundColor Cyan
Write-Host "1. 关闭所有Cursor窗口" -ForegroundColor White
Write-Host "2. 重新启动Cursor" -ForegroundColor White
Write-Host "3. 如果问题仍然存在，请尝试安全模式启动" -ForegroundColor White
Write-Host ""
Write-Host "安全模式启动方法：" -ForegroundColor Yellow
Write-Host "- 按住 Shift 键启动Cursor" -ForegroundColor White
Write-Host "- 选择 '禁用所有扩展'" -ForegroundColor White
Write-Host ""
Write-Host "如果问题仍然存在：" -ForegroundColor Red
Write-Host "- 检查工程是否包含过多文件 (>10,000个)" -ForegroundColor White
Write-Host "- 检查工程总大小是否过大 (>500MB)" -ForegroundColor White
Write-Host "- 考虑逐步打开工程文件" -ForegroundColor White
Write-Host "" 