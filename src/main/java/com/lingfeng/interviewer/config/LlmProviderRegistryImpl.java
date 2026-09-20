package com.lingfeng.interviewer.config;

import com.lingfeng.interviewer.common.LlmProviderEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmProviderRegistryImpl implements LlmProviderRegistry{

    //concurrentHashMap是线程安全的map,多线程
    private final Map<LlmProviderEnum,ChatClient> cache = new ConcurrentHashMap<>();

    private final Map<String,ChatModel> chatModelMap;

    private final LlmProperties llmProperties;

    public LlmProviderRegistryImpl(Map<String, ChatModel> chatModelMap, LlmProperties llmProperties) {
        this.chatModelMap = chatModelMap;
        this.llmProperties = llmProperties;
    }

    @Override
    public ChatClient getClient(LlmProviderEnum provider) {
        //懒加载：第一次调用时构建，之后从缓存拿
        return cache.computeIfAbsent(provider,p->{
            ChatModel model = resolveModel(p);
            return ChatClient.builder(model).build();
        });
    }

    @Override
    public ChatClient getDefault() {
        String defaultName = llmProperties.getDefaultProvider();
        return getClient(LlmProviderEnum.valueOf(defaultName.toUpperCase()));
    }

    private ChatModel resolveModel(LlmProviderEnum provider){
        String beanName = switch (provider){
            case DEEPSEEK -> "deepseekChatModel";
            case OLLAMA -> "ollamaChatModel";
        };
        ChatModel model = chatModelMap.get(beanName);
        if(model == null){
            throw new RuntimeException("未找到模型" + beanName);
        }
        return model;
    }
}
