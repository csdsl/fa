package com.business.fa.controller;

import com.business.fa.function.OrderService;
import com.business.fa.function.WeatherService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 第七课：AI Agent — 综合实战
 *
 * 这个 Agent 同时具备：
 * 1. 记忆 — 记住对话上下文（MessageChatMemoryAdvisor）
 * 2. 工具调用 — 查天气、查订单（Function Calling）
 * 3. 知识检索 — 查公司政策（RAG）
 * 4. 角色设定 — system prompt 定义行为
 *
 * 模型会自主判断：
 * - 用户问天气 → 调用 WeatherService
 * - 用户问订单 → 调用 OrderService
 * - 用户问公司政策 → 从向量库检索
 * - 用户闲聊 → 直接回答
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final ChatClient chatClient;
    private final WeatherService weatherService;
    private final OrderService orderService;

    public AgentController(ChatClient.Builder builder,
                           ChatMemory chatMemory,
                           VectorStore vectorStore,
                           WeatherService weatherService,
                           OrderService orderService) {
        this.weatherService = weatherService;
        this.orderService = orderService;

        this.chatClient = builder
                .defaultSystem("""
                        你是一个智能客服助手，名字叫小智。你的职责：
                        1. 回答用户关于公司政策的问题（年假、报销、技术栈等）
                        2. 帮用户查询订单状态
                        3. 帮用户查询天气信息
                        4. 友好地与用户闲聊
                        
                        回答要简洁、专业、友好。如果不确定，诚实地说不知道。
                        """)
                // 记忆：记住对话上下文
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(3).build())
                                .build()
                )
                .build();
    }

    /**
     * Agent 对话入口
     *
     * 测试场景：
     * 1. http://localhost:8080/agent/chat?message=你好，我叫小明&conversationId=test1
     * 2. http://localhost:8080/agent/chat?message=我叫什么名字？&conversationId=test1
     *    → 记住了"小明"（记忆）
     * 3. http://localhost:8080/agent/chat?message=帮我查一下订单ORD001&conversationId=test1
     *    → 调用 OrderService（工具调用）
     * 4. http://localhost:8080/agent/chat?message=北京天气怎么样&conversationId=test1
     *    → 调用 WeatherService（工具调用）
     * 5. http://localhost:8080/agent/chat?message=公司年假有几天&conversationId=test1
     *    → 从向量库检索公司政策（RAG）
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message,
                       @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(message)
                .tools(weatherService, orderService)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * Agent 流式对话
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String message,
                                   @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                .user(message)
                .tools(weatherService, orderService)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
