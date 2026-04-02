package com.business.fa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 第三课：对话记忆（Chat Memory）
 *
 * 核心概念：
 * 1. ChatMemory — Spring AI 自动配置的记忆组件
 *    引入 spring-ai-starter-model-chat-memory 后，Spring Boot 会自动创建：
 *    - InMemoryChatMemoryRepository（内存存储）
 *    - MessageWindowChatMemory（滑动窗口策略，默认保留最近的消息）
 *
 * 2. MessageChatMemoryAdvisor — Advisor（拦截器），自动把历史消息塞进 prompt
 * 3. conversationId — 会话ID，区分不同用户/不同对话
 *
 * 工作原理：
 *   用户发消息 → Advisor 从 ChatMemory 取出历史 → 拼到 prompt 里一起发给模型
 *   → 模型看到完整上下文 → 返回回复 → Advisor 把这轮对话存回 ChatMemory
 */
@RestController
@RequestMapping("/memory")
public class MemoryChatController {

    private final ChatClient chatClient;

    /**
     * ChatMemory 由 Spring AI 自动注入（starter 自动配置）
     */
    public MemoryChatController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 带记忆的对话
     *
     * 测试步骤：
     * 1. http://localhost:8080/memory/chat?message=我叫小明&conversationId=user1
     * 2. http://localhost:8080/memory/chat?message=我叫什么名字？&conversationId=user1
     *    → 模型能回答"小明"
     * 3. http://localhost:8080/memory/chat?message=我叫什么名字？&conversationId=user2
     *    → 模型不知道，因为 user2 是另一个会话
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 带记忆的流式对话
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String message,
                                   @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
