package com.baymc.auth.storage.repository;

import com.baymc.auth.common.identity.NameLockLookup;
import com.baymc.auth.common.model.NameLock;

import java.util.List;
import java.util.Optional;

/*
 * 预留名仓库接口
 *
 * <p>扩展身份分流查询能力, 提供预留名保存和管理列表读取
 */
public interface NameLockRepository extends NameLockLookup {
    @Override
    Optional<NameLock> findActiveByName(String playerName);

    Optional<NameLock> findAnyByName(String playerName);

    NameLock save(NameLock nameLock);

    List<NameLock> listActive(int limit);
}
