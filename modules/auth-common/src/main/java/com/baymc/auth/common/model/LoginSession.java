package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

public record LoginSession(
    UUID uuid,
    String playerName,
    AccountType accountType,
    String serverName,
    Instant loginTime,
    Instant expireTime,
    boolean totpPassed,
    String ip,
    Instant lastSeen
) {
    public boolean validAt(Instant now) {
        return expireTime.isAfter(now);
    }
}
