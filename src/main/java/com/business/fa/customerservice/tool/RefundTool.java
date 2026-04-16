package com.business.fa.customerservice.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 退换货工具 — 模拟退换货流程
 */
@Component
public class RefundTool {

    private final AtomicInteger ticketCounter = new AtomicInteger(1000);
    private final Map<String, String> refundRecords = new ConcurrentHashMap<>();

    @Tool(description = "为用户提交退款/退货申请，返回退款工单号")
    public String submitRefund(
            @ToolParam(description = "订单号") String orderId,
            @ToolParam(description = "退款原因，如：质量问题、不想要了、发错货等") String reason) {

        String ticketId = "RF" + ticketCounter.incrementAndGet();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        refundRecords.put(ticketId, String.format("订单%s | 原因: %s | 时间: %s | 状态: 审核中", orderId, reason, now));

        return String.format("""
                退款申请已提交成功！
                工单号: %s
                关联订单: %s
                退款原因: %s
                提交时间: %s
                当前状态: 审核中（预计1个工作日内审核）
                
                请保持手机畅通，审核结果会通过短信通知您。""", ticketId, orderId, reason, now);
    }

    @Tool(description = "查询退款工单的处理进度")
    public String queryRefundStatus(
            @ToolParam(description = "退款工单号，格式如 RF1001") String ticketId) {
        String record = refundRecords.get(ticketId);
        if (record == null) {
            return "未找到退款工单 " + ticketId + "，请确认工单号是否正确。";
        }
        return "工单 " + ticketId + " 详情：" + record;
    }
}
