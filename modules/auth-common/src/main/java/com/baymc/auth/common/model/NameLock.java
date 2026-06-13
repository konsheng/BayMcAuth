package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 名字预留记录
 *
 * <p>保存离线名预留的归属, 状态和撤销信息, 用于 Velocity 分流判断
 */
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
