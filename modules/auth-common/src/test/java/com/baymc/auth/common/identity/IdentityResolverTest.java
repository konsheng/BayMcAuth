package com.baymc.auth.common.identity;

import com.baymc.auth.common.blacklist.BlacklistService;
import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.config.YamlDocument;
import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.NameLock;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class IdentityResolverTest {
    @Test
    void resolvesInConfiguredPriority() throws Exception {
        AuthConfig config = AuthConfig.from(document("""
            account-types:
              name-affix: true
              name-plain: true
              name-chinese: true
            offline-affix:
              mode: both
              prefixes:
                - "_"
              suffixes:
                - "_"
              case-sensitive: true
              reject-wrong-case: true
            chinese-name:
              allow: true
            """), ignored -> { });
        BlacklistService blacklist = new BlacklistService();
        IdentityResolver resolver = new IdentityResolver(config, blacklist);

        assertEquals(AccountType.OFFLINE_AFFIX, resolver.resolve("_Test", emptyLookup()).accountType());
        assertEquals(AccountType.OFFLINE_CHINESE, resolver.resolve("中文名", emptyLookup()).accountType());
        assertEquals(AccountType.PREMIUM, resolver.resolve("PremiumName", emptyLookup()).accountType());
        assertEquals(AccountType.OFFLINE_PLAIN, resolver.resolve("Reserved", name -> Optional.of(lock())).accountType());
    }

    @Test
    void rejectsBlacklistedName() throws Exception {
        AuthConfig config = AuthConfig.from(document("""
            account-types:
              name-affix: true
              name-plain: true
              name-chinese: true
            """), ignored -> { });
        BlacklistService blacklist = new BlacklistService();
        blacklist.replaceUsernameKeywords(java.util.List.of("admin"));

        assertFalse(new IdentityResolver(config, blacklist).resolve("AdminUser", emptyLookup()).allowed());
    }

    private NameLockLookup emptyLookup() {
        return ignored -> Optional.empty();
    }

    private NameLock lock() {
        return new NameLock(1L, "reserved", "Reserved", null, AccountType.OFFLINE_PLAIN, "ADMIN_RESERVED",
            true, "console", null, Instant.now(), false, null, null, null, null);
    }

    private static YamlDocument document(String text) throws IOException {
        return YamlDocument.fromString(text, defaultConfig());
    }

    private static String defaultConfig() throws IOException {
        return Files.readString(findProjectRoot().resolve("modules/auth-paper/src/main/resources/config.yml"));
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Project root not found");
    }
}
