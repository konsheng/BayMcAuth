package com.baymc.auth.common.model;

import java.util.Map;
import java.util.UUID;

public record IdentityDecision(
    boolean allowed,
    AccountType accountType,
    UUID forcedUuid,
    String reasonKey,
    Map<String, String> reasonPlaceholders,
    String auditMessage
) {
    public static IdentityDecision deny(String reasonKey) {
        return deny(reasonKey, Map.of());
    }

    public static IdentityDecision deny(String reasonKey, Map<String, String> reasonPlaceholders) {
        return new IdentityDecision(false, null, null, reasonKey, Map.copyOf(reasonPlaceholders), reasonKey);
    }

    public static IdentityDecision allow(AccountType accountType, UUID forcedUuid, String auditMessage) {
        return new IdentityDecision(true, accountType, forcedUuid, null, Map.of(), auditMessage);
    }
}
