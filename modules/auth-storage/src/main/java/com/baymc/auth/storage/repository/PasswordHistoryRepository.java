package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.PasswordHistoryEntry;

import java.util.List;
import java.util.UUID;

/*
 * 密码历史仓库接口
 *
 * <p>保存密码变更历史并按用户查询最近记录
 */
public interface PasswordHistoryRepository {
    PasswordHistoryEntry add(PasswordHistoryEntry entry);

    List<PasswordHistoryEntry> findByUserUuid(UUID userUuid, int limit);
}
