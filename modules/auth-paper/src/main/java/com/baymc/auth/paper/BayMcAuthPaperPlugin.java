package com.baymc.auth.paper;

import com.baymc.auth.common.audit.AuditLogger;
import com.baymc.auth.common.audit.AuditSink;
import com.baymc.auth.common.audit.ConsoleAuditSink;
import com.baymc.auth.common.audit.FileAuditSink;
import com.baymc.auth.common.blacklist.BlacklistLoader;
import com.baymc.auth.common.blacklist.BlacklistService;
import com.baymc.auth.common.config.AuthConfig;
import com.baymc.auth.common.config.YamlDocument;
import com.baymc.auth.common.model.AuditEntry;
import com.baymc.auth.common.text.Messages;
import com.baymc.auth.common.util.ExceptionSummary;
import com.baymc.auth.common.util.ResourceUtil;
import com.baymc.auth.paper.command.BayMcAuthCommand;
import com.baymc.auth.paper.command.PaperMessageSender;
import com.baymc.auth.paper.command.ShortCommand;
import com.baymc.auth.paper.listener.LoginProtectionListener;
import com.baymc.auth.paper.listener.PlayerConnectionListener;
import com.baymc.auth.paper.protection.LoginStateService;
import com.baymc.auth.paper.scheduler.PaperScheduler;
import com.baymc.auth.paper.service.AuthService;
import com.baymc.auth.paper.service.FailureLockService;
import com.baymc.auth.paper.service.InviteService;
import com.baymc.auth.paper.service.PendingConfirmationService;
import com.baymc.auth.paper.service.ReserveService;
import com.baymc.auth.paper.session.SessionService;
import com.baymc.auth.storage.cache.InMemoryRepositories;
import com.baymc.auth.storage.cache.LocalSessionStore;
import com.baymc.auth.storage.mysql.MySqlStorage;
import com.baymc.auth.storage.redis.RedisSessionStore;
import com.baymc.auth.storage.repository.*;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/*
 * Paper/Folia 插件入口
 *
 * <p>负责装配配置, 存储, 命令, 监听器和 reload
 * 数据库初始化失败时进入降级状态, 新玩家会被拒绝进入认证流程
 */
public final class BayMcAuthPaperPlugin extends JavaPlugin {
    private RuntimeConfigRef configRef;
    private PaperMessageSender messageSender;
    private BlacklistService blacklistService;
    private MySqlStorage mySqlStorage;
    private SessionService sessionService;
    private AuthService authService;
    private StorageParts storageParts;
    private boolean databaseAvailable;

