package com.baymc.auth.velocity;

import com.baymc.auth.common.BayMcAuthConstants;
import com.baymc.auth.common.audit.AuditLogger;
import com.baymc.auth.common.audit.AuditSink;
import com.baymc.auth.common.audit.ConsoleAuditSink;
import com.baymc.auth.common.audit.FileAuditSink;
import com.baymc.auth.common.blacklist.BlacklistLoader;
import com.baymc.auth.common.blacklist.BlacklistService;
import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.config.YamlDocument;
import com.baymc.auth.common.identity.IdentityResolver;
import com.baymc.auth.common.model.AuditEntry;
import com.baymc.auth.common.model.AuditEventType;
import com.baymc.auth.common.model.AuditResult;
import com.baymc.auth.common.model.IdentityContext;
import com.baymc.auth.common.model.IdentityDecision;
import com.baymc.auth.common.text.Messages;
import com.baymc.auth.common.util.ExceptionSummary;
import com.baymc.auth.common.util.NameUtil;
import com.baymc.auth.common.util.ResourceUtil;
import com.baymc.auth.storage.cache.InMemoryRepositories;
import com.baymc.auth.storage.mysql.MySqlStorage;
import com.baymc.auth.storage.repository.AuditRepository;
import com.baymc.auth.storage.repository.IdentityContextRepository;
import com.baymc.auth.storage.repository.NameLockRepository;
import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * Velocity 插件入口
 *
 * <p>代理端只负责连接进入后端前的身份分流
 * 密码验证, TOTP 和登录保护由 Paper/Folia 子服端完成
 */
@Plugin(
    id = BayMcAuthConstants.PLUGIN_ID,
    name = BayMcAuthConstants.PLUGIN_NAME,
    version = BayMcAuthConstants.VERSION,
    description = "Velocity 与 Paper/Folia 网络统一认证插件",
    authors = {"Konsheng"}
)
public final class BayMcAuthVelocityPlugin {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private AuthConfig config;
    private Messages messages;
    private BlacklistService blacklistService;
    private IdentityResolver identityResolver;
    private NameLockRepository nameLocks;
    private IdentityContextRepository identityContexts;
    private AuditLogger audits;
    private MySqlStorage mySqlStorage;
    private boolean databaseAvailable;

