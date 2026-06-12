package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

public record IdentityContext(
    String contextId,
    String playerNameLower,
    UUID uuid,
    AccountType accountType,
    String ip,
    String serverName,
    Instant issuedAt,
    Instant expiresAt,
    Instant consumedAt
) {
    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
