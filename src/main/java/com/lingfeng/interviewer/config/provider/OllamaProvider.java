package com.lingfeng.interviewer.config.provider;

import com.lingfeng.interviewer.config.LlmProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Ollama（本地模型）的提供者实现。
 * 和 DeepSeekProvider 结构完全一样，区别只在名字和注入的 Bean。
 *
 * 加了个开关：服务器上不跑 Ollama 时，配置 llm.providers.ollama.enabled=false，
 * 这个 Bean 就不会被创建 —— 于是它不会注册进 LlmProviderRegistry，
 * 前端下拉框也不会出现这个选项。这就是 SPI 的好处：按环境动态增减提供者。
 * （不配置时 matchIfMissing 让它默认启用，本地开发不受影响）
 *
 * 注意：这里禁用的只是「用 Ollama 做对话生成」。
 * 如果 RAG 的 embedding 也用的是 Ollama（spring.ai.ollama.embedding），
 * 那服务器上还是得跑 Ollama，只是别用它来面试。
 */
@Component
@ConditionalOnProperty(
        name = "llm.providers.ollama.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OllamaProvider implements LlmProvider {

    private final ChatModel chatModel;

    public OllamaProvider(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public ChatClient chatClient() {
        return ChatClient.builder(chatModel).build();
    }
}
