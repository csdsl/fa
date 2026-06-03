package com.business.fa.customerservice.service;

import com.business.fa.customerservice.model.SessionSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 对话摘要服务
 *
 * 用轻量模型（qwen-turbo）生成对话摘要，节省成本
 * 摘要存储在内存中（生产环境存数据库）
 */
@Service
public class SummaryService {

    private final ChatClient summaryClient;
    private final ChatMemoryRepository memoryRepository;

    // 存储摘要（生产环境用数据库）
    private final Map<String, SessionSummary> summaries = new ConcurrentHashMap<>();

    public SummaryService(OpenAiChatModel chatModel, ChatMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
        this.summaryClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-turbo")  // 用便宜模型生成摘要
                        .temperature(0.0)
                        .build())
                .build();
    }

    /**
     * 为指定会话生成摘要
     */
    public SessionSummary generateSummary(String sessionId) {
        // 1. 获取对话历史
        List<Message> messages = memoryRepository.findByConversationId(sessionId);
        if (messages.isEmpty()) {
            return null;
        }

        // 2. 拼接对话内容
        String conversation = messages.stream()
                .map(msg -> {
                    String role = switch (msg.getMessageType().name()) {
                        case "USER" -> "用户";
                        case "ASSISTANT" -> "客服";
                        default -> "系统";
                    };
                    return role + "：" + msg.getText();
                })
                .collect(Collectors.joining("\n"));

        // 3. 用模型生成结构化摘要
        record SummaryResult(String summary, String userIntent, String resolution) {}

        SummaryResult result = summaryClient.prompt()
                .system("""
                        你是一个对话分析助手。根据客服对话内容，生成结构化摘要。
                        
                        要求：
                        - summary：用一句话概括对话内容（不超过50字）
                        - userIntent：用户的主要意图（如：咨询退货政策、查询订单物流、投诉服务等）
                        - resolution：解决情况，只能是以下之一：已解决、未解决、转人工
                        """)
                .user("以下是客服对话记录：\n\n" + conversation)
                .call()
                .entity(SummaryResult.class);

        // 4. 构建摘要对象并存储
        SessionSummary summary = new SessionSummary(
                sessionId,
                result != null ? result.summary() : "无法生成摘要",
                result != null ? result.userIntent() : "未知",
                result != null ? result.resolution() : "未知",
                messages.size(),
                LocalDateTime.now()
        );

        summaries.put(sessionId, summary);
        return summary;
    }

    /**
     * 获取已生成的摘要
     */
    public SessionSummary getSummary(String sessionId) {
        return summaries.get(sessionId);
    }

    /**
     * 获取所有摘要列表
     */
    public List<SessionSummary> listSummaries() {
        return List.copyOf(summaries.values());
    }
}
