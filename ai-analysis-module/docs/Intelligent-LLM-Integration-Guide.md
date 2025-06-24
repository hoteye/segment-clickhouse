# 智能大模型集成使用指南

## 概述

本项目已成功集成了多种大模型能力，为交易链路数据分析和问题根因定位提供了强大的智能分析支持。

## 🚀 核心优势

### 1. 多模型智能路由
- **DeepSeek系列**: 主力模型，支持中文分析，成本低廉
  - `deepseek-chat`: 通用分析、业务诊断
  - `deepseek-coder`: 代码分析、技术问题定位  
  - `deepseek-math`: 数据计算、统计分析、异常检测
- **Claude 3.5 Sonnet**: 复杂推理、根因分析
- **GPT-4o**: 综合分析、多模态支持
- **本地模型**: Ollama等本地部署方案

### 2. 智能分析场景
- ✅ 交易链路性能分析
- ✅ 实时异常检测与预警
- ✅ 根因定位与诊断
- ✅ 性能瓶颈识别
- ✅ 智能优化建议
- ✅ 预测性分析

### 3. 降级保障机制
- 多模型自动降级
- 本地分析兜底
- 健康检查与监控

## 📋 环境配置

### 必需配置
```yaml
ai:
  llm:
    provider: deepseek  # 主要提供商
    enabled: true
    fallback-enabled: true
    
    # DeepSeek配置
    deepseek:
      api-key: ${AI_DEEPSEEK_API_KEY:your-api-key}
      base-url: https://api.deepseek.com/v1
      model: deepseek-chat
      timeout: 15000
      max-tokens: 2000
      temperature: 0.7
```

### 环境变量
```bash
# DeepSeek API Key (推荐)
export AI_DEEPSEEK_API_KEY="sk-your-deepseek-api-key"

# OpenAI API Key (可选)
export AI_OPENAI_API_KEY="sk-your-openai-api-key"

# Azure OpenAI (可选)
export AI_AZURE_API_KEY="your-azure-key"
export AI_AZURE_ENDPOINT="https://your-resource.openai.azure.com"
```

## 🎯 核心API接口

### 1. 一站式智能诊断
**最推荐使用** - 集成所有分析能力的一站式接口

```bash
# 综合诊断演示
curl -X GET "http://localhost:8082/ai-analysis/api/intelligent-analysis/demo/comprehensive"

# 自定义数据诊断
curl -X POST "http://localhost:8082/ai-analysis/api/intelligent-analysis/diagnosis/one-stop" \
  -H "Content-Type: application/json" \
  -d '{
    "timeSeriesData": [...],
    "currentMetrics": {...},
    "incidentData": {...},
    "traceData": {...},
    "errorLogs": [...]
  }'
```

### 2. 高级异常检测
```bash
curl -X POST "http://localhost:8082/ai-analysis/api/intelligent-analysis/anomaly/advanced-detect" \
  -H "Content-Type: application/json" \
  -d '{
    "timeSeriesData": [
      {
        "timestamp": "2024-01-01T10:00:00",
        "responseTime": 150,
        "errorRate": 2.5,
        "throughput": 1000,
        "cpuUsage": 60,
        "memoryUsage": 70
      }
    ],
    "currentMetrics": {
      "responseTime": 1200,
      "errorRate": 8.5,
      "throughput": 800,
      "cpuUsage": 85,
      "memoryUsage": 78
    }
  }'
```

### 3. 根因分析
```bash
curl -X POST "http://localhost:8082/ai-analysis/api/intelligent-analysis/rootcause/comprehensive-analysis" \
  -H "Content-Type: application/json" \
  -d '{
    "incidentData": {
      "incidentId": "INC-2024-001",
      "startTime": "2024-01-01T10:00:00",
      "severity": "HIGH",
      "affectedServices": ["payment-service", "user-service"],
      "errorMessages": ["Connection timeout", "Database connection failed"]
    }
  }'
```

### 4. 交易链路分析
```bash
curl -X POST "http://localhost:8082/ai-analysis/api/intelligent-analysis/transaction/intelligent-analysis" \
  -H "Content-Type: application/json" \
  -d '{
    "traceData": {
      "traceId": "trace-12345",
      "duration": 2500,
      "spanCount": 8,
      "errorCount": 2,
      "services": ["frontend", "api-gateway", "payment-service", "database"]
    },
    "errorLogs": [
      {
        "timestamp": "2024-01-01T10:30:00",
        "level": "ERROR",
        "service": "payment-service",
        "message": "Database connection timeout"
      }
    ]
  }'
```

## 🔧 核心服务说明

### 1. AdvancedAnomalyDetectionService
高级异常检测引擎，支持：
- 统计学异常检测（Z-Score、IQR）
- 时间序列异常检测
- 模式异常检测（周期性、级联故障）
- 业务逻辑异常检测

### 2. RootCauseAnalysisEngine
智能根因分析引擎，提供：
- 多维度分析（技术、时间、依赖、资源）
- 证据收集与关联
- 根因候选生成与评分
- 大模型深度推理

### 3. LLMAnalysisService (增强版)
支持：
- 智能模型选择
- 多模型分析对比
- 专业提示词工程
- 多模型降级调用

