package com.nirmaan.reimburse.common.audit;

/**
 * Optional per-request label added to every audit entry, e.g. "AI connector: ChatGPT" while an MCP tool runs,
 * so the audit log shows which actions came through an AI assistant.
 */
public final class AuditContext {

    private static final ThreadLocal<String> VIA = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setVia(String via) {
        VIA.set(via);
    }

    public static String via() {
        return VIA.get();
    }

    public static void clear() {
        VIA.remove();
    }
}
