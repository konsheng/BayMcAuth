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

    @Test
    void paperCommandsUseDetailedActionPermissionsAtRuntime() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/java/com/baymc/auth/paper/command/BayMcAuthCommand.java");
        String text = Files.readString(source);

        assertFalse(text.contains("canUseUserCommand"));
        assertFalse(text.contains("PERMISSION_ADMIN"));
        assertFalse(text.contains("case \"help\" -> user(sender"));
        assertTrue(text.contains("case \"help\" -> permitted(sender, BayMcAuthConstants.PERMISSION_HELP"));
        assertTrue(text.contains("case \"status\" -> permitted(sender, BayMcAuthConstants.PERMISSION_STATUS"));
        assertTrue(text.contains("case \"register\" -> permitted(sender, BayMcAuthConstants.PERMISSION_REGISTER"));
        assertTrue(text.contains("case \"login\" -> permitted(sender, BayMcAuthConstants.PERMISSION_LOGIN"));
        assertTrue(text.contains("case \"logout\" -> permitted(sender, BayMcAuthConstants.PERMISSION_LOGOUT"));
        assertTrue(text.contains("case \"resetpassword\" -> permitted(sender, BayMcAuthConstants.PERMISSION_RESET_PASSWORD"));
        assertTrue(text.contains("case \"confirm\" -> permitted(sender, BayMcAuthConstants.PERMISSION_CONFIRM"));
        assertTrue(text.contains("case \"velocity\" -> permitted(sender, BayMcAuthConstants.PERMISSION_VELOCITY_HELP"));
        assertContainsAll(text,
            "BayMcAuthConstants.PERMISSION_TOTP_SETUP",
            "BayMcAuthConstants.PERMISSION_TOTP_CONFIRM",
            "BayMcAuthConstants.PERMISSION_TOTP_CODE",
            "BayMcAuthConstants.PERMISSION_TOTP_DISABLE",
            "BayMcAuthConstants.PERMISSION_TOTP_STATUS",
            "BayMcAuthConstants.PERMISSION_PASSWORD_ENABLE",
            "BayMcAuthConstants.PERMISSION_PASSWORD_DISABLE",
            "BayMcAuthConstants.PERMISSION_PASSWORD_CHANGE",
            "BayMcAuthConstants.PERMISSION_INVITE_CREATE",
            "BayMcAuthConstants.PERMISSION_INVITE_LIST",
            "BayMcAuthConstants.PERMISSION_INVITE_EXPORT",
            "BayMcAuthConstants.PERMISSION_INVITE_INFO",
            "BayMcAuthConstants.PERMISSION_INVITE_REVOKE",
            "BayMcAuthConstants.PERMISSION_RESERVE_OFFLINE",
            "BayMcAuthConstants.PERMISSION_RESERVE_INFO",
            "BayMcAuthConstants.PERMISSION_RESERVE_LIST",
            "BayMcAuthConstants.PERMISSION_RESERVE_REVOKE",
            "BayMcAuthConstants.PERMISSION_USER_INFO",
            "BayMcAuthConstants.PERMISSION_USER_HISTORY",
            "BayMcAuthConstants.PERMISSION_LOCK",
            "BayMcAuthConstants.PERMISSION_UNLOCK",
            "BayMcAuthConstants.PERMISSION_RESET_2FA");
    }

    @Test
    void commonConstantsDoNotDefinePermissionPackages() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-common/src/main/java/com/baymc/auth/common/BayMcAuthConstants.java");
        String text = Files.readString(source);

        assertFalse(text.contains("PERMISSION_" + "USER = \"baymcauth." + "user\""));
        assertFalse(text.contains("PERMISSION_" + "ADMIN = \"baymcauth." + "admin\""));
        assertFalse(text.contains("PERMISSION_" + "INVITE = \"baymcauth." + "invite\""));
        assertFalse(text.contains("PERMISSION_" + "RESERVE = \"baymcauth." + "reserve\""));
        assertFalse(text.contains("PERMISSION_" + "VELOCITY = \"baymcauth." + "velocity\""));
        assertTrue(text.contains("PERMISSION_USER_INFO"));
        assertTrue(text.contains("PERMISSION_USER_HISTORY"));
        assertTrue(text.contains("PERMISSION_INVITE_CREATE"));
        assertTrue(text.contains("PERMISSION_RESERVE_OFFLINE"));
        assertTrue(text.contains("PERMISSION_VELOCITY_HELP"));
    }

    @Test
    void pluginYmlAssignsDetailedPermissionsToDirectCommands() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/resources/plugin.yml");
        String text = Files.readString(source).replace("\r\n", "\n");

        assertCommandPermission(text, "register", "baymcauth.register");
        assertCommandPermission(text, "login", "baymcauth.login");
        assertCommandPermission(text, "logout", "baymcauth.logout");
        assertCommandPermission(text, "resetpassword", "baymcauth.resetpassword");
        assertFalse(text.contains("  baymcauth:\n")
            && text.substring(text.indexOf("  baymcauth:\n"), text.indexOf("  register:\n")).contains("permission: \"baymcauth." + "user\""));
        assertFalse(text.contains("  2fa:\n")
            && text.substring(text.indexOf("  2fa:\n"), text.indexOf("  resetpassword:\n")).contains("permission: \"baymcauth." + "user\""));
    }

    @Test
    void pluginYmlDescriptionsUseChinese() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/resources/plugin.yml");
        String text = Files.readString(source).replace("\r\n", "\n");

        assertFalse(text.contains("description: \"BayMcAuth authentication plugin for BayMc\""));
        assertFalse(text.contains("description: \"Alias of "));
        assertFalse(text.contains("短命令"));
        assertFalse(text.contains("需要游戏内执行"));
        assertFalse(text.contains("需要在 Velocity 端执行"));
        assertTrue(text.contains("description: \"Velocity 与 Paper/Folia 网络统一认证插件\""));
        assertTrue(text.contains("description: \"显示帮助并执行 BayMcAuth 认证管理命令\""));
        assertTrue(text.contains("description: \"注册当前账号\""));
        assertTrue(text.contains("description: \"通过 TOTP 重置当前账号密码\""));
    }

    @Test
    void readmeCommandDescriptionsUseActionText() throws Exception {
        Path source = findProjectRoot().resolve("README.md");
        String text = Files.readString(source).replace("\r\n", "\n");
        String commands = section(text, "## ⌨️ 命令\n", "## 🔐 权限\n");

        assertFalse(commands.contains("短命令"));
        assertFalse(commands.contains("的短命令"));
        assertFalse(commands.contains("需要游戏内执行"));
        assertFalse(commands.contains("需要在 Velocity 端执行"));
        assertFalse(commands.contains("需要 `/baymcauth confirm` 确认"));
        assertTrue(commands.contains("- **`/register <密码> <确认密码> <邀请码>`**<br>\n  权限：`baymcauth.register`<br>\n  注册当前账号"));
        assertTrue(commands.contains("- **`/2fa setup`**<br>\n  权限：`baymcauth.2fa.setup`<br>\n  生成 TOTP 手动 secret"));
        assertTrue(commands.contains("- **`/resetpassword <新密码> <确认密码> <TOTP 验证码>`**<br>\n  权限：`baymcauth.resetpassword`<br>\n  通过 TOTP 重置当前账号密码"));
        assertTrue(commands.contains("- **`/baymcauth velocity status`**<br>\n  权限：`baymcauth.velocity.status`<br>\n  查看 Velocity 端数据库状态"));
    }

    @Test
    void readmePermissionEntriesUseLineBreakFormat() throws Exception {
        Path source = findProjectRoot().resolve("README.md");
        String text = Files.readString(source).replace("\r\n", "\n");
        String permissions = section(text, "## 🔐 权限\n", "## 🛡️ 数据安全\n");

        assertFalse(permissions.contains("- `baymcauth."));
        assertFalse(permissions.contains("`\n默认："));
        assertFalse(permissions.contains("- **`baymcauth." + "user`**"));
        assertFalse(permissions.contains("- **`baymcauth." + "admin`**"));
        assertFalse(permissions.contains("- **`baymcauth." + "invite`**"));
        assertFalse(permissions.contains("- **`baymcauth." + "reserve`**"));
        assertFalse(permissions.contains("- **`baymcauth." + "velocity`**"));
        assertTrue(permissions.contains("- **`baymcauth.register`**<br>\n  默认：`true`<br>\n  注册当前账号"));
        assertTrue(permissions.contains("- **`baymcauth.velocity.affix.reload`**<br>\n  默认：`op`<br>\n  显示 Velocity 端离线名前后缀模式"));
    }

    @Test
    void pluginYmlPermissionDescriptionsUseChinese() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/resources/plugin.yml");
        String text = Files.readString(source).replace("\r\n", "\n");

        assertPermissionDescription(text, "baymcauth.help", "查看 BayMcAuth 帮助");
        assertPermissionDescription(text, "baymcauth.status", "查看 Paper/Folia 端认证服务状态");
        assertPermissionDescription(text, "baymcauth.register", "注册当前账号");
        assertPermissionDescription(text, "baymcauth.login", "登录当前账号");
        assertPermissionDescription(text, "baymcauth.logout", "登出当前账号");
        assertPermissionDescription(text, "baymcauth.resetpassword", "通过 TOTP 重置当前账号密码");
        assertPermissionDescription(text, "baymcauth.confirm", "确认当前执行者名下的高风险操作");
        assertPermissionDescription(text, "baymcauth.password.enable", "为当前账号启用密码登录");
        assertPermissionDescription(text, "baymcauth.password.disable", "关闭当前账号密码登录");
        assertPermissionDescription(text, "baymcauth.password.change", "修改当前账号密码");
        assertPermissionDescription(text, "baymcauth.2fa.setup", "创建当前账号的 TOTP 待确认密钥");
        assertPermissionDescription(text, "baymcauth.2fa.confirm", "确认并启用当前账号 TOTP");
        assertPermissionDescription(text, "baymcauth.2fa.code", "提交当前账号 TOTP 验证码");
        assertPermissionDescription(text, "baymcauth.2fa.disable", "关闭当前账号 TOTP");
        assertPermissionDescription(text, "baymcauth.2fa.status", "查看当前账号 TOTP 状态");
        assertPermissionDescription(text, "baymcauth.reload", "重载 Paper/Folia 端配置和语言文件");
        assertPermissionDescription(text, "baymcauth.invite.create", "创建邀请码");
        assertPermissionDescription(text, "baymcauth.invite.list", "列出邀请码");
        assertPermissionDescription(text, "baymcauth.invite.export", "导出邀请码列表");
        assertPermissionDescription(text, "baymcauth.invite.info", "查看邀请码详情");
        assertPermissionDescription(text, "baymcauth.invite.revoke", "撤销邀请码");
        assertPermissionDescription(text, "baymcauth.reserve.offline", "预留无前后缀离线名");
        assertPermissionDescription(text, "baymcauth.reserve.info", "查看预留名详情");
        assertPermissionDescription(text, "baymcauth.reserve.list", "列出预留名");
        assertPermissionDescription(text, "baymcauth.reserve.revoke", "撤销预留名");
        assertPermissionDescription(text, "baymcauth.user.info", "查看用户基础信息");
        assertPermissionDescription(text, "baymcauth.user.history", "查看用户历史信息");
        assertPermissionDescription(text, "baymcauth.lock", "锁定用户账号");
        assertPermissionDescription(text, "baymcauth.unlock", "解锁用户账号");
        assertPermissionDescription(text, "baymcauth.reset2fa", "重置玩家 TOTP");
        assertPermissionDescription(text, "baymcauth.velocity.help", "查看 Velocity 端 BayMcAuth 帮助");
        assertPermissionDescription(text, "baymcauth.velocity.status", "查看 Velocity 端认证服务状态");
        assertPermissionDescription(text, "baymcauth.velocity.reload", "重载 Velocity 端配置和语言文件");
        assertPermissionDescription(text, "baymcauth.velocity.affix.status", "查看 Velocity 端离线名前后缀模式");
        assertPermissionDescription(text, "baymcauth.velocity.affix.reload", "重新显示 Velocity 端离线名前后缀模式");
    }

    @Test
    void pluginYmlDefinesOnlyDetailedPermissions() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/resources/plugin.yml");
        String text = Files.readString(source).replace("\r\n", "\n");

        assertFalse(text.contains("  baymcauth." + "user:\n"));
        assertFalse(text.contains("  baymcauth." + "admin:\n"));
        assertFalse(text.contains("  baymcauth." + "invite:\n"));
        assertFalse(text.contains("  baymcauth." + "reserve:\n"));
        assertFalse(text.contains("  baymcauth." + "velocity:\n"));
        assertFalse(text.contains("children:"));

        assertTrue(permissionEntry(text, "baymcauth.help").contains("    default: true"));
        assertTrue(permissionEntry(text, "baymcauth.register").contains("    default: true"));
        assertTrue(permissionEntry(text, "baymcauth.password.change").contains("    default: true"));
        assertTrue(permissionEntry(text, "baymcauth.2fa.status").contains("    default: true"));
        assertTrue(permissionEntry(text, "baymcauth.reload").contains("    default: \"op\""));
        assertTrue(permissionEntry(text, "baymcauth.invite.create").contains("    default: \"op\""));
        assertTrue(permissionEntry(text, "baymcauth.reserve.offline").contains("    default: \"op\""));
        assertTrue(permissionEntry(text, "baymcauth.velocity.affix.reload").contains("    default: \"op\""));
    }

    @Test
    void defaultLanguageContainsPlayerVisibleRuntimeKeys() throws Exception {
        Path source = findProjectRoot().resolve("modules/auth-paper/src/main/resources/lang/zh_CN.yml");
        String text = Files.readString(source).replace("\r\n", "\n");

        assertContainsAll(text,
            "  status:\n",
            "    available: \"可用\"",
            "    unavailable: \"不可用\"",
            "    unknown: \"未知\"",
            "  action:\n",
            "    revoke-invite: \"撤销邀请码 <code>\"",
            "    revoke-reserve: \"撤销预留名 <player>\"",
            "    lock-user: \"锁定用户 <player>\"",
            "    unlock-user: \"解锁用户 <player>\"",
            "    reset-totp: \"重置玩家 TOTP <player>\"",
            "  command-velocity-only: \"<prefix><red>Velocity 端只处理 /baymcauth velocity 子命令</red>\"",
            "  help:\n",
            "  reason:\n",
            "    database-unavailable: \"数据库不可用, 无法完成身份分流\"",
            "    identity-route-error: \"身份分流异常\"",
            "    identity-name-invalid: \"玩家名格式无效\"",
            "    username-blacklisted: \"玩家名命中黑名单\"",
            "    offline-affix-case-invalid: \"离线名前后缀大小写错误\"",
            "    identity-context-missing: \"缺少 Velocity 身份分流结果\"");
    }

    @Test
    void paperPlayerVisibleDynamicTextsUseLanguageKeys() throws Exception {
        Path command = findProjectRoot().resolve("modules/auth-paper/src/main/java/com/baymc/auth/paper/command/BayMcAuthCommand.java");
        String commandText = Files.readString(command);
        Path service = findProjectRoot().resolve("modules/auth-paper/src/main/java/com/baymc/auth/paper/service/AuthService.java");
        String serviceText = Files.readString(service);

        assertContainsAll(commandText,
            "messages.text(authService.databaseAvailable() ? \"common.status.available\" : \"common.status.unavailable\")",
            "messages.text(\"common.status.unknown\")",
            "messages.text(\"admin.action.revoke-invite\"",
            "messages.text(\"admin.action.revoke-reserve\"",
            "messages.text(\"admin.action.lock-user\"",
            "messages.text(\"admin.action.unlock-user\"",
            "messages.text(\"admin.action.reset-totp\"");
        assertFalse(commandText.contains("Map.of(\"action\", \"撤销"));
        assertFalse(commandText.contains("Map.of(\"action\", \"锁定"));
        assertFalse(commandText.contains("Map.of(\"action\", \"解锁"));
        assertFalse(commandText.contains("Map.of(\"action\", \"重置"));
        assertTrue(serviceText.contains("messages.text(\"velocity.reason.identity-context-missing\")"));
        assertFalse(serviceText.contains("\"缺少 Velocity 身份分流结果\""));
    }

    private static void assertCommandPermission(String text, String command, String permission) {
        assertTrue(text.contains("  " + command + ":\n")
            && text.contains("    permission: \"" + permission + "\""));
    }

    private static void assertContainsAll(String text, String... expected) {
        for (String value : expected) {
            assertTrue(text.contains(value), value);
        }
    }

    private static void assertPermissionDescription(String text, String permission, String description) {
        String entry = permissionEntry(text, permission);
        String expected = "    description: \"" + description + "\"";
        assertTrue(entry.contains(expected), permission);
        assertFalse(entry.contains("Alias of"), permission);
        assertFalse(entry.contains("短命令"), permission);
    }

    private static String permissionEntry(String text, String permission) {
        String marker = "  " + permission + ":\n";
        int from = text.indexOf(marker);
        assertTrue(from >= 0, marker);
        int to = text.indexOf("\n  baymcauth.", from + marker.length());
        return to >= 0 ? text.substring(from, to) : text.substring(from);
    }

    private static String section(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.indexOf(end, from + start.length());
        assertTrue(from >= 0, start);
        assertTrue(to > from, end);
        return text.substring(from, to);
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
