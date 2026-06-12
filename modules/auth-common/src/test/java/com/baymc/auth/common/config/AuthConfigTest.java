package com.baymc.auth.common.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class AuthConfigTest {
    @Test
    void loginPromptReadsOnlyDeclaredIntervalKeys() {
        List<String> warnings = new ArrayList<>();

        AuthConfig config = AuthConfig.from(YamlDocument.fromString("""
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
}
