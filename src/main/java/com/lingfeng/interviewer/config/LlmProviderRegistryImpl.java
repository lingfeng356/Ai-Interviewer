package com.lingfeng.interviewer.config;

import com.lingfeng.interviewer.common.BusinessException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LLM 注册中心实现（SPI 版）。
 *
 * 改造前：一堆 switch 硬编码 bean 名，新增一个模型要改 3 个地方（枚举 / switch / Bean）。
 * 改造后：所有 LlmProvider 实现由 Spring 自动注入进来，这里一行都不用动。
 *
 * 这就是「注册中心」和「硬编码分发器」的区别 ——
 * 新的提供者能自己注册进来，中心不需要改。
 */
@Component
public class LlmProviderRegistryImpl implements LlmProviderRegistry {

    /**
     * 模型名 -> 提供者。
     * Spring 启动时会把容器里所有 LlmProvider 实现收集成 List 传进构造器，
     * 我们这里是"建花名册"，不是"手工登记"。
     */
    private final Map<String, LlmProvider> providerMap;

    /**
     * 模型名 -> 已经构建好的 ChatClient。
     * ChatClient 是无状态的，所以构建一次缓存起来复用即可（懒加载）。
     * 用 ConcurrentHashMap 是因为它会被多线程并发访问。
     */
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    private final LlmProperties llmProperties;

    public LlmProviderRegistryImpl(List<LlmProvider> providers, LlmProperties llmProperties) {
        this.llmProperties = llmProperties;

        // 用每个实现自己声明的 name() 当 key，建成一张表
        // 第三个参数是"key 冲突时怎么办"：保留先注册的那个（正常不会冲突）
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(
                        LlmProvider::name,
                        Function.identity(),
                        (first, second) -> first));
    }

    @Override
    public ChatClient getClient(String providerName) {
        String key = normalize(providerName);

        LlmProvider provider = providerMap.get(key);
        if (provider == null) {
            // 这里抛 BusinessException（400），前端能拿到友好提示
            throw new BusinessException("不支持的模型: " + providerName);
        }

        // computeIfAbsent：第一次用到才构建，之后直接拿缓存
        return clientCache.computeIfAbsent(key, k -> provider.chatClient());
    }

    @Override
    public ChatClient getDefault() {
        return getClient(llmProperties.getDefaultProvider());
    }

    @Override
    public Set<String> availableProviders() {
        return providerMap.keySet();
    }

    /**
     * 去空格 + 转小写。
     * 这样前端传 "DeepSeek"、" deepseek " 都能对上，不用调用方自己处理。
     */
    private String normalize(String providerName) {
        return providerName == null ? "" : providerName.trim().toLowerCase();
    }
}
