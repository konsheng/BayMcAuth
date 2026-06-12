package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.IdentityContext;

import java.util.Optional;

public interface IdentityContextRepository {
    IdentityContext save(IdentityContext context);

    Optional<IdentityContext> findLatest(String playerName, String ip);

    void consume(String contextId);

    void purgeExpired();
}
