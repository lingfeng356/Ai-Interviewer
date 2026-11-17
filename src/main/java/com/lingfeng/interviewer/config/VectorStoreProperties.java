package com.lingfeng.interviewer.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量库（PostgreSQL + pgvector）的连接配置。
 *
 * 原来这些值是硬编码在 VectorStoreConfig 里的，换环境就得改 Java 代码、重新打包。
 * 挪到配置文件之后，本地 / 测试 / 生产各用各的 yml（或环境变量）就行。
 *
 * 对应 application.yml 里的：
 *   vector-store:
 *     url: jdbc:postgresql://localhost:5432/ai_interviewer
 *     username: postgres
 *     password: xxx
 *     dimensions: 768
 */
@Data
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "vector-store")
public class VectorStoreProperties {

    /** PostgreSQL 连接串 */
    private String url = "jdbc:postgresql://localhost:5432/ai_interviewer";

    private String username = "postgres";

    private String password;

    /**
     * 向量维度。
     * 必须和 embedding 模型的输出维度一致 —— 现在用的 shaw/dmeta-embedding-zh 是 768 维，
     * 换 embedding 模型时这里和 pgvector 里的表都要一起改。
     */
    private int dimensions = 768;

    /**
     * 启动时是否自动建表 / 建索引。
     * 第一次部署方便，之后建议关掉（表结构已经存在了，不需要每次启动都去检查）。
     */
    private boolean initializeSchema = true;
}
