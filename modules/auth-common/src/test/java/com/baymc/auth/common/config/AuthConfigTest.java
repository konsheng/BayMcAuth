package com.baymc.auth.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthConfigTest {
    @Test
    void settingsDebugDefaultsToFalse() throws Exception {
        AuthConfig config = AuthConfig.from(document("""
            settings:
              language: zh_CN
              timezone: Asia/Shanghai
            """), ignored -> { });

        assertFalse(config.settings().debug());
    }

    @Test
    void settingsDebugCanBeEnabled() throws Exception {
        AuthConfig config = AuthConfig.from(document("""
            settings:
              language: zh_CN
              timezone: Asia/Shanghai
              debug: true
            """), ignored -> { });

        assertTrue(config.settings().debug());
    }

    @Test
    void databaseTablePrefixDefaultsToBayMcAuthPrefix() throws Exception {
        AuthConfig config = AuthConfig.from(document("""
            database:
              type: mysql
            """), ignored -> { });

        assertEquals("baymcauth_", config.database().tablePrefix());
    }

    @Test
    void typeErrorsUseDefaultsFromDefaultConfig() throws Exception {
        List<String> warnings = new ArrayList<>();

        AuthConfig config = AuthConfig.from(document("""
            database:
              table-prefix:
                - invalid
            login-prompt:
              bossbar:
                update-interval:
                  - invalid
            """), warnings::add);

        assertEquals("baymcauth_", config.database().tablePrefix());
        assertEquals(Duration.ofSeconds(1), config.loginPrompt().bossbar().interval());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("database.table-prefix") && warning.contains("baymcauth_")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("login-prompt.bossbar.update-interval") && warning.contains("1s")));
    }

    @Test
    void loginPromptReadsOnlyDeclaredIntervalKeys() throws Exception {
        List<String> warnings = new ArrayList<>();

        AuthConfig config = AuthConfig.from(document("""
            login-prompt:
              bossbar:
                enabled: true
                update-interval: "1s"
              title:
                enabled: true
                send-interval: "10s"
              subtitle:
                enabled: true
                send-interval: "10s"
              actionbar:
                enabled: true
                update-interval: "1s"
              chat:
                enabled: true
                send-interval: "15s"
            """), warnings::add);

        assertEquals(Duration.ofSeconds(1), config.loginPrompt().bossbar().interval());
        assertEquals(Duration.ofSeconds(10), config.loginPrompt().title().interval());
        assertEquals(Duration.ofSeconds(10), config.loginPrompt().subtitle().interval());
        assertEquals(Duration.ofSeconds(1), config.loginPrompt().actionbar().interval());
        assertEquals(Duration.ofSeconds(15), config.loginPrompt().chat().interval());
        assertFalse(warnings.stream().anyMatch(warning -> warning.contains("login-prompt.bossbar.send-interval")));
        assertFalse(warnings.stream().anyMatch(warning -> warning.contains("login-prompt.title.update-interval")));
        assertFalse(warnings.stream().anyMatch(warning -> warning.contains("login-prompt.subtitle.update-interval")));
        assertFalse(warnings.stream().anyMatch(warning -> warning.contains("login-prompt.actionbar.send-interval")));
        assertFalse(warnings.stream().anyMatch(warning -> warning.contains("login-prompt.chat.update-interval")));
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
