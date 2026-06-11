package com.business.fa.tokenrelay.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端 Token 实体
 * 分配给使用中转站的用户
 */
@Data
public class ClientToken {
    private String id;
    private String token;          // 客户端使用的 Bearer token
    private String name;           // 用户名/备注
    private boolean enabled = true;
    private long quotaLimit;       // Token 额度上限（0=无限）
    private long usedTokens;       // 已使用 token 数
    private long totalRequests;    // 总请求数
    private LocalDateTime expireAt; // 过期时间（null=永不过期）
    private LocalDateTime createAt;
}
