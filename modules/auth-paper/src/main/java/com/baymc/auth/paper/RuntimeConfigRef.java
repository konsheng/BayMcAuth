package com.baymc.auth.paper;

import com.baymc.auth.common.config.AuthConfig;

/*
 * 运行时配置引用
 *
 * <p>在 reload 后以线程可见的方式替换当前认证配置
 */
public final class RuntimeConfigRef {
    private volatile AuthConfig config;

    public RuntimeConfigRef(AuthConfig config) {
        this.config = config;
    }

    public AuthConfig get() {
        return config;
    }

    public void set(AuthConfig config) {
        this.config = config;
    }
}
