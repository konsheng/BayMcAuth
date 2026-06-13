package com.baymc.auth.paper.service;

import com.baymc.auth.common.audit.AuditLogger;
import com.baymc.auth.common.blacklist.BlacklistService;
import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.model.*;
import com.baymc.auth.common.security.InviteCodeGenerator;
import com.baymc.auth.common.security.PasswordHasher;
import com.baymc.auth.common.security.TotpUtil;
import com.baymc.auth.common.util.NameUtil;
import com.baymc.auth.paper.RuntimeConfigRef;
import com.baymc.auth.paper.command.PaperMessageSender;
import com.baymc.auth.paper.protection.LoginStateService;
import com.baymc.auth.paper.session.SessionService;
import com.baymc.auth.storage.repository.*;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/*
 * Paper 认证业务服务
 *
 * <p>命令和监听器只调用该服务
 * 注册, 登录, TOTP, 会话和审计的副作用集中在这里, 避免平台事件中泄露安全细节
 */
public final class AuthService {
    private final RuntimeConfigRef configRef;
    private final UserRepository users;
    private final PasswordHistoryRepository passwordHistory;
    private final InviteCodeRepository inviteCodes;
    private final FailureRepository failures;
    private final IdentityContextRepository identityContexts;
    private final BlacklistService blacklistService;
    private final LoginStateService loginStates;
    private final SessionService sessions;
    private final FailureLockService failureLocks;
    private final AuditLogger audits;
    private final PaperMessageSender messages;
    private final String serverName;
    private volatile boolean databaseAvailable;

    public AuthService(
        RuntimeConfigRef configRef,
        UserRepository users,
        PasswordHistoryRepository passwordHistory,
        InviteCodeRepository inviteCodes,
        FailureRepository failures,
        IdentityContextRepository identityContexts,
        BlacklistService blacklistService,
        LoginStateService loginStates,
        SessionService sessions,
        FailureLockService failureLocks,
        AuditLogger audits,
        PaperMessageSender messages,
        String serverName,
        boolean databaseAvailable
    ) {
        this.configRef = configRef;
        this.users = users;
        this.passwordHistory = passwordHistory;
        this.inviteCodes = inviteCodes;
        this.failures = failures;
        this.identityContexts = identityContexts;
        this.blacklistService = blacklistService;
        this.loginStates = loginStates;
        this.sessions = sessions;
        this.failureLocks = failureLocks;
        this.audits = audits;
        this.messages = messages;
        this.serverName = serverName;
        this.databaseAvailable = databaseAvailable;
    }

    public boolean databaseAvailable() {
        return databaseAvailable;
    }

    public void setDatabaseAvailable(boolean databaseAvailable) {
        this.databaseAvailable = databaseAvailable;
    }

    public void handleJoin(Player player) {
        if (!databaseAvailable) {
            loginStates.set(player.getUniqueId(), player.getName(), AccountType.PREMIUM, AuthStep.DATABASE_UNAVAILABLE, Instant.now().plusSeconds(5));
            player.kick(messages.messages().component("common.database-unavailable", Map.of()));
            return;
        }
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        Optional<IdentityContext> context = identityContexts.findLatest(player.getName(), ip);
        if (context.isEmpty()) {
            loginStates.set(player.getUniqueId(), player.getName(), AccountType.PREMIUM, AuthStep.IDENTITY_MISSING, Instant.now().plusSeconds(5));
            player.kick(messages.messages().component("velocity.denied", Map.of("reason", messages.text("velocity.reason.identity-context-missing"))));
            return;
        }
        identityContexts.consume(context.get().contextId());
        AccountType accountType = context.get().accountType();
        UUID uuid = context.get().uuid() == null ? player.getUniqueId() : context.get().uuid();
        sessions.find(uuid).ifPresentOrElse(session -> {
            loginStates.authenticate(player.getUniqueId(), player.getName(), accountType);
            audit(AuditEventType.PLAYER_LOGIN_SUCCESS, player, accountType, AuditResult.SUCCESS, "会话恢复登录成功");
        }, () -> startAuthFlow(player, uuid, accountType, ip));
    }

