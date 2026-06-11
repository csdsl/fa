package com.business.fa.tokenrelay.controller;

import com.business.fa.tokenrelay.model.ApiKey;
import com.business.fa.tokenrelay.model.ClientToken;
import com.business.fa.tokenrelay.model.PayOrder;
import com.business.fa.tokenrelay.model.RechargeCode;
import com.business.fa.tokenrelay.model.RelayRequest;
import com.business.fa.tokenrelay.model.RelayResponse;
import com.business.fa.tokenrelay.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 中转站 Controller
 *
 * 中转接口（需客户端 Token 鉴权）：
 * - POST /relay/v1/chat/completions    中转对话
 * - GET  /relay/v1/models              列出可用模型
 *
 * 管理接口（无鉴权，建议生产环境加网关保护）：
 * - /relay/admin/keys/**               上游 Key 管理
 * - /relay/admin/tokens/**             客户端 Token 管理
 * - /relay/admin/usage/**              用量统计
 */
@RestController
@RequestMapping("/relay")
public class TokenRelayController {

    private final RelayService relayService;
    private final ApiKeyService apiKeyService;
    private final ClientTokenService clientTokenService;
    private final RechargeService rechargeService;
    private final PayService payService;
    private final XunhuPayService xunhuPayService;
    private final UsageTracker usageTracker;

    public TokenRelayController(RelayService relayService, ApiKeyService apiKeyService,
                                ClientTokenService clientTokenService, RechargeService rechargeService,
                                PayService payService, XunhuPayService xunhuPayService,
                                UsageTracker usageTracker) {
        this.relayService = relayService;
        this.apiKeyService = apiKeyService;
        this.clientTokenService = clientTokenService;
        this.rechargeService = rechargeService;
        this.payService = payService;
        this.xunhuPayService = xunhuPayService;
        this.usageTracker = usageTracker;
    }

    // ==================== 中转接口（需鉴权） ====================

