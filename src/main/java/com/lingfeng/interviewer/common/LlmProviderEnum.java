package com.lingfeng.interviewer.common;

public enum LlmProviderEnum {
    DEEPSEEK,
    OLLAMA;

    /**
     * 从字符串解析枚举，不合法就抛异常
     */
    public static LlmProviderEnum fromString(String name) {
        if (name == null || name.isEmpty()) {
            return DEEPSEEK;   // 默认
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("不支持的模型: " + name);
        }
    }
}
