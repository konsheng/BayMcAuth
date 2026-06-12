package com.baymc.auth.storage.redis;

import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.LoginSession;
import com.baymc.auth.storage.repository.SessionStore;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/*
 * Redis 会话存储
 *
 * <p>序列化格式采用简单键值文本, 避免引入额外 JSON 依赖
 * Redis 异常由上层 SessionService 捕获并回退到本地会话
 */
public final class RedisSessionStore implements SessionStore {
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;
    private final String keyPrefix;
    private final Duration expire;

    public RedisSessionStore(AuthConfig.Redis redis, Duration expire) {
        this.client = RedisClient.create(redis.uri());
        this.connection = client.connect();
        this.commands = connection.sync();
        this.keyPrefix = redis.keyPrefix();
        this.expire = expire;
    }

    @Override
    public Optional<LoginSession> find(UUID uuid) {
        String raw = commands.get(key(uuid));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        LoginSession session = decode(raw);
        if (!session.validAt(Instant.now())) {
            delete(uuid);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void save(LoginSession session) {
        commands.set(key(session.uuid()), encode(session), SetArgs.Builder.ex(expire));
    }

    @Override
    public void delete(UUID uuid) {
        commands.del(key(uuid));
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }

    private String key(UUID uuid) {
        return keyPrefix + ":session:" + uuid;
    }

    private String encode(LoginSession session) {
        return "uuid=" + session.uuid()
            + "\nplayer_name=" + safe(session.playerName())
            + "\naccount_type=" + session.accountType()
            + "\nserver_name=" + safe(session.serverName())
            + "\nlogin_time=" + session.loginTime().toEpochMilli()
            + "\nexpire_time=" + session.expireTime().toEpochMilli()
            + "\ntotp_passed=" + session.totpPassed()
            + "\nip=" + safe(session.ip())
            + "\nlast_seen=" + session.lastSeen().toEpochMilli();
    }

    private LoginSession decode(String raw) {
        Map<String, String> map = new HashMap<>();
        raw.lines().forEach(line -> {
            int split = line.indexOf('=');
            if (split > 0) {
                map.put(line.substring(0, split), line.substring(split + 1));
            }
        });
        return new LoginSession(
            UUID.fromString(map.get("uuid")),
            map.get("player_name"),
            AccountType.valueOf(map.get("account_type")),
            map.get("server_name"),
            Instant.ofEpochMilli(Long.parseLong(map.get("login_time"))),
            Instant.ofEpochMilli(Long.parseLong(map.get("expire_time"))),
            Boolean.parseBoolean(map.get("totp_passed")),
            map.get("ip"),
            Instant.ofEpochMilli(Long.parseLong(map.get("last_seen")))
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\n", " ");
    }
}
