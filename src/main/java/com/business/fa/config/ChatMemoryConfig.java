package com.business.fa.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatMemory 配置 — 控制历史消息窗口大小
 *
 * maxMessages(10) 表示每个会话只保留最近 10 条消息（约5轮对话）
 * 超出的旧消息不会发给模型，从而节省 token
 *
 * 默认的自动配置没有限制，对话越长 token 消耗越大
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    @Primary
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)   // 只保留最近10条消息
                .build();
    }
}
