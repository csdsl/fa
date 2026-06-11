package com.business.fa.tokenrelay.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 充值订单
 */
@Data
public class PayOrder {
    private String id;
    private String clientTokenId;   // 客户端用户 ID
    private String clientName;      // 用户名
    private String planName;        // 套餐名
    private long tokenAmount;       // 充值额度
    private double price;           // 金额（元）
    private String status;          // pending=待确认, confirmed=已确认, rejected=已拒绝
    private LocalDateTime createAt;
    private LocalDateTime confirmAt;
}
