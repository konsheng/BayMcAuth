package com.baymc.auth.storage.cache;

import com.baymc.auth.common.model.*;
import com.baymc.auth.common.security.InviteCodeGenerator;
import com.baymc.auth.common.util.NameUtil;
import com.baymc.auth.storage.repository.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/*
 * 本地内存仓库
 *
 * <p>用于开发环境和降级状态
 * 该实现不提供跨进程持久化, 数据库可用时应优先使用 MySQL 仓库
 */
public final class InMemoryRepositories {
    private final AtomicLong ids = new AtomicLong(1L);
    private final Map<UUID, UserRecord> users = new ConcurrentHashMap<>();
    private final Map<String, InviteCode> invites = new ConcurrentHashMap<>();
    private final Map<String, NameLock> nameLocks = new ConcurrentHashMap<>();
    private final List<PasswordHistoryEntry> passwordHistory = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<FailureRecord> failures = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<AuditEntry> audits = java.util.Collections.synchronizedList(new ArrayList<>());
    private final Map<String, IdentityContext> contexts = new ConcurrentHashMap<>();

    public UserRepository users() {
        return new Users();
    }

    public InviteCodeRepository inviteCodes() {
        return new Invites();
    }

    public NameLockRepository nameLocks() {
        return new NameLocks();
    }

    public PasswordHistoryRepository passwordHistory() {
        return new PasswordHistory();
    }

    public FailureRepository failures() {
        return new Failures();
    }

    public AuditRepository audits() {
        return new Audits();
    }

    public IdentityContextRepository identityContexts() {
        return new IdentityContexts();
    }

    private final class Users implements UserRepository {
        @Override
        public Optional<UserRecord> findByUuid(UUID uuid) {
            return Optional.ofNullable(users.get(uuid));
        }

        @Override
        public List<UserRecord> findByName(String playerName) {
            String lower = NameUtil.lower(playerName);
            return users.values().stream().filter(user -> user.playerNameLower().equals(lower)).toList();
        }

        @Override
        public Optional<UserRecord> findByNameAndType(String playerName, AccountType accountType) {
            String lower = NameUtil.lower(playerName);
            return users.values().stream().filter(user -> user.playerNameLower().equals(lower) && user.accountType() == accountType).findFirst();
        }

        @Override
        public UserRecord save(UserRecord user) {
            UserRecord saved = user.id() == 0L ? withId(user, ids.getAndIncrement()) : user;
            users.put(saved.uuid(), saved);
            return saved;
        }

        private UserRecord withId(UserRecord user, long id) {
            return new UserRecord(id, user.uuid(), user.playerName(), user.playerNameLower(), user.accountType(),
                user.passwordEnabled(), user.passwordPlain(), user.passwordCipher(), user.totpEnabled(),
                user.totpConfirmed(), user.totpSecret(), user.totpPendingSecret(), user.registerInviteCode(),
                user.registerInviteId(), user.locked(), user.lockedReason(), user.lockedBy(), user.lockedByUuid(),
                user.lockedAt(), user.registerIp(), user.lastLoginIp(), user.lastLoginAt(), user.lastServerName(),
                user.createdAt(), user.updatedAt());
        }
    }

    private final class Invites implements InviteCodeRepository {
        @Override
        public Optional<InviteCode> findByCodeKey(String codeKey) {
            return Optional.ofNullable(invites.get(InviteCodeGenerator.key(codeKey)));
        }

        @Override
        public InviteCode save(InviteCode inviteCode) {
            InviteCode saved = inviteCode.id() == 0L ? withId(inviteCode, ids.getAndIncrement()) : inviteCode;
            invites.put(saved.codeKey(), saved);
            return saved;
        }

        @Override
        public List<InviteCode> list(int limit) {
            return invites.values().stream().sorted(Comparator.comparing(InviteCode::createdAt).reversed()).limit(limit).toList();
        }

        private InviteCode withId(InviteCode invite, long id) {
            return new InviteCode(id, invite.code(), invite.codeKey(), invite.batchId(), invite.used(), invite.usedByUuid(),
                invite.usedByName(), invite.usedByNameLower(), invite.usedAccountType(), invite.usedIp(), invite.usedAt(),
                invite.createdBy(), invite.createdByUuid(), invite.createdAt(), invite.expiresAt(), invite.revoked(),
                invite.revokedBy(), invite.revokedByUuid(), invite.revokedAt(), invite.note());
        }
    }

