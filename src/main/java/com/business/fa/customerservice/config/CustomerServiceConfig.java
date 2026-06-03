package com.business.fa.customerservice.config;

import com.business.fa.config.VectorStorePersistence;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 客服知识库配置
 *
 * 启动时检查向量库文件是否存在：
 * - 存在：跳过加载（数据已持久化）
 * - 不存在：加载默认知识文档并持久化
 */
@Configuration
public class CustomerServiceConfig {

    private static final String VECTOR_STORE_FILE = "data/vector-store.json";

    @Value("classpath:knowledge/product-faq.txt")
    private Resource productFaq;

    @Value("classpath:knowledge/shipping-policy.txt")
    private Resource shippingPolicy;

    @Value("classpath:knowledge/refund-policy.txt")
    private Resource refundPolicy;

    @Bean
    public CommandLineRunner loadCustomerServiceKnowledge(VectorStore vectorStore,
                                                          VectorStorePersistence persistence) {
        return args -> {
            // 如果向量库文件已存在，说明数据已持久化，跳过加载
            File file = new File(VECTOR_STORE_FILE);
            if (file.exists() && file.length() > 100) {
                System.out.println("✅ 向量库已从文件加载，跳过默认知识导入");
                return;
            }

            // 首次启动，加载默认知识
            List<Document> allChunks = new ArrayList<>();
            allChunks.addAll(loadAndChunk(productFaq, "product-faq"));
            allChunks.addAll(loadAndChunk(shippingPolicy, "shipping"));
            allChunks.addAll(loadAndChunk(refundPolicy, "refund"));

            vectorStore.add(allChunks);
            persistence.save();
            System.out.println("✅ 客服知识库已加载 " + allChunks.size() + " 个文档块并持久化");
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
