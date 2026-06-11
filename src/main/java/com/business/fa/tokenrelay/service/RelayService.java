package com.business.fa.tokenrelay.service;

import com.business.fa.tokenrelay.model.ApiKey;
import com.business.fa.tokenrelay.model.RelayRequest;
import com.business.fa.tokenrelay.model.RelayResponse;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 核心中转服务
 * 将 OpenAI 格式的请求转为 Spring AI 调用，支持流式/非流式
 */
@Service
public class RelayService {

    private final OpenAiChatModel chatModel;
    private final ApiKeyService apiKeyService;
    private final UsageTracker usageTracker;

    public RelayService(OpenAiChatModel chatModel, ApiKeyService apiKeyService, UsageTracker usageTracker) {
        this.chatModel = chatModel;
        this.apiKeyService = apiKeyService;
        this.usageTracker = usageTracker;
    }

    /**
     * 非流式中转
     */
    public RelayResponse relay(RelayRequest request, String clientToken) {
        ApiKey selectedKey = apiKeyService.selectKey(request.getModel())
                .orElseThrow(() -> new RuntimeException("No available API key for model: " + request.getModel()));

        // 构建 Prompt
        Prompt prompt = buildPrompt(request, selectedKey);

        // 调用模型
        ChatResponse chatResponse = chatModel.call(prompt);

        // 提取 token 用量
        long promptTokens = 0, completionTokens = 0;
        if (chatResponse.getMetadata() != null && chatResponse.getMetadata().getUsage() != null) {
            promptTokens = chatResponse.getMetadata().getUsage().getPromptTokens();
            completionTokens = chatResponse.getMetadata().getUsage().getCompletionTokens();
        }

        // 记录用量
        apiKeyService.recordUsage(selectedKey.getId(), promptTokens + completionTokens);
        usageTracker.record(selectedKey.getId(), clientToken, request.getModel(), promptTokens, completionTokens);

        // 组装响应
        String content = chatResponse.getResult().getOutput().getText();
        return RelayResponse.builder()
                .id("chatcmpl-" + UUID.randomUUID().toString().substring(0, 8))
                .object("chat.completion")
                .created(System.currentTimeMillis() / 1000)
                .model(request.getModel())
                .choices(List.of(RelayResponse.Choice.builder()
                        .index(0)
                        .message(RelayResponse.Message.builder()
                                .role("assistant")
                                .content(content)
                                .build())
                        .finishReason("stop")
                        .build()))
                .usage(RelayResponse.Usage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .build())
                .build();
    }

    /**
     * 流式中转
     */
    public Flux<String> relayStream(RelayRequest request, String clientToken) {
        ApiKey selectedKey = apiKeyService.selectKey(request.getModel())
                .orElseThrow(() -> new RuntimeException("No available API key for model: " + request.getModel()));

        Prompt prompt = buildPrompt(request, selectedKey);

        return Flux.from(chatModel.stream(prompt))
                .map(chatResponse -> {
                    String content = "";
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        content = chatResponse.getResult().getOutput().getText();
                        if (content == null) content = "";
                    }
                    // SSE 格式
                    String chunk = String.format(
                            "{\"id\":\"chatcmpl-%s\",\"object\":\"chat.completion.chunk\",\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"%s\"},\"finish_reason\":null}]}",
                            UUID.randomUUID().toString().substring(0, 8),
                            request.getModel(),
                            escapeJson(content));
                    return "data: " + chunk + "\n\n";
                })
                .concatWith(Flux.just("data: [DONE]\n\n"))
                .doOnComplete(() -> {
                    // 流式完成后异步记录（简化处理，用估算值）
                    apiKeyService.recordUsage(selectedKey.getId(), 100);
                    usageTracker.record(selectedKey.getId(), clientToken, request.getModel(), 50, 50);
                });
    }

    private Prompt buildPrompt(RelayRequest request, ApiKey selectedKey) {
        List<Message> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (RelayRequest.Message msg : request.getMessages()) {
                switch (msg.getRole()) {
                    case "system" -> messages.add(new SystemMessage(msg.getContent()));
                    case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                    default -> messages.add(new UserMessage(msg.getContent()));
                }
            }
        }

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(request.getModel());

        if (request.getTemperature() != null) {
            optionsBuilder.temperature(request.getTemperature());
        }
        if (request.getTopP() != null) {
            optionsBuilder.topP(request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            optionsBuilder.maxTokens(request.getMaxTokens());
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
