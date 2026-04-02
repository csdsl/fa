package com.business.fa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 第一课：最简单的大模型调用
 *
 * 核心概念：
 * 1. ChatClient — Spring AI 提供的统一客户端，屏蔽了不同模型提供商的差异
 * 2. prompt — 你发给模型的文本指令
 * 3. content() — 模型返回的文本内容
 */
@RestController
public class ChatController {

    private final ChatClient chatClient;

    /**
     * ChatClient.Builder 由 Spring AI 自动注入，
     * 它会根据 application.properties 的配置自动连接到千问
     */
    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 最基础的调用：发送一个问题，获取回答
     * 访问: http://localhost:8080/chat?message=你好
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()    // 创建一个 prompt
                .user(message)         // 设置用户消息
                .call()                // 调用模型
                .content();            // 获取文本回复
    }

    /**
     * 带 system prompt 的调用
     * system prompt 用来设定模型的角色和行为规则
     * 访问: http://localhost:8080/chat/with-role?message=Java和Python哪个好
     */
    @GetMapping("/chat/with-role")
    public String chatWithRole(@RequestParam String message) {
        return chatClient.prompt()
                .system("你是一个资深的Java架构师，回答问题时简洁专业，并给出代码示例")
                .user(message)
                .call()
                .content();
    }

    /**
     * 第二课：流式输出
     *
     * 核心区别：
     * - call()    → 同步，等全部生成完才返回（像发短信）
     * - stream()  → 流式，边生成边返回（像打电话）
     *
     * Flux<String> 是 Reactor 的响应式类型，代表一个"数据流"
     * produces = "text/event-stream" 告诉浏览器这是 SSE（Server-Sent Events）
     *
     * 访问: http://localhost:8080/chat/stream?message=给我讲一个笑话
     */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .stream()              // 流式调用，替代 call()
                .content();            // 返回 Flux<String>，每个元素是一小段文本
    }

    /**
     * 流式 + system prompt
     * 访问: http://localhost:8080/chat/stream/with-role?message=解释一下Spring的IOC
     */
    @GetMapping(value = "/chat/stream/with-role", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStreamWithRole(@RequestParam String message) {
        return chatClient.prompt()
                .system("你是一个耐心的编程老师，用通俗易懂的方式解释技术概念，多用比喻")
                .user(message)
                .stream()
                .content();
    }
}
