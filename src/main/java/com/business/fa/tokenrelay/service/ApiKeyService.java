package com.business.fa.tokenrelay.service;

import com.business.fa.tokenrelay.model.ApiKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API Key 管理服务（持久化到数据库）
 */
@Service
public class ApiKeyService {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public ApiKeyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ApiKey> rowMapper = (rs, rowNum) -> {
        ApiKey k = new ApiKey();
        k.setId(rs.getString("id"));
        k.setName(rs.getString("name"));
        k.setKey(rs.getString("api_key"));
        k.setBaseUrl(rs.getString("base_url"));
        k.setModel(rs.getString("model"));
        k.setEnabled(rs.getBoolean("enabled"));
        k.setTotalTokensUsed(rs.getLong("total_tokens_used"));
        k.setTotalRequests(rs.getLong("total_requests"));
        k.setQuotaLimit(rs.getLong("quota_limit"));
        return k;
    };

    /**
     * 添加 Key
     */
    public ApiKey addKey(ApiKey apiKey) {
        if (apiKey.getId() == null || apiKey.getId().isBlank()) {
            apiKey.setId(UUID.randomUUID().toString().substring(0, 8));
        }
        jdbcTemplate.update(
                "INSERT INTO relay_api_key (id, name, api_key, base_url, model, enabled, total_tokens_used, total_requests, quota_limit) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?)",
                apiKey.getId(), apiKey.getName(), apiKey.getKey(), apiKey.getBaseUrl(),
                apiKey.getModel() != null ? apiKey.getModel() : "*",
                apiKey.isEnabled(), apiKey.getQuotaLimit());
        return apiKey;
    }

    /**
     * 移除 Key
     */
    public void removeKey(String id) {
        jdbcTemplate.update("DELETE FROM relay_api_key WHERE id = ?", id);
    }

    /**
     * 获取所有 Key（脱敏）
     */
    public List<ApiKey> listKeys() {
        List<ApiKey> keys = jdbcTemplate.query("SELECT * FROM relay_api_key ORDER BY create_time DESC", rowMapper);
        return keys.stream().map(this::maskKey).toList();
    }

    /**
     * 轮询选出一个可用的 Key（支持按模型过滤）
     */
    public Optional<ApiKey> selectKey(String model) {
        List<ApiKey> all = jdbcTemplate.query("SELECT * FROM relay_api_key WHERE enabled = true", rowMapper);
        List<ApiKey> available = all.stream()
                .filter(k -> matchModel(k, model))
                .filter(k -> k.getQuotaLimit() == 0 || k.getTotalTokensUsed() < k.getQuotaLimit())
                .toList();

        if (available.isEmpty()) {
            return Optional.empty();
        }

        int idx = Math.abs(roundRobin.getAndIncrement()) % available.size();
        return Optional.of(available.get(idx));
    }

    /**
     * 更新 Key 用量
     */
    public void recordUsage(String keyId, long tokens) {
        jdbcTemplate.update(
                "UPDATE relay_api_key SET total_tokens_used = total_tokens_used + ?, total_requests = total_requests + 1 WHERE id = ?",
                tokens, keyId);
    }

    /**
     * 启用/禁用 Key
     */
    public void toggleKey(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE relay_api_key SET enabled = ? WHERE id = ?", enabled, id);
    }

    private boolean matchModel(ApiKey key, String model) {
        if (key.getModel() == null || key.getModel().equals("*")) return true;
        return Arrays.asList(key.getModel().split(",")).contains(model);
    }

    private ApiKey maskKey(ApiKey original) {
        ApiKey masked = new ApiKey();
        masked.setId(original.getId());
        masked.setName(original.getName());
        masked.setBaseUrl(original.getBaseUrl());
        masked.setModel(original.getModel());
        masked.setEnabled(original.isEnabled());
        masked.setTotalTokensUsed(original.getTotalTokensUsed());
        masked.setTotalRequests(original.getTotalRequests());
        masked.setQuotaLimit(original.getQuotaLimit());
        String k = original.getKey();
        masked.setKey(k != null && k.length() > 8 ? k.substring(0, 8) + "****" : "****");
        return masked;
    }
}
