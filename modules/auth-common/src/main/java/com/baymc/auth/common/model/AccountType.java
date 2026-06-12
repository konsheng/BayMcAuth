package com.baymc.auth.common.model;

/*
 * 固定账号类型
 *
 * <p>Velocity 端根据玩家名和管理员预留规则选择类型
 * Paper 端根据类型决定注册, 登录和 TOTP 流程
 */
public enum AccountType {
    PREMIUM,
    OFFLINE_AFFIX,
    OFFLINE_PLAIN,
    OFFLINE_CHINESE;

    public boolean requiresRegister() {
        return this != PREMIUM;
    }
}
