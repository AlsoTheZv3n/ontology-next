package com.nexoai.ontology.core.tenant;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        UUID id = CURRENT_TENANT.get();
        return id != null ? id : DEFAULT_TENANT_ID;
    }

    /**
     * Returns the current tenant ID, or null if none is set.
     * Useful for RLS policy integration where null means "no tenant filter".
     */
    public static UUID getTenantIdOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentUser(String email) {
        CURRENT_USER.set(email);
    }

    public static String getCurrentUser() {
        return CURRENT_USER.get() != null ? CURRENT_USER.get() : "anonymous";
    }

    public static void setCurrentRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getCurrentRole() {
        return CURRENT_ROLE.get() != null ? CURRENT_ROLE.get() : "MEMBER";
    }

    public static boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(CURRENT_ROLE.get());
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
        CURRENT_ROLE.remove();
    }
}
