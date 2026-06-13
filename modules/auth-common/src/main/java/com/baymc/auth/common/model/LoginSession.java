package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

/*
 * 登录会话记录
 *
 * <p>描述玩家已认证会话的账号类型, 来源服务器, 过期时间和 TOTP 状态
 */
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
