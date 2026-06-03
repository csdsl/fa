package com.business.fa.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatMemory 配置 — 文件持久化 + 滑动窗口
 *
 * 对话历史存储在 data/memory/ 目录下，每个会话一个 JSON 文件
 * 重启后自动加载，不会丢失对话记录
 *
 * 滑动窗口 maxMessages(10) 控制发给模型的消息数量（节省 token）
 * 但文件中会保存完整的对话历史（用于回溯查看）
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new FileChatMemoryRepository("data/memory");
    }

    @Bean
    @Primary
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)   // 发给模型的最多10条，节省 token
                .build();
    }
}
