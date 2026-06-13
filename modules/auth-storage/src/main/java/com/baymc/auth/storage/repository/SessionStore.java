package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.LoginSession;

import java.util.Optional;
import java.util.UUID;

/*
 * 会话存储接口
 *
 * <p>抽象本地和 Redis 登录会话读写删除能力
 */
public interface SessionStore extends AutoCloseable {
    Optional<LoginSession> find(UUID uuid);

    void save(LoginSession session);

    void delete(UUID uuid);

    @Override
    default void close() {
    }
}
