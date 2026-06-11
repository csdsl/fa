package com.business.fa.tokenrelay.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Token 中转站用量追踪
 * 独立于客服模块的 TokenUsageAdvisor，专门追踪中转站的用量
 */
@Service
public class UsageTracker {

    private final JdbcTemplate jdbcTemplate;

    public UsageTracker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 记录一次中转请求的用量
     */
    public void record(String relayKeyId, String clientToken, String model,
                       long promptTokens, long completionTokens) {
        jdbcTemplate.update(
                "INSERT INTO relay_usage (relay_key_id, client_token, model, prompt_tokens, completion_tokens, total_tokens) VALUES (?, ?, ?, ?, ?, ?)",
                relayKeyId, clientToken, model, promptTokens, completionTokens, promptTokens + completionTokens);
    }

    /**
     * 查询总用量
     */
    public Map<String, Object> getOverallStats() {
        return jdbcTemplate.queryForMap(
                "SELECT COUNT(*) as totalRequests, COALESCE(SUM(prompt_tokens),0) as totalPromptTokens, " +
                        "COALESCE(SUM(completion_tokens),0) as totalCompletionTokens, " +
                        "COALESCE(SUM(total_tokens),0) as totalTokens FROM relay_usage");
    }

    /**
     * 按 Key 统计
     */
    public List<Map<String, Object>> getStatsByKey() {
        return jdbcTemplate.queryForList(
                "SELECT relay_key_id as keyId, COUNT(*) as requests, " +
                        "SUM(prompt_tokens) as promptTokens, SUM(completion_tokens) as completionTokens, " +
                        "SUM(total_tokens) as totalTokens FROM relay_usage GROUP BY relay_key_id ORDER BY totalTokens DESC");
    }

    /**
     * 按天统计
     */
    public List<Map<String, Object>> getDailyStats() {
        return jdbcTemplate.queryForList(
                "SELECT DATE(create_time) as date, COUNT(*) as requests, " +
                        "SUM(prompt_tokens) as promptTokens, SUM(completion_tokens) as completionTokens, " +
                        "SUM(total_tokens) as totalTokens FROM relay_usage GROUP BY DATE(create_time) ORDER BY date DESC");
    }

    /**
     * 按模型统计
     */
    public List<Map<String, Object>> getStatsByModel() {
        return jdbcTemplate.queryForList(
                "SELECT model, COUNT(*) as requests, " +
                        "SUM(prompt_tokens) as promptTokens, SUM(completion_tokens) as completionTokens, " +
                        "SUM(total_tokens) as totalTokens FROM relay_usage GROUP BY model ORDER BY totalTokens DESC");
    }

    /**
     * 按客户端 Token 统计
     */
    public List<Map<String, Object>> getStatsByClient() {
        return jdbcTemplate.queryForList(
                "SELECT client_token as clientToken, COUNT(*) as requests, " +
                        "SUM(total_tokens) as totalTokens FROM relay_usage GROUP BY client_token ORDER BY totalTokens DESC LIMIT 50");
    }
}