    @Override
    public void onEnable() {
        Path dataDirectory = getDataFolder().toPath();
        String defaultConfig = ResourceUtil.readText(BayMcAuthPaperPlugin.class, "/config.yml");
        String defaultLang = ResourceUtil.readText(BayMcAuthPaperPlugin.class, "/lang/zh_CN.yml");
        AuthConfig config = loadConfig(dataDirectory, defaultConfig);
        Messages messages = loadMessages(dataDirectory, defaultLang);

        this.configRef = new RuntimeConfigRef(config);
        this.messageSender = new PaperMessageSender(messages);
        this.blacklistService = new BlacklistService();
        loadBlacklists(dataDirectory, config);
        this.storageParts = createStorage(config);
        AuditLogger auditLogger = createAuditLogger(dataDirectory, config, storageParts.audits());
        this.sessionService = createSessionService(config);

        LoginStateService loginStates = new LoginStateService();
        FailureLockService failureLocks = new FailureLockService(configRef, storageParts.failures());
        this.authService = new AuthService(configRef, storageParts.users(), storageParts.passwordHistory(), storageParts.inviteCodes(),
            storageParts.failures(), storageParts.identityContexts(), blacklistService, loginStates, sessionService, failureLocks,
            auditLogger, messageSender, getServer().getName(), databaseAvailable);
        InviteService inviteService = new InviteService(storageParts.inviteCodes(), configRef);
        ReserveService reserveService = new ReserveService(storageParts.nameLocks(), storageParts.users(), blacklistService);
        PendingConfirmationService confirmations = new PendingConfirmationService();
        PaperScheduler scheduler = new PaperScheduler(this);

        BayMcAuthCommand mainCommand = new BayMcAuthCommand(authService, messageSender, configRef, inviteService, reserveService,
            confirmations, storageParts.users(), () -> reload(dataDirectory, defaultConfig, defaultLang));
        registerCommand("baymcauth", mainCommand);
        registerCommand("register", new ShortCommand(mainCommand, "register"));
        registerCommand("login", new ShortCommand(mainCommand, "login"));
        registerCommand("logout", new ShortCommand(mainCommand, "logout"));
        registerCommand("2fa", new ShortCommand(mainCommand, "2fa"));
        registerCommand("resetpassword", new ShortCommand(mainCommand, "resetpassword"));

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(authService, loginStates, messageSender, scheduler, config.login().timeout()), this);
        getServer().getPluginManager().registerEvents(new LoginProtectionListener(loginStates, messageSender), this);
        getLogger().info("BayMcAuth Paper/Folia 端已启动");
    }

    @Override
    public void onDisable() {
        if (sessionService != null) {
            sessionService.close();
        }
        if (mySqlStorage != null) {
            mySqlStorage.close();
        }
    }

    private void reload(Path dataDirectory, String defaultConfig, String defaultLang) {
        AuthConfig config = loadConfig(dataDirectory, defaultConfig);
        Messages messages = loadMessages(dataDirectory, defaultLang);
        configRef.set(config);
        messageSender.setMessages(messages);
        loadBlacklists(dataDirectory, config);
        if (authService != null) {
            authService.setDatabaseAvailable(databaseAvailable);
        }
    }

    private AuthConfig loadConfig(Path dataDirectory, String defaultConfig) {
        try {
            return AuthConfig.from(YamlDocument.load(dataDirectory.resolve("config.yml"), defaultConfig, getLogger()::warning), getLogger()::warning);
        } catch (IOException exception) {
            throw new IllegalStateException("加载配置文件失败", exception);
        }
    }

    private Messages loadMessages(Path dataDirectory, String defaultLang) {
        try {
            return Messages.load(dataDirectory.resolve("lang").resolve("zh_CN.yml"), defaultLang, getLogger()::warning);
        } catch (IOException exception) {
            throw new IllegalStateException("加载语言文件失败", exception);
        }
    }

    private StorageParts createStorage(AuthConfig config) {
        try {
            this.mySqlStorage = new MySqlStorage(config.database());
            this.databaseAvailable = true;
            return new StorageParts(mySqlStorage.users(), mySqlStorage.passwordHistory(), mySqlStorage.inviteCodes(), mySqlStorage.nameLocks(),
                mySqlStorage.failures(), mySqlStorage.audits(), mySqlStorage.identityContexts());
        } catch (RuntimeException exception) {
            this.databaseAvailable = false;
            if (config.settings().debug()) {
                getLogger().log(Level.SEVERE, "数据库初始化失败, BayMcAuth 已进入降级状态", exception);
            } else {
                getLogger().severe("数据库初始化失败, BayMcAuth 已进入降级状态");
                getLogger().severe("失败原因");
                ExceptionSummary.databaseFailureLines(exception).forEach(getLogger()::severe);
            }
            InMemoryRepositories repositories = new InMemoryRepositories();
            return new StorageParts(repositories.users(), repositories.passwordHistory(), repositories.inviteCodes(), repositories.nameLocks(),
                repositories.failures(), repositories.audits(), repositories.identityContexts());
        }
    }

    private SessionService createSessionService(AuthConfig config) {
        if (!config.redisSession()) {
            return new SessionService(new LocalSessionStore(), getLogger()::warning);
        }
        try {
            return new SessionService(new RedisSessionStore(config.redis(), config.session().expire()), getLogger()::warning);
        } catch (RuntimeException exception) {
            getLogger().warning("Redis 初始化失败, 已切换为本地会话: " + exception.getMessage());
            return new SessionService(new LocalSessionStore(), getLogger()::warning);
        }
    }

    private AuditLogger createAuditLogger(Path dataDirectory, AuthConfig config, AuditRepository audits) {
        AuditLogger auditLogger = new AuditLogger();
        List<AuditSink> sinks = new ArrayList<>();
        if (config.audit().enabled() && config.audit().console()) {
            sinks.add(new ConsoleAuditSink(getLogger()::info));
        }
        if (config.audit().enabled() && config.audit().file()) {
            sinks.add(new FileAuditSink(dataDirectory.resolve(config.audit().directory())));
        }
        if (config.audit().enabled() && config.audit().database()) {
            sinks.add(audits::add);
        }
        auditLogger.replaceSinks(sinks);
        return auditLogger;
    }

    private void loadBlacklists(Path dataDirectory, AuthConfig config) {
        blacklistService.replaceUsernameKeywords(BlacklistLoader.load(config.blacklist().username(), dataDirectory.resolve("blacklist").resolve("username.cache"), getLogger()::warning));
        blacklistService.replacePasswordKeywords(BlacklistLoader.load(config.blacklist().password(), dataDirectory.resolve("blacklist").resolve("password.cache"), getLogger()::warning));
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("plugin.yml 缺少命令: " + name);
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private record StorageParts(
        UserRepository users,
        PasswordHistoryRepository passwordHistory,
        InviteCodeRepository inviteCodes,
        NameLockRepository nameLocks,
        FailureRepository failures,
        AuditRepository audits,
        IdentityContextRepository identityContexts
    ) {
    }
}