    @Inject
    public BayMcAuthVelocityPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        reloadInternal();
        registerCommand();
        logger.info("BayMcAuth Velocity 端已启动");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (mySqlStorage != null) {
            mySqlStorage.close();
        }
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        String ip = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        if (!databaseAvailable) {
            deny(event, username, ip, "数据库不可用, 无法完成身份分流");
            return;
        }
        IdentityDecision decision;
        try {
            decision = identityResolver.resolve(username, nameLocks);
        } catch (RuntimeException exception) {
            deny(event, username, ip, "身份分流异常");
            return;
        }
        if (!decision.allowed()) {
            deny(event, username, ip, decision.reason());
            return;
        }
        if (decision.accountType().requiresRegister()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        } else {
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        }
        Instant now = Instant.now();
        IdentityContext context = new IdentityContext(UUID.randomUUID().toString(), NameUtil.lower(username), decision.forcedUuid(),
            decision.accountType(), ip, "Velocity", now, now.plus(BayMcAuthConstants.IDENTITY_CONTEXT_TTL), null);
        identityContexts.save(context);
        audits.write(AuditEntry.now(AuditEventType.VELOCITY_IDENTITY_ROUTE, username, decision.forcedUuid(), decision.accountType(),
            ip, "Velocity", AuditResult.SUCCESS, null, decision.auditMessage()));
    }

    private void deny(PreLoginEvent event, String username, String ip, String reason) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(messages.component("velocity.denied", Map.of("reason", reason))));
        if (audits != null) {
            audits.write(AuditEntry.now(AuditEventType.VELOCITY_IDENTITY_ROUTE, username, null, null, ip, "Velocity", AuditResult.DENIED, reason,
                "玩家 " + username + " 被 Velocity 拒绝, 原因 " + reason));
        }
    }

    private void reloadInternal() {
        String defaultConfig = ResourceUtil.readText(BayMcAuthVelocityPlugin.class, "/config.yml");
        String defaultLang = ResourceUtil.readText(BayMcAuthVelocityPlugin.class, "/lang/zh_CN.yml");
        try {
            this.config = AuthConfig.from(YamlDocument.load(dataDirectory.resolve("config.yml"), defaultConfig, logger::warn), logger::warn);
            this.messages = Messages.load(dataDirectory.resolve("lang").resolve("zh_CN.yml"), defaultLang, logger::warn);
        } catch (IOException exception) {
            throw new IllegalStateException("Velocity 配置或语言文件加载失败", exception);
        }
        this.blacklistService = new BlacklistService();
        loadBlacklists();
        try {
            if (mySqlStorage != null) {
                mySqlStorage.close();
            }
            this.mySqlStorage = new MySqlStorage(config.database());
            this.databaseAvailable = true;
            this.nameLocks = mySqlStorage.nameLocks();
            this.identityContexts = mySqlStorage.identityContexts();
            this.audits = createAuditLogger(mySqlStorage.audits());
        } catch (RuntimeException exception) {
            if (config.settings().debug()) {
                logger.error("Velocity 数据库初始化失败, 新玩家将被拒绝连接", exception);
            } else {
                logger.error("Velocity 数据库初始化失败, 新玩家将被拒绝连接");
                logger.error("失败原因");
                ExceptionSummary.databaseFailureLines(exception).forEach(logger::error);
            }
            this.databaseAvailable = false;
            InMemoryRepositories repositories = new InMemoryRepositories();
            this.nameLocks = repositories.nameLocks();
            this.identityContexts = repositories.identityContexts();
            this.audits = createAuditLogger(repositories.audits());
        }
        this.identityResolver = new IdentityResolver(config, blacklistService);
    }

    private void loadBlacklists() {
        blacklistService.replaceUsernameKeywords(BlacklistLoader.load(config.blacklist().username(), dataDirectory.resolve("blacklist").resolve("username.cache"), logger::warn));
        blacklistService.replacePasswordKeywords(BlacklistLoader.load(config.blacklist().password(), dataDirectory.resolve("blacklist").resolve("password.cache"), logger::warn));
    }

    private AuditLogger createAuditLogger(AuditRepository auditRepository) {
        AuditLogger logger = new AuditLogger();
        List<AuditSink> sinks = new ArrayList<>();
        if (config.audit().enabled() && config.audit().console()) {
            sinks.add(new ConsoleAuditSink(this.logger::info));
        }
        if (config.audit().enabled() && config.audit().file()) {
            sinks.add(new FileAuditSink(dataDirectory.resolve(config.audit().directory())));
        }
        if (config.audit().enabled() && config.audit().database()) {
            sinks.add(auditRepository::add);
        }
        logger.replaceSinks(sinks);
        return logger;
    }

    private void registerCommand() {
        VelocityCommand command = new VelocityCommand();
        proxyServer.getCommandManager().register(
            proxyServer.getCommandManager().metaBuilder("baymcauth").aliases("auth").build(),
            command
        );
    }

    private final class VelocityCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0 || !"velocity".equalsIgnoreCase(args[0])) {
                if (invocation.source() instanceof Player player) {
                    player.spoofChatInput("/baymcauth " + String.join(" ", args));
                } else {
                    invocation.source().sendMessage(Component.text("Velocity 端只处理 /baymcauth velocity 子命令"));
                }
                return;
            }
            if (args.length == 1 || "help".equalsIgnoreCase(args[1])) {
                if (!requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY_HELP)) {
                    return;
                }
                invocation.source().sendMessage(Component.text("/baymcauth velocity status"));
                invocation.source().sendMessage(Component.text("/baymcauth velocity reload"));
                invocation.source().sendMessage(Component.text("/baymcauth velocity affix status"));
                invocation.source().sendMessage(Component.text("/baymcauth velocity affix reload"));
                return;
            }
            switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                case "status" -> {
                    if (!requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY_STATUS)) {
                        return;
                    }
                    invocation.source().sendMessage(messages.component("velocity.status", Map.of("database", databaseAvailable ? "available" : "unavailable")));
                }
                case "reload" -> {
                    if (!requirePermission(invocation, BayMcAuthConstants.PERMISSION_VELOCITY_RELOAD)) {
                        return;
                    }
                    reloadInternal();
                    invocation.source().sendMessage(messages.component("velocity.reload-success", Map.of()));
                }
                case "affix" -> {
                    String action = args.length >= 3 ? args[2].toLowerCase(java.util.Locale.ROOT) : "status";
                    String permission = switch (action) {
                        case "status" -> BayMcAuthConstants.PERMISSION_VELOCITY_AFFIX_STATUS;
                        case "reload" -> BayMcAuthConstants.PERMISSION_VELOCITY_AFFIX_RELOAD;
                        default -> null;
                    };
                    if (permission == null) {
                        invocation.source().sendMessage(messages.component("common.unknown-command", Map.of()));
                        return;
                    }
                    if (!requirePermission(invocation, permission)) {
                        return;
                    }
                    invocation.source().sendMessage(messages.component("velocity.affix-status", Map.of("mode", config.offlineAffix().mode())));
                }
                default -> invocation.source().sendMessage(messages.component("common.unknown-command", Map.of()));
            }
        }

        private boolean requirePermission(Invocation invocation, String permission) {
            if (invocation.source().hasPermission(permission) || invocation.source().hasPermission(BayMcAuthConstants.PERMISSION_ADMIN)) {
                return true;
            }
            invocation.source().sendMessage(messages.component("common.no-permission", Map.of()));
            return false;
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0 || args.length == 1) {
                return List.of("velocity");
            }
            if (args.length == 2) {
                return List.of("help", "status", "reload", "affix");
            }
            if (args.length == 3 && "affix".equalsIgnoreCase(args[1])) {
                return List.of("status", "reload");
            }
            return List.of();
        }
    }
}
