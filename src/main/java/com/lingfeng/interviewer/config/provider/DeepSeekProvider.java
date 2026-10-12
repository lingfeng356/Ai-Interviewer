package com.lingfeng.interviewer.config.provider;

import com.lingfeng.interviewer.config.LlmProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 的提供者实现。
 *
 * 打上 @Component 就相当于"自己来注册中心报到"，
 * 注册中心不需要知道有这个类，也能拿到它。
 */
@Component
public class DeepSeekProvider implements LlmProvider {

    private final ChatModel chatModel;

    /**
     * 构造器注入。
     * @Qualifier 指定拿 ChatModelConfig 里那个名为 deepseekChatModel 的 Bean
     * （Spring AI 自己也可能自动配置出同类型的 Bean，所以这里必须点名）。
     */
    public DeepSeekProvider(@Qualifier("deepseekChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public ChatClient chatClient() {
        return ChatClient.builder(chatModel).build();
    }
}
