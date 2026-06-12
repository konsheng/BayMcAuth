package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.InviteCode;

import java.util.List;
import java.util.Optional;

public interface InviteCodeRepository {
    Optional<InviteCode> findByCodeKey(String codeKey);

    InviteCode save(InviteCode inviteCode);

    List<InviteCode> list(int limit);
}
