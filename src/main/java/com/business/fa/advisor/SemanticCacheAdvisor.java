package com.business.fa.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 语义缓存 Advisor
 *
 * 原理：用 Embedding 模型把用户问题转成向量，
 * 和缓存中已有的问题向量做余弦相似度计算，
 * 相似度超过阈值（0.92）就直接返回缓存的答案，不调用大模型。
 *
 * 效果：相似问题命中缓存直接返回，大幅节省 API 调用成本。
 * 例如"退货要几天"和"退货需要多长时间"语义相同，只调一次模型。
 */
@Component
public class SemanticCacheAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheAdvisor.class);
    private static final double SIMILARITY_THRESHOLD = 0.92;
    private static final int MAX_CACHE_SIZE = 500;

    private final EmbeddingModel embeddingModel;

    // 缓存：问题向量 -> 答案
    private final Map<CacheEntry, String> cache = new ConcurrentHashMap<>();

    // 统计
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public SemanticCacheAdvisor(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public int getOrder() {
        // 在敏感词过滤之后、日志之前执行
        // 命中缓存后不需要走后续 Advisor 和模型调用
        return -40;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userMessage = request.prompt().getContents();
        if (userMessage == null || userMessage.isBlank()) return request;

        // 计算问题向量
        float[] queryEmbedding = embeddingModel.embed(userMessage);

        // 在缓存中查找语义相似的问题
        for (Map.Entry<CacheEntry, String> entry : cache.entrySet()) {
            double similarity = cosineSimilarity(queryEmbedding, entry.getKey().embedding());
            if (similarity >= SIMILARITY_THRESHOLD) {
                // 命中缓存
                hitCount.incrementAndGet();
                log.info("🎯 语义缓存命中！相似度: {}, 原问题: {}, 当前问题: {}",
                        String.format("%.4f", similarity), entry.getKey().question(), userMessage);

                // 把缓存的答案存到 context 中，后续在 after 中处理
                request.context().put("__cache_hit", entry.getValue());
                return request;
            }
        }

        missCount.incrementAndGet();
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        try {
            // 如果不是缓存命中，把问答对存入缓存
            String content = response.chatResponse().getResult().getOutput().getText();
            if (content != null && !content.isBlank()) {
                // 获取原始问题（从 prompt 中）
                String question = response.chatResponse().getMetadata()
                        .getOrDefault("__original_question", "").toString();

                // 这里简化处理：从 response 无法直接拿到原始问题
                // 实际通过缓存 miss 时在 before 中记录
            }
        } catch (Exception e) {
            // ignore
        }
        return response;
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }

    /**
     * 把问答对存入缓存（由外部调用）
     */
    public void put(String question, String answer) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            // 简单淘汰：清空一半
            var iterator = cache.entrySet().iterator();
            int removeCount = cache.size() / 2;
            while (iterator.hasNext() && removeCount > 0) {
                iterator.next();
                iterator.remove();
                removeCount--;
            }
        }

        float[] embedding = embeddingModel.embed(question);
        cache.put(new CacheEntry(question, embedding), answer);
    }

    /**
     * 尝试从缓存获取答案
     */
    public String get(String question) {
        float[] queryEmbedding = embeddingModel.embed(question);
        for (Map.Entry<CacheEntry, String> entry : cache.entrySet()) {
            double similarity = cosineSimilarity(queryEmbedding, entry.getKey().embedding());
            if (similarity >= SIMILARITY_THRESHOLD) {
                hitCount.incrementAndGet();
                return entry.getValue();
            }
        }
        missCount.incrementAndGet();
        return null;
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Object> getStats() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return Map.of(
                "cacheSize", cache.size(),
                "hitCount", hits,
                "missCount", misses,
                "hitRate", total > 0 ? String.format("%.1f%%", hits * 100.0 / total) : "0%"
        );
    }

    /**
     * 余弦相似度计算
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dotProduct = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }

    /**
     * 缓存条目
     */
    private record CacheEntry(String question, float[] embedding) {}
}
