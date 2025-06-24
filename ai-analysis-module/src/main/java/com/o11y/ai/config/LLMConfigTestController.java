package com.o11y.ai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置测试控制器
 */
@RestController
@RequestMapping("/api/ai-analysis/llm")
public class LLMConfigTestController {

    @Autowired
    private AiAnalysisProperties properties;

    /**
     * 获取当前 LLM 配置信息
     */
    @GetMapping("/config")
    public Map<String, Object> getLLMConfig() {
        Map<String, Object> config = new HashMap<>();

        AiAnalysisProperties.Llm llm = properties.getLlm();
        config.put("provider", llm.getProvider());
        config.put("enabled", llm.isEnabled());
        config.put("fallbackEnabled", llm.isFallbackEnabled());

        // 根据当前提供商返回相应配置（不暴露敏感信息）
        switch (llm.getProvider().toLowerCase()) {
            case "openai":
                Map<String, Object> openaiConfig = new HashMap<>();
                openaiConfig.put("baseUrl", llm.getOpenai().getBaseUrl());
                openaiConfig.put("model", llm.getOpenai().getModel());
                openaiConfig.put("timeout", llm.getOpenai().getTimeout());
                openaiConfig.put("maxTokens", llm.getOpenai().getMaxTokens());
                openaiConfig.put("temperature", llm.getOpenai().getTemperature());
                openaiConfig.put("hasApiKey",
                        llm.getOpenai().getApiKey() != null && !llm.getOpenai().getApiKey().trim().isEmpty());
                config.put("openai", openaiConfig);
                break;

            case "azure":
                Map<String, Object> azureConfig = new HashMap<>();
                azureConfig.put("endpoint", llm.getAzure().getEndpoint());
                azureConfig.put("deploymentName", llm.getAzure().getDeploymentName());
                azureConfig.put("apiVersion", llm.getAzure().getApiVersion());
                azureConfig.put("hasApiKey",
                        llm.getAzure().getApiKey() != null && !llm.getAzure().getApiKey().trim().isEmpty());
                config.put("azure", azureConfig);
                break;

            case "deepseek":
                Map<String, Object> deepseekConfig = new HashMap<>();
                deepseekConfig.put("baseUrl", llm.getDeepseek().getBaseUrl());
                deepseekConfig.put("model", llm.getDeepseek().getModel());
                deepseekConfig.put("timeout", llm.getDeepseek().getTimeout());
                deepseekConfig.put("maxTokens", llm.getDeepseek().getMaxTokens());
                deepseekConfig.put("temperature", llm.getDeepseek().getTemperature());
                deepseekConfig.put("hasApiKey",
                        llm.getDeepseek().getApiKey() != null && !llm.getDeepseek().getApiKey().trim().isEmpty());
                config.put("deepseek", deepseekConfig);
                break;

            case "ollama":
            case "local":
                Map<String, Object> localConfig = new HashMap<>();
                localConfig.put("url", llm.getLocal().getUrl());
                localConfig.put("model", llm.getLocal().getModel());
                localConfig.put("timeout", llm.getLocal().getTimeout());
                config.put("local", localConfig);
                break;
        }

        return config;
    }

    /**
     * 测试 LLM 连接
     */
    @GetMapping("/test")
    public Map<String, Object> testLLMConnection() {
        Map<String, Object> result = new HashMap<>();

        try {
            AiAnalysisProperties.Llm llm = properties.getLlm();
            result.put("provider", llm.getProvider());
            result.put("enabled", llm.isEnabled());

            // 简单的连接测试
            switch (llm.getProvider().toLowerCase()) {
                case "deepseek":
                    result.put("status", "configured");
                    result.put("message", "DeepSeek 配置已加载");
                    result.put("hasApiKey",
                            llm.getDeepseek().getApiKey() != null && !llm.getDeepseek().getApiKey().trim().isEmpty());
                    result.put("baseUrl", llm.getDeepseek().getBaseUrl());
                    result.put("model", llm.getDeepseek().getModel());
                    break;
                case "openai":
                    result.put("status", "configured");
                    result.put("message", "OpenAI 配置已加载");
                    result.put("hasApiKey",
                            llm.getOpenai().getApiKey() != null && !llm.getOpenai().getApiKey().trim().isEmpty());
                    break;
                case "azure":
                    result.put("status", "configured");
                    result.put("message", "Azure OpenAI 配置已加载");
                    result.put("hasApiKey",
                            llm.getAzure().getApiKey() != null && !llm.getAzure().getApiKey().trim().isEmpty());
                    break;
                case "ollama":
                case "local":
                    result.put("status", "configured");
                    result.put("message", "本地 LLM 配置已加载");
                    result.put("url", llm.getLocal().getUrl());
                    break;
                default:
                    result.put("status", "unknown");
                    result.put("message", "未知的 LLM 提供商");
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "配置读取失败: " + e.getMessage());
        }

        return result;
    }
}
