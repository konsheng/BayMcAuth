package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.IdentityContext;

import java.util.Optional;

/*
 * 身份上下文仓库接口
 *
 * <p>保存和消费 Velocity 到 Paper 的临时身份分流结果
 */
public interface IdentityContextRepository {
    IdentityContext save(IdentityContext context);

    Optional<IdentityContext> findLatest(String playerName, String ip);

    void consume(String contextId);

    void purgeExpired();
}
