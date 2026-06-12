package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.FailureActionType;
import com.baymc.auth.common.model.FailureRecord;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FailureRepository {
    FailureRecord add(FailureRecord failure);

    List<FailureRecord> findSince(UUID userUuid, String ip, FailureActionType actionType, Instant since);
}
