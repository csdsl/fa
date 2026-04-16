package com.business.fa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第十课：多模型切换控制器
 *
 * 三种切换策略：
 * 1. 手动指定 — 用户选择用哪个模型
 * 2. 自动路由 — 根据问题复杂度自动选择
 * 3. 降级兜底 — 强模型失败时降级到弱模型
 */
@RestController
@RequestMapping("/multi")
public class MultiModelController {

    private final ChatClient lightClient;
    private final ChatClient powerClient;

    public MultiModelController(@Qualifier("lightClient") ChatClient lightClient,
                                @Qualifier("powerClient") ChatClient powerClient) {
        this.lightClient = lightClient;
        this.powerClient = powerClient;
    }

    // ========== 策略一：手动指定模型 ==========

    /**
     * 用户通过参数选择模型
     *
     * 访问: http://localhost:8080/multi/chat?message=你好&model=light
     * 访问: http://localhost:8080/multi/chat?message=解释Java的GC原理&model=power
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "light") String model) {
        ChatClient client = "power".equals(model) ? powerClient : lightClient;
        return client.prompt()
                .user(message)
                .call()
                .content();
    }

    // ========== 策略二：自动路由 ==========

    /**
     * 根据问题长度和关键词自动选择模型
     * 实际项目中可以用更复杂的分类逻辑（甚至用一个小模型来分类）
     *
     * 访问: http://localhost:8080/multi/auto?message=你好
     * 访问: http://localhost:8080/multi/auto?message=请用Java实现一个线程安全的LRU缓存
     */
    @GetMapping("/auto")
    public String autoRoute(@RequestParam String message) {
        ChatClient client = needsPowerModel(message) ? powerClient : lightClient;
        String modelUsed = client == powerClient ? "qwen-plus" : "qwen-turbo";

        String response = client.prompt()
                .user(message)
                .call()
                .content();

        return "[模型: " + modelUsed + "]\n\n" + response;
    }

    /**
     * 简单的路由判断逻辑
     * 实际项目中可以更复杂：用分类模型、关键词权重、用户等级等
     */
    private boolean needsPowerModel(String message) {
        // 长问题通常更复杂
        if (message.length() > 50) return true;

        // 包含技术关键词的用强模型
        String[] techKeywords = {"代码", "实现", "算法", "架构", "设计模式",
                "原理", "源码", "性能", "优化", "debug"};
        for (String keyword : techKeywords) {
            if (message.contains(keyword)) return true;
        }

        return false;
    }

    // ========== 策略三：降级兜底 ==========

    /**
     * 先用强模型，失败了降级到弱模型
     *
     * 访问: http://localhost:8080/multi/fallback?message=解释量子计算
     */
    @GetMapping("/fallback")
    public String fallback(@RequestParam String message) {
        try {
            return "[qwen-plus] " + powerClient.prompt()
                    .user(message)
                    .call()
                    .content();
        } catch (Exception e) {
            // 强模型失败（限流、超时等），降级到轻量模型
            return "[降级到 qwen-turbo] " + lightClient.prompt()
                    .user(message)
                    .call()
                    .content();
        }
    }
}
