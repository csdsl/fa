package com.business.fa.customerservice.controller;

import com.business.fa.customerservice.model.SessionSummary;
import com.business.fa.customerservice.service.SummaryService;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话管理接口 — 查看对话历史 + 生成摘要
 */
@RestController
@RequestMapping("/cs/session")
public class SessionController {

    private final ChatMemoryRepository memoryRepository;
    private final SummaryService summaryService;

    public SessionController(ChatMemoryRepository memoryRepository, SummaryService summaryService) {
        this.memoryRepository = memoryRepository;
        this.summaryService = summaryService;
    }

    /**
     * 获取所有会话列表
     * 访问: http://localhost:8080/cs/session/list
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listSessions() {
        List<String> ids = memoryRepository.findConversationIds();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", ids.size(),
                "sessions", ids
        ));
    }

    /**
     * 获取某个会话的对话历史
     * 访问: http://localhost:8080/cs/session/detail?sessionId=s1
     */
    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> getSession(@RequestParam String sessionId) {
        List<Message> messages = memoryRepository.findByConversationId(sessionId);
        if (messages.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "error", "会话不存在: " + sessionId));
        }

        List<Map<String, String>> history = messages.stream()
                .map(msg -> Map.of(
                        "role", msg.getMessageType().name().toLowerCase(),
                        "content", msg.getText() != null ? msg.getText() : ""
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionId", sessionId,
                "messageCount", messages.size(),
                "messages", history
        ));
    }

    /**
     * 删除某个会话
     * 测试: curl -X DELETE http://localhost:8080/cs/session/s1
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        memoryRepository.deleteByConversationId(sessionId);
        return ResponseEntity.ok(Map.of(
                "success", true, "message", "会话已删除: " + sessionId));
    }

    /**
     * 为指定会话生成摘要
     *
     * 访问: http://localhost:8080/cs/session/summary?sessionId=s1
     * 用模型自动分析对话内容，生成一句话摘要、意图、解决情况
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> generateSummary(@RequestParam String sessionId) {
        SessionSummary summary = summaryService.generateSummary(sessionId);
        if (summary == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "error", "会话不存在或无消息: " + sessionId));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "summary", Map.of(
                        "sessionId", summary.sessionId(),
                        "summary", summary.summary(),
                        "userIntent", summary.userIntent(),
                        "resolution", summary.resolution(),
                        "messageCount", summary.messageCount(),
                        "createTime", summary.createTime().toString()
                )
        ));
    }

    /**
     * 获取所有已生成的摘要列表
     *
     * 访问: http://localhost:8080/cs/session/summaries
     */
    @GetMapping("/summaries")
    public ResponseEntity<Map<String, Object>> listSummaries() {
        List<SessionSummary> summaries = summaryService.listSummaries();
        List<Map<String, Object>> list = summaries.stream().map(s -> Map.<String, Object>of(
                "sessionId", s.sessionId(),
                "summary", s.summary(),
                "userIntent", s.userIntent(),
                "resolution", s.resolution(),
                "messageCount", s.messageCount(),
                "createTime", s.createTime().toString()
        )).toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "total", summaries.size(),
                "summaries", list
        ));
    }
}
