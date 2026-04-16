package com.business.fa.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 第十课：多模型配置
 *
 * 核心思想：不同场景用不同模型
 * - 简单任务（闲聊、翻译）→ qwen-turbo（便宜、快）
 * - 复杂任务（推理、代码）→ qwen-plus（贵、强）
 *
 * Spring AI 的 ChatClient 支持在运行时覆盖模型参数，
 * 所以我们可以用同一个 ChatModel，通过 options 切换模型。
 */
@Configuration
public class MultiModelConfig {

    /**
     * 轻量级 ChatClient — 用 qwen-turbo
     * 适合简单任务：闲聊、翻译、摘要
     */
    @Bean("lightClient")
    public ChatClient lightClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-turbo")
                        .temperature(0.7)    // 较高温度，回答更有创意
                        .build())
                .defaultSystem("你是一个轻松友好的助手，回答简洁明了。")
                .build();
    }

    /**
     * 强力 ChatClient — 用 qwen-plus
     * 适合复杂任务：代码生成、逻辑推理、专业分析
     */
    @Bean("powerClient")
    public ChatClient powerClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-plus")
                        .temperature(0.1)    // 低温度，回答更精确
                        .build())
                .defaultSystem("你是一个严谨的技术专家，回答要准确、详细、有深度。")
                .build();
    }
}
