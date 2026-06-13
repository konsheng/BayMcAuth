package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 密码历史记录
 *
 * <p>保存密码变更时的玩家, 类型, 操作者和来源信息
 */
public record PasswordHistoryEntry(
    long id,
    UUID userUuid,
    String playerName,
    String playerNameLower,
    AccountType accountType,
    String passwordPlain,
    PasswordChangeType changeType,
    String changedBy,
    UUID changedByUuid,
    String ip,
    String serverName,
    Instant createdAt
) {
}
