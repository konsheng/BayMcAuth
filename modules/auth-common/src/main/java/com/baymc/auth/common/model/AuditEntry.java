package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 审计事件记录
 *
 * <p>保存一次认证相关动作的主体, 结果, 来源和文本描述
 */
public record AuditEntry(
    long id,
    AuditEventType eventType,
    String playerName,
    String playerNameLower,
    UUID uuid,
    AccountType accountType,
    String ip,
    String serverName,
    AuditResult result,
    String reason,
    String message,
    Instant createdAt
) {
    public static AuditEntry now(
        AuditEventType type,
        String playerName,
        UUID uuid,
        AccountType accountType,
        String ip,
        String serverName,
        AuditResult result,
        String reason,
        String message
    ) {
        String lower = playerName == null ? null : playerName.toLowerCase(java.util.Locale.ROOT);
        return new AuditEntry(0L, type, playerName, lower, uuid, accountType, ip, serverName, result, reason, message, Instant.now());
    }
}
