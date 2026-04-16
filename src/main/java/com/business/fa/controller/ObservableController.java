package com.business.fa.controller;

import com.business.fa.advisor.LoggingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第十一课：带可观测性的对话接口
 *
 * 每次调用都会在控制台打印：
 * - 用户问了什么
 * - 模型答了什么
 * - 花了多少 token（输入 + 输出）
 * - 耗时多久
 *
 * 访问: http://localhost:8080/observe/chat?message=什么是Spring Boot
 * 然后看控制台日志输出
 */
@RestController
@RequestMapping("/observe")
public class ObservableController {

    private final ChatClient chatClient;

    public ObservableController(ChatClient.Builder builder, LoggingAdvisor loggingAdvisor) {
        this.chatClient = builder
                .defaultAdvisors(loggingAdvisor)
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
