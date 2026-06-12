package com.baymc.auth.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YamlDocumentTest {
    @TempDir
    Path tempDir;

    @Test
    void preservesExistingValueAndAppendsMissingKeys() throws Exception {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, """
            # 用户已有注释
            settings:
              language: en_US
            """);

        YamlDocument document = YamlDocument.load(file, """
            settings:
              language: zh_CN
              timezone: Asia/Shanghai
            database:
              type: mysql
            """, ignored -> { });

        Object settings = document.values().get("settings");
        assertTrue(settings instanceof java.util.Map<?, ?>);
        assertEquals("en_US", ((java.util.Map<?, ?>) settings).get("language"));
        String text = Files.readString(file);
        assertTrue(text.contains("用户已有注释"));
        assertTrue(text.contains("timezone"));
        assertTrue(text.contains("database"));
    }

    @Test
    void appendsMissingLoginPromptIntervalFromDefaults() throws Exception {
        Path file = tempDir.resolve("config.yml");
        Files.writeString(file, """
            login-prompt:
              title:
                enabled: false
            """);

        YamlDocument document = YamlDocument.load(file, """
            login-prompt:
              title:
                enabled: true
                send-interval: "10s"
            """, ignored -> { });

        Map<?, ?> loginPrompt = (Map<?, ?>) document.values().get("login-prompt");
        Map<?, ?> title = (Map<?, ?>) loginPrompt.get("title");
        assertEquals(false, title.get("enabled"));
        assertEquals("10s", title.get("send-interval"));
        assertTrue(Files.readString(file).contains("send-interval"));
    }
}
