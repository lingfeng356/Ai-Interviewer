package com.lingfeng.interviewer.config;

import com.lingfeng.interviewer.common.LlmProviderEnum;
import org.springframework.ai.chat.client.ChatClient;

public interface LlmProviderRegistry {
    ChatClient getClient(LlmProviderEnum provider);
    ChatClient getDefault();
}
