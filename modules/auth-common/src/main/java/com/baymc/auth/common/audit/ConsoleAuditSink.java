package com.baymc.auth.common.audit;

import com.baymc.auth.common.model.AuditEntry;

import java.util.function.Consumer;

/*
 * 控制台审计输出
 *
 * <p>把审计消息转交给平台日志函数, 用于快速查看运行时审计事件
 */
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
