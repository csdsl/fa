package com.business.fa.customerservice.controller;

import com.business.fa.advisor.LoggingAdvisor;
import com.business.fa.advisor.SensitiveWordAdvisor;
import com.business.fa.advisor.TokenUsageAdvisor;
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
 * 智能路由客服 - 多轮意图识别 + 分流处理
 */
@RestController
@RequestMapping("/smart")
public class SmartServiceController {

    private final IntentRecognizer intentRecognizer;
    private final ChatClient consultClient;
    private final ChatClient complaintClient;
    private final ChatClient refundClient;
    private final ChatClient orderClient;
    private final ChatClient chatClient;

    private final CustomerOrderTool orderTool;
    private final RefundTool refundTool;
    private final TicketTool ticketTool;

    public SmartServiceController(IntentRecognizer intentRecognizer,
                                  OpenAiChatModel chatModel,
                                  ChatMemory chatMemory,
                                  VectorStore vectorStore,
                                  LoggingAdvisor loggingAdvisor,
                                  SensitiveWordAdvisor sensitiveWordAdvisor,
                                  TokenUsageAdvisor tokenUsageAdvisor,
                                  CustomerOrderTool orderTool,
                                  RefundTool refundTool,
                                  TicketTool ticketTool) {
        this.intentRecognizer = intentRecognizer;
        this.orderTool = orderTool;
        this.refundTool = refundTool;
        this.ticketTool = ticketTool;

        this.consultClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("you are a professional customer service agent named XiaoZhi. Answer product and policy questions based on knowledge base. Be concise and professional.")
                .defaultAdvisors(
                        sensitiveWordAdvisor,
                        loggingAdvisor,
                        tokenUsageAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(3).build())
                                .build()
                )
                .build();

        this.complaintClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("you are a senior customer service agent. The user is upset and complaining. First apologize sincerely, then ask about the specific issue, escalate to human agent if needed.")
                .defaultAdvisors(
                        sensitiveWordAdvisor,
                        loggingAdvisor,
                        tokenUsageAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        this.refundClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("you are an after-sales agent. Help user with refund/return. Ask for order number if not provided, confirm reason, submit refund request.")
                .defaultAdvisors(
                        sensitiveWordAdvisor,
                        loggingAdvisor,
                        tokenUsageAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(2).build())
                                .build()
                )
                .build();

        this.orderClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-plus").build())
                .defaultSystem("you are a customer service agent. Help user query order status and logistics. Ask for order number if not provided.")
                .defaultAdvisors(
                        sensitiveWordAdvisor,
                        loggingAdvisor,
                        tokenUsageAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();

        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen-turbo").build())
                .defaultSystem("you are a friendly customer service chatbot named XiaoZhi. Keep answers short.")
                .defaultAdvisors(
                        sensitiveWordAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        try {
            IntentResult intentResult = intentRecognizer.recognize(message);
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
                    "error", "service unavailable: " + e.getMessage()
            ));
        }
    }

    private String route(Intent intent, String message, String sessionId) {
        return switch (intent) {
            case CONSULT -> consultClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case COMPLAINT -> complaintClient.prompt()
                    .user(message)
                    .tools(ticketTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case REFUND -> refundClient.prompt()
                    .user(message)
                    .tools(refundTool, orderTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            case ORDER_QUERY -> orderClient.prompt()
                    .user(message)
                    .tools(orderTool)
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
