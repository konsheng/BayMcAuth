package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.LoginSession;

import java.util.Optional;
import java.util.UUID;

public interface SessionStore extends AutoCloseable {
    Optional<LoginSession> find(UUID uuid);

    void save(LoginSession session);

    void delete(UUID uuid);

    @Override
    default void close() {
    }
}
