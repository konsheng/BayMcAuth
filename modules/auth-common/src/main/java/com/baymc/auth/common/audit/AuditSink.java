package com.baymc.auth.common.audit;

import com.baymc.auth.common.model.AuditEntry;

public interface AuditSink {
    void write(AuditEntry entry);
}
