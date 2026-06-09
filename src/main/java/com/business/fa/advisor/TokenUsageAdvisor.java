package com.business.fa.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 用量统计 Advisor
 *
 * 记录每次模型调用的 token 消耗，支持：
 * - 按天统计
 * - 按会话统计
 * - 总量统计
 * - 持久化到 MySQL token_usage 表
 */
@Component
public class TokenUsageAdvisor implements BaseAdvisor {

    private final JdbcTemplate jdbcTemplate;

    public TokenUsageAdvisor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    // 按天统计
    private final Map<String, DailyUsage> dailyStats = new ConcurrentHashMap<>();

    // 按会话统计
    private final Map<String, SessionUsage> sessionStats = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return 1; // 在 LoggingAdvisor 之后执行
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        try {
            Usage usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                long prompt = usage.getPromptTokens();
                long completion = usage.getCompletionTokens();

                totalPromptTokens.addAndGet(prompt);
                totalCompletionTokens.addAndGet(completion);
                totalRequests.incrementAndGet();

                // 按天统计（内存）
                String today = LocalDate.now().toString();
                dailyStats.computeIfAbsent(today, k -> new DailyUsage())
                        .add(prompt, completion);

                // 按会话统计（内存）
                String sessionId = response.chatResponse().getMetadata()
                        .getOrDefault("conversationId", "unknown").toString();
                sessionStats.computeIfAbsent(sessionId, k -> new SessionUsage())
                        .add(prompt, completion);

                // 持久化到 MySQL（带租户ID）
                String tenantId = com.business.fa.tenant.TenantContext.getTenantId();
                jdbcTemplate.update(
                        "INSERT INTO token_usage (tenant_id, session_id, prompt_tokens, completion_tokens, total_tokens) VALUES (?, ?, ?, ?, ?)",
                        tenantId, sessionId, prompt, completion, prompt + completion);
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

    // --- 查询方法（从数据库读取） ---

    public Map<String, Object> getOverallStats() {
        // 从数据库汇总
        Map<String, Object> dbStats = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) as totalRequests, COALESCE(SUM(prompt_tokens),0) as totalPromptTokens, COALESCE(SUM(completion_tokens),0) as totalCompletionTokens, COALESCE(SUM(total_tokens),0) as totalTokens FROM token_usage");
        return Map.of(
                "totalRequests", dbStats.get("totalRequests"),
                "totalPromptTokens", dbStats.get("totalPromptTokens"),
                "totalCompletionTokens", dbStats.get("totalCompletionTokens"),
                "totalTokens", dbStats.get("totalTokens")
        );
    }

    public List<Map<String, Object>> getDailyStats() {
        return jdbcTemplate.queryForList(
                "SELECT DATE(create_time) as date, COUNT(*) as requests, SUM(prompt_tokens) as promptTokens, SUM(completion_tokens) as completionTokens, SUM(total_tokens) as totalTokens FROM token_usage GROUP BY DATE(create_time) ORDER BY date DESC");
    }

    public List<Map<String, Object>> getSessionStats() {
        return jdbcTemplate.queryForList(
                "SELECT session_id as sessionId, COUNT(*) as requests, SUM(prompt_tokens) as promptTokens, SUM(completion_tokens) as completionTokens, SUM(total_tokens) as totalTokens FROM token_usage GROUP BY session_id ORDER BY totalTokens DESC LIMIT 50");
    }

    // --- 内部类 ---

    public static class DailyUsage {
        private final AtomicLong promptTokens = new AtomicLong(0);
        private final AtomicLong completionTokens = new AtomicLong(0);
        private final AtomicLong requests = new AtomicLong(0);

        void add(long prompt, long completion) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
            requests.incrementAndGet();
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "requests", requests.get(),
                    "promptTokens", promptTokens.get(),
                    "completionTokens", completionTokens.get(),
                    "totalTokens", promptTokens.get() + completionTokens.get()
            );
        }
    }

    public static class SessionUsage {
        private final AtomicLong promptTokens = new AtomicLong(0);
        private final AtomicLong completionTokens = new AtomicLong(0);
        private final AtomicLong requests = new AtomicLong(0);

        void add(long prompt, long completion) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
            requests.incrementAndGet();
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "requests", requests.get(),
                    "promptTokens", promptTokens.get(),
                    "completionTokens", completionTokens.get(),
                    "totalTokens", promptTokens.get() + completionTokens.get()
            );
        }
    }
}
