package com.business.fa.tokenrelay.service;

import com.business.fa.tokenrelay.model.ClientToken;
import com.business.fa.tokenrelay.model.RechargeCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 充值码服务（持久化到数据库）
 */
@Service
public class RechargeService {

    private final JdbcTemplate jdbcTemplate;
    private final ClientTokenService clientTokenService;

    public RechargeService(JdbcTemplate jdbcTemplate, ClientTokenService clientTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientTokenService = clientTokenService;
    }

    private final RowMapper<RechargeCode> rowMapper = (rs, rowNum) -> {
        RechargeCode rc = new RechargeCode();
        rc.setId(rs.getString("id"));
        rc.setCode(rs.getString("code"));
        rc.setTokenAmount(rs.getLong("token_amount"));
        rc.setUsed(rs.getBoolean("used"));
        rc.setUsedBy(rs.getString("used_by"));
        var usedAt = rs.getTimestamp("used_at");
        rc.setUsedAt(usedAt != null ? usedAt.toLocalDateTime() : null);
        var createAt = rs.getTimestamp("create_at");
        rc.setCreateAt(createAt != null ? createAt.toLocalDateTime() : null);
        rc.setNote(rs.getString("note"));
        return rc;
    };

    /**
     * 批量生成充值码
     */
    public List<RechargeCode> generateCodes(int count, long tokenAmount, String note) {
        List<RechargeCode> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RechargeCode rc = new RechargeCode();
            rc.setId(UUID.randomUUID().toString().substring(0, 8));
            rc.setCode(generateCode());
            rc.setTokenAmount(tokenAmount);
            rc.setUsed(false);
            rc.setCreateAt(LocalDateTime.now());
            rc.setNote(note);

            jdbcTemplate.update(
                    "INSERT INTO relay_recharge_code (id, code, token_amount, used, create_at, note) VALUES (?, ?, ?, false, ?, ?)",
                    rc.getId(), rc.getCode(), rc.getTokenAmount(), rc.getCreateAt(), rc.getNote());
            codes.add(rc);
        }
        return codes;
    }

    /**
     * 兑换充值码
     */
    public Map<String, Object> redeem(String clientTokenStr, String code) {
        // 宽松校验客户端 Token
        ClientToken ct = clientTokenService.validateLoose(clientTokenStr);
        if (ct == null) {
            return Map.of("success", false, "message", "无效的客户端 Token，请先登录");
        }

        // 查充值码
        List<RechargeCode> list = jdbcTemplate.query(
                "SELECT * FROM relay_recharge_code WHERE code = ?", rowMapper, code.trim());
        if (list.isEmpty()) {
            return Map.of("success", false, "message", "充值码不存在");
        }
        RechargeCode rc = list.get(0);
        if (rc.isUsed()) {
            return Map.of("success", false, "message", "充值码已被使用");
        }

        // 标记充值码已使用
        jdbcTemplate.update(
                "UPDATE relay_recharge_code SET used = true, used_by = ?, used_at = ? WHERE id = ?",
                ct.getId(), LocalDateTime.now(), rc.getId());

        // 增加客户端额度
        clientTokenService.addQuota(ct.getToken(), rc.getTokenAmount());

        // 重新查询最新额度
        ClientToken updated = clientTokenService.getByToken(clientTokenStr);
        long currentQuota = updated != null ? updated.getQuotaLimit() : 0;

        return Map.of(
                "success", true,
                "message", "充值成功",
                "addedTokens", rc.getTokenAmount(),
                "currentQuota", currentQuota
        );
    }

    /**
     * 列出所有充值码
     */
    public List<RechargeCode> listAll() {
        return jdbcTemplate.query("SELECT * FROM relay_recharge_code ORDER BY create_at DESC", rowMapper);
    }

    /**
     * 列出未使用的充值码
     */
    public List<RechargeCode> listUnused() {
        return jdbcTemplate.query("SELECT * FROM relay_recharge_code WHERE used = false ORDER BY create_at DESC", rowMapper);
    }

    /**
     * 删除充值码
     */
    public void deleteCode(String code) {
        jdbcTemplate.update("DELETE FROM relay_recharge_code WHERE code = ?", code);
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
