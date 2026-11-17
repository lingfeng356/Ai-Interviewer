package com.lingfeng.interviewer.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class VectorStoreConfig {

    @Autowired
    private VectorStoreProperties properties;

    /**
     * 向量库用的是独立的 PostgreSQL 数据源 ——
     * 业务数据在 MySQL，向量数据在 PG，两者互不影响。
     *
     * 连接信息全部来自 application.yml 的 vector-store.* ，
     * 换环境只改配置，不用动代码。
     */
    @Bean
    public PgVectorStore pgVectorStore(EmbeddingModel embeddingModel) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                // 维度必须和 embedding 模型一致，否则写入会报错
                .dimensions(properties.getDimensions())
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                // 首次启动会自动建 vector 扩展和 vector_store 表
                .initializeSchema(properties.isInitializeSchema())
                .build();
    }
}
