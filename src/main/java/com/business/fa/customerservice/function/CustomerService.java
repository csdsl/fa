package com.business.fa.customerservice.function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomerService {
    // 模拟订单数据库
    private static final Map<String, Map<String, String>> ORDERS = Map.of(
            "ORD20240001", Map.of(
                    "product", "iPhone 16 Pro 256G 黑色",
                    "price", "8999",
                    "status", "已发货",
                    "createTime", "2026-03-28 14:30:00",
                    "logistics", "顺丰快递 SF1234567890",
                    "address", "北京市朝阳区xxx小区"
            ),
            "ORD20240002", Map.of(
                    "product", "AirPods Pro 2",
                    "price", "1799",
                    "status", "已签收",
                    "createTime", "2026-03-25 10:00:00",
                    "logistics", "中通快递 ZT9876543210",
                    "address", "上海市浦东新区xxx路"
            ),
            "ORD20240003", Map.of(
                    "product", "MacBook Air M3 16G/512G",
                    "price", "9499",
                    "status", "待发货",
                    "createTime", "2026-04-01 09:15:00",
                    "logistics", "暂无物流信息",
                    "address", "广州市天河区xxx大厦"
            )
    );

    // 模拟用户-订单关系
    private static final Map<String, List<String>> USER_ORDERS = Map.of(
            "user001", List.of("ORD20240001", "ORD20240002"),
            "user002", List.of("ORD20240003")
    );

    @Tool(description = "根据订单号查询订单详情，包括商品信息、价格、状态、物流等")
    public String queryOrderDetail(
            @ToolParam(description = "订单号，格式如 ORD20240001") String orderId) {
        Map<String, String> order = ORDERS.get(orderId);
        if (order == null) {
            return "未找到订单 " + orderId + "，请确认订单号是否正确。";
        }
        return String.format("""
                订单号: %s
                商品: %s
                价格: ¥%s
                状态: %s
                下单时间: %s
                物流: %s
                收货地址: %s""",
                orderId, order.get("product"), order.get("price"),
                order.get("status"), order.get("createTime"),
                order.get("logistics"), order.get("address"));
    }

    @Tool(description = "根据用户ID查询该用户的所有订单列表")
    public String queryUserOrders(
            @ToolParam(description = "用户ID，如 user001") String userId) {
        List<String> orderIds = USER_ORDERS.get(userId);
        if (orderIds == null || orderIds.isEmpty()) {
            return "该用户暂无订单记录。";
        }
        StringBuilder sb = new StringBuilder("用户 " + userId + " 的订单：\n");
        for (String orderId : orderIds) {
            Map<String, String> order = ORDERS.get(orderId);
            if (order != null) {
                sb.append(String.format("- %s | %s | ¥%s | %s\n",
                        orderId, order.get("product"), order.get("price"), order.get("status")));
            }
        }
        return sb.toString();
    }
}
