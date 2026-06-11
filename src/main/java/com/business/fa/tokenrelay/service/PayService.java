package com.business.fa.tokenrelay.service;

import com.business.fa.tokenrelay.model.ClientToken;
import com.business.fa.tokenrelay.model.PayOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 支付订单服务
 */
@Service
public class PayService {

    private final JdbcTemplate jdbcTemplate;
    private final ClientTokenService clientTokenService;

    public PayService(JdbcTemplate jdbcTemplate, ClientTokenService clientTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientTokenService = clientTokenService;
    }

    private final RowMapper<PayOrder> rowMapper = (rs, rowNum) -> {
        PayOrder o = new PayOrder();
        o.setId(rs.getString("id"));
        o.setClientTokenId(rs.getString("client_token_id"));
        o.setClientName(rs.getString("client_name"));
        o.setPlanName(rs.getString("plan_name"));
        o.setTokenAmount(rs.getLong("token_amount"));
        o.setPrice(rs.getDouble("price"));
        o.setStatus(rs.getString("status"));
        var createAt = rs.getTimestamp("create_at");
        o.setCreateAt(createAt != null ? createAt.toLocalDateTime() : null);
        var confirmAt = rs.getTimestamp("confirm_at");
        o.setConfirmAt(confirmAt != null ? confirmAt.toLocalDateTime() : null);
        return o;
    };

    /**
     * 用户创建充值订单
     */
    public PayOrder createOrder(String clientTokenStr, String planName, long tokenAmount, double price) {
        ClientToken ct = clientTokenService.validateLoose(clientTokenStr);
        if (ct == null) return null;

        PayOrder order = new PayOrder();
        order.setId(UUID.randomUUID().toString().substring(0, 12));
        order.setClientTokenId(ct.getId());
        order.setClientName(ct.getName());
        order.setPlanName(planName);
        order.setTokenAmount(tokenAmount);
        order.setPrice(price);
        order.setStatus("pending");
        order.setCreateAt(LocalDateTime.now());

        jdbcTemplate.update(
                "INSERT INTO relay_pay_order (id, client_token_id, client_name, plan_name, token_amount, price, status, create_at) VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)",
                order.getId(), order.getClientTokenId(), order.getClientName(),
                order.getPlanName(), order.getTokenAmount(), order.getPrice(), order.getCreateAt());

        return order;
    }

    /**
     * 用户标记"已支付" → 自动确认并加额度
     */
    public Map<String, Object> markPaidAndConfirm(String orderId) {
        List<PayOrder> list = jdbcTemplate.query(
                "SELECT * FROM relay_pay_order WHERE id = ? AND status = 'pending' AND create_at > ?",
                rowMapper, orderId, LocalDateTime.now().minusMinutes(30));
        if (list.isEmpty()) {
            return Map.of("success", false, "message", "订单不存在或已过期");
        }

        PayOrder order = list.get(0);

        // 直接给用户加额度
        jdbcTemplate.update(
                "UPDATE relay_client_token SET quota_limit = quota_limit + ? WHERE id = ?",
                order.getTokenAmount(), order.getClientTokenId());

        // 更新订单状态为已确认
        jdbcTemplate.update(
                "UPDATE relay_pay_order SET status = 'confirmed', confirm_at = ? WHERE id = ?",
                LocalDateTime.now(), orderId);

        return Map.of(
                "success", true,
                "message", "充值成功",
                "addedTokens", order.getTokenAmount()
        );
    }

    /**
     * 管理员确认到账 → 自动加额度
     */
    public Map<String, Object> confirmOrder(String orderId) {
        List<PayOrder> list = jdbcTemplate.query("SELECT * FROM relay_pay_order WHERE id = ?", rowMapper, orderId);
        if (list.isEmpty()) return Map.of("success", false, "message", "订单不存在");

        PayOrder order = list.get(0);
        if ("confirmed".equals(order.getStatus())) {
            return Map.of("success", false, "message", "订单已确认过");
        }

        // 给用户加额度
        jdbcTemplate.update(
                "UPDATE relay_client_token SET quota_limit = quota_limit + ? WHERE id = ?",
                order.getTokenAmount(), order.getClientTokenId());

        // 更新订单状态
        jdbcTemplate.update(
                "UPDATE relay_pay_order SET status = 'confirmed', confirm_at = ? WHERE id = ?",
                LocalDateTime.now(), orderId);

        return Map.of("success", true, "message", "已确认，已为用户充值 " + order.getTokenAmount() + " token");
    }

    /**
     * 管理员拒绝订单
     */
    public void rejectOrder(String orderId) {
        jdbcTemplate.update("UPDATE relay_pay_order SET status = 'rejected', confirm_at = ? WHERE id = ?",
                LocalDateTime.now(), orderId);
    }

    /**
     * 列出待确认的订单（排除超过30分钟未支付的）
     */
    public List<PayOrder> listPending() {
        return jdbcTemplate.query(
                "SELECT * FROM relay_pay_order WHERE status IN ('pending','paid') AND create_at > ? ORDER BY create_at DESC",
                rowMapper, LocalDateTime.now().minusMinutes(30));
    }

    /**
     * 查询订单状态
     */
    public String getOrderStatus(String orderId) {
        List<String> list = jdbcTemplate.queryForList(
                "SELECT status FROM relay_pay_order WHERE id = ?", String.class, orderId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 列出所有订单
     */
    public List<PayOrder> listAll() {
        return jdbcTemplate.query("SELECT * FROM relay_pay_order ORDER BY create_at DESC LIMIT 100", rowMapper);
    }

    /**
     * 查询用户自己的订单
     */
    public List<PayOrder> listByClient(String clientTokenStr) {
        ClientToken ct = clientTokenService.validateLoose(clientTokenStr);
        if (ct == null) return List.of();
        return jdbcTemplate.query("SELECT * FROM relay_pay_order WHERE client_token_id = ? ORDER BY create_at DESC",
                rowMapper, ct.getId());
    }
}
