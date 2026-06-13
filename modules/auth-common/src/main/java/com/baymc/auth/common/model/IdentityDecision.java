package com.baymc.auth.common.model;

import java.util.Map;
import java.util.UUID;

/*
 * 身份分流决策结果
 *
 * <p>携带是否允许进入, 账号类型, 强制 UUID 和玩家可见拒绝原因语言键
 */
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
