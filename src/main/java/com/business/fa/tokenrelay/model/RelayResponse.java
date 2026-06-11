package com.business.fa.tokenrelay.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 兼容 OpenAI 格式的响应体
 */
@Data
@Builder
public class RelayResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @Data
    @Builder
    public static class Choice {
        private int index;
        private Message message;
        private String finishReason;
    }

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @Builder
    public static class Usage {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
    }
}
