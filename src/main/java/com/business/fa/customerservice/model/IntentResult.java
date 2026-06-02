package com.business.fa.customerservice.model;

/**
 * 意图识别结果
 * 用结构化输出让模型返回这个对象
 */
public record IntentResult(
        Intent intent,          // 识别到的意图
        String confidence,      // 置信度（高/中/低）
        String summary          // 用户诉求的一句话摘要
) {
    public enum Intent {
        CONSULT,      // 咨询（产品、政策、物流等）
        COMPLAINT,    // 投诉（情绪激动、不满）
        REFUND,       // 退换货
        ORDER_QUERY,  // 查订单/物流
        CHAT          // 闲聊/打招呼
    }
}
