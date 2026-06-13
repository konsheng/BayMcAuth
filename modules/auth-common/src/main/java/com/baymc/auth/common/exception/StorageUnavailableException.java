package com.baymc.auth.common.exception;

/*
 * 存储不可用异常
 *
 * <p>表示认证依赖的持久化组件不可用, 上层可据此拒绝或降级处理
 */
public final class StorageUnavailableException extends AuthException {
    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
