package com.business.fa.controller;

import com.business.fa.model.BookInfo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 第四课：结构化输出（Structured Output）
 *
 * 核心概念：
 * 1. entity(Class) — 告诉 Spring AI 你期望的返回类型，它会：
 *    a. 自动在 prompt 里追加格式要求（让模型输出 JSON）
 *    b. 自动把模型返回的 JSON 反序列化成 Java 对象
 *
 * 2. 这比你自己写"请返回JSON格式"然后手动解析要可靠得多
 *
 * 3. 支持单个对象、List、Map 等常见类型
 */
@RestController
@RequestMapping("/structured")
public class StructuredController {

    private final ChatClient chatClient;

    public StructuredController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 返回单个对象
     * 访问: http://localhost:8080/structured/book?name=三体
     */
    @GetMapping("/book")
    public BookInfo getBookInfo(@RequestParam String name) {
        return chatClient.prompt()
                .user("介绍一下这本书：" + name)
                .call()
                .entity(BookInfo.class);   // 关键：指定返回类型
    }

    /**
     * 返回列表
     * 访问: http://localhost:8080/structured/books?topic=科幻
     */
    @GetMapping("/books")
    public List<BookInfo> getBooks(@RequestParam String topic) {
        return chatClient.prompt()
                .user("推荐3本关于" + topic + "的书")
                .call()
                .entity(new org.springframework.core.ParameterizedTypeReference<List<BookInfo>>() {});
    }
}
