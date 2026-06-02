package com.business.fa.customerservice.controller;

import com.business.fa.advisor.LoggingAdvisor;
import com.business.fa.customerservice.model.IntentResult;
import com.business.fa.customerservice.model.IntentResult.Intent;
import com.business.fa.customerservice.service.IntentRecognizer;
import com.business.fa.customerservice.tool.CustomerOrderTool;
import com.business.fa.customerservice.tool.RefundTool;
import com.business.fa.customerservice.tool.TicketTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能路由客服 — 多轮意图识别 + 分流处理
 *
 * 流程：
 * 1. 用户发消息
 * 2. IntentRecognizer 用轻量模型识别意图
 * 3. 根据意图路由到不同的处理策略（不同的 system prompt + 不同的工具集）
 * 4. 返回回答
 *
 * 好处：
 * - 投诉类走安抚话术 + 自动转人工
 * - 退货类带退款工具
 * - 咨询类走 RAG 知识库
 * - 订单类带查询工具
 * - 闲聊类用轻量模型，省钱
 */
@RestController
@RequestMapping("/smart")
public class SmartServiceController {

    private final IntentRecognizer intentRecognizer;
    private final ChatClient consultClient;    // 咨询专用
    private final ChatClient complaintClient;  // 投诉专用
    private final ChatClient refundClient;     // 退货专用
    private final ChatClient orderClient;      // 订单查询专用
    private final ChatClient chatClient;       // 闲聊专用

    private final CustomerOrderTool orderTool;
    private final RefundTool refundTool;
    private final TicketTool ticketTool;

    public SmartServiceController(IntentRecognizer intentRecognizer,
                                  OpenAiChatModel chatModel,
                                  ChatMemory chatMemory,
                                  VectorStore vectorStore,
                                  LoggingAdvisor loggingAdvisor,
                                  CustomerOrderTool orderTool,
                                  RefundTool refundTool,
                                  TicketTool ticketTool) {
        this.intentRecognizer = intentRecognizer;
        this.orderTool = orderTool;
        this.refundTool = refundTool;
        this.ticketTool = ticketTool;

        // 咨询客户端：RAG 知识库 + 专业话术
        this.consultClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("""
                        你是电商平台的专业客服"小智"。用户在咨询产品或政策问题。
                        基于提供的知识库内容准确回答，不要编造信息。
                        回答简洁专业，结尾问"还有其他问题吗？"
                        """)
                .defaultAdvisors(
                        loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(3).build())
                                .build()
                )
                .build();

        // 投诉客户端：安抚话术 + 转人工工具
        this.complaintClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("""
                        你是电商平台的高级客服"小智"。用户当前情绪不佳，正在投诉。
                        
                        处理原则：
                        1. 先真诚道歉，表达理解和共情
                        2. 询问具体问题，不要急于解释或辩解
                        3. 能解决的当场解决，不能解决的立即转人工客服
                        4. 语气温和、耐心，不要用机械化的模板话术
                        5. 如果用户要求赔偿或升级处理，主动创建人工工单
                        """)
                .defaultAdvisors(
                        loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        // 退货客户端：退款工具 + 政策知识
        this.refundClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("""
                        你是电商平台的售后客服"小智"。用户想退换货。
                        
                        处理流程：
                        1. 询问订单号（如果用户没提供）
                        2. 确认退货原因
                        3. 告知退换货政策（基于知识库）
                        4. 帮用户提交退款申请
                        5. 告知后续流程和时间
                        """)
                .defaultAdvisors(
                        loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(2).build())
                                .build()
                )
                .build();

        // 订单查询客户端：订单工具
        this.orderClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("""
                        你是电商平台的客服"小智"。用户想查订单或物流信息。
                        如果用户提供了订单号，直接查询。
                        如果没提供，礼貌地询问订单号。
                        """)
                .defaultAdvisors(
                        loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        // 闲聊客户端：轻量模型，省钱
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("qwen-turbo")  // 闲聊用便宜模型
                        .build())
                .defaultSystem("你是电商平台的客服小智，友好地和用户聊天。回答简短。")
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * 智能路由对话
     *
     * 测试：
     * 1. /smart/chat?message=你好&sessionId=s1                          → CHAT（闲聊）
     * 2. /smart/chat?message=你们支持哪些支付方式&sessionId=s1           → CONSULT（咨询）
     * 3. /smart/chat?message=我要退货，订单号ORD20240002&sessionId=s1   → REFUND（退货）
     * 4. /smart/chat?message=帮我查一下ORD20240001到哪了&sessionId=s1   → ORDER_QUERY（订单）
     * 5. /smart/chat?message=你们太过分了，我要投诉&sessionId=s1         → COMPLAINT（投诉）
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        try {
            // 1. 意图识别
            IntentResult intentResult = intentRecognizer.recognize(message);

            // 2. 根据意图路由
            String response = route(intentResult.intent(), message, sessionId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reply", response,
                    "intent", intentResult.intent().name(),
                    "confidence", intentResult.confidence(),
                    "intentSummary", intentResult.summary(),
                    "sessionId", sessionId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "服务暂时不可用：" + e.getMessage()
            ));
        }
    }

    /**
     * 根据意图路由到不同的处理逻辑
     */
    private String route(Intent intent, String message, String sessionId) {
        return switch (intent) {
            case CONSULT -> consultClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case COMPLAINT -> complaintClient.prompt()
                    .user(message)
                    .tools(ticketTool)  // 投诉可能需要转人工
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case REFUND -> refundClient.prompt()
                    .user(message)
                    .tools(refundTool, orderTool)  // 退货需要查订单+提交退款
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case ORDER_QUERY -> orderClient.prompt()
                    .user(message)
                    .tools(orderTool)  // 查订单工具
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case CHAT -> chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();
        };
    }
}
