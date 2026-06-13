package com.baymc.auth.paper.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 高风险操作确认服务
 *
 * <p>按执行者保存待确认操作, 并在确认时校验有效期后执行
 */
public final class PendingConfirmationService {
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public void put(String actor, String action, Runnable runnable, Duration ttl) {
        pending.put(actor, new Pending(action, runnable, Instant.now().plus(ttl)));
    }

    public Optional<Pending> take(String actor) {
        Pending value = pending.remove(actor);
        if (value == null || value.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public record Pending(String action, Runnable runnable, Instant expiresAt) {
    }
}
