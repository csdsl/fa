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
public class ChatTestController {

    private final ChatClient chatClient;

    /**
     * ChatClient.Builder 由 Spring AI 自动注入，
     * 它会根据 application.properties 的配置自动连接到千问
     */
    public ChatTestController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/chatTest")
    public String chat(@RequestParam String message) {
        return chatClient.prompt().user(message).call().content();
    }

    public String chatWithRole(@RequestParam String message) {
        return chatClient.prompt().system("你是一个资深的Java架构师，回答问题时简洁专业，并给出代码示例").user(message).call().content();
    }

}
