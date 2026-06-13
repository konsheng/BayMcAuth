package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 临时身份上下文
 *
 * <p>保存 Velocity 分流结果, 供 Paper/Folia 子服在玩家加入时消费
 */
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
