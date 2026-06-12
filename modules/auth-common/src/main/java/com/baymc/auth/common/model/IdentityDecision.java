package com.baymc.auth.common.model;

import java.util.UUID;

public record IdentityDecision(
    boolean allowed,
    AccountType accountType,
    UUID forcedUuid,
    String reason,
    String auditMessage
) {
    public static IdentityDecision deny(String reason) {
        return new IdentityDecision(false, null, null, reason, reason);
    }

    public static IdentityDecision allow(AccountType accountType, UUID forcedUuid, String auditMessage) {
        return new IdentityDecision(true, accountType, forcedUuid, null, auditMessage);
    }
}
