package com.baymc.auth.storage.repository;

import com.baymc.auth.common.identity.NameLockLookup;
import com.baymc.auth.common.model.NameLock;

import java.util.List;
import java.util.Optional;

public interface NameLockRepository extends NameLockLookup {
    @Override
    Optional<NameLock> findActiveByName(String playerName);

    Optional<NameLock> findAnyByName(String playerName);

    NameLock save(NameLock nameLock);

    List<NameLock> listActive(int limit);
}
