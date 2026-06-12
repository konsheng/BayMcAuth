package com.baymc.auth.common.audit;

import com.baymc.auth.common.model.AuditEntry;

import java.util.ArrayList;
import java.util.List;

public final class AuditLogger {
    private final List<AuditSink> sinks = new ArrayList<>();

    public void replaceSinks(List<AuditSink> newSinks) {
        sinks.clear();
        sinks.addAll(newSinks);
    }

    public void addSink(AuditSink sink) {
        sinks.add(sink);
    }

    public void write(AuditEntry entry) {
        for (AuditSink sink : List.copyOf(sinks)) {
            try {
                sink.write(entry);
            } catch (RuntimeException ignored) {
                // 审计 sink 失败不能中断认证主流程
            }
        }
    }
}
