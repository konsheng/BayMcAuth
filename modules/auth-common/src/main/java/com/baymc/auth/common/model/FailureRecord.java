package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 认证失败记录
 *
 * <p>保存失败动作的玩家, IP, 账号类型和时间, 用于失败锁定判断
 */
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
