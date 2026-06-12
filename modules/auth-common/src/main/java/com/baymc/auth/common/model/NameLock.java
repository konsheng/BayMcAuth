package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

public record NameLock(
    long id,
    String nameLower,
    String playerName,
    UUID ownerUuid,
    AccountType accountType,
    String lockType,
    boolean active,
    String createdBy,
    UUID createdByUuid,
    Instant createdAt,
    boolean revoked,
    String revokedBy,
    UUID revokedByUuid,
    Instant revokedAt,
    String note
) {
    public boolean usable() {
        return active && !revoked;
    }
}
