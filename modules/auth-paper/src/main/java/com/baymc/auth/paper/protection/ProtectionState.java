package com.baymc.auth.paper.protection;

import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.AuthStep;

import java.time.Instant;
import java.util.UUID;

public record ProtectionState(UUID uuid, String playerName, AccountType accountType, AuthStep step, Instant deadline) {
    public boolean authenticated() {
        return step == AuthStep.AUTHENTICATED;
    }
}
