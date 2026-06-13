package com.baymc.auth.storage.cache;

import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.InviteCode;
import com.baymc.auth.common.model.LoginSession;
import com.baymc.auth.common.model.NameLock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * 内存仓库测试
 *
 * <p>覆盖本地降级仓库的用户, 邀请码, 预留名, 失败记录和审计行为
 */
final class InMemoryRepositoriesTest {
    @Test
    void storesInviteAndNameLock() {
        InMemoryRepositories repositories = new InMemoryRepositories();
        Instant now = Instant.now();
        InviteCode invite = repositories.inviteCodes().save(new InviteCode(0L, "BAYMC-AAAA-BBBB", "BAYMC-AAAA-BBBB", "batch",
            false, null, null, null, null, null, null, "console", null, now, now.plusSeconds(60), false, null, null, null, null));
        NameLock lock = repositories.nameLocks().save(new NameLock(0L, "reserved", "Reserved", null, AccountType.OFFLINE_PLAIN,
            "ADMIN_RESERVED", true, "console", null, now, false, null, null, null, null));

        assertTrue(repositories.inviteCodes().findByCodeKey(invite.codeKey()).isPresent());
        assertTrue(repositories.nameLocks().findActiveByName(lock.playerName()).isPresent());
    }

    @Test
    void localSessionExpires() {
        LocalSessionStore store = new LocalSessionStore();
        UUID uuid = UUID.randomUUID();
        store.save(new LoginSession(uuid, "Player", AccountType.PREMIUM, "server", Instant.now(), Instant.now().minusSeconds(1), true, "127.0.0.1", Instant.now()));

        assertFalse(store.find(uuid).isPresent());
    }

    @Test
    void localSessionReturnsValidSession() {
        LocalSessionStore store = new LocalSessionStore();
        UUID uuid = UUID.randomUUID();
        store.save(new LoginSession(uuid, "Player", AccountType.PREMIUM, "server", Instant.now(), Instant.now().plusSeconds(60), true, "127.0.0.1", Instant.now()));

        assertEquals("Player", store.find(uuid).orElseThrow().playerName());
    }
}
