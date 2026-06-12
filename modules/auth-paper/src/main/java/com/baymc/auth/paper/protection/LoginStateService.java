package com.baymc.auth.paper.protection;

import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.AuthStep;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoginStateService {
    private final Map<UUID, ProtectionState> states = new ConcurrentHashMap<>();

    public void set(UUID uuid, String playerName, AccountType accountType, AuthStep step, Instant deadline) {
        states.put(uuid, new ProtectionState(uuid, playerName, accountType, step, deadline));
    }

    public void authenticate(UUID uuid, String playerName, AccountType accountType) {
        states.put(uuid, new ProtectionState(uuid, playerName, accountType, AuthStep.AUTHENTICATED, Instant.MAX));
    }

    public Optional<ProtectionState> state(UUID uuid) {
        return Optional.ofNullable(states.get(uuid));
    }

    public boolean authenticated(UUID uuid) {
        return state(uuid).map(ProtectionState::authenticated).orElse(false);
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }
}
