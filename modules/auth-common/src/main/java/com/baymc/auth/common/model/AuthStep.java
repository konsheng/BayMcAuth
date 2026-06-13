package com.baymc.auth.common.model;

/*
 * 玩家认证流程阶段
 *
 * <p>表示玩家当前需要注册, 登录, TOTP 验证或已经通过认证
 */
public enum AuthStep {
    AUTHENTICATED,
    REGISTER_REQUIRED,
    LOGIN_REQUIRED,
    TOTP_REQUIRED,
    LOCKED,
    DATABASE_UNAVAILABLE,
    IDENTITY_MISSING
}
