package com.business.fa.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第六课：RAG（检索增强生成）控制器
 *
 * 工作流程：
 *   用户提问"年假有几天"
 *   → QuestionAnswerAdvisor 自动把问题转成向量
 *   → 在 VectorStore 中检索最相关的文档块
 *   → 把检索到的内容 + 用户问题一起发给模型
 *   → 模型基于检索到的"公司政策"来回答
 *
 * 对比没有 RAG 的情况：
 *   模型根本不知道你公司的年假政策，只能瞎猜或说"我不知道"
 */
@RestController
@RequestMapping("/rag")
public class RagController {

    private final ChatClient chatClient;

    public RagController(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
                // QuestionAnswerAdvisor 自动完成：检索 → 注入上下文 → 提问
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().topK(3).build())  // topK=3 取最相关的3个文档块
                        .build())
                .build();
    }

    /**
     * 基于私有知识的问答
     *
     * 测试：
     * 1. http://localhost:8080/rag/chat?message=入职3年有几天年假
     * 2. http://localhost:8080/rag/chat?message=出差餐饮补贴多少
     * 3. http://localhost:8080/rag/chat?message=公司用什么数据库
     * 4. http://localhost:8080/rag/chat?message=报销需要多久内提交
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
