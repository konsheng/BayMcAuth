package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.AuditEntry;

public interface AuditRepository {
    AuditEntry add(AuditEntry entry);
}
