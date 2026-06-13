package com.baymc.auth.common.identity;

import com.baymc.auth.common.model.NameLock;

import java.util.Optional;

/*
 * 预留名查询接口
 *
 * <p>供身份分流逻辑按玩家名查询当前有效的离线名预留记录
 */
public interface NameLockLookup {
    Optional<NameLock> findActiveByName(String playerName);
}
