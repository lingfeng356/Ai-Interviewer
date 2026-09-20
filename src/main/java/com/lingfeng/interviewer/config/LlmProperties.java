package com.lingfeng.interviewer.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private String defaultProvider;
    private Map<String,ProviderConfig> providers;

    //内部类
    @Data
    public static class ProviderConfig{
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}
