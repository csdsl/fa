package com.business.fa.tokenrelay.model;

import lombok.Data;

/**
 * API Key 实体
 */
@Data
public class ApiKey {
    private String id;
    private String name;
    private String key;           // 上游 API Key
    private String baseUrl;       // 上游地址
    private String model;         // 支持的模型（逗号分隔，*表示全部）
    private boolean enabled = true;
    private long totalTokensUsed;
    private long totalRequests;
    private long quotaLimit;      // 额度上限（0=无限）
}
