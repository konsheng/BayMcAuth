package com.baymc.auth.common.model;

/*
 * 失败记录动作类型
 *
 * <p>区分注册, 密码登录和 TOTP 验证等会参与失败锁定统计的动作
 */
public enum FailureActionType {
    LOGIN_PASSWORD,
    LOGIN_TOTP,
    RESET_PASSWORD_TOTP,
    REGISTER,
    CONFIRM_TOTP,
    DISABLE_TOTP
}
