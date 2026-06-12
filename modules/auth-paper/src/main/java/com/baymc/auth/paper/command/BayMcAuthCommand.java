package com.baymc.auth.paper.command;

import com.baymc.auth.common.BayMcAuthConstants;
import com.baymc.auth.common.model.AccountType;
import com.baymc.auth.common.model.UserRecord;
import com.baymc.auth.paper.RuntimeConfigRef;
import com.baymc.auth.paper.service.AuthService;
import com.baymc.auth.paper.service.InviteService;
import com.baymc.auth.paper.service.PendingConfirmationService;
import com.baymc.auth.paper.service.ReserveService;
import com.baymc.auth.storage.repository.UserRepository;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/*
 * Paper 主命令分发
 *
 * <p>/auth 是 /baymcauth 的固定别名
 * 短命令通过 ShortCommand 进入同一套分发逻辑, 避免行为分叉
 */
public final class BayMcAuthCommand implements CommandExecutor, TabCompleter {
    private final AuthService authService;
    private final PaperMessageSender messages;
    private final RuntimeConfigRef configRef;
    private final InviteService inviteService;
    private final ReserveService reserveService;
    private final PendingConfirmationService confirmations;
    private final UserRepository users;
    private final Runnable reloadAction;

    public BayMcAuthCommand(
        AuthService authService,
        PaperMessageSender messages,
        RuntimeConfigRef configRef,
        InviteService inviteService,
        ReserveService reserveService,
        PendingConfirmationService confirmations,
        UserRepository users,
        Runnable reloadAction
    ) {
        this.authService = authService;
        this.messages = messages;
        this.configRef = configRef;
        this.inviteService = inviteService;
        this.reserveService = reserveService;
        this.confirmations = confirmations;
        this.users = users;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return permitted(sender, BayMcAuthConstants.PERMISSION_HELP, () -> {
                messages.send(sender, sender.hasPermission(BayMcAuthConstants.PERMISSION_ADMIN) ? "help.admin" : "help.user");
                return true;
            });
        }
        return execute(sender, args[0].toLowerCase(java.util.Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
    }

    public boolean execute(CommandSender sender, String subcommand, String[] args) {
        return switch (subcommand) {
            case "help" -> permitted(sender, BayMcAuthConstants.PERMISSION_HELP, () -> help(sender));
            case "status" -> permitted(sender, BayMcAuthConstants.PERMISSION_STATUS, () -> status(sender));
            case "register" -> permitted(sender, BayMcAuthConstants.PERMISSION_REGISTER, () -> player(sender, player -> requireArgs(sender, args, 3) && authService.register(player, args[0], args[1], args[2])));
            case "login" -> permitted(sender, BayMcAuthConstants.PERMISSION_LOGIN, () -> player(sender, player -> requireArgs(sender, args, 1) && authService.login(player, args[0], args.length >= 2 ? args[1] : null)));
            case "logout" -> permitted(sender, BayMcAuthConstants.PERMISSION_LOGOUT, () -> player(sender, player -> {
                authService.logout(player);
                messages.send(sender, "login.required");
                return true;
            }));
            case "2fa" -> player(sender, player -> totp(player, args));
            case "resetpassword" -> permitted(sender, BayMcAuthConstants.PERMISSION_RESET_PASSWORD, () -> player(sender, player -> requireArgs(sender, args, 3) && authService.resetPassword(player, args[0], args[1], args[2])));
            case "password" -> player(sender, player -> password(player, args));
            case "invite" -> invite(sender, args);
            case "reserve" -> reserve(sender, args);
            case "user" -> user(sender, args);
            case "lock" -> admin(sender, BayMcAuthConstants.PERMISSION_LOCK, () -> requireArgs(sender, args, 2) && lock(sender, args[0], join(args, 1)));
            case "unlock" -> admin(sender, BayMcAuthConstants.PERMISSION_UNLOCK, () -> requireArgs(sender, args, 1) && unlock(sender, args[0]));
            case "reset2fa" -> admin(sender, BayMcAuthConstants.PERMISSION_RESET_2FA, () -> requireArgs(sender, args, 1) && reset2fa(sender, args[0]));
            case "confirm" -> permitted(sender, BayMcAuthConstants.PERMISSION_CONFIRM, () -> confirm(sender));
            case "reload" -> admin(sender, BayMcAuthConstants.PERMISSION_RELOAD, () -> {
                reloadAction.run();
                messages.send(sender, "admin.reload-success");
                return true;
            });
            case "velocity" -> permitted(sender, BayMcAuthConstants.PERMISSION_VELOCITY_HELP, () -> {
                messages.send(sender, "velocity.command-on-paper");
                return true;
            });
            default -> {
                messages.send(sender, "common.unknown-command");
                yield true;
            }
        };
    }

    private boolean help(CommandSender sender) {
        messages.send(sender, sender.hasPermission(BayMcAuthConstants.PERMISSION_ADMIN) ? "help.admin" : "help.user");
        return true;
    }

    private boolean status(CommandSender sender) {
        messages.send(sender, "status.line", Map.of(
            "database", authService.databaseAvailable() ? "available" : "unavailable",
            "redis", "unknown"
        ));
        return true;
    }

    private boolean totp(Player player, String[] args) {
        if (!requireArgs(player, args, 1)) {
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "setup" -> permitted(player, BayMcAuthConstants.PERMISSION_TOTP_SETUP, () -> {
                authService.setupTotp(player);
                return true;
            });
            case "confirm" -> permitted(player, BayMcAuthConstants.PERMISSION_TOTP_CONFIRM, () -> requireArgs(player, args, 2) && authService.confirmTotp(player, args[1]));
            case "code" -> permitted(player, BayMcAuthConstants.PERMISSION_TOTP_CODE, () -> requireArgs(player, args, 2) && authService.totpCode(player, args[1]));
            case "disable" -> permitted(player, BayMcAuthConstants.PERMISSION_TOTP_DISABLE, () -> requireArgs(player, args, 3) && authService.disableTotp(player, args[1], args[2]));
            case "status" -> permitted(player, BayMcAuthConstants.PERMISSION_TOTP_STATUS, () -> {
                UserRecord user = users.findByUuid(player.getUniqueId()).orElse(null);
                messages.send(player, "totp.status", Map.of(
                    "enabled", String.valueOf(user != null && user.totpEnabled()),
                    "confirmed", String.valueOf(user != null && user.totpConfirmed())
                ));
                return true;
            });
            default -> {
                messages.send(player, "common.unknown-command");
                yield true;
            }
        };
    }

    private boolean password(Player player, String[] args) {
        if (!requireArgs(player, args, 1)) {
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "enable" -> permitted(player, BayMcAuthConstants.PERMISSION_PASSWORD_ENABLE, () -> requireArgs(player, args, 3) && authService.passwordEnable(player, args[1], args[2]));
            case "disable" -> permitted(player, BayMcAuthConstants.PERMISSION_PASSWORD_DISABLE, () -> requireArgs(player, args, 2) && authService.passwordDisable(player, args[1]));
            case "change" -> permitted(player, BayMcAuthConstants.PERMISSION_PASSWORD_CHANGE, () -> requireArgs(player, args, 4) && authService.passwordChange(player, args[1], args[2], args[3]));
            default -> {
                messages.send(player, "common.unknown-command");
                yield true;
            }
        };
    }

    private boolean invite(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 1)) {
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "create" -> admin(sender, BayMcAuthConstants.PERMISSION_INVITE_CREATE, () -> {
                int count = args.length >= 2 ? Integer.parseInt(args[1]) : 1;
                int days = args.length >= 3 ? Integer.parseInt(args[2]) : configRef.get().invite().defaultExpireDays();
                var codes = inviteService.create(sender, count, days);
                messages.send(sender, "invite.created", Map.of("count", String.valueOf(codes.size()), "codes", String.join(", ", codes.stream().map(item -> item.code()).toList())));
                return true;
            });
            case "list" -> admin(sender, BayMcAuthConstants.PERMISSION_INVITE_LIST, () -> {
                inviteService.list().forEach(invite -> messages.send(sender, "invite.list-item", inviteMap(invite)));
                return true;
            });
            case "export" -> admin(sender, BayMcAuthConstants.PERMISSION_INVITE_EXPORT, () -> {
                inviteService.list().forEach(invite -> messages.send(sender, "invite.list-item", inviteMap(invite)));
                return true;
            });
            case "info" -> admin(sender, BayMcAuthConstants.PERMISSION_INVITE_INFO, () -> {
                if (requireArgs(sender, args, 2)) {
                    messages.send(sender, "invite.info", inviteMap(inviteService.info(args[1])));
                }
                return true;
            });
            case "revoke" -> admin(sender, BayMcAuthConstants.PERMISSION_INVITE_REVOKE, () -> {
                if (requireArgs(sender, args, 2)) {
                    String code = args[1];
                    confirmations.put(sender.getName(), "撤销邀请码 " + code, () -> {
                        inviteService.revoke(code, sender);
                        messages.send(sender, "invite.revoked", Map.of("code", code));
                    }, configRef.get().confirm().expire());
                    messages.send(sender, "admin.confirm-needed", Map.of("action", "撤销邀请码 " + code));
                }
                return true;
            });
            default -> {
                messages.send(sender, "common.unknown-command");
                yield true;
            }
        };
    }

    private boolean reserve(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 1)) {
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "offline" -> admin(sender, BayMcAuthConstants.PERMISSION_RESERVE_OFFLINE, () -> {
                if (requireArgs(sender, args, 2)) {
                    var lock = reserveService.reserve(sender, args[1]);
                    messages.send(sender, "admin.reserve-success", Map.of("player", lock.playerName()));
                }
                return true;
            });
            case "info" -> admin(sender, BayMcAuthConstants.PERMISSION_RESERVE_INFO, () -> {
                if (requireArgs(sender, args, 2)) {
                    var lock = reserveService.info(args[1]);
                    messages.send(sender, "admin.user-info", Map.of("player", lock.playerName(), "uuid", String.valueOf(lock.ownerUuid()), "account_type", lock.accountType().name(), "locked", String.valueOf(!lock.usable())));
                }
                return true;
            });
            case "list" -> admin(sender, BayMcAuthConstants.PERMISSION_RESERVE_LIST, () -> {
                reserveService.list().forEach(lock -> messages.send(sender, "admin.user-info", Map.of("player", lock.playerName(), "uuid", String.valueOf(lock.ownerUuid()), "account_type", lock.accountType().name(), "locked", String.valueOf(!lock.usable()))));
                return true;
            });
            case "revoke" -> admin(sender, BayMcAuthConstants.PERMISSION_RESERVE_REVOKE, () -> {
                if (requireArgs(sender, args, 2)) {
                    String playerName = args[1];
                    confirmations.put(sender.getName(), "撤销预留名 " + playerName, () -> {
                        reserveService.revoke(sender, playerName);
                        messages.send(sender, "admin.reserve-revoked", Map.of("player", playerName));
                    }, configRef.get().confirm().expire());
                    messages.send(sender, "admin.confirm-needed", Map.of("action", "撤销预留名 " + playerName));
                }
                return true;
            });
            default -> {
                messages.send(sender, "common.unknown-command");
                yield true;
            }
        };
    }

    private boolean user(CommandSender sender, String[] args) {
        if (!requireArgs(sender, args, 2)) {
            return true;
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "info" -> admin(sender, BayMcAuthConstants.PERMISSION_USER_INFO, () -> userInfo(sender, args[1]));
            case "history" -> admin(sender, BayMcAuthConstants.PERMISSION_USER_HISTORY, () -> userInfo(sender, args[1]));
            default -> {
                messages.send(sender, "common.unknown-command");
                yield true;
            }
        };
    }

    private boolean userInfo(CommandSender sender, String playerName) {
        var found = users.findByName(playerName).stream().findFirst();
        if (found.isEmpty()) {
            messages.send(sender, "admin.user-not-found");
            return true;
        }
        UserRecord user = found.get();
        messages.send(sender, "admin.user-info", Map.of(
            "player", user.playerName(),
            "uuid", user.uuid().toString(),
            "account_type", user.accountType().name(),
            "locked", String.valueOf(user.locked())
        ));
        return true;
    }

    private boolean lock(CommandSender sender, String playerName, String reason) {
        UserRecord user = users.findByName(playerName).stream().findFirst().orElseThrow();
        confirmations.put(sender.getName(), "锁定用户 " + playerName, () -> {
            UserRecord locked = new UserRecord(user.id(), user.uuid(), user.playerName(), user.playerNameLower(), user.accountType(), user.passwordEnabled(),
                user.passwordPlain(), user.passwordCipher(), user.totpEnabled(), user.totpConfirmed(), user.totpSecret(), user.totpPendingSecret(),
                user.registerInviteCode(), user.registerInviteId(), true, reason, sender.getName(), sender instanceof Player player ? player.getUniqueId() : null,
                Instant.now(), user.registerIp(), user.lastLoginIp(), user.lastLoginAt(), user.lastServerName(), user.createdAt(), Instant.now());
            users.save(locked);
            messages.send(sender, "admin.lock-success", Map.of("player", playerName));
        }, configRef.get().confirm().expire());
        messages.send(sender, "admin.confirm-needed", Map.of("action", "锁定用户 " + playerName));
        return true;
    }

    private boolean unlock(CommandSender sender, String playerName) {
        UserRecord user = users.findByName(playerName).stream().findFirst().orElseThrow();
        confirmations.put(sender.getName(), "解锁用户 " + playerName, () -> {
            UserRecord unlocked = new UserRecord(user.id(), user.uuid(), user.playerName(), user.playerNameLower(), user.accountType(), user.passwordEnabled(),
                user.passwordPlain(), user.passwordCipher(), user.totpEnabled(), user.totpConfirmed(), user.totpSecret(), user.totpPendingSecret(),
                user.registerInviteCode(), user.registerInviteId(), false, null, null, null, null, user.registerIp(), user.lastLoginIp(),
                user.lastLoginAt(), user.lastServerName(), user.createdAt(), Instant.now());
            users.save(unlocked);
            messages.send(sender, "admin.unlock-success", Map.of("player", playerName));
        }, configRef.get().confirm().expire());
        messages.send(sender, "admin.confirm-needed", Map.of("action", "解锁用户 " + playerName));
        return true;
    }

    private boolean reset2fa(CommandSender sender, String playerName) {
        UserRecord user = users.findByName(playerName).stream().findFirst().orElseThrow();
        confirmations.put(sender.getName(), "重置玩家 TOTP " + playerName, () -> {
            UserRecord reset = user.withoutTotp(Instant.now());
            users.save(reset);
            messages.send(sender, "admin.reset2fa-success", Map.of("player", playerName));
        }, configRef.get().confirm().expire());
        messages.send(sender, "admin.confirm-needed", Map.of("action", "重置玩家 TOTP " + playerName));
        return true;
    }

    private boolean confirm(CommandSender sender) {
        var pending = confirmations.take(sender.getName());
        if (pending.isEmpty()) {
            messages.send(sender, "admin.confirm-none");
            return true;
        }
        pending.get().runnable().run();
        messages.send(sender, "admin.confirm-success", Map.of("action", pending.get().action()));
        return true;
    }

    private boolean player(CommandSender sender, PlayerAction action) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "common.player-only");
            return true;
        }
        return action.run(player);
    }

    private boolean permitted(CommandSender sender, String permission, UserAction action) {
        if (!sender.hasPermission(permission) && !sender.hasPermission(BayMcAuthConstants.PERMISSION_ADMIN)) {
            messages.send(sender, "common.no-permission");
            return true;
        }
        return action.run();
    }

    private boolean admin(CommandSender sender, String permission, AdminAction action) {
        if (!sender.hasPermission(permission) && !sender.hasPermission(BayMcAuthConstants.PERMISSION_ADMIN)) {
            messages.send(sender, "common.no-permission");
            return true;
        }
        try {
            return action.run();
        } catch (RuntimeException exception) {
            messages.send(sender, "admin.user-not-found");
            return true;
        }
    }

    private boolean requireArgs(CommandSender sender, String[] args, int count) {
        if (args.length < count) {
            messages.send(sender, "common.unknown-command");
            return false;
        }
        return true;
    }

    private Map<String, String> inviteMap(com.baymc.auth.common.model.InviteCode invite) {
        return Map.of(
            "code", invite.code(),
            "used", String.valueOf(invite.used()),
            "revoked", String.valueOf(invite.revoked()),
            "expires", invite.expiresAt().toString()
        );
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("help", "status", "register", "login", "logout", "2fa", "resetpassword", "password", "invite", "reserve", "user", "lock", "unlock", "reset2fa", "confirm", "reload", "velocity"), args[0]);
        }
        if (args.length == 2 && "2fa".equalsIgnoreCase(args[0])) {
            return filter(List.of("setup", "confirm", "code", "disable", "status"), args[1]);
        }
        if (args.length == 2 && "password".equalsIgnoreCase(args[0])) {
            return filter(List.of("enable", "disable", "change"), args[1]);
        }
        if (args.length == 2 && "invite".equalsIgnoreCase(args[0])) {
            return filter(List.of("create", "export", "list", "info", "revoke"), args[1]);
        }
        if (args.length == 2 && "reserve".equalsIgnoreCase(args[0])) {
            return filter(List.of("offline", "info", "list", "revoke"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
                result.add(value);
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface PlayerAction {
        boolean run(Player player);
    }

    @FunctionalInterface
    private interface UserAction {
        boolean run();
    }

    @FunctionalInterface
    private interface AdminAction {
        boolean run();
    }
}
