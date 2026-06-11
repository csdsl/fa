package com.business.fa.tokenrelay.service;

import com.business.fa.tokenrelay.model.ClientToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 虎皮椒支付服务
 * 官网：https://www.xunhupay.com
 * 文档：https://www.xunhupay.com/doc/api/page/3
 *
 * 流程：
 * 1. 用户选套餐 → createPayUrl() 调虎皮椒 API 创建订单，返回支付链接
 * 2. 用户浏览器跳转支付链接完成支付
 * 3. 虎皮椒异步回调 notify_url → handleNotify() 验签 → 加额度
 * 4. 前端轮询订单状态 → confirmed → 显示充值成功
 */
@Service
public class XunhuPayService {

    @Value("${relay.pay.xunhu.appid:}")
    private String appid;

    @Value("${relay.pay.xunhu.appsecret:}")
    private String appsecret;

    @Value("${relay.pay.xunhu.api-url:https://api.xunhupay.com/payment/do.html}")
    private String apiUrl;

    @Value("${relay.pay.xunhu.notify-url:}")
    private String notifyUrl;

    @Value("${relay.pay.xunhu.return-url:}")
    private String returnUrl;

    private final JdbcTemplate jdbcTemplate;
    private final ClientTokenService clientTokenService;
    private final WebClient webClient;

    public XunhuPayService(JdbcTemplate jdbcTemplate, ClientTokenService clientTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientTokenService = clientTokenService;
        this.webClient = WebClient.create();
    }

    /**
     * 创建支付订单，返回支付链接
     *
     * @param type 支付方式：wechat / alipay
     */
    public Map<String, Object> createPayUrl(String clientTokenStr, String planName,
                                            long tokenAmount, double price, String type) {
        ClientToken ct = clientTokenService.validateLoose(clientTokenStr);
        if (ct == null) {
            return Map.of("success", false, "message", "无效的Token");
        }

        String orderId = "R" + System.currentTimeMillis() + new Random().nextInt(1000);

        // 存订单
        jdbcTemplate.update(
                "INSERT INTO relay_pay_order (id, client_token_id, client_name, plan_name, token_amount, price, status, create_at) VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)",
                orderId, ct.getId(), ct.getName(), planName, tokenAmount, price, LocalDateTime.now());

        // 虎皮椒请求参数（按字母排序）
        TreeMap<String, String> params = new TreeMap<>();
        params.put("version", "1.1");
        params.put("appid", appid);
        params.put("trade_order_id", orderId);
        params.put("total_fee", String.format("%.2f", price));
        params.put("title", "Token充值-" + planName);
        params.put("time", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("notify_url", notifyUrl);
        params.put("return_url", returnUrl);
        params.put("nonce_str", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        // wechat=微信, alipay=支付宝
        params.put("type", type != null ? type : "alipay");

        String sign = sign(params);
        params.put("hash", sign);

        // 调虎皮椒 API
        try {
            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(buildFormBody(params))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String payUrl = extractJsonField(response, "url");
            int errcode = extractJsonInt(response, "errcode");

            if (errcode == 0 && payUrl != null && !payUrl.isEmpty()) {
                return Map.of("success", true, "orderId", orderId, "payUrl", payUrl);
            } else {
                String errmsg = extractJsonField(response, "errmsg");
                return Map.of("success", false, "message", "支付创建失败: " + (errmsg != null ? errmsg : "未知错误"), "orderId", orderId);
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", "支付接口调用失败: " + e.getMessage(), "orderId", orderId);
        }
    }

    /**
     * 虎皮椒异步回调
     * 验签通过后自动加额度
     */
    public boolean handleNotify(Map<String, String> params) {
        String hash = params.get("hash");
        String orderId = params.get("trade_order_id");
        String status = params.get("status");

        // 验签
        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        sortedParams.remove("hash");
        String expectedSign = sign(sortedParams);

        if (!expectedSign.equalsIgnoreCase(hash)) {
            return false;
        }

        // 虎皮椒支付成功状态为 "OD"
        if (!"OD".equals(status)) {
            return true;
        }

        // 防重复
        List<Map<String, Object>> orderList = jdbcTemplate.queryForList(
                "SELECT * FROM relay_pay_order WHERE id = ? AND status = 'pending'", orderId);
        if (orderList.isEmpty()) {
            return true;
        }

        long tokenAmount = ((Number) orderList.get(0).get("token_amount")).longValue();
        String clientTokenId = (String) orderList.get(0).get("client_token_id");

        // 加额度
        jdbcTemplate.update(
                "UPDATE relay_client_token SET quota_limit = quota_limit + ? WHERE id = ?",
                tokenAmount, clientTokenId);

        // 更新订单状态
        jdbcTemplate.update(
                "UPDATE relay_pay_order SET status = 'confirmed', confirm_at = ? WHERE id = ?",
                LocalDateTime.now(), orderId);

        return true;
    }

    private String sign(TreeMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                if (sb.length() > 0) sb.append("&");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        sb.append(appsecret);
        return md5(sb.toString());
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String extractJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private int extractJsonInt(String json, String field) {
        if (json == null) return -1;
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);
        if (start == -1) return -1;
        start += key.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        try { return Integer.parseInt(json.substring(start, end)); } catch (Exception e) { return -1; }
    }
}
