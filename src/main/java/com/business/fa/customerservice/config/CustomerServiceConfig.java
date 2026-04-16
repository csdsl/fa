package com.business.fa.customerservice.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 客服知识库配置 — 启动时加载所有知识文档到向量库
 */
@Configuration
public class CustomerServiceConfig {

    @Value("classpath:knowledge/product-faq.txt")
    private Resource productFaq;

    @Value("classpath:knowledge/shipping-policy.txt")
    private Resource shippingPolicy;

    @Value("classpath:knowledge/refund-policy.txt")
    private Resource refundPolicy;

    @Bean
    public CommandLineRunner loadCustomerServiceKnowledge(VectorStore vectorStore) {
        return args -> {
            List<Document> allChunks = new ArrayList<>();

            // 加载每个知识文档，按空行分段，并标记来源
            allChunks.addAll(loadAndChunk(productFaq, "产品FAQ"));
            allChunks.addAll(loadAndChunk(shippingPolicy, "物流配送"));
            allChunks.addAll(loadAndChunk(refundPolicy, "退换货政策"));

            vectorStore.add(allChunks);
            System.out.println("✅ 客服知识库已加载 " + allChunks.size() + " 个文档块");
        };
    }

    private List<Document> loadAndChunk(Resource resource, String source) throws Exception {
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        return Arrays.stream(content.split("\n\n"))
                .filter(s -> !s.isBlank())
                .map(chunk -> new Document(chunk, Map.of("source", source)))
                .toList();
    }
}
