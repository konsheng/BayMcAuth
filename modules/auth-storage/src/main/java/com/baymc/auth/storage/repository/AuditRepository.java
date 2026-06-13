package com.baymc.auth.storage.repository;

import com.baymc.auth.common.model.AuditEntry;

/*
 * 审计仓库接口
 *
 * <p>定义审计事件持久化入口, 供文件外的存储后端实现
 */
public interface AuditRepository {
    AuditEntry add(AuditEntry entry);
}
