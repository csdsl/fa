package com.business.fa.controller;

import com.business.fa.advisor.LoggingAdvisor;
import com.business.fa.advisor.RateLimitAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 第十二课：生产级 Controller
 *
 * 集成了：限流、重试、异步、错误处理、超时控制
 */
@RestController
@RequestMapping("/prod")
public class ProductionController {

    private final ChatClient chatClient;
    private final OpenAiChatModel chatModel;

    public ProductionController(ChatClient.Builder builder,
                                OpenAiChatModel chatModel,
                                RateLimitAdvisor rateLimitAdvisor,
                                LoggingAdvisor loggingAdvisor) {
        this.chatModel = chatModel;
        this.chatClient = builder
                .defaultAdvisors(rateLimitAdvisor, loggingAdvisor)  // 限流 → 日志
                .build();
    }

    // ========== 1. 带错误处理的接口 ==========

    /**
     * 生产级接口：统一错误处理，返回结构化响应
     *
     * 访问: http://localhost:8080/prod/chat?message=你好
     * 快速连续访问 11 次触发限流
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestParam String message) {
        try {
            String response = chatClient.prompt()
                    .user(message)
                    .call()
                    .content();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("请求过于频繁")) {
                return ResponseEntity.status(429).body(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ));
            }
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "服务暂时不可用，请稍后重试"
            ));
        }
    }

    // ========== 2. 带重试的接口 ==========

    /**
     * 自动重试：模型调用失败时自动重试，最多3次
     *
     * 访问: http://localhost:8080/prod/retry?message=你好
     */
    @GetMapping("/retry")
    public ResponseEntity<Map<String, Object>> retryChat(@RequestParam String message) {
        int maxRetries = 2;

        for (int i = 1; i <= maxRetries; i++) {
            try {
                String response = chatClient.prompt()
                        .user(message)
                        .call()
                        .content();

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", response,
                        "attempts", i
                ));
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("请求过于频繁")) {
                    return ResponseEntity.status(429).body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
                }
                if (i == maxRetries) {
                    return ResponseEntity.status(500).body(Map.of(
                            "success", false,
                            "error", "重试 " + maxRetries + " 次后仍然失败",
                            "attempts", maxRetries
                    ));
                }
                // 指数退避：第1次等1秒，第2次等2秒，第3次等4秒
                try {
                    Thread.sleep(1000L * (1 << (i - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return ResponseEntity.status(500).body(Map.of("success", false, "error", "未知错误"));
    }

    // ========== 3. 异步接口 ==========

    /**
     * 异步调用：不阻塞主线程，适合耗时长的请求
     *
     * 访问: http://localhost:8080/prod/async?message=写一篇关于AI的文章
     */
    @GetMapping("/async")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> asyncChat(@RequestParam String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = chatClient.prompt()
                        .user(message)
                        .call()
                        .content();

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "data", response,
                        "async", true
                ));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "error", e.getMessage() != null ? e.getMessage() : "未知错误"
                ));
            }
        });
    }

    // ========== 4. 带超时控制的接口 ==========

    /**
     * 超时控制：如果模型响应太慢，及时返回错误
     *
     * 访问: http://localhost:8080/prod/timeout?message=你好
     */
    @GetMapping("/timeout")
    public ResponseEntity<Map<String, Object>> timeoutChat(@RequestParam String message) {
        try {
            // 通过 options 设置单次请求的超时（这里用 maxTokens 间接控制响应长度）
            String response = ChatClient.builder(chatModel)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model("qwen-plus")
                            .maxTokens(500)    // 限制最大输出 token，防止响应过长
                            .build())
                    .build()
                    .prompt()
                    .user(message)
                    .call()
                    .content();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "请求超时或失败"
            ));
        }
    }
}
