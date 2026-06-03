package com.business.fa.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 向量数据库配置 - 文件持久化
 *
 * SimpleVectorStore 支持 save/load 到本地文件
 * 启动时自动加载已有向量数据，不需要重新 Embedding
 */
@Configuration
public class RagConfig {

    private static final String VECTOR_STORE_FILE = "data/vector-store.json";

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        // 启动时加载已有的向量数据
        File file = new File(VECTOR_STORE_FILE);
        if (file.exists()) {
            store.load(file);
        }

        return store;
    }

    /**
     * 提供保存方法，供其他组件调用
     */
    @Bean
    public VectorStorePersistence vectorStorePersistence(VectorStore vectorStore) {
        return new VectorStorePersistence((SimpleVectorStore) vectorStore, VECTOR_STORE_FILE);
    }
}
