package com.baymc.auth.paper.service;

import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.model.FailureActionType;
import com.baymc.auth.common.model.FailureRecord;
import com.baymc.auth.paper.RuntimeConfigRef;
import com.baymc.auth.storage.repository.FailureRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FailureLockService {
    private final RuntimeConfigRef configRef;
    private final FailureRepository failures;

    public FailureLockService(RuntimeConfigRef configRef, FailureRepository failures) {
        this.configRef = configRef;
        this.failures = failures;
    }

    public Optional<String> lockReason(UUID uuid, String ip, FailureActionType type) {
        AuthConfig config = configRef.get();
        if (!config.failureLock().enabled()) {
            return Optional.empty();
        }
        List<AuthConfig.Threshold> thresholds = switch (type) {
            case LOGIN_TOTP, CONFIRM_TOTP, DISABLE_TOTP, RESET_PASSWORD_TOTP -> config.failureLock().totpAccountThresholds();
            default -> config.failureLock().passwordAccountThresholds();
        };
        for (AuthConfig.Threshold threshold : thresholds.reversed()) {
            List<FailureRecord> recent = failures.findSince(uuid, ip, type, Instant.now().minus(threshold.lock()));
            if (recent.size() >= threshold.attempts()) {
                return Optional.of("失败次数过多, 锁定 " + threshold.lock().toSeconds() + " 秒");
            }
        }
        for (AuthConfig.Threshold threshold : config.failureLock().ipThresholds().reversed()) {
            List<FailureRecord> recent = failures.findSince(null, ip, type, Instant.now().minus(threshold.lock()));
            if (recent.size() >= threshold.attempts()) {
                return Optional.of("IP 失败次数过多, 锁定 " + threshold.lock().toSeconds() + " 秒");
            }
        }
        return Optional.empty();
    }
}
