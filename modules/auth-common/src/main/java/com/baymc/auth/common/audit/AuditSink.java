package com.baymc.auth.common.audit;

import com.baymc.auth.common.model.AuditEntry;

/*
 * 审计输出目标接口
 *
 * <p>用于把统一的审计事件写入控制台, 文件或数据库等后端
 */
public interface AuditSink {
    void write(AuditEntry entry);
}
