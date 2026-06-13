package com.baymc.auth.common.model;

/*
 * 密码变更类型
 *
 * <p>标记密码历史来源, 区分注册, 重置, 修改和正版账号启用密码
 */
public enum PasswordChangeType {
    REGISTER,
    CHANGE_PASSWORD,
    RESET_PASSWORD_TOTP,
    ADMIN_RESET_PASSWORD,
    PREMIUM_ENABLE_PASSWORD
}
