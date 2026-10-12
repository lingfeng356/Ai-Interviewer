package com.lingfeng.interviewer.config;

import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM 提供者（SPI 接口）。
 *
 * 这是「注册中心」能成立的关键：这里只定规矩，具体是谁来实现由实现方自己送上门。
 * 每接入一个新模型，只要写一个实现类并打上 @Component，
 * Spring 就会自动把它收集进 LlmProviderRegistry —— 注册中心本身一行都不用改。
 *
 * 对比改造前：那时候是 switch 硬编码 bean 名，加一个模型要改枚举、改 switch、加 Bean，
 * 改漏一处就报错。现在加模型 = 加一个类。
 */
public interface LlmProvider {

    /**
     * 对外标识。要小写，并且和 application.yml 里 llm.providers.xxx 的 key 一致，
     * 比如 "deepseek"、"ollama"。
     */
    String name();

    /**
     * 返回这个模型对应的 ChatClient。
     * 调用方（注册中心）会做懒加载缓存，所以这里直接每次 new 一个也没关系。
     */
    ChatClient chatClient();
}
