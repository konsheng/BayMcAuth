package com.baymc.auth.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BayMcAuthVelocityPluginTest {
    @Test
    void pluginMainDoesNotRegisterItselfAsListener() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-velocity/src/main/java/com/baymc/auth/velocity/BayMcAuthVelocityPlugin.java");
        String text = Files.readString(source);

        assertFalse(text.contains("getEventManager().register(this, this)"));
        assertFalse(text.contains("getEventManager().register(this,this)"));
    }

    @Test
    void databaseFailureStackTraceIsControlledByDebugMode() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-velocity/src/main/java/com/baymc/auth/velocity/BayMcAuthVelocityPlugin.java");
        String text = Files.readString(source);

        assertTrue(text.contains("if (config.settings().debug())"));
        assertTrue(text.contains("Velocity 数据库初始化失败, 新玩家将被拒绝连接"));
        assertTrue(text.contains("\"失败原因\""));
        assertFalse(text.contains("失败原因" + ":"));
        assertTrue(text.contains("ExceptionSummary.databaseFailureLines(exception)"));
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