### 4. IntelligentAnalysisController
统一API入口，提供：
- 一站式智能诊断
- 预测性分析
- 性能优化建议
- 完整的演示接口

## 📊 实际应用场景

### 场景1：交易系统性能突降
```json
{
  "问题": "支付成功率从99%降至85%",
  "分析路径": "异常检测 → 链路分析 → 根因定位 → 优化建议",
  "AI分析结果": {
    "根因": "数据库连接池耗尽",
    "影响": "15%交易失败",
    "建议": ["增加连接池大小", "优化长查询", "添加熔断器"]
  }
}
```

### 场景2：系统响应时间异常
```json
{
  "问题": "API响应时间从100ms增至2000ms",
  "AI分析": {
    "瓶颈点": "数据库查询",
    "根因": "缺失索引导致全表扫描",
    "优化方案": "添加复合索引，预计提升80%性能"
  }
}
```

### 场景3：级联故障诊断
```json
{
  "问题": "用户服务故障导致整个交易链路异常",
  "AI分析": {
    "故障传播路径": ["用户服务", "认证服务", "支付服务", "订单服务"],
    "根本原因": "Redis连接数耗尽",
    "防护建议": ["实施服务降级", "增加熔断器", "优化连接池"]
  }
}
```

## 💡 最佳实践

### 1. 模型选择策略
```java
// 代码分析场景
String model = llmService.selectOptimalModel("code_analysis", context);

// 数据分析场景  
String model = llmService.selectOptimalModel("statistical_analysis", context);

// 业务分析场景
String model = llmService.selectOptimalModel("business_analysis", context);
```

### 2. 多模型对比分析
```java
List<String> models = Arrays.asList("deepseek-chat", "deepseek-coder", "gpt-4o");
Map<String, Object> result = llmService.multiModelAnalysis(prompt, models);
```

### 3. 专业提示词构建
```java
String prompt = llmService.buildSpecializedPrompt("transaction_analysis", data, context);
String result = llmService.analyzeWithPrompt(prompt);
```

## 🔍 监控与调试

### 健康检查
```bash
curl -X GET "http://localhost:8082/ai-analysis/api/intelligent-analysis/health"
```

### LLM状态检查
```bash
curl -X GET "http://localhost:8082/ai-analysis/api/llm/status"
```

### 日志级别配置
```yaml
logging:
  level:
    com.o11y.ai: DEBUG  # 开启详细日志
```

## 🚀 快速开始

### 1. 启动服务
```bash
cd d:\java-demo\segment-alarm-clickhouse
java -jar target/segment-alarm-clickhouse-1.0.5-shaded.jar
```

### 2. 验证集成
```bash
# 健康检查
curl http://localhost:8082/ai-analysis/api/intelligent-analysis/health

# 运行综合演示
curl http://localhost:8082/ai-analysis/api/intelligent-analysis/demo/comprehensive
```

### 3. 测试分析能力
```powershell
# PowerShell示例
$response = Invoke-RestMethod -Uri "http://localhost:8082/ai-analysis/api/intelligent-analysis/demo/comprehensive" -Method Get
$response | ConvertTo-Json -Depth 10
```

## 💰 成本优化建议

### 1. 模型成本对比
- **DeepSeek**: ¥0.14/万tokens (最经济)
- **GPT-3.5**: ¥0.75/万tokens  
- **GPT-4**: ¥3.0/万tokens
- **Claude**: ¥2.25/万tokens
- **本地模型**: 免费（需要GPU资源）

### 2. 智能路由策略
```yaml
# 推荐配置：成本与性能平衡
ai:
  llm:
    provider: deepseek  # 主要使用DeepSeek
    fallback-enabled: true  # 启用降级
    
    # 场景化配置
    routing:
      code-analysis: deepseek-coder
      data-analysis: deepseek-math  
      business-analysis: deepseek-chat
      complex-reasoning: claude-3-5-sonnet  # 复杂场景才使用
```

## 🔧 故障排查

### 常见问题
1. **API Key配置错误**
   ```bash
   # 检查环境变量
   echo $AI_DEEPSEEK_API_KEY
   ```

2. **网络连接问题**
   ```bash
   # 测试API连通性
   curl -H "Authorization: Bearer $AI_DEEPSEEK_API_KEY" https://api.deepseek.com/v1/models
   ```

3. **模型响应慢**
   ```yaml
   # 调整超时配置
   ai:
     llm:
       deepseek:
         timeout: 30000  # 增加超时时间
   ```

## 📈 扩展规划

### 短期规划
- [ ] 增加更多大模型支持（Claude、Gemini）
- [ ] 优化提示词工程
- [ ] 增强异常检测算法

### 中期规划  
- [ ] 自定义模型微调
- [ ] 智能学习与优化
- [ ] 更丰富的分析报告

### 长期规划
- [ ] 多模态分析能力
- [ ] 实时流式分析
- [ ] 自动化运维决策

## 📞 技术支持

如有问题，请查看：
1. 项目日志：`logs/ai-analysis.log`
2. 配置文件：`application.yml` 
3. API文档：本文档
4. 技术讨论：项目Issues

---

**注意**: 本指南基于当前项目架构编写，随着功能迭代可能会有更新。建议定期查看最新版本。
