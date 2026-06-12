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
    public static final String VERSION = "1.0.0";
    public static final String DATA_DIRECTORY = "BayMcAuth";
    public static final String MAIN_COMMAND = "baymcauth";
    public static final String MAIN_ALIAS = "auth";
    public static final String PERMISSION_USER = "baymcauth.user";
    public static final String PERMISSION_ADMIN = "baymcauth.admin";
    public static final String PERMISSION_RELOAD = "baymcauth.reload";
    public static final String PERMISSION_INVITE = "baymcauth.invite";
    public static final String PERMISSION_RESERVE = "baymcauth.reserve";
    public static final String PERMISSION_VELOCITY = "baymcauth.velocity";
    public static final Duration IDENTITY_CONTEXT_TTL = Duration.ofMinutes(2);

    private BayMcAuthConstants() {
    }
}
