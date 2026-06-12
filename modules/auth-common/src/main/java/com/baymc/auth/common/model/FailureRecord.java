package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

public record FailureRecord(
    long id,
    UUID userUuid,
    String playerName,
    String playerNameLower,
    String ip,
    AccountType accountType,
    FailureActionType actionType,
    String reason,
    String serverName,
    Instant failedAt
) {
}
