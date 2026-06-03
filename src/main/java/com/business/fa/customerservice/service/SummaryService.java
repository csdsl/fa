package com.business.fa.customerservice.service;

import com.business.fa.customerservice.model.SessionSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话摘要服务 - MySQL 持久化
 */
@Service
public class SummaryService {

    private final ChatClient summaryClient;
    private final ChatMemoryRepository memoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public SummaryService(OpenAiChatModel chatModel,
                          ChatMemoryRepository memoryRepository,
                          JdbcTemplate jdbcTemplate) {
        this.memoryRepository = memoryRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.summaryClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-turbo")
                        .temperature(0.0)
                        .build())
                .build();
    }

    /**
     * 为指定会话生成摘要并存入数据库
     */
    public SessionSummary generateSummary(String sessionId) {
        List<Message> messages = memoryRepository.findByConversationId(sessionId);
        if (messages.isEmpty()) return null;

        String conversation = messages.stream()
                .map(msg -> {
                    String role = switch (msg.getMessageType().name()) {
                        case "USER" -> "user";
                        case "ASSISTANT" -> "agent";
                        default -> "system";
                    };
                    return role + ": " + msg.getText();
                })
                .collect(Collectors.joining("\n"));

        record SummaryResult(String summary, String userIntent, String resolution) {}

        SummaryResult result = summaryClient.prompt()
                .system("你是一个对话分析助手。根据客服对话内容生成中文摘要。summary用一句中文概括（不超过50字），userIntent用中文描述用户意图，resolution只能是：已解决、未解决、转人工。所有字段必须用中文回答。")
                .user(conversation)
                .call()
                .entity(SummaryResult.class);

        String summary = result != null ? result.summary() : "unknown";
        String intent = result != null ? result.userIntent() : "unknown";
        String resolution = result != null ? result.resolution() : "unknown";

        // 存入 MySQL（REPLACE INTO 支持重复生成）
        jdbcTemplate.update(
                "REPLACE INTO session_summary (session_id, summary, user_intent, resolution, message_count) VALUES (?, ?, ?, ?, ?)",
                sessionId, summary, intent, resolution, messages.size());

        return new SessionSummary(sessionId, summary, intent, resolution, messages.size(), LocalDateTime.now());
    }

    /**
     * 获取已生成的摘要
     */
    public SessionSummary getSummary(String sessionId) {
        List<SessionSummary> list = jdbcTemplate.query(
                "SELECT session_id, summary, user_intent, resolution, message_count, create_time FROM session_summary WHERE session_id = ?",
                (rs, rowNum) -> new SessionSummary(
                        rs.getString("session_id"),
                        rs.getString("summary"),
                        rs.getString("user_intent"),
                        rs.getString("resolution"),
                        rs.getInt("message_count"),
                        rs.getTimestamp("create_time").toLocalDateTime()
                ), sessionId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 获取所有摘要列表
     */
    public List<SessionSummary> listSummaries() {
        return jdbcTemplate.query(
                "SELECT session_id, summary, user_intent, resolution, message_count, create_time FROM session_summary ORDER BY create_time DESC",
                (rs, rowNum) -> new SessionSummary(
                        rs.getString("session_id"),
                        rs.getString("summary"),
                        rs.getString("user_intent"),
                        rs.getString("resolution"),
                        rs.getInt("message_count"),
                        rs.getTimestamp("create_time").toLocalDateTime()
                ));
    }
}
