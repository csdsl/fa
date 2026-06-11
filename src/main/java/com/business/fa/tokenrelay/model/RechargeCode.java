package com.business.fa.tokenrelay.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 充值码实体
 */
@Data
public class RechargeCode {
    private String id;
    private String code;            // 兑换码
    private long tokenAmount;       // 充值的 token 额度
    private boolean used;           // 是否已使用
    private String usedBy;          // 使用者的客户端 Token ID
    private LocalDateTime usedAt;   // 使用时间
    private LocalDateTime createAt;
    private String note;            // 备注（如：赠送、购买等）
}
