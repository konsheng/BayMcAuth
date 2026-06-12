package com.baymc.auth.common.model;

public enum FailureActionType {
    LOGIN_PASSWORD,
    LOGIN_TOTP,
    RESET_PASSWORD_TOTP,
    REGISTER,
    CONFIRM_TOTP,
    DISABLE_TOTP
}
