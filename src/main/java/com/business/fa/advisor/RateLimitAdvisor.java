package com.business.fa.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流 Advisor
 *
 * 为什么需要限流？
 * - 大模型 API 按 token 计费，不限流可能被恶意刷接口导致巨额账单
 * - 模型提供商本身也有 QPS 限制，超了会报错
 *
 * 这里实现一个简单的滑动窗口限流：每个用户每分钟最多 10 次请求
 */
@Component
public class RateLimitAdvisor implements BaseAdvisor {

    private static final int MAX_REQUESTS_PER_MINUTE = 3;

    // 记录每个用户的请求次数和时间窗口
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        // 限流应该在最前面，被限流的请求不应该走后续逻辑
        return -100;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        // 从 advisor params 中获取用户标识，默认 "anonymous"
        String userId = request.context()
                .getOrDefault("userId", "anonymous").toString();

        WindowCounter counter = counters.computeIfAbsent(userId, k -> new WindowCounter());

        if (!counter.tryAcquire()) {
            throw new RuntimeException("请求过于频繁，请稍后再试（每分钟最多 " + MAX_REQUESTS_PER_MINUTE + " 次）");
        }

        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public Scheduler getScheduler() {
        return Schedulers.boundedElastic();
    }

    /**
     * 简单的滑动窗口计数器
     */
    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            // 超过1分钟，重置窗口
            if (now - windowStart > 60_000) {
                count.set(0);
                windowStart = now;
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
        }
    }
}
