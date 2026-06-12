package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.UserRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<UserRecord> findByUuid(UUID uuid);

    List<UserRecord> findByName(String playerName);

    Optional<UserRecord> findByNameAndType(String playerName, AccountType accountType);

    UserRecord save(UserRecord user);
}
