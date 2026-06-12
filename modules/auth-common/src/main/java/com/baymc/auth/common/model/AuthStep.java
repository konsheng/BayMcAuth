package com.baymc.auth.common.model;

public enum AuthStep {
    AUTHENTICATED,
    REGISTER_REQUIRED,
    LOGIN_REQUIRED,
    TOTP_REQUIRED,
    LOCKED,
    DATABASE_UNAVAILABLE,
    IDENTITY_MISSING
}