    public void logout(Player player) {
        sessions.delete(player.getUniqueId());
        loginStates.set(player.getUniqueId(), player.getName(), currentType(player), AuthStep.LOGIN_REQUIRED, Instant.now().plus(configRef.get().login().timeout()));
        audit(AuditEventType.PLAYER_LOGOUT, player, currentType(player), AuditResult.SUCCESS, "玩家 " + player.getName() + " 登出");
    }

    public boolean register(Player player, String password, String confirm, String inviteCode) {
        ProtectionStateView state = state(player);
        if (state.step() != AuthStep.REGISTER_REQUIRED) {
            messages.send(player, "register.not-required");
            return false;
        }
        if (!databaseAvailable) {
            messages.send(player, "common.database-unavailable");
            return false;
        }
        AuthConfig config = configRef.get();
        if (!validatePassword(player, password, confirm)) {
            recordFailure(player, null, state.accountType(), FailureActionType.REGISTER, "密码不符合要求");
            return false;
        }
        InviteCode usedInvite = null;
        if (config.invite().requireForOfflineRegister() && state.accountType().requiresRegister()) {
            if (inviteCode == null || inviteCode.isBlank()) {
                messages.send(player, "register.invite-required");
                return false;
            }
            Optional<InviteCode> invite = inviteCodes.findByCodeKey(InviteCodeGenerator.key(inviteCode));
            if (invite.isEmpty() || !invite.get().canUse(Instant.now())) {
                messages.send(player, "register.invalid-invite");
                recordFailure(player, null, state.accountType(), FailureActionType.REGISTER, "邀请码无效");
                return false;
            }
            usedInvite = invite.get();
        }
        Instant now = Instant.now();
        UUID uuid = player.getUniqueId();
        String cipher = PasswordHasher.hash(password, config.password().bcryptCost());
        UserRecord user = new UserRecord(0L, uuid, player.getName(), NameUtil.lower(player.getName()), state.accountType(), true,
            config.password().storeCurrentPlain() ? password : null, cipher, false, false, null, null,
            usedInvite == null ? null : usedInvite.code(), usedInvite == null ? null : usedInvite.id(), false, null, null, null,
            null, playerIp(player), null, null, null, now, now);
        user = users.save(user);
        if (config.password().storeHistoryPlain()) {
            passwordHistory.add(new PasswordHistoryEntry(0L, user.uuid(), user.playerName(), user.playerNameLower(), user.accountType(),
                password, PasswordChangeType.REGISTER, player.getName(), player.getUniqueId(), playerIp(player), serverName, now));
        }
        if (usedInvite != null) {
            inviteCodes.save(new InviteCode(usedInvite.id(), usedInvite.code(), usedInvite.codeKey(), usedInvite.batchId(), true,
                user.uuid(), user.playerName(), user.playerNameLower(), user.accountType(), playerIp(player), now, usedInvite.createdBy(),
                usedInvite.createdByUuid(), usedInvite.createdAt(), usedInvite.expiresAt(), usedInvite.revoked(), usedInvite.revokedBy(),
                usedInvite.revokedByUuid(), usedInvite.revokedAt(), usedInvite.note()));
        }
        completeLogin(player, user, true);
        messages.send(player, "register.success", Map.of("player", player.getName()));
        audit(AuditEventType.PLAYER_REGISTER, player, user.accountType(), AuditResult.SUCCESS, "玩家 " + player.getName() + " 注册成功, 类型 " + user.accountType());
        return true;
    }

