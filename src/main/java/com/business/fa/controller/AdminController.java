package com.business.fa.controller;

import com.business.fa.advisor.SemanticCacheAdvisor;
import com.business.fa.advisor.TokenUsageAdvisor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台接口 - Token 用量统计 + 缓存统计
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final TokenUsageAdvisor tokenUsageAdvisor;
    private final SemanticCacheAdvisor semanticCacheAdvisor;

    public AdminController(TokenUsageAdvisor tokenUsageAdvisor, SemanticCacheAdvisor semanticCacheAdvisor) {
        this.tokenUsageAdvisor = tokenUsageAdvisor;
        this.semanticCacheAdvisor = semanticCacheAdvisor;
    }

    /**
     * 总体 Token 用量
     */
    @GetMapping("/token-usage")
    public ResponseEntity<Map<String, Object>> tokenUsage() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "overall", tokenUsageAdvisor.getOverallStats(),
                "daily", tokenUsageAdvisor.getDailyStats(),
                "sessions", tokenUsageAdvisor.getSessionStats()
        ));
    }

    /**
     * 按天统计
     */
    @GetMapping("/token-usage/daily")
    public ResponseEntity<Map<String, Object>> dailyUsage() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "daily", tokenUsageAdvisor.getDailyStats()
        ));
    }

    /**
     * 按会话统计
     */
    @GetMapping("/token-usage/sessions")
    public ResponseEntity<Map<String, Object>> sessionUsage() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessions", tokenUsageAdvisor.getSessionStats()
        ));
    }

    /**
     * 语义缓存统计
     * GET http://localhost:8080/admin/cache-stats
     */
    @GetMapping("/cache-stats")
    public ResponseEntity<Map<String, Object>> cacheStats() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "cache", semanticCacheAdvisor.getStats()
        ));
    }
}
