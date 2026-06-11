package com.business.fa.tokenrelay.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 兼容 OpenAI 格式的请求体
 */
@Data
public class RelayRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Boolean stream;

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    /**
     * 转为 Spring AI 可识别的 prompt 文本（简单拼接）
     */
    public String toPromptText() {
        if (messages == null || messages.isEmpty()) return "";
        return messages.get(messages.size() - 1).getContent();
    }
}