    public boolean login(Player player, String password, String totpCode) {
        ProtectionStateView state = state(player);
        if (state.step() != AuthStep.LOGIN_REQUIRED && state.step() != AuthStep.TOTP_REQUIRED) {
            messages.send(player, "login.not-required");
            return false;
        }
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty()) {
            messages.send(player, "login.not-registered");
            return false;
        }
        Optional<String> lock = failureLocks.lockReason(user.get().uuid(), playerIp(player), FailureActionType.LOGIN_PASSWORD);
        if (lock.isPresent()) {
            messages.send(player, "login.failed");
            return false;
        }
        if (!PasswordHasher.verify(password, user.get().passwordCipher())) {
            messages.send(player, "login.failed");
            recordFailure(player, user.get().uuid(), user.get().accountType(), FailureActionType.LOGIN_PASSWORD, "密码错误");
            audit(AuditEventType.PLAYER_LOGIN_FAILED, player, user.get().accountType(), AuditResult.FAILED, "玩家 " + player.getName() + " 登录失败, 密码错误");
            return false;
        }
        if (user.get().totpEnabled() && user.get().totpConfirmed()) {
            if (totpCode == null || totpCode.isBlank()) {
                loginStates.set(player.getUniqueId(), player.getName(), user.get().accountType(), AuthStep.TOTP_REQUIRED, Instant.now().plus(configRef.get().login().timeout()));
                messages.send(player, "login.need-totp");
                return true;
            }
            if (!TotpUtil.verify(user.get().totpSecret(), totpCode, configRef.get().totp().digits(), configRef.get().totp().period(), configRef.get().totp().window())) {
                messages.send(player, "totp.code-failed");
                recordFailure(player, user.get().uuid(), user.get().accountType(), FailureActionType.LOGIN_TOTP, "TOTP 错误");
                audit(AuditEventType.TOTP_FAILED, player, user.get().accountType(), AuditResult.FAILED, "玩家 " + player.getName() + " TOTP 验证失败");
                return false;
            }
        }
        completeLogin(player, user.get(), true);
        messages.send(player, "login.success", Map.of("player", player.getName()));
        return true;
    }

    public boolean totpCode(Player player, String code) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty() || !user.get().totpEnabled()) {
            messages.send(player, "totp.code-failed");
            return false;
        }
        if (!TotpUtil.verify(user.get().totpSecret(), code, configRef.get().totp().digits(), configRef.get().totp().period(), configRef.get().totp().window())) {
            messages.send(player, "totp.code-failed");
            recordFailure(player, user.get().uuid(), user.get().accountType(), FailureActionType.LOGIN_TOTP, "TOTP 错误");
            return false;
        }
        completeLogin(player, user.get(), true);
        messages.send(player, "totp.code-success");
        return true;
    }

    public void setupTotp(Player player) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty()) {
            messages.send(player, "login.not-registered");
            return;
        }
        if (user.get().totpEnabled()) {
            messages.send(player, "totp.already-enabled");
            return;
        }
        String secret = TotpUtil.generateSecret();
        users.save(user.get().withTotpPendingSecret(secret, Instant.now()));
        messages.send(player, "totp.setup.secret", Map.of("issuer", configRef.get().totp().issuer(), "account", player.getName(), "secret", secret));
        messages.send(player, "totp.setup.confirm");
        audit(AuditEventType.TOTP_SETUP, player, user.get().accountType(), AuditResult.SUCCESS, "玩家 " + player.getName() + " 创建 TOTP pending secret");
    }

    public boolean confirmTotp(Player player, String code) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty() || user.get().totpPendingSecret() == null) {
            messages.send(player, "totp.no-pending");
            return false;
        }
        if (!TotpUtil.verify(user.get().totpPendingSecret(), code, configRef.get().totp().digits(), configRef.get().totp().period(), configRef.get().totp().window())) {
            messages.send(player, "totp.code-failed");
            recordFailure(player, user.get().uuid(), user.get().accountType(), FailureActionType.CONFIRM_TOTP, "TOTP confirm 错误");
            return false;
        }
        users.save(user.get().withTotpConfirmed(user.get().totpPendingSecret(), Instant.now()));
        messages.send(player, "totp.enabled");
        audit(AuditEventType.TOTP_CONFIRM, player, user.get().accountType(), AuditResult.SUCCESS, "玩家 " + player.getName() + " 启用 TOTP");
        return true;
    }

    public boolean disableTotp(Player player, String password, String code) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty() || !user.get().totpEnabled()) {
            messages.send(player, "totp.disabled");
            return false;
        }
        if (!PasswordHasher.verify(password, user.get().passwordCipher())
            || !TotpUtil.verify(user.get().totpSecret(), code, configRef.get().totp().digits(), configRef.get().totp().period(), configRef.get().totp().window())) {
            messages.send(player, "totp.code-failed");
            recordFailure(player, user.get().uuid(), user.get().accountType(), FailureActionType.DISABLE_TOTP, "关闭 TOTP 验证失败");
            return false;
        }
        users.save(user.get().withoutTotp(Instant.now()));
        messages.send(player, "totp.disabled");
        audit(AuditEventType.TOTP_DISABLE, player, user.get().accountType(), AuditResult.SUCCESS, "玩家 " + player.getName() + " 关闭 TOTP");
        return true;
    }

    public boolean resetPassword(Player player, String password, String confirm, String code) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty() || !user.get().totpEnabled()) {
            messages.send(player, "totp.code-failed");
            return false;
        }
        if (!validatePassword(player, password, confirm)) {
            return false;
        }
        if (!TotpUtil.verify(user.get().totpSecret(), code, configRef.get().totp().digits(), configRef.get().totp().period(), configRef.get().totp().window())) {
            messages.send(player, "totp.code-failed");
            recordFailure(player, user.get().uuid(), user.get().accountType(), FailureActionType.RESET_PASSWORD_TOTP, "TOTP 重置密码失败");
            return false;
        }
        updatePassword(player, user.get(), password, PasswordChangeType.RESET_PASSWORD_TOTP);
        sessions.delete(user.get().uuid());
        loginStates.set(player.getUniqueId(), player.getName(), user.get().accountType(), AuthStep.LOGIN_REQUIRED, Instant.now().plus(configRef.get().login().timeout()));
        messages.send(player, "password.change-success");
        audit(AuditEventType.TOTP_RESET_PASSWORD, player, user.get().accountType(), AuditResult.SUCCESS, "玩家 " + player.getName() + " 使用 TOTP 重置密码");
        return true;
    }

    public boolean passwordEnable(Player player, String password, String confirm) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty()) {
            messages.send(player, "login.not-registered");
            return false;
        }
        if (!validatePassword(player, password, confirm)) {
            return false;
        }
        updatePassword(player, user.get(), password, PasswordChangeType.PREMIUM_ENABLE_PASSWORD);
        messages.send(player, "password.enable-success");
        return true;
    }

    public boolean passwordChange(Player player, String oldPassword, String password, String confirm) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty() || !PasswordHasher.verify(oldPassword, user.get().passwordCipher())) {
            messages.send(player, "login.failed");
            return false;
        }
        if (!validatePassword(player, password, confirm)) {
            return false;
        }
        updatePassword(player, user.get(), password, PasswordChangeType.CHANGE_PASSWORD);
        sessions.delete(user.get().uuid());
        messages.send(player, "password.change-success");
        return true;
    }

    public boolean passwordDisable(Player player, String password) {
        Optional<UserRecord> user = users.findByUuid(player.getUniqueId());
        if (user.isEmpty() || !PasswordHasher.verify(password, user.get().passwordCipher())) {
            messages.send(player, "login.failed");
            return false;
        }
        UserRecord old = user.get();
        UserRecord updated = new UserRecord(old.id(), old.uuid(), old.playerName(), old.playerNameLower(), old.accountType(), false,
            null, null, old.totpEnabled(), old.totpConfirmed(), old.totpSecret(), old.totpPendingSecret(), old.registerInviteCode(),
            old.registerInviteId(), old.locked(), old.lockedReason(), old.lockedBy(), old.lockedByUuid(), old.lockedAt(), old.registerIp(),
            old.lastLoginIp(), old.lastLoginAt(), old.lastServerName(), old.createdAt(), Instant.now());
        users.save(updated);
        messages.send(player, "password.disable-success");
        return true;
    }

    public void remove(Player player) {
        loginStates.remove(player.getUniqueId());
    }

    private void startAuthFlow(Player player, UUID uuid, AccountType accountType, String ip) {
        Optional<UserRecord> user = users.findByUuid(uuid);
        if (user.isPresent() && user.get().locked()) {
            loginStates.set(player.getUniqueId(), player.getName(), accountType, AuthStep.LOCKED, Instant.now().plusSeconds(5));
            player.kick(messages.messages().component("admin.user-info", Map.of(
                "player", player.getName(),
                "uuid", uuid.toString(),
                "account_type", accountType.name(),
                "locked", "true"
            )));
            return;
        }
        if (user.isEmpty() && accountType.requiresRegister()) {
            loginStates.set(player.getUniqueId(), player.getName(), accountType, AuthStep.REGISTER_REQUIRED, Instant.now().plus(configRef.get().login().timeout()));
            messages.send(player, "register.required");
            return;
        }
        if (user.isEmpty()) {
            UserRecord premium = createPremiumUser(player, uuid, ip);
            completeLogin(player, premium, false);
            return;
        }
        if (!user.get().passwordEnabled() && accountType == AccountType.PREMIUM && configRef.get().login().allowPremiumAutoLogin()) {
            completeLogin(player, user.get(), false);
            return;
        }
        loginStates.set(player.getUniqueId(), player.getName(), accountType, AuthStep.LOGIN_REQUIRED, Instant.now().plus(configRef.get().login().timeout()));
        messages.send(player, "login.required");
    }

    private UserRecord createPremiumUser(Player player, UUID uuid, String ip) {
        Instant now = Instant.now();
        UserRecord premium = new UserRecord(0L, uuid, player.getName(), NameUtil.lower(player.getName()), AccountType.PREMIUM, false,
            null, null, false, false, null, null, null, null, false, null, null, null, null, ip, null, null, null, now, now);
        return users.save(premium);
    }

    private void completeLogin(Player player, UserRecord user, boolean totpPassed) {
        Instant now = Instant.now();
        UserRecord updated = users.save(user.withLogin(playerIp(player), serverName, now));
        LoginSession session = new LoginSession(updated.uuid(), updated.playerName(), updated.accountType(), serverName, now,
            now.plus(configRef.get().session().expire()), totpPassed, playerIp(player), now);
        sessions.save(session);
        loginStates.authenticate(player.getUniqueId(), player.getName(), updated.accountType());
        audit(AuditEventType.PLAYER_LOGIN_SUCCESS, player, updated.accountType(), AuditResult.SUCCESS, "玩家 " + player.getName() + " 登录成功, 类型 " + updated.accountType() + ", 服务器 " + serverName);
    }

    private boolean validatePassword(Player player, String password, String confirm) {
        AuthConfig.Password config = configRef.get().password();
        if (!password.equals(confirm)) {
            messages.send(player, "password.confirm-mismatch");
            return false;
        }
        if (password.length() < config.minLength() || password.length() > config.maxLength()) {
            messages.send(player, "password.length", Map.of("min", String.valueOf(config.minLength()), "max", String.valueOf(config.maxLength())));
            return false;
        }
        if (blacklistService.passwordBlocked(password)) {
            messages.send(player, "password.blacklisted");
            audit(AuditEventType.PASSWORD_BLACKLIST_HIT, player, currentType(player), AuditResult.DENIED, "玩家 " + player.getName() + " 密码命中黑名单");
            return false;
        }
        return true;
    }

    private void updatePassword(Player player, UserRecord user, String password, PasswordChangeType changeType) {
        String cipher = PasswordHasher.hash(password, configRef.get().password().bcryptCost());
        UserRecord updated = users.save(user.withPassword(configRef.get().password().storeCurrentPlain() ? password : null, cipher, Instant.now()));
        if (configRef.get().password().storeHistoryPlain()) {
            passwordHistory.add(new PasswordHistoryEntry(0L, updated.uuid(), updated.playerName(), updated.playerNameLower(), updated.accountType(),
                password, changeType, player.getName(), player.getUniqueId(), playerIp(player), serverName, Instant.now()));
        }
    }

    private void recordFailure(Player player, UUID userUuid, AccountType accountType, FailureActionType actionType, String reason) {
        failures.add(new FailureRecord(0L, userUuid, player.getName(), NameUtil.lower(player.getName()), playerIp(player), accountType, actionType, reason, serverName, Instant.now()));
    }

    private void audit(AuditEventType type, Player player, AccountType accountType, AuditResult result, String message) {
        audits.write(AuditEntry.now(type, player.getName(), player.getUniqueId(), accountType, playerIp(player), serverName, result, null, message));
    }

    private String playerIp(Player player) {
        return player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress();
    }

    private AccountType currentType(Player player) {
        return state(player).accountType();
    }

    private ProtectionStateView state(Player player) {
        return loginStates.state(player.getUniqueId())
            .map(state -> new ProtectionStateView(state.accountType(), state.step()))
            .orElse(new ProtectionStateView(AccountType.PREMIUM, AuthStep.AUTHENTICATED));
    }

    private record ProtectionStateView(AccountType accountType, AuthStep step) {
    }
}
