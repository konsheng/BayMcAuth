package com.baymc.auth.common.identity;

import com.baymc.auth.common.model.NameLock;

import java.util.Optional;

public interface NameLockLookup {
    Optional<NameLock> findActiveByName(String playerName);
}
