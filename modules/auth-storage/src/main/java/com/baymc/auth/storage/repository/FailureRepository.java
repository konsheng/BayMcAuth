package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.FailureActionType;
import com.baymc.auth.common.model.FailureRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * 失败记录仓库接口
 *
 * <p>用于写入认证失败记录并按账号或 IP 查询近期失败
 */
public interface FailureRepository {
    FailureRecord add(FailureRecord failure);

    List<FailureRecord> findSince(UUID userUuid, String ip, FailureActionType actionType, Instant since);
}
