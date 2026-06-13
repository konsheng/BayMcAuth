package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.UserRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * 用户仓库接口
 *
 * <p>提供账号按 UUID, 名字和账号类型查询以及保存能力
 */
public interface UserRepository {
    Optional<UserRecord> findByUuid(UUID uuid);

    List<UserRecord> findByName(String playerName);

    Optional<UserRecord> findByNameAndType(String playerName, AccountType accountType);

    UserRecord save(UserRecord user);
}
