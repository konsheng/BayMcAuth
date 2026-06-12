package com.baymc.auth.common.model;

import java.time.Instant;
import java.util.UUID;

public record UserRecord(
    long id,
    UUID uuid,
    String playerName,
    String playerNameLower,
    AccountType accountType,
    boolean passwordEnabled,
    String passwordPlain,
    String passwordCipher,
    boolean totpEnabled,
    boolean totpConfirmed,
    String totpSecret,
    String totpPendingSecret,
    String registerInviteCode,
    Long registerInviteId,
    boolean locked,
    String lockedReason,
    String lockedBy,
    UUID lockedByUuid,
    Instant lockedAt,
    String registerIp,
    String lastLoginIp,
    Instant lastLoginAt,
    String lastServerName,
    Instant createdAt,
    Instant updatedAt
) {
    public UserRecord withPassword(String plain, String cipher, Instant now) {
        return new UserRecord(id, uuid, playerName, playerNameLower, accountType, true, plain, cipher,
            totpEnabled, totpConfirmed, totpSecret, totpPendingSecret, registerInviteCode, registerInviteId,
            locked, lockedReason, lockedBy, lockedByUuid, lockedAt, registerIp, lastLoginIp, lastLoginAt,
            lastServerName, createdAt, now);
    }

    public UserRecord withTotpPendingSecret(String secret, Instant now) {
        return new UserRecord(id, uuid, playerName, playerNameLower, accountType, passwordEnabled, passwordPlain,
            passwordCipher, totpEnabled, totpConfirmed, totpSecret, secret, registerInviteCode, registerInviteId,
            locked, lockedReason, lockedBy, lockedByUuid, lockedAt, registerIp, lastLoginIp, lastLoginAt,
            lastServerName, createdAt, now);
    }

    public UserRecord withTotpConfirmed(String secret, Instant now) {
        return new UserRecord(id, uuid, playerName, playerNameLower, accountType, passwordEnabled, passwordPlain,
            passwordCipher, true, true, secret, null, registerInviteCode, registerInviteId, locked, lockedReason,
            lockedBy, lockedByUuid, lockedAt, registerIp, lastLoginIp, lastLoginAt, lastServerName, createdAt, now);
    }

    public UserRecord withoutTotp(Instant now) {
        return new UserRecord(id, uuid, playerName, playerNameLower, accountType, passwordEnabled, passwordPlain,
            passwordCipher, false, false, null, null, registerInviteCode, registerInviteId, locked, lockedReason,
            lockedBy, lockedByUuid, lockedAt, registerIp, lastLoginIp, lastLoginAt, lastServerName, createdAt, now);
    }

    public UserRecord withLogin(String ip, String serverName, Instant now) {
        return new UserRecord(id, uuid, playerName, playerNameLower, accountType, passwordEnabled, passwordPlain,
            passwordCipher, totpEnabled, totpConfirmed, totpSecret, totpPendingSecret, registerInviteCode,
            registerInviteId, locked, lockedReason, lockedBy, lockedByUuid, lockedAt, registerIp, ip, now,
            serverName, createdAt, now);
    }
}
