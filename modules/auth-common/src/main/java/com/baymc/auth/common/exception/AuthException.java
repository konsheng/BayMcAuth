package com.baymc.auth.common.exception;

/*
 * 认证模块基础异常
 *
 * <p>作为平台无关业务异常的父类型, 便于上层统一捕获和降级
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
