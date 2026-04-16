package com.business.fa.customerservice.controller;

import com.business.fa.advisor.LoggingAdvisor;
import com.business.fa.advisor.RateLimitAdvisor;
import com.business.fa.customerservice.tool.CustomerOrderTool;
import com.business.fa.customerservice.tool.RefundTool;
import com.business.fa.customerservice.tool.TicketTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 智能客服主接口
 *
 * 整合了：
 * - RAG 知识库（产品FAQ、物流政策、退换货政策）
 * - 对话记忆（多轮对话）
 * - 工具调用（查订单、退款、转人工）
 * - 限流 + 日志监控
 */
@RestController
@RequestMapping("/cs")
public class CustomerServiceController {

    private final ChatClient chatClient;
    private final CustomerOrderTool orderTool;
    private final RefundTool refundTool;
    private final TicketTool ticketTool;

    public CustomerServiceController(ChatClient.Builder builder,
                                     ChatMemory chatMemory,
                                     VectorStore vectorStore,
                                     RateLimitAdvisor rateLimitAdvisor,
                                     LoggingAdvisor loggingAdvisor,
                                     CustomerOrderTool orderTool,
                                     RefundTool refundTool,
                                     TicketTool ticketTool) {
        this.orderTool = orderTool;
        this.refundTool = refundTool;
        this.ticketTool = ticketTool;

        this.chatClient = builder
                .defaultSystem("""
                        你是"小智"，一名专业、友好的在线客服。你服务于一家电子产品电商平台。
                        
                        【你的职责】
                        1. 回答用户关于产品、物流、退换货等政策问题（基于知识库）
                        2. 帮用户查询订单状态和物流信息
                        3. 帮用户提交退款/退货申请
                        4. 无法解决的问题转交人工客服
                        
                        【行为规则】
                        - 始终保持礼貌、耐心、专业
                        - 回答要简洁，不要长篇大论
                        - 如果需要订单号但用户没提供，主动询问
                        - 如果用户情绪激动，先安抚再解决问题
                        - 涉及退款金额等敏感操作，要跟用户确认后再执行
                        - 你不知道的事情，诚实说不知道，不要编造
                        - 超出你能力范围的问题，主动转人工客服
                        
                        【回答格式】
                        - 使用简洁的中文回答
                        - 重要信息（订单号、金额、时间）要突出显示
                        - 每次回答结尾可以问"还有其他问题吗？"
                        """)
                .defaultAdvisors(
                        rateLimitAdvisor,
                        loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(3).build())
                                .build()
                )
                .build();
    }

    /**
     * 客服对话 — 同步
     *
     * 测试场景：
     * 1. /cs/chat?message=你好&sessionId=s1
     * 2. /cs/chat?message=我想退货，订单号是ORD20240002&sessionId=s1
     * 3. /cs/chat?message=退货运费谁出？&sessionId=s1
     * 4. /cs/chat?message=帮我查一下ORD20240001的物流&sessionId=s1
     * 5. /cs/chat?message=你们支持哪些支付方式&sessionId=s1
     * 6. /cs/chat?message=我要投诉，你们的服务太差了&sessionId=s1
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        try {
            String response = chatClient.prompt()
                    .user(message)
                    .tools(orderTool, refundTool, ticketTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reply", response,
                    "sessionId", sessionId
            ));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("请求过于频繁")) {
                return ResponseEntity.status(429).body(Map.of(
                        "success", false,
                        "error", "您的消息发送过于频繁，请稍后再试"
                ));
            }
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "客服系统暂时繁忙，请稍后重试"
            ));
        }
    }

    /**
     * 客服对话 — 流式（打字机效果）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chatStream(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        return chatClient.prompt()
                .user(message)
                .tools(orderTool, refundTool, ticketTool)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content();
    }
}
