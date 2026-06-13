package com.baymc.auth.common.model;

/*
 * 审计事件结果
 *
 * <p>描述一次审计事件最终是成功, 失败还是被策略拒绝
 */
public enum AuditResult {
    SUCCESS,
    FAILED,
    DENIED,
    DEGRADED
}
