package com.lingfeng.interviewer.config;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Set;

/**
 * LLM 注册中心：按名字拿 ChatClient，业务层不用关心底层是哪个模型。
 */
public interface LlmProviderRegistry {

    /**
     * 按名字取 ChatClient。名字不区分大小写、会自动去空格。
     * 传入未注册的模型会抛 BusinessException。
     */
    ChatClient getClient(String providerName);

    /** 配置里 llm.default-provider 指定的那个模型 */
    ChatClient getDefault();

    /** 所有已注册的模型名，给前端下拉框动态展示用 */
    Set<String> availableProviders();
}
