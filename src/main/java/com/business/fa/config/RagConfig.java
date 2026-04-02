package com.business.fa.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 第六课：RAG 配置
 *
 * 启动时把文档加载到向量数据库中，流程：
 * 1. 读取文本文件
 * 2. 按段落分割成小块（chunk）
 * 3. 存入 VectorStore → 自动调用 Embedding 模型把文本转成向量并存储
 */
@Configuration
public class RagConfig {

    @Value("classpath:docs/company-policy.txt")
    private Resource companyPolicy;

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public CommandLineRunner loadDocs(VectorStore vectorStore) {
        return args -> {
            // 1. 读取文件内容
            String content = companyPolicy.getContentAsString(StandardCharsets.UTF_8);

            // 2. 按空行分段，每段作为一个文档块
            //    这是最简单的分块方式，适合我们这种结构清晰的文档
            List<Document> chunks = Arrays.stream(content.split("\n\n"))
                    .filter(s -> !s.isBlank())
                    .map(Document::new)
                    .toList();

            // 3. 存入向量数据库（自动做 Embedding）
            vectorStore.add(chunks);

            System.out.println("✅ 已加载 " + chunks.size() + " 个文档块到向量数据库");
        };
    }
}
