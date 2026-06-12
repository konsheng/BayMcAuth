package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.PasswordHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository {
    PasswordHistoryEntry add(PasswordHistoryEntry entry);

    List<PasswordHistoryEntry> findByUserUuid(UUID userUuid, int limit);
}
