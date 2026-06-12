package com.baymc.auth.paper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BayMcAuthPaperPluginTest {
    @Test
    void databaseFailureStackTraceIsControlledByDebugMode() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/java/com/baymc/auth/paper/BayMcAuthPaperPlugin.java");
        String text = Files.readString(source);

        assertTrue(text.contains("if (config.settings().debug())"));
        assertTrue(text.contains("数据库初始化失败, BayMcAuth 已进入降级状态"));
        assertTrue(text.contains("\"失败原因\""));
        assertFalse(text.contains("失败原因" + ":"));
        assertTrue(text.contains("ExceptionSummary.databaseFailureLines(exception)"));
        assertTrue(text.contains("getLogger().log(Level.SEVERE"));
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
