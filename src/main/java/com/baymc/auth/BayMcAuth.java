package com.baymc.auth;

import com.baymc.auth.command.BayMcAuthCommand;
import com.baymc.auth.command.ChangePasswordCommand;
import com.baymc.auth.command.DestroySessionCommand;
import com.baymc.auth.command.ForceChangePasswordCommand;
import com.baymc.auth.command.ForceLoginCommand;
import com.baymc.auth.command.ForceRegisterCommand;
import com.baymc.auth.command.ForceUnregisterCommand;
import com.baymc.auth.command.PremiumCommand;
import com.baymc.auth.command.TotpCommand;
import com.baymc.auth.command.UnregisterCommand;
import com.baymc.auth.config.ConfigurationException;
import com.baymc.auth.config.ConfigurationLoader;
import com.baymc.auth.dependencies.DatabaseLibrary;
import com.baymc.auth.event.AuthPluginReloadEvent;
import com.baymc.auth.event.PreAuthorizationEvent;
import com.baymc.auth.event.PreEvent;
import com.baymc.auth.event.PreRegisterEvent;
import com.baymc.auth.event.TaskEvent;
import com.baymc.auth.floodgate.FloodgateApiHolder;
import com.baymc.auth.handler.AuthSessionHandler;
import com.baymc.auth.listener.AuthListener;
import com.baymc.auth.listener.BackendEndpointsListener;
import com.baymc.auth.model.RegisteredPlayer;
import com.baymc.auth.model.SQLRuntimeException;
import com.google.common.util.concurrent.Striped;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.db.DatabaseType;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableInfo;
import com.j256.ormlite.table.TableUtils;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiter;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiters;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.elytrium.commons.utils.reflection.ReflectionException;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.command.LimboCommandMeta;
import net.elytrium.limboapi.api.file.WorldFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.title.Title;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

@Plugin(
    id = "baymcauth",
    name = "BayMcAuth",
    version = BuildConstants.AUTH_VERSION,
    url = "https://github.com/konsheng/BayMcAuth",
    authors = "Konsheng",
    dependencies = {
        @Dependency(id = "limboapi"),
        @Dependency(id = "floodgate", optional = true)
    }
)

public class BayMcAuth {

  public static final Ratelimiter<InetAddress> RATELIMITER = Ratelimiters.createWithMilliseconds(5000);

  @MonotonicNonNull
  private static Logger LOGGER;
  private static final MiniMessage PREFIX_SERIALIZER = MiniMessage.miniMessage();
  private static final MiniMessage SERIALIZER = MiniMessage.builder()
      .tags(TagResolver.builder()
          .resolver(StandardTags.defaults())
          // 单独解析前缀, 防止递归引用和样式影响后续正文
          .tag("prefix", (arguments, context) -> Tag.selfClosingInserting(PREFIX_SERIALIZER.deserialize(Settings.IMP.PREFIX)))
          .build())
      .build();

  private final Map<String, CachedSessionUser> cachedAuthChecks = new ConcurrentHashMap<>();
  private final Striped<Lock> authenticationLocks = Striped.lock(256);
  private final Map<String, CachedPremiumUser> premiumCache = new ConcurrentHashMap<>();
  private final Map<InetAddress, CachedBruteforceUser> bruteforceCache = new ConcurrentHashMap<>();
  private final Map<UUID, Runnable> postLoginTasks = new ConcurrentHashMap<>();
  private final Set<String> unsafePasswords = new HashSet<>();
  private final Set<String> forcedPreviously = Collections.synchronizedSet(new HashSet<>());
  private final Set<String> pendingLogins = ConcurrentHashMap.newKeySet();

  private final HttpClient client = HttpClient.newHttpClient();

  private final ProxyServer server;
  private final Path dataDirectory;
  private final File dataDirectoryFile;
  private final LimboFactory factory;
  private final FloodgateApiHolder floodgateApi;
  private final Map<String, AuthSessionHandler> authenticatingPlayers;

  @Nullable
  private Component loginPremium;
  @Nullable
  private Title loginPremiumTitle;
  @Nullable
  private Component loginFloodgate;
  @Nullable
  private Title loginFloodgateTitle;
  private Component registrationsDisabledKick;
  private Component bruteforceAttemptKick;
  private Component nicknameInvalidKick;
  private Component reconnectKick;
  private ScheduledTask purgeCacheTask;
  private ScheduledTask purgePremiumCacheTask;
  private ScheduledTask purgeBruteforceCacheTask;

  private ConnectionSource connectionSource;
  private Dao<RegisteredPlayer, String> playerDao;
  private Pattern nicknameValidationPattern;
  private Limbo authServer;

