package com.baymc.auth.storage.cache;

import com.baymc.auth.common.model.LoginSession;
import com.baymc.auth.storage.repository.SessionStore;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 本地会话存储
 *
 * <p>在内存中保存登录会话, 用于 Redis 不可用或本地会话模式
 */
public final class LocalSessionStore implements SessionStore {
    private final Map<UUID, LoginSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<LoginSession> find(UUID uuid) {
        LoginSession session = sessions.get(uuid);
        if (session == null || !session.validAt(Instant.now())) {
            sessions.remove(uuid);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void save(LoginSession session) {
        sessions.put(session.uuid(), session);
    }

    @Override
    public void delete(UUID uuid) {
        sessions.remove(uuid);
    }
}
