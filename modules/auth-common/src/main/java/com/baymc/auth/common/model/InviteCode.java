package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 邀请码记录
 *
 * <p>保存邀请码的创建, 使用, 撤销和过期信息, 用于离线账号注册校验
 */
public record InviteCode(
    long id,
    String code,
    String codeKey,
    String batchId,
    boolean used,
    UUID usedByUuid,
    String usedByName,
    String usedByNameLower,
    AccountType usedAccountType,
    String usedIp,
    Instant usedAt,
    String createdBy,
    UUID createdByUuid,
    Instant createdAt,
    Instant expiresAt,
    boolean revoked,
    String revokedBy,
    UUID revokedByUuid,
    Instant revokedAt,
    String note
) {
    public boolean canUse(Instant now) {
        return !used && !revoked && expiresAt.isAfter(now);
    }
}
