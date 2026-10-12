package com.lingfeng.interviewer.config.provider;

import com.lingfeng.interviewer.config.LlmProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Ollama（本地模型）的提供者实现。
 * 和 DeepSeekProvider 结构完全一样，区别只在名字和注入的 Bean。
 */
@Component
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
