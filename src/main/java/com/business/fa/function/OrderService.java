package com.business.fa.function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 模拟订单服务 — Agent 可调用的工具之一
 */
@Service
public class OrderService {

    // 模拟订单数据
    private static final Map<String, String> ORDERS = Map.of(
            "ORD001", "订单ORD001：iPhone 16，已发货，预计4月5日送达",
            "ORD002", "订单ORD002：MacBook Pro，处理中，预计4月8日发货",
            "ORD003", "订单ORD003：AirPods Pro，已签收"
    );

    @Tool(description = "根据订单号查询订单状态和物流信息")
    public String queryOrder(@ToolParam(description = "订单号，如 ORD001") String orderId) {
        return ORDERS.getOrDefault(orderId, "未找到订单：" + orderId);
    }
}