    /**
     * 对话补全 - 兼容 OpenAI /v1/chat/completions
     */
    @PostMapping("/v1/chat/completions")
    public Object chatCompletions(
            @RequestBody RelayRequest request,
            @RequestHeader(value = "Authorization", defaultValue = "") String auth) {

        String tokenStr = extractToken(auth);

        // 鉴权校验
        ClientToken clientToken = clientTokenService.validate(tokenStr);
        if (clientToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", Map.of(
                                    "message", "Invalid or expired API key",
                                    "type", "invalid_request_error",
                                    "code", "invalid_api_key"
                            )
                    ));
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<String> stream = relayService.relayStream(request, tokenStr);
            // 流式完成后记录客户端用量（估算）
            stream = stream.doOnComplete(() -> clientTokenService.recordUsage(tokenStr, 100));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(stream);
        }

        // 非流式
        RelayResponse response = relayService.relay(request, tokenStr);
        // 记录客户端用量
        if (response.getUsage() != null) {
            clientTokenService.recordUsage(tokenStr, response.getUsage().getTotalTokens());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 列出可用模型 - 兼容 OpenAI /v1/models
     */
    @GetMapping("/v1/models")
    public Map<String, Object> listModels(
            @RequestHeader(value = "Authorization", defaultValue = "") String auth) {
        String tokenStr = extractToken(auth);
        ClientToken clientToken = clientTokenService.validate(tokenStr);
        if (clientToken == null) {
            return Map.of("error", Map.of("message", "Invalid API key", "code", "invalid_api_key"));
        }

        return Map.of(
                "object", "list",
                "data", List.of(
                        modelInfo("qwen-turbo", "通义千问-turbo"),
                        modelInfo("qwen-plus", "通义千问-plus"),
                        modelInfo("qwen-max", "通义千问-max")
                )
        );
    }

    // ==================== 客户端 Token 管理 ====================

    /**
     * 创建客户端 Token
     */
    @PostMapping("/admin/tokens")
    public ClientToken createClientToken(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "未命名用户");
        long quota = body.containsKey("quotaLimit") ? ((Number) body.get("quotaLimit")).longValue() : 0;
        String expireStr = (String) body.get("expireAt");
        LocalDateTime expireAt = null;
        if (expireStr != null && !expireStr.isBlank()) {
            expireAt = LocalDateTime.parse(expireStr);
        }
        return clientTokenService.createToken(name, quota, expireAt);
    }

    /**
     * 列出所有客户端 Token
     */
    @GetMapping("/admin/tokens")
    public List<ClientToken> listClientTokens() {
        return clientTokenService.listTokens();
    }

    /**
     * 删除客户端 Token
     */
    @DeleteMapping("/admin/tokens/{id}")
    public Map<String, String> deleteClientToken(@PathVariable String id) {
        clientTokenService.deleteToken(id);
        return Map.of("status", "deleted", "id", id);
    }

    /**
     * 启用/禁用客户端 Token
     */
    @PutMapping("/admin/tokens/{id}/toggle")
    public Map<String, Object> toggleClientToken(@PathVariable String id, @RequestParam boolean enabled) {
        clientTokenService.toggleToken(id, enabled);
        return Map.of("id", id, "enabled", enabled);
    }

    /**
     * 重置客户端用量
     */
    @PutMapping("/admin/tokens/{id}/reset")
    public Map<String, String> resetClientUsage(@PathVariable String id) {
        clientTokenService.resetUsage(id);
        return Map.of("status", "reset", "id", id);
    }

    // ==================== 上游 Key 管理 ====================

    @PostMapping("/admin/keys")
    public ApiKey addKey(@RequestBody ApiKey apiKey) {
        return apiKeyService.addKey(apiKey);
    }

    @GetMapping("/admin/keys")
    public List<ApiKey> listKeys() {
        return apiKeyService.listKeys();
    }

    @DeleteMapping("/admin/keys/{id}")
    public Map<String, String> removeKey(@PathVariable String id) {
        apiKeyService.removeKey(id);
        return Map.of("status", "removed", "id", id);
    }

    @PutMapping("/admin/keys/{id}/toggle")
    public Map<String, Object> toggleKey(@PathVariable String id, @RequestParam boolean enabled) {
        apiKeyService.toggleKey(id, enabled);
        return Map.of("id", id, "enabled", enabled);
    }

    // ==================== 用量统计 ====================

    @GetMapping("/admin/usage")
    public Map<String, Object> getUsageStats() {
        return usageTracker.getOverallStats();
    }

    @GetMapping("/admin/usage/by-key")
    public List<Map<String, Object>> getUsageByKey() {
        return usageTracker.getStatsByKey();
    }

    @GetMapping("/admin/usage/daily")
    public List<Map<String, Object>> getDailyUsage() {
        return usageTracker.getDailyStats();
    }

    @GetMapping("/admin/usage/by-model")
    public List<Map<String, Object>> getUsageByModel() {
        return usageTracker.getStatsByModel();
    }

    @GetMapping("/admin/usage/by-client")
    public List<Map<String, Object>> getUsageByClient() {
        return usageTracker.getStatsByClient();
    }

    // ==================== 充值码管理（管理员） ====================

    /**
     * 批量生成充值码
     */
    @PostMapping("/admin/codes/generate")
    public List<RechargeCode> generateCodes(@RequestBody Map<String, Object> body) {
        int count = body.containsKey("count") ? ((Number) body.get("count")).intValue() : 1;
        long amount = body.containsKey("tokenAmount") ? ((Number) body.get("tokenAmount")).longValue() : 1000000;
        String note = (String) body.getOrDefault("note", "");
        return rechargeService.generateCodes(count, amount, note);
    }

    /**
     * 列出所有充值码
     */
    @GetMapping("/admin/codes")
    public List<RechargeCode> listCodes(@RequestParam(defaultValue = "all") String filter) {
        return "unused".equals(filter) ? rechargeService.listUnused() : rechargeService.listAll();
    }

    /**
     * 删除充值码
     */
    @DeleteMapping("/admin/codes/{code}")
    public Map<String, String> deleteCode(@PathVariable String code) {
        rechargeService.deleteCode(code);
        return Map.of("status", "deleted");
    }

    // ==================== 用户自助接口 ====================

    /**
     * 用户查询自己的余额和用量
     */
    @GetMapping("/user/me")
    public Object getUserInfo(@RequestHeader(value = "Authorization", defaultValue = "") String auth) {
        String tokenStr = extractToken(auth);
        ClientToken ct = clientTokenService.validateLoose(tokenStr);
        if (ct == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid token"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("name", ct.getName());
        result.put("usedTokens", ct.getUsedTokens());
        result.put("quotaLimit", ct.getQuotaLimit());
        result.put("remaining", ct.getQuotaLimit() > 0 ? ct.getQuotaLimit() - ct.getUsedTokens() : -1);
        result.put("totalRequests", ct.getTotalRequests());
        result.put("expireAt", ct.getExpireAt() != null ? ct.getExpireAt().toString() : null);
        result.put("createAt", ct.getCreateAt() != null ? ct.getCreateAt().toString() : null);
        return result;
    }

    /**
     * 用户兑换充值码
     */
    @PostMapping("/user/redeem")
    public Object redeemCode(
            @RequestHeader(value = "Authorization", defaultValue = "") String auth,
            @RequestBody Map<String, String> body) {
        String tokenStr = extractToken(auth);
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return Map.of("success", false, "message", "请输入充值码");
        }
        return rechargeService.redeem(tokenStr, code);
    }

    // ==================== 用户支付充值 ====================

    /**
     * 用户创建充值订单 → 返回支付链接
     */
    @PostMapping("/user/pay/create")
    public Object createPayOrder(
            @RequestHeader(value = "Authorization", defaultValue = "") String auth,
            @RequestBody Map<String, Object> body) {
        String tokenStr = extractToken(auth);
        String planName = (String) body.getOrDefault("planName", "");
        long tokenAmount = body.containsKey("tokenAmount") ? ((Number) body.get("tokenAmount")).longValue() : 0;
        double price = body.containsKey("price") ? ((Number) body.get("price")).doubleValue() : 0;
        String type = (String) body.getOrDefault("payType", "alipay");

        if (tokenAmount <= 0 || price <= 0) {
            return Map.of("success", false, "message", "请选择套餐");
        }

        return xunhuPayService.createPayUrl(tokenStr, planName, tokenAmount, price, type);
    }

    /**
     * 用户标记已支付（保留兼容，实际由回调自动处理）
     */
    @PostMapping("/user/pay/paid")
    public Map<String, Object> markPaid(@RequestBody Map<String, String> body) {
        String orderId = body.get("orderId");
        if (orderId == null) return Map.of("success", false, "message", "缺少订单ID");
        // 不再自动确认，等待虎皮椒回调
        return Map.of("success", true, "message", "等待支付确认中...");
    }

    /**
     * 易支付异步回调（GET/POST 都支持）
     */
    @RequestMapping("/pay/notify")
    public String payNotify(@RequestParam Map<String, String> params) {
        boolean success = xunhuPayService.handleNotify(params);
        return success ? "success" : "fail";
    }

    /**
     * 用户查询订单状态（前端轮询）
     */
    @GetMapping("/user/pay/status")
    public Map<String, Object> getOrderStatus(@RequestParam String orderId) {
        String status = payService.getOrderStatus(orderId);
        return Map.of("orderId", orderId, "status", status != null ? status : "not_found");
    }

    /**
     * 用户查看自己的订单
     */
    @GetMapping("/user/pay/orders")
    public Object listMyOrders(@RequestHeader(value = "Authorization", defaultValue = "") String auth) {
        String tokenStr = extractToken(auth);
        return payService.listByClient(tokenStr);
    }

    // ==================== 管理员订单管理 ====================

    /**
     * 列出待确认的订单
     */
    @GetMapping("/admin/orders/pending")
    public List<PayOrder> listPendingOrders() {
        return payService.listPending();
    }

    /**
     * 列出所有订单
     */
    @GetMapping("/admin/orders")
    public List<PayOrder> listAllOrders() {
        return payService.listAll();
    }

    /**
     * 确认订单（到账后点击，自动加额度）
     */
    @PostMapping("/admin/orders/{id}/confirm")
    public Map<String, Object> confirmOrder(@PathVariable String id) {
        return payService.confirmOrder(id);
    }

    /**
     * 拒绝订单
     */
    @PostMapping("/admin/orders/{id}/reject")
    public Map<String, String> rejectOrder(@PathVariable String id) {
        payService.rejectOrder(id);
        return Map.of("status", "rejected", "id", id);
    }

    /**
     * 用户自助注册，获取 API Key
     */
    @PostMapping("/user/register")
    public Object register(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isBlank()) {
            return Map.of("success", false, "message", "请输入用户名");
        }
        // 默认给 0 额度（需要充值码充值），无过期
        ClientToken ct = clientTokenService.createToken(name, 0, null);
        return Map.of(
                "success", true,
                "message", "注册成功，请保存你的 API Key",
                "token", ct.getToken(),
                "name", ct.getName()
        );
    }

    // ==================== 辅助方法 ====================

    private String extractToken(String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return "";
    }

    private Map<String, Object> modelInfo(String id, String name) {
        return Map.of(
                "id", id,
                "object", "model",
                "owned_by", "dashscope",
                "name", name
        );
    }
}
