package com.business.fa.tokenrelay.service;

import com.business.fa.tokenrelay.model.ClientToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 客户端 Token 管理服务（持久化到数据库）
 */
@Service
public class ClientTokenService {

    private final JdbcTemplate jdbcTemplate;

    public ClientTokenService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ClientToken> rowMapper = (rs, rowNum) -> {
        ClientToken ct = new ClientToken();
        ct.setId(rs.getString("id"));
        ct.setToken(rs.getString("token"));
        ct.setName(rs.getString("name"));
        ct.setEnabled(rs.getBoolean("enabled"));
        ct.setQuotaLimit(rs.getLong("quota_limit"));
        ct.setUsedTokens(rs.getLong("used_tokens"));
        ct.setTotalRequests(rs.getLong("total_requests"));
        var expireAt = rs.getTimestamp("expire_at");
        ct.setExpireAt(expireAt != null ? expireAt.toLocalDateTime() : null);
        var createAt = rs.getTimestamp("create_at");
        ct.setCreateAt(createAt != null ? createAt.toLocalDateTime() : null);
        return ct;
    };

    /**
     * 创建客户端 Token
     */
    public ClientToken createToken(String name, long quotaLimit, LocalDateTime expireAt) {
        ClientToken ct = new ClientToken();
        ct.setId(UUID.randomUUID().toString().substring(0, 8));
        ct.setToken("sk-relay-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        ct.setName(name);
        ct.setQuotaLimit(quotaLimit);
        ct.setExpireAt(expireAt);
        ct.setCreateAt(LocalDateTime.now());
        ct.setEnabled(true);

        jdbcTemplate.update(
                "INSERT INTO relay_client_token (id, token, name, enabled, quota_limit, used_tokens, total_requests, expire_at, create_at) VALUES (?, ?, ?, ?, ?, 0, 0, ?, ?)",
                ct.getId(), ct.getToken(), ct.getName(), ct.isEnabled(), ct.getQuotaLimit(),
                ct.getExpireAt(), ct.getCreateAt());
        return ct;
    }

    /**
     * 严格校验（中转调用用，检查额度）
     */
    public ClientToken validate(String token) {
        if (token == null || token.isBlank()) return null;
        List<ClientToken> list = jdbcTemplate.query(
                "SELECT * FROM relay_client_token WHERE token = ?", rowMapper, token);
        if (list.isEmpty()) return null;

        ClientToken ct = list.get(0);
        if (!ct.isEnabled()) return null;
        if (ct.getExpireAt() != null && ct.getExpireAt().isBefore(LocalDateTime.now())) return null;
        if (ct.getQuotaLimit() > 0 && ct.getUsedTokens() >= ct.getQuotaLimit()) return null;
        return ct;
    }

    /**
     * 宽松校验（查余额、充值用，不检查额度）
     */
    public ClientToken validateLoose(String token) {
        if (token == null || token.isBlank()) return null;
        List<ClientToken> list = jdbcTemplate.query(
                "SELECT * FROM relay_client_token WHERE token = ?", rowMapper, token);
        if (list.isEmpty()) return null;

        ClientToken ct = list.get(0);
        if (!ct.isEnabled()) return null;
        if (ct.getExpireAt() != null && ct.getExpireAt().isBefore(LocalDateTime.now())) return null;
        return ct;
    }

    /**
     * 记录用量
     */
    public void recordUsage(String token, long tokens) {
        jdbcTemplate.update(
                "UPDATE relay_client_token SET used_tokens = used_tokens + ?, total_requests = total_requests + 1 WHERE token = ?",
                tokens, token);
    }

    /**
     * 增加额度（充值）
     */
    public void addQuota(String token, long amount) {
        jdbcTemplate.update(
                "UPDATE relay_client_token SET quota_limit = quota_limit + ? WHERE token = ?",
                amount, token);
    }

    /**
     * 列出所有客户端 Token
     */
    public List<ClientToken> listTokens() {
        return jdbcTemplate.query("SELECT * FROM relay_client_token ORDER BY create_at DESC", rowMapper);
    }

    /**
     * 删除 Token
     */
    public void deleteToken(String id) {
        jdbcTemplate.update("DELETE FROM relay_client_token WHERE id = ?", id);
    }

    /**
     * 启用/禁用
     */
    public void toggleToken(String id, boolean enabled) {
        jdbcTemplate.update("UPDATE relay_client_token SET enabled = ? WHERE id = ?", enabled, id);
    }

    /**
     * 重置用量
     */
    public void resetUsage(String id) {
        jdbcTemplate.update("UPDATE relay_client_token SET used_tokens = 0, total_requests = 0 WHERE id = ?", id);
    }

    /**
     * 根据 token 字符串获取
     */
    public ClientToken getByToken(String token) {
        List<ClientToken> list = jdbcTemplate.query(
                "SELECT * FROM relay_client_token WHERE token = ?", rowMapper, token);
        return list.isEmpty() ? null : list.get(0);
    }
}
