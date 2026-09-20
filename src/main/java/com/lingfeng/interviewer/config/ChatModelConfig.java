package com.lingfeng.interviewer.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {

    @Autowired
    private LlmProperties llmProperties;

    @Bean("deepseekChatModel")
    public DeepSeekChatModel deepSeekChatModel(){
        LlmProperties.ProviderConfig config = llmProperties.getProviders().get("deepseek");
        DeepSeekApi deepSeekApi = DeepSeekApi.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .build();

        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(config.getModel())
                        .build())
                .toolCallingManager(ToolCallingManager.builder().build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Bean("ollamaChatModel")
    public OllamaChatModel ollamaChatModel() {
        LlmProperties.ProviderConfig config = llmProperties.getProviders().get("ollama");

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(config.getBaseUrl())
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(config.getModel())
                        .build())
                .build();
    }
}