  @Inject
  public BayMcAuth(Logger logger, ProxyServer server, @DataDirectory Path dataDirectory) {
    setLogger(logger);

    this.server = server;

    this.dataDirectory = dataDirectory.resolveSibling("BayMcAuth");

    this.dataDirectoryFile = this.dataDirectory.toFile();

    this.authenticatingPlayers = new ConcurrentHashMap<>();
    this.factory = (LimboFactory) this.server.getPluginManager().getPlugin("limboapi").flatMap(PluginContainer::getInstance).orElseThrow();

    if (this.server.getPluginManager().getPlugin("floodgate").isPresent()) {
      this.floodgateApi = new FloodgateApiHolder();
    } else {
      this.floodgateApi = null;
    }
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    System.setProperty("com.j256.simplelogging.level", "ERROR");

    try {
      this.reload();
    } catch (ConfigurationException exception) {
      LOGGER.error("配置加载失败, 代理服务器将关闭: {}", exception.getMessage());
      this.server.shutdown();
    } catch (SQLRuntimeException exception) {
      LOGGER.error("初始化数据库时发生 SQL 异常", exception);
      this.server.shutdown();
    }
  }

  public void reload() {
    ConfigurationLoader.Loaded loaded = ConfigurationLoader.load(this.dataDirectory);
    Settings next = loaded.settings();

    if (!next.MAIN.ONLINE_MODE_NEED_AUTH_STRICT && !next.MAIN.SAVE_PREMIUM_ACCOUNTS) {
      next.MAIN.SAVE_PREMIUM_ACCOUNTS = true;
      LOGGER.error("由于已禁用 online-mode-need-auth-strict, 已强制启用 save-premium-accounts, 以防止正版账号被冒用");
    }

    if (this.floodgateApi == null && !next.MAIN.FLOODGATE_NEED_AUTH) {
      throw new ConfigurationException("config.yml: main.floodgate-need-auth 设置为 false 时必须安装 floodgate 插件");
    }

    Settings.IMP = next;
    loaded.warnings().forEach(LOGGER::warn);

    TaskEvent.reload();
    AuthSessionHandler.reload();

    this.loginPremium = Settings.IMP.MAIN.STRINGS.LOGIN_PREMIUM.isEmpty() ? null : SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_PREMIUM);
    if (Settings.IMP.MAIN.STRINGS.LOGIN_PREMIUM_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.LOGIN_PREMIUM_SUBTITLE.isEmpty()) {
      this.loginPremiumTitle = null;
    } else {
      this.loginPremiumTitle = Title.title(
          SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_PREMIUM_TITLE),
          SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_PREMIUM_SUBTITLE),
          Settings.IMP.MAIN.PREMIUM_TITLE_SETTINGS.toTimes()
      );
    }

    this.loginFloodgate = Settings.IMP.MAIN.STRINGS.LOGIN_FLOODGATE.isEmpty() ? null : SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_FLOODGATE);
    if (Settings.IMP.MAIN.STRINGS.LOGIN_FLOODGATE_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.LOGIN_FLOODGATE_SUBTITLE.isEmpty()) {
      this.loginFloodgateTitle = null;
    } else {
      this.loginFloodgateTitle = Title.title(
          SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_FLOODGATE_TITLE),
          SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_FLOODGATE_SUBTITLE),
          Settings.IMP.MAIN.PREMIUM_TITLE_SETTINGS.toTimes()
      );
    }

    this.bruteforceAttemptKick = SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_WRONG_PASSWORD_KICK);
    this.nicknameInvalidKick = SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.NICKNAME_INVALID_KICK);
    this.reconnectKick = SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.RECONNECT_KICK);
    this.registrationsDisabledKick = SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.REGISTRATIONS_DISABLED_KICK);

    if (Settings.IMP.MAIN.CHECK_PASSWORD_STRENGTH) {
      try {
        this.unsafePasswords.clear();
        Path unsafePasswordsPath = Paths.get(this.dataDirectoryFile.getAbsolutePath(), Settings.IMP.MAIN.UNSAFE_PASSWORDS_FILE);
        if (Files.notExists(unsafePasswordsPath)) {
          Files.createFile(unsafePasswordsPath);
        }

        try (Stream<String> unsafePasswordsStream = Files.lines(unsafePasswordsPath)) {
          this.unsafePasswords.addAll(unsafePasswordsStream.collect(Collectors.toList()));
        }
      } catch (IOException e) {
        throw new IllegalArgumentException("加载弱密码列表失败", e);
      }
    }

    this.cachedAuthChecks.clear();
    this.premiumCache.clear();
    this.bruteforceCache.clear();

    Settings.DATABASE dbConfig = Settings.IMP.DATABASE;
    DatabaseLibrary databaseLibrary = dbConfig.STORAGE_TYPE;
    try {
      this.connectionSource = databaseLibrary.connectToORM(
          this.dataDirectoryFile.toPath().toAbsolutePath(),
          dbConfig.HOSTNAME,
          dbConfig.DATABASE + dbConfig.CONNECTION_PARAMETERS,
          dbConfig.USER,
          dbConfig.PASSWORD
      );
    } catch (ReflectiveOperationException e) {
      throw new ReflectionException("初始化数据库驱动失败", e);
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    } catch (IOException | URISyntaxException e) {
      throw new IllegalArgumentException("初始化数据库连接时发生错误", e);
    }

    this.nicknameValidationPattern = Pattern.compile(Settings.IMP.MAIN.ALLOWED_NICKNAME_REGEX);

    try {
      try {
        TableUtils.createTableIfNotExists(this.connectionSource, RegisteredPlayer.class);
      } catch (SQLException e) {
        if (!e.getMessage().contains("CREATE INDEX")) {
          throw e;
        }
      }

      this.playerDao = DaoManager.createDao(this.connectionSource, RegisteredPlayer.class);
      this.migrateDb(this.playerDao);
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    }

    CommandManager manager = this.server.getCommandManager();
    manager.unregister("unregister");
    manager.unregister("forceregister");
    manager.unregister("forcelogin");
    manager.unregister("premium");
    manager.unregister("forceunregister");
    manager.unregister("changepassword");
    manager.unregister("forcechangepassword");
    manager.unregister("destroysession");
    manager.unregister("2fa");
    manager.unregister("baymcauth");

    manager.register("unregister", new UnregisterCommand(this, this.playerDao), "unreg");
    manager.register("forceregister", new ForceRegisterCommand(this, this.playerDao), "forcereg");
    manager.register("forcelogin", new ForceLoginCommand(this));
    manager.register("premium", new PremiumCommand(this, this.playerDao), "license");
    manager.register("forceunregister", new ForceUnregisterCommand(this, this.server, this.playerDao), "forceunreg");
    manager.register("changepassword", new ChangePasswordCommand(this, this.playerDao), "changepass", "cp");
    manager.register("forcechangepassword", new ForceChangePasswordCommand(this, this.server, this.playerDao), "forcechangepass", "fcp");
    manager.register("destroysession", new DestroySessionCommand(this), "logout");
    if (Settings.IMP.MAIN.ENABLE_TOTP) {
      manager.register("2fa", new TotpCommand(this.playerDao), "totp");
    }
    manager.register("baymcauth", new BayMcAuthCommand(this), "auth", "ba", "bauth");

    Settings.MAIN.AUTH_COORDS authCoords = Settings.IMP.MAIN.AUTH_COORDS;
    VirtualWorld authWorld = this.factory.createVirtualWorld(
        Settings.IMP.MAIN.DIMENSION,
        authCoords.X, authCoords.Y, authCoords.Z,
        (float) authCoords.YAW, (float) authCoords.PITCH
    );

    if (Settings.IMP.MAIN.LOAD_WORLD) {
      try {
        Path path = this.dataDirectory.resolve(Settings.IMP.MAIN.WORLD_FILE_PATH);
        WorldFile file = this.factory.openWorldFile(Settings.IMP.MAIN.WORLD_FILE_TYPE, path);

        Settings.MAIN.WORLD_COORDS coords = Settings.IMP.MAIN.WORLD_COORDS;
        file.toWorld(this.factory, authWorld, coords.X, coords.Y, coords.Z, Settings.IMP.MAIN.WORLD_LIGHT_LEVEL);
      } catch (IOException e) {
        throw new IllegalArgumentException("加载认证世界文件失败", e);
      }
    }

    if (this.authServer != null) {
      this.authServer.dispose();
    }

    this.authServer = this.factory
        .createLimbo(authWorld)
        .setName("BayMcAuth")
        .setWorldTime(Settings.IMP.MAIN.WORLD_TICKS)
        .setGameMode(Settings.IMP.MAIN.GAME_MODE)
        .registerCommand(new LimboCommandMeta(this.filterCommands(Settings.IMP.MAIN.REGISTER_COMMAND)))
        .registerCommand(new LimboCommandMeta(this.filterCommands(Settings.IMP.MAIN.LOGIN_COMMAND)));

    if (Settings.IMP.MAIN.ENABLE_TOTP) {
      this.authServer.registerCommand(new LimboCommandMeta(this.filterCommands(Settings.IMP.MAIN.TOTP_COMMAND)));
    }

    EventManager eventManager = this.server.getEventManager();
    eventManager.unregisterListeners(this);
    eventManager.register(this, new AuthListener(this, this.playerDao, this.floodgateApi));
    if (Settings.IMP.MAIN.BACKEND_API.ENABLED) {
      eventManager.register(this, new BackendEndpointsListener(this));
    } else {
      this.server.getChannelRegistrar().unregister(BackendEndpointsListener.API_CHANNEL);
    }

    if (this.purgeCacheTask != null) {
      this.purgeCacheTask.cancel();
    }

    this.purgeCacheTask = this.server.getScheduler()
        .buildTask(this, () -> this.checkCache(this.cachedAuthChecks, Settings.IMP.MAIN.PURGE_CACHE_MILLIS))
        .delay(Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .repeat(Settings.IMP.MAIN.PURGE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .schedule();

    if (this.purgePremiumCacheTask != null) {
      this.purgePremiumCacheTask.cancel();
    }

    this.purgePremiumCacheTask = this.server.getScheduler()
        .buildTask(this, () -> this.checkCache(this.premiumCache, Settings.IMP.MAIN.PURGE_PREMIUM_CACHE_MILLIS))
        .delay(Settings.IMP.MAIN.PURGE_PREMIUM_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .repeat(Settings.IMP.MAIN.PURGE_PREMIUM_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .schedule();

    if (this.purgeBruteforceCacheTask != null) {
      this.purgeBruteforceCacheTask.cancel();
    }

    this.purgeBruteforceCacheTask = this.server.getScheduler()
        .buildTask(this, () -> this.checkCache(this.bruteforceCache, Settings.IMP.MAIN.PURGE_BRUTEFORCE_CACHE_MILLIS))
        .delay(Settings.IMP.MAIN.PURGE_BRUTEFORCE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .repeat(Settings.IMP.MAIN.PURGE_BRUTEFORCE_CACHE_MILLIS, TimeUnit.MILLISECONDS)
        .schedule();

    eventManager.fireAndForget(new AuthPluginReloadEvent());
  }

  private List<String> filterCommands(List<String> commands) {
    return commands.stream().filter(command -> command.startsWith("/")).map(command -> command.substring(1)).collect(Collectors.toList());
  }

  private void checkCache(Map<?, ? extends CachedUser> userMap, long time) {
    userMap.entrySet().stream()
        .filter(userEntry -> userEntry.getValue().getCheckTime() + time <= System.currentTimeMillis())
        .map(Map.Entry::getKey)
        .forEach(userMap::remove);
  }

  public void migrateDb(Dao<?, ?> dao) {
    TableInfo<?, ?> tableInfo = dao.getTableInfo();

    Set<FieldType> tables = new HashSet<>();
    Collections.addAll(tables, tableInfo.getFieldTypes());

    String findSql;
    String database = Settings.IMP.DATABASE.DATABASE;
    String tableName = tableInfo.getTableName();
    DatabaseLibrary databaseLibrary = Settings.IMP.DATABASE.STORAGE_TYPE;
    switch (databaseLibrary) {
      case SQLITE: {
        findSql = "SELECT name FROM PRAGMA_TABLE_INFO('" + tableName + "')";
        break;
      }
      case H2: {
        findSql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '" + tableName + "';";
        break;
      }
      case POSTGRESQL: {
        findSql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_CATALOG = '" + database + "' AND TABLE_NAME = '" + tableName + "';";
        break;
      }
      case MARIADB:
      case MYSQL: {
        findSql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '" + database + "' AND TABLE_NAME = '" + tableName + "';";
        break;
      }
      default: {
        LOGGER.error("数据库类型配置无效");
        this.server.shutdown();
        return;
      }
    }

    try (GenericRawResults<String[]> queryResult = dao.queryRaw(findSql)) {
      queryResult.forEach(result -> tables.removeIf(table -> table.getColumnName().equalsIgnoreCase(result[0])));

      tables.forEach(table -> {
        try {
          StringBuilder builder = new StringBuilder("ALTER TABLE ");
          if (databaseLibrary == DatabaseLibrary.POSTGRESQL) {
            builder.append('"');
          }
          builder.append(tableName);
          if (databaseLibrary == DatabaseLibrary.POSTGRESQL) {
            builder.append('"');
          }
          builder.append(" ADD ");
          String columnDefinition = table.getColumnDefinition();
          DatabaseType databaseType = dao.getConnectionSource().getDatabaseType();
          if (columnDefinition == null) {
            List<String> dummy = List.of();
            databaseType.appendColumnArg(table.getTableName(), builder, table, dummy, dummy, dummy, dummy);
          } else {
            databaseType.appendEscapedEntityName(builder, table.getColumnName());
            builder.append(" ").append(columnDefinition).append(" ");
          }

          dao.executeRawNoArgs(builder.toString());
        } catch (SQLException e) {
          throw new SQLRuntimeException(e);
        }
      });
    } catch (Exception e) {
      throw new SQLRuntimeException(e);
    }
  }

  public void cacheAuthUser(Player player) {
    String username = player.getUsername();
    String lowercaseUsername = username.toLowerCase(Locale.ROOT);
    this.cachedAuthChecks.put(lowercaseUsername, new CachedSessionUser(System.currentTimeMillis(), player.getRemoteAddress().getAddress(), username));
  }

  public void removePlayerFromCache(String username) {
    this.removePlayerFromCacheLowercased(username.toLowerCase(Locale.ROOT));
  }

  public void removePlayerFromCacheLowercased(String username) {
    this.cachedAuthChecks.remove(username);
    this.premiumCache.remove(username);
  }

  public Lock getAuthenticationLock(String username) {
    return this.authenticationLocks.get(username.toLowerCase(Locale.ROOT));
  }

  public boolean updatePassword(Dao<RegisteredPlayer, String> dao, RegisteredPlayer account, String newHash) throws SQLException {
    String username = account.getLowercaseNickname();
    Lock lock = this.getAuthenticationLock(username);
    AuthSessionHandler pending;
    lock.lock();
    try {
      UpdateBuilder<RegisteredPlayer, String> update = dao.updateBuilder();
      update.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, username);
      update.updateColumnValue(RegisteredPlayer.HASH_FIELD, newHash);
      if (update.update() == 0) {
        return false;
      }

      this.removePlayerFromCacheLowercased(username);
      pending = this.authenticatingPlayers.get(account.getNickname());
      if (pending != null && !pending.endPendingAuthentication()) {
        pending = null;
      }
    } finally {
      lock.unlock();
    }

    if (pending != null) {
      pending.disconnectForPasswordChange();
    }
    return true;
  }

  public boolean needAuth(Player player) {
    String username = player.getUsername();
    String lowercaseUsername = username.toLowerCase(Locale.ROOT);
    Lock lock = this.getAuthenticationLock(lowercaseUsername);
    lock.lock();
    try {
      CachedSessionUser sessionUser = this.cachedAuthChecks.get(lowercaseUsername);
      return sessionUser == null || !sessionUser.getInetAddress().equals(player.getRemoteAddress().getAddress())
          || !sessionUser.getUsername().equals(username);
    } finally {
      lock.unlock();
    }
  }

  public void authPlayer(Player player) {
    boolean isFloodgate = !Settings.IMP.MAIN.FLOODGATE_NEED_AUTH && this.floodgateApi.isFloodgatePlayer(player.getUniqueId());
    if (!isFloodgate && this.isForcedPreviously(player.getUsername()) && this.isPremium(player.getUsername())) {
      player.disconnect(this.reconnectKick);
      return;
    }

    if (this.getBruteforceAttempts(player.getRemoteAddress().getAddress()) >= Settings.IMP.MAIN.BRUTEFORCE_MAX_ATTEMPTS) {
      player.disconnect(this.bruteforceAttemptKick);
      return;
    }

    String nickname = player.getUsername();
    if (!this.nicknameValidationPattern.matcher((isFloodgate) ? nickname.substring(this.floodgateApi.getPrefixLength()) : nickname).matches()) {
      player.disconnect(this.nicknameInvalidKick);
      return;
    }

    RegisteredPlayer registeredPlayer = AuthSessionHandler.fetchInfo(this.playerDao, nickname);

    boolean onlineMode = player.isOnlineMode();
    TaskEvent.Result result = TaskEvent.Result.NORMAL;

    if (onlineMode || isFloodgate) {
      if (registeredPlayer == null || registeredPlayer.getHash().isEmpty()) {
        RegisteredPlayer nicknameRegisteredPlayer = registeredPlayer;
        registeredPlayer = AuthSessionHandler.fetchInfo(this.playerDao, player.getUniqueId());

        if (nicknameRegisteredPlayer != null && registeredPlayer == null && nicknameRegisteredPlayer.getHash().isEmpty()) {
          registeredPlayer = nicknameRegisteredPlayer;
          registeredPlayer.setPremiumUuid(player.getUniqueId().toString());
          try {
            this.playerDao.update(registeredPlayer);
          } catch (SQLException e) {
            throw new SQLRuntimeException(e);
          }
        }

        if (nicknameRegisteredPlayer == null && registeredPlayer == null && Settings.IMP.MAIN.SAVE_PREMIUM_ACCOUNTS) {
          registeredPlayer = new RegisteredPlayer(player).setPremiumUuid(player.getUniqueId());

          try {
            this.playerDao.create(registeredPlayer);
          } catch (SQLException e) {
            throw new SQLRuntimeException(e);
          }
        }

        if (registeredPlayer == null || registeredPlayer.getHash().isEmpty()) {
          // 当前连接处于 LOGIN 状态, 暂时不能发送数据包
          // 等待连接切换到 PLAY 状态后再发送
          this.postLoginTasks.put(player.getUniqueId(), () -> {
            if (onlineMode) {
              if (this.loginPremium != null) {
                player.sendMessage(this.loginPremium);
              }
              if (this.loginPremiumTitle != null) {
                player.showTitle(this.loginPremiumTitle);
              }
            } else {
              if (this.loginFloodgate != null) {
                player.sendMessage(this.loginFloodgate);
              }
              if (this.loginFloodgateTitle != null) {
                player.showTitle(this.loginFloodgateTitle);
              }
            }
          });

          result = TaskEvent.Result.BYPASS;
        }
      }
    }

    if (registeredPlayer == null && Settings.IMP.MAIN.DISABLE_REGISTRATIONS) {
      player.disconnect(this.registrationsDisabledKick);
      return;
    }

    // 同一事件的异步返回和主动完成都可能到达, 只执行一次认证决定
    AtomicBoolean decisionSent = new AtomicBoolean();
    Consumer<TaskEvent> eventConsumer = event -> {
      if (event.getResult() != TaskEvent.Result.WAIT && decisionSent.compareAndSet(false, true)) {
        this.sendPlayer(event, event instanceof PreAuthorizationEvent authorization ? authorization.getPlayerInfo() : null);
      }
    };
    TaskEvent event = registeredPlayer == null
        ? new PreRegisterEvent(eventConsumer, result, player)
        : new PreAuthorizationEvent(eventConsumer, result, player, registeredPlayer);
    this.server.getEventManager().fire(event).thenAcceptAsync(eventConsumer);
  }

  private void sendPlayer(TaskEvent event, RegisteredPlayer registeredPlayer) {
    Player player = ((PreEvent) event).getPlayer();

    switch (event.getResult()) {
      case BYPASS: {
        boolean passwordIsCurrent = false;
        Lock lock = this.getAuthenticationLock(registeredPlayer == null ? player.getUsername() : registeredPlayer.getLowercaseNickname());
        lock.lock();
        try {
          passwordIsCurrent = registeredPlayer == null || AuthSessionHandler.matchesCurrentPassword(this.playerDao, registeredPlayer);
          if (passwordIsCurrent) {
            this.cacheAuthUser(player);
            this.updateLoginData(player);
          }
        } catch (SQLException e) {
          throw new SQLRuntimeException(e);
        } finally {
          lock.unlock();
          if (passwordIsCurrent) {
            this.factory.passLoginLimbo(player);
          }
        }
        if (!passwordIsCurrent) {
          player.disconnect(SERIALIZER.deserialize(Settings.IMP.MAIN.STRINGS.PASSWORD_CHANGED_KICK));
        }
        break;
      }
      case CANCEL: {
        player.disconnect(event.getReason());
        break;
      }
      case WAIT: {
        return;
      }
      case NORMAL:
      default: {
        this.authServer.spawnPlayer(player, new AuthSessionHandler(this.playerDao, player, this, registeredPlayer));
        break;
      }
    }
  }

  public void updateLoginData(Player player) throws SQLException {
    String lowercaseNickname = player.getUsername().toLowerCase(Locale.ROOT);
    UpdateBuilder<RegisteredPlayer, String> updateBuilder = this.playerDao.updateBuilder();
    updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, lowercaseNickname);
    updateBuilder.updateColumnValue(RegisteredPlayer.LOGIN_IP_FIELD, player.getRemoteAddress().getAddress().getHostAddress());
    updateBuilder.updateColumnValue(RegisteredPlayer.LOGIN_DATE_FIELD, System.currentTimeMillis());
    updateBuilder.update();
  }

  private boolean validateScheme(JsonElement jsonElement, List<String> scheme) {
    if (!scheme.isEmpty()) {
      if (!(jsonElement instanceof JsonObject)) {
        return false;
      }

      JsonObject object = (JsonObject) jsonElement;
      for (String field : scheme) {
        if (!object.has(field)) {
          return false;
        }
      }
    }

    return true;
  }

  public PremiumResponse isPremiumExternal(String nickname) {
    try {
      HttpResponse<String> response = this.client.send(
          HttpRequest.newBuilder()
              .uri(URI.create(String.format(Settings.IMP.MAIN.ISPREMIUM_AUTH_URL, URLEncoder.encode(nickname, StandardCharsets.UTF_8))))
              .build(),
          HttpResponse.BodyHandlers.ofString()
      );

      int statusCode = response.statusCode();

      if (Settings.IMP.MAIN.STATUS_CODE_RATE_LIMIT.contains(statusCode)) {
        return new PremiumResponse(PremiumState.RATE_LIMIT);
      }

      JsonElement jsonElement = JsonParser.parseString(response.body());

      if (Settings.IMP.MAIN.STATUS_CODE_USER_EXISTS.contains(statusCode)
          && this.validateScheme(jsonElement, Settings.IMP.MAIN.USER_EXISTS_JSON_VALIDATOR_FIELDS)) {
        return new PremiumResponse(PremiumState.PREMIUM_USERNAME, ((JsonObject) jsonElement).get(Settings.IMP.MAIN.JSON_UUID_FIELD).getAsString());
      }

      if (Settings.IMP.MAIN.STATUS_CODE_USER_NOT_EXISTS.contains(statusCode)
          && this.validateScheme(jsonElement, Settings.IMP.MAIN.USER_NOT_EXISTS_JSON_VALIDATOR_FIELDS)) {
        return new PremiumResponse(PremiumState.CRACKED);
      }

      return new PremiumResponse(PremiumState.ERROR);
    } catch (Throwable t) {
      LOGGER.error("通过 Mojang 查询正版账号信息失败", t);
      return new PremiumResponse(PremiumState.ERROR);
    }
  }

  public PremiumResponse isPremiumInternal(String nickname) {
    try {
      QueryBuilder<RegisteredPlayer, String> crackedCountQuery = this.playerDao.queryBuilder();
      crackedCountQuery.where()
          .eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, nickname)
          .and()
          .ne(RegisteredPlayer.HASH_FIELD, "");
      crackedCountQuery.setCountOf(true);

      QueryBuilder<RegisteredPlayer, String> premiumCountQuery = this.playerDao.queryBuilder();
      premiumCountQuery.where()
          .eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, nickname)
          .and()
          .eq(RegisteredPlayer.HASH_FIELD, "");
      premiumCountQuery.setCountOf(true);

      if (this.playerDao.countOf(crackedCountQuery.prepare()) != 0) {
        return new PremiumResponse(PremiumState.CRACKED);
      }

      if (this.playerDao.countOf(premiumCountQuery.prepare()) != 0) {
        return new PremiumResponse(PremiumState.PREMIUM);
      }

      return new PremiumResponse(PremiumState.UNKNOWN);
    } catch (SQLException e) {
      LOGGER.error("查询账号正版状态失败", e);
      return new PremiumResponse(PremiumState.ERROR);
    }
  }

  public boolean isPremiumUuid(UUID uuid) {
    try {
      QueryBuilder<RegisteredPlayer, String> premiumCountQuery = this.playerDao.queryBuilder();
      premiumCountQuery.where()
          .eq(RegisteredPlayer.PREMIUM_UUID_FIELD, uuid.toString())
          .and()
          .eq(RegisteredPlayer.HASH_FIELD, "");
      premiumCountQuery.setCountOf(true);

      return this.playerDao.countOf(premiumCountQuery.prepare()) != 0;
    } catch (SQLException e) {
      LOGGER.error("查询账号正版状态失败", e);
      return false;
    }
  }

  @SafeVarargs
  private boolean checkIsPremiumAndCache(String nickname, Function<String, PremiumResponse>... functions) {
    String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
    if (this.premiumCache.containsKey(lowercaseNickname)) {
      return this.premiumCache.get(lowercaseNickname).isPremium();
    }

    boolean premium = false;
    boolean unknown = false;
    boolean wasRateLimited = false;
    boolean wasError = false;
    UUID uuid = null;

    for (Function<String, PremiumResponse> function : functions) {
      PremiumResponse check;
      try {
        check = function.apply(lowercaseNickname);
      } catch (Throwable t) {
        check = new PremiumResponse(PremiumState.ERROR);
        LOGGER.error("检查玩家账号状态失败", t);
      }

      if (check.getUuid() != null) {
        uuid = check.getUuid();
      }

      switch (check.getState()) {
        case CRACKED: {
          return this.setPremiumCacheLowercased(lowercaseNickname, false).isPremium();
        }
        case PREMIUM: {
          return this.setForcedPremiumCacheLowercased(lowercaseNickname, true).isPremium();
        }
        case PREMIUM_USERNAME: {
          premium = true;
          break;
        }
        case UNKNOWN: {
          unknown = true;
          break;
        }
        case RATE_LIMIT: {
          wasRateLimited = true;
          break;
        }
        default:
        case ERROR: {
          wasError = true;
          break;
        }
      }
    }

    if (unknown) {
      if (uuid != null && this.isPremiumUuid(uuid)) {
        return this.setForcedPremiumCacheLowercased(lowercaseNickname, true).isPremium();
      }

      if (Settings.IMP.MAIN.ONLINE_MODE_NEED_AUTH) {
        return false;
      }
    }

    if (wasRateLimited && unknown || wasRateLimited && wasError) {
      return Settings.IMP.MAIN.ON_RATE_LIMIT_PREMIUM;
    }

    if (wasError && unknown || !premium) {
      return Settings.IMP.MAIN.ON_SERVER_ERROR_PREMIUM;
    }

    return this.setPremiumCacheLowercased(lowercaseNickname, true).isPremium();
  }

  public boolean isPremium(String nickname) {
    if (Settings.IMP.MAIN.FORCE_OFFLINE_MODE) {
      return false;
    } else {
      if (Settings.IMP.MAIN.CHECK_PREMIUM_PRIORITY_INTERNAL) {
        return checkIsPremiumAndCache(nickname, this::isPremiumInternal, this::isPremiumExternal);
      } else {
        return checkIsPremiumAndCache(nickname, this::isPremiumExternal, this::isPremiumInternal);
      }
    }
  }

  public CachedPremiumUser getPremiumCache(String nickname) {
    return this.premiumCache.get(nickname.toLowerCase(Locale.ROOT));
  }

  public CachedPremiumUser setForcedPremiumCacheLowercased(String lowercasedNickname, boolean value) {
    CachedPremiumUser premiumUser = this.setPremiumCacheLowercased(lowercasedNickname, value);
    premiumUser.setForcePremium(value);
    return premiumUser;
  }

  public CachedPremiumUser setPremiumCacheLowercased(String lowercasedNickname, boolean value) {
    CachedPremiumUser premiumUser = new CachedPremiumUser(System.currentTimeMillis(), value);
    this.premiumCache.put(lowercasedNickname, premiumUser);
    return premiumUser;
  }

  public void incrementBruteforceAttempts(InetAddress address) {
    this.getBruteforceUser(address).incrementAttempts();
  }

  public int getBruteforceAttempts(InetAddress address) {
    return this.getBruteforceUser(address).getAttempts();
  }

  private CachedBruteforceUser getBruteforceUser(InetAddress address) {
    CachedBruteforceUser user = this.bruteforceCache.get(address);
    if (user == null) {
      user = new CachedBruteforceUser(System.currentTimeMillis());
      this.bruteforceCache.put(address, user);
    }

    return user;
  }

  public void clearBruteforceAttempts(InetAddress address) {
    this.bruteforceCache.remove(address);
  }

  public void saveForceOfflineMode(String nickname) {
    this.forcedPreviously.add(nickname);
  }

  public void unsetForcedPreviously(String nickname) {
    this.forcedPreviously.remove(nickname);
  }

  public boolean isForcedPreviously(String nickname) {
    return this.forcedPreviously.contains(nickname);
  }

  public Set<String> getPendingLogins() {
    return this.pendingLogins;
  }

  public Map<UUID, Runnable> getPostLoginTasks() {
    return this.postLoginTasks;
  }

  public Set<String> getUnsafePasswords() {
    return this.unsafePasswords;
  }

  public ProxyServer getServer() {
    return this.server;
  }

  public ConnectionSource getConnectionSource() {
    return this.connectionSource;
  }

  public Dao<RegisteredPlayer, String> getPlayerDao() {
    return this.playerDao;
  }

  private static void setLogger(Logger logger) {
    LOGGER = logger;
  }

  public static MiniMessage getSerializer() {
    return SERIALIZER;
  }

  public Limbo getAuthServer() {
    return this.authServer;
  }

  public Pattern getNicknameValidationPattern() {
    return this.nicknameValidationPattern;
  }

  public void addAuthenticatingPlayer(String nickname, AuthSessionHandler handler) {
    this.authenticatingPlayers.put(nickname, handler);
  }

  public void removeAuthenticatingPlayer(String nickname) {
    this.authenticatingPlayers.remove(nickname);
  }

  public void removeAuthenticatingPlayer(String nickname, AuthSessionHandler handler) {
    this.authenticatingPlayers.remove(nickname, handler);
  }

  public AuthSessionHandler getAuthenticatingPlayer(String nickname) {
    return this.authenticatingPlayers.get(nickname);
  }

  public Map<String, AuthSessionHandler> getAuthenticatingPlayers() {
    return this.authenticatingPlayers;
  }

  public static class CachedUser {

    private final long checkTime;

    public CachedUser(long checkTime) {
      this.checkTime = checkTime;
    }

    public long getCheckTime() {
      return this.checkTime;
    }
  }

  private static class CachedSessionUser extends CachedUser {

    private final InetAddress inetAddress;
    private final String username;

    public CachedSessionUser(long checkTime, InetAddress inetAddress, String username) {
      super(checkTime);

      this.inetAddress = inetAddress;
      this.username = username;
    }

    public InetAddress getInetAddress() {
      return this.inetAddress;
    }

    public String getUsername() {
      return this.username;
    }
  }

  public static class CachedPremiumUser extends CachedUser {

    private final boolean premium;
    private boolean forcePremium;

    public CachedPremiumUser(long checkTime, boolean premium) {
      super(checkTime);

      this.premium = premium;
    }

    public void setForcePremium(boolean forcePremium) {
      this.forcePremium = forcePremium;
    }

    public boolean isForcePremium() {
      return this.forcePremium;
    }

    public boolean isPremium() {
      return this.premium;
    }
  }

  private static class CachedBruteforceUser extends CachedUser {

    private int attempts;

    public CachedBruteforceUser(long checkTime) {
      super(checkTime);
    }

    public void incrementAttempts() {
      this.attempts++;
    }

    public int getAttempts() {
      return  this.attempts;
    }
  }

  public static class PremiumResponse {

    private final PremiumState state;
    private final UUID uuid;

    public PremiumResponse(PremiumState state) {
      this.state = state;
      this.uuid = null;
    }

    public PremiumResponse(PremiumState state, UUID uuid) {
      this.state = state;
      this.uuid = uuid;
    }

    @SuppressFBWarnings("CT_CONSTRUCTOR_THROW")
    public PremiumResponse(PremiumState state, String uuid) {
      this.state = state;
      if (uuid.contains("-")) {
        this.uuid = UUID.fromString(uuid);
      } else {
        this.uuid = new UUID(Long.parseUnsignedLong(uuid.substring(0, 16), 16), Long.parseUnsignedLong(uuid.substring(16), 16));
      }
    }

    public PremiumState getState() {
      return this.state;
    }

    public UUID getUuid() {
      return this.uuid;
    }
  }

  public enum PremiumState {
    PREMIUM,
    PREMIUM_USERNAME,
    CRACKED,
    UNKNOWN,
    RATE_LIMIT,
    ERROR
  }
}
