package com.business.fa.tenant;

/**
 * 租户上下文 - 用 ThreadLocal 存储当前请求的租户ID
 * 所有业务代码通过 TenantContext.getTenantId() 获取当前租户
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final String DEFAULT_TENANT = "default";

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        String id = CURRENT_TENANT.get();
        return id != null ? id : DEFAULT_TENANT;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