    private final class NameLocks implements NameLockRepository {
        @Override
        public Optional<NameLock> findActiveByName(String playerName) {
            return findAnyByName(playerName).filter(NameLock::usable);
        }

        @Override
        public Optional<NameLock> findAnyByName(String playerName) {
            return Optional.ofNullable(nameLocks.get(NameUtil.lower(playerName)));
        }

        @Override
        public NameLock save(NameLock nameLock) {
            NameLock saved = nameLock.id() == 0L ? withId(nameLock, ids.getAndIncrement()) : nameLock;
            nameLocks.put(saved.nameLower(), saved);
            return saved;
        }

        @Override
        public List<NameLock> listActive(int limit) {
            return nameLocks.values().stream().filter(NameLock::usable).limit(limit).toList();
        }

        private NameLock withId(NameLock lock, long id) {
            return new NameLock(id, lock.nameLower(), lock.playerName(), lock.ownerUuid(), lock.accountType(), lock.lockType(),
                lock.active(), lock.createdBy(), lock.createdByUuid(), lock.createdAt(), lock.revoked(), lock.revokedBy(),
                lock.revokedByUuid(), lock.revokedAt(), lock.note());
        }
    }

    private final class PasswordHistory implements PasswordHistoryRepository {
        @Override
        public PasswordHistoryEntry add(PasswordHistoryEntry entry) {
            PasswordHistoryEntry saved = new PasswordHistoryEntry(ids.getAndIncrement(), entry.userUuid(), entry.playerName(),
                entry.playerNameLower(), entry.accountType(), entry.passwordPlain(), entry.changeType(), entry.changedBy(),
                entry.changedByUuid(), entry.ip(), entry.serverName(), entry.createdAt());
            passwordHistory.add(saved);
            return saved;
        }

        @Override
        public List<PasswordHistoryEntry> findByUserUuid(UUID userUuid, int limit) {
            return passwordHistory.stream().filter(entry -> entry.userUuid().equals(userUuid)).limit(limit).toList();
        }
    }

    private final class Failures implements FailureRepository {
        @Override
        public FailureRecord add(FailureRecord failure) {
            FailureRecord saved = new FailureRecord(ids.getAndIncrement(), failure.userUuid(), failure.playerName(),
                failure.playerNameLower(), failure.ip(), failure.accountType(), failure.actionType(), failure.reason(),
                failure.serverName(), failure.failedAt());
            failures.add(saved);
            return saved;
        }

        @Override
        public List<FailureRecord> findSince(UUID userUuid, String ip, FailureActionType actionType, Instant since) {
            return failures.stream().filter(failure ->
                failure.actionType() == actionType
                    && failure.failedAt().isAfter(since)
                    && ((userUuid != null && userUuid.equals(failure.userUuid())) || (ip != null && ip.equals(failure.ip())))
            ).toList();
        }
    }

    private final class Audits implements AuditRepository {
        @Override
        public AuditEntry add(AuditEntry entry) {
            AuditEntry saved = new AuditEntry(ids.getAndIncrement(), entry.eventType(), entry.playerName(), entry.playerNameLower(),
                entry.uuid(), entry.accountType(), entry.ip(), entry.serverName(), entry.result(), entry.reason(), entry.message(),
                entry.createdAt());
            audits.add(saved);
            return saved;
        }
    }

    private final class IdentityContexts implements IdentityContextRepository {
        @Override
        public IdentityContext save(IdentityContext context) {
            contexts.put(context.contextId(), context);
            return context;
        }

        @Override
        public Optional<IdentityContext> findLatest(String playerName, String ip) {
            String lower = NameUtil.lower(playerName);
            Instant now = Instant.now();
            return contexts.values().stream()
                .filter(context -> context.consumedAt() == null)
                .filter(context -> !context.expired(now))
                .filter(context -> context.playerNameLower().equals(lower))
                .filter(context -> ip == null || ip.equals(context.ip()))
                .max(Comparator.comparing(IdentityContext::issuedAt));
        }

        @Override
        public void consume(String contextId) {
            IdentityContext old = contexts.get(contextId);
            if (old != null) {
                contexts.put(contextId, new IdentityContext(old.contextId(), old.playerNameLower(), old.uuid(), old.accountType(),
                    old.ip(), old.serverName(), old.issuedAt(), old.expiresAt(), Instant.now()));
            }
        }

        @Override
        public void purgeExpired() {
            Instant now = Instant.now();
            contexts.values().removeIf(context -> context.expired(now));
        }
    }
}
