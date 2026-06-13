package com.baymc.auth.common;

import java.time.Duration;

/*
 * 插件固定常量
 *
 * <p>命令名, 插件名和固定权限属于设计契约
 * 这些值不进入运行时配置, 修改后需要重新构建 Jar
 */
public final class BayMcAuthConstants {
    public static final String PLUGIN_ID = "baymcauth";
    public static final String PLUGIN_NAME = "BayMcAuth";
    public static final String VERSION = BayMcAuthBuildInfo.VERSION;
    public static final String DATA_DIRECTORY = "BayMcAuth";
    public static final String MAIN_COMMAND = "baymcauth";
    public static final String MAIN_ALIAS = "auth";
    public static final String PERMISSION_HELP = "baymcauth.help";
    public static final String PERMISSION_STATUS = "baymcauth.status";
    public static final String PERMISSION_REGISTER = "baymcauth.register";
    public static final String PERMISSION_LOGIN = "baymcauth.login";
    public static final String PERMISSION_LOGOUT = "baymcauth.logout";
    public static final String PERMISSION_RESET_PASSWORD = "baymcauth.resetpassword";
    public static final String PERMISSION_CONFIRM = "baymcauth.confirm";
    public static final String PERMISSION_PASSWORD_ENABLE = "baymcauth.password.enable";
    public static final String PERMISSION_PASSWORD_DISABLE = "baymcauth.password.disable";
    public static final String PERMISSION_PASSWORD_CHANGE = "baymcauth.password.change";
    public static final String PERMISSION_TOTP_SETUP = "baymcauth.2fa.setup";
    public static final String PERMISSION_TOTP_CONFIRM = "baymcauth.2fa.confirm";
    public static final String PERMISSION_TOTP_CODE = "baymcauth.2fa.code";
    public static final String PERMISSION_TOTP_DISABLE = "baymcauth.2fa.disable";
    public static final String PERMISSION_TOTP_STATUS = "baymcauth.2fa.status";
    public static final String PERMISSION_RELOAD = "baymcauth.reload";
    public static final String PERMISSION_INVITE_CREATE = "baymcauth.invite.create";
    public static final String PERMISSION_INVITE_LIST = "baymcauth.invite.list";
    public static final String PERMISSION_INVITE_EXPORT = "baymcauth.invite.export";
    public static final String PERMISSION_INVITE_INFO = "baymcauth.invite.info";
    public static final String PERMISSION_INVITE_REVOKE = "baymcauth.invite.revoke";
    public static final String PERMISSION_RESERVE_OFFLINE = "baymcauth.reserve.offline";
    public static final String PERMISSION_RESERVE_INFO = "baymcauth.reserve.info";
    public static final String PERMISSION_RESERVE_LIST = "baymcauth.reserve.list";
    public static final String PERMISSION_RESERVE_REVOKE = "baymcauth.reserve.revoke";
    public static final String PERMISSION_USER_INFO = "baymcauth.user.info";
    public static final String PERMISSION_USER_HISTORY = "baymcauth.user.history";
    public static final String PERMISSION_LOCK = "baymcauth.lock";
    public static final String PERMISSION_UNLOCK = "baymcauth.unlock";
    public static final String PERMISSION_RESET_2FA = "baymcauth.reset2fa";
    public static final String PERMISSION_VELOCITY_HELP = "baymcauth.velocity.help";
    public static final String PERMISSION_VELOCITY_STATUS = "baymcauth.velocity.status";
    public static final String PERMISSION_VELOCITY_RELOAD = "baymcauth.velocity.reload";
    public static final String PERMISSION_VELOCITY_AFFIX_STATUS = "baymcauth.velocity.affix.status";
    public static final String PERMISSION_VELOCITY_AFFIX_RELOAD = "baymcauth.velocity.affix.reload";
    public static final Duration IDENTITY_CONTEXT_TTL = Duration.ofMinutes(2);

    private BayMcAuthConstants() {
    }
}
