package com.baymc.auth.common.audit;

import com.baymc.auth.common.model.AuditEntry;

import java.util.function.Consumer;

public final class ConsoleAuditSink implements AuditSink {
    private final Consumer<String> logger;

    public ConsoleAuditSink(Consumer<String> logger) {
        this.logger = logger;
    }

    @Override
    public void write(AuditEntry entry) {
        logger.accept("[Audit] " + entry.message());
    }
}
