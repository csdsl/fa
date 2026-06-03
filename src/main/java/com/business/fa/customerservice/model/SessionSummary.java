package com.business.fa.customerservice.model;

import java.time.LocalDateTime;

/**
 * 对话摘要
 */
public record SessionSummary(
        String sessionId,
        String summary,           // 一句话摘要
        String userIntent,        // 用户主要意图
        String resolution,        // 解决情况（已解决/未解决/转人工）
        int messageCount,         // 消息条数
        LocalDateTime createTime
) {}
