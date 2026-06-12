package com.baymc.auth.paper.session;

import com.baymc.auth.common.model.LoginSession;
import com.baymc.auth.storage.cache.LocalSessionStore;
import com.baymc.auth.storage.repository.SessionStore;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/*
 * 会话服务
 *
 * <p>Redis 异常时保留本地会话并向上层报告降级
 * 成功登录总是先写本地会话, Redis 可用时再写 Redis
 */
public final class SessionService implements AutoCloseable {
    private final LocalSessionStore local = new LocalSessionStore();
    private final SessionStore remote;
    private final Consumer<String> warnings;
    private volatile boolean remoteAvailable;

    public SessionService(SessionStore remote, Consumer<String> warnings) {
        this.remote = remote;
        this.warnings = warnings;
        this.remoteAvailable = !(remote instanceof LocalSessionStore);
    }

    public Optional<LoginSession> find(UUID uuid) {
        Optional<LoginSession> localSession = local.find(uuid);
        if (localSession.isPresent()) {
            return localSession;
        }
        if (!remoteAvailable) {
            return Optional.empty();
        }
        try {
            Optional<LoginSession> remoteSession = remote.find(uuid);
            remoteSession.ifPresent(local::save);
            return remoteSession;
        } catch (RuntimeException exception) {
            remoteAvailable = false;
            warnings.accept("Redis 会话读取失败, 已切换为本地会话: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public void save(LoginSession session) {
        local.save(session);
        if (!remoteAvailable) {
            return;
        }
        try {
            remote.save(session);
        } catch (RuntimeException exception) {
            remoteAvailable = false;
            warnings.accept("Redis 会话写入失败, 已切换为本地会话: " + exception.getMessage());
        }
    }

    public void delete(UUID uuid) {
        local.delete(uuid);
        if (remoteAvailable) {
            try {
                remote.delete(uuid);
            } catch (RuntimeException exception) {
                remoteAvailable = false;
                warnings.accept("Redis 会话删除失败, 已切换为本地会话: " + exception.getMessage());
            }
        }
    }

    public boolean remoteAvailable() {
        return remoteAvailable;
    }

    @Override
    public void close() {
        remote.close();
    }
}
