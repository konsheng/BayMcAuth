package com.baymc.auth.paper.protection;

import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.AuthStep;

import java.time.Instant;
import java.util.UUID;

/*
 * 玩家保护状态
 *
 * <p>记录玩家认证阶段, 账号类型和当前阶段的截止时间
 */
public record ProtectionState(UUID uuid, String playerName, AccountType accountType, AuthStep step, Instant deadline) {
    public boolean authenticated() {
        return step == AuthStep.AUTHENTICATED;
    }
}
