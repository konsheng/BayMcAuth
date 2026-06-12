package com.baymc.auth.paper;

import com.baymc.auth.common.config.AuthConfig;

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
