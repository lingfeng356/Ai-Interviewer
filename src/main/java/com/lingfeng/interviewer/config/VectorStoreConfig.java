package com.lingfeng.interviewer.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    @Bean
    public PgVectorStore pgVectorStore(EmbeddingModel embeddingModel){
        // PostgreSQL 独立数据源
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost:5432/ai_interviewer");
        dataSource.setUsername("postgres");
        dataSource.setPassword("123456");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(768)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
