package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.InviteCode;

import java.util.List;
import java.util.Optional;

/*
 * 邀请码仓库接口
 *
 * <p>提供邀请码查询, 保存和列表读取能力
 */
public interface InviteCodeRepository {
    Optional<InviteCode> findByCodeKey(String codeKey);

    InviteCode save(InviteCode inviteCode);

    List<InviteCode> list(int limit);
}
