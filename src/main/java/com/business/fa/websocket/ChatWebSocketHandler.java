package com.business.fa.websocket;

import com.business.fa.advisor.SemanticCacheAdvisor;
import com.business.fa.customerservice.model.IntentResult;
import com.business.fa.customerservice.service.IntentRecognizer;
import com.business.fa.customerservice.tool.CustomerOrderTool;
import com.business.fa.customerservice.tool.RefundTool;
import com.business.fa.customerservice.tool.TicketTool;
import com.business.fa.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 聊天处理器
 *
 * 支持流式输出：模型生成的每个 token 实时推送到前端
 * 消息格式（JSON）：
 * - 客户端发送：{"message": "你好", "sessionId": "s1", "tenantId": "shop1"}
 * - 服务端推送：{"type": "token", "content": "你"}  -- 流式token
 *              {"type": "done", "intent": "CHAT"}   -- 完成
 *              {"type": "error", "content": "..."}  -- 错误
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final IntentRecognizer intentRecognizer;
    private final SemanticCacheAdvisor semanticCache;
    private final ChatClient chatClient;
    private final CustomerOrderTool orderTool;
    private final RefundTool refundTool;
    private final TicketTool ticketTool;

    // 记录活跃连接
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(IntentRecognizer intentRecognizer,
                                SemanticCacheAdvisor semanticCache,
                                OpenAiChatModel chatModel,
                                ChatMemory chatMemory,
                                CustomerOrderTool orderTool,
                                RefundTool refundTool,
                                TicketTool ticketTool) {
        this.intentRecognizer = intentRecognizer;
        this.semanticCache = semanticCache;
        this.orderTool = orderTool;
        this.refundTool = refundTool;
        this.ticketTool = ticketTool;

        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("you are a helpful customer service agent named XiaoZhi. Be concise and friendly. Answer in Chinese.")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        // 简单解析 JSON（避免引入额外依赖）
        String userMessage = extractField(payload, "message");
        String sessionId = extractField(payload, "sessionId");
        String tenantId = extractField(payload, "tenantId");

        if (userMessage == null || userMessage.isBlank()) return;
        if (sessionId == null) sessionId = session.getId();
        if (tenantId != null) TenantContext.setTenantId(tenantId);

        try {
            // 1. 检查语义缓存
            String cached = semanticCache.get(userMessage);
            if (cached != null) {
                sendMessage(session, "{\"type\":\"token\",\"content\":" + jsonEscape(cached) + "}");
                sendMessage(session, "{\"type\":\"done\",\"intent\":\"CACHED\",\"cached\":true}");
                return;
            }

            // 2. 意图识别
            IntentResult intent = intentRecognizer.recognize(userMessage);

            // 3. 流式调用模型
            final String sid = sessionId;
            Flux<String> flux = chatClient.prompt()
                    .user(userMessage)
                    .tools(orderTool, refundTool, ticketTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sid))
                    .stream()
                    .content();

            StringBuilder fullResponse = new StringBuilder();
            flux.subscribe(
                    token -> {
                        fullResponse.append(token);
                        sendMessage(session, "{\"type\":\"token\",\"content\":" + jsonEscape(token) + "}");
                    },
                    error -> {
                        sendMessage(session, "{\"type\":\"error\",\"content\":" + jsonEscape(error.getMessage()) + "}");
                    },
                    () -> {
                        // 完成，缓存结果
                        if (intent.intent() != IntentResult.Intent.ORDER_QUERY) {
                            semanticCache.put(userMessage, fullResponse.toString());
                        }
                        sendMessage(session, "{\"type\":\"done\",\"intent\":\"" + intent.intent().name() + "\",\"cached\":false}");
                    }
            );
        } catch (Exception e) {
            sendMessage(session, "{\"type\":\"error\",\"content\":" + jsonEscape(e.getMessage()) + "}");
        } finally {
            TenantContext.clear();
        }
    }

    private void sendMessage(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (IOException e) {
            log.warn("WebSocket send failed: {}", e.getMessage());
        }
    }

    private String jsonEscape(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf("\"", colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
