package com.baymc.auth.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Velocity 插件源码回归测试
 *
 * <p>验证事件注册, 权限校验, 日志策略和玩家可见文本语言化约束
 */
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

    @Test
    void velocityCommandsUseDetailedActionPermissionsAtRuntime() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-velocity/src/main/java/com/baymc/auth/velocity/BayMcAuthVelocityPlugin.java");
        String text = Files.readString(source);

        assertTrue(text.contains("requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY_HELP)"));
        assertTrue(text.contains("requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY_STATUS)"));
        assertTrue(text.contains("requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY_RELOAD)"));
        assertTrue(text.contains("case \"status\" -> BayMcAuthConstants.PERMISSION_VELOCITY_AFFIX_STATUS"));
        assertTrue(text.contains("case \"reload\" -> BayMcAuthConstants.PERMISSION_VELOCITY_AFFIX_RELOAD"));
        assertFalse(text.contains("requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY)"));
        assertFalse(text.contains("PERMISSION_ADMIN"));
    }

    @Test
    void velocityPluginDescriptionUsesChinese() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-velocity/src/main/java/com/baymc/auth/velocity/BayMcAuthVelocityPlugin.java");
        String text = Files.readString(source);

        assertFalse(text.contains("description = \"BayMcAuth authentication plugin for BayMc\""));
        assertTrue(text.contains("description = \"Velocity 与 Paper/Folia 网络统一认证插件\""));
    }

    @Test
    void velocityPlayerVisibleTextsUseLanguageKeys() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-velocity/src/main/java/com/baymc/auth/velocity/BayMcAuthVelocityPlugin.java");
        String text = Files.readString(source);

        assertFalse(text.contains("Component.text("));
        assertFalse(text.contains("Velocity 端只处理 /baymcauth velocity 子命令"));
        assertFalse(text.contains("deny(event, username, ip, \"数据库不可用"));
        assertFalse(text.contains("deny(event, username, ip, \"身份分流异常"));
        assertTrue(text.contains("deny(event, username, ip, \"velocity.reason.database-unavailable\", Map.of())"));
        assertTrue(text.contains("deny(event, username, ip, \"velocity.reason.identity-route-error\", Map.of())"));
        assertTrue(text.contains("messages.text(reasonKey, reasonPlaceholders)"));
        assertTrue(text.contains("send(invocation, \"velocity.command-velocity-only\")"));
        assertTrue(text.contains("send(invocation, \"velocity.help\")"));
        assertTrue(text.contains("messages.text(databaseAvailable ? \"common.status.available\" : \"common.status.unavailable\")"));
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
