package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

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
