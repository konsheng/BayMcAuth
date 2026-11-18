package com.baymc.auth.handler;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.event.PostAuthorizationEvent;
import com.baymc.auth.event.PostRegisterEvent;
import com.baymc.auth.event.TaskEvent;
import com.baymc.auth.model.RegisteredPlayer;
import com.baymc.auth.model.SQLRuntimeException;
import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.proxy.Player;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthSessionHandler implements LimboSessionHandler {

  public static final CodeVerifier TOTP_CODE_VERIFIER = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
  private static final BCrypt.Verifyer HASH_VERIFIER = BCrypt.verifyer();
  private static final BCrypt.Hasher HASHER = BCrypt.withDefaults();
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthSessionHandler.class);

  private static Component ratelimited;
  private static BossBar.Color bossbarColor;
  private static BossBar.Overlay bossbarOverlay;
  private static Component ipLimitKick;
  private static Component databaseErrorKick;
  private static Component passwordChangedKick;
  private static String wrongNicknameCaseKick;
  private static Component timesUp;
  private static Component registerSuccessful;
  @Nullable
  private static Title registerSuccessfulTitle;
  private static Component[] loginWrongPassword;
  private static Component loginWrongPasswordKick;
  private static Component totp;
  @Nullable
  private static Title totpTitle;
  private static Component register;
  @Nullable
  private static Title registerTitle;
  private static Component[] login;
  @Nullable
  private static Title loginTitle;
  private static Component registerDifferentPasswords;
  private static Component registerPasswordTooLong;
  private static Component registerPasswordTooShort;
  private static Component registerPasswordUnsafe;
  private static Component loginSuccessful;
  @Nullable
  private static Title loginSuccessfulTitle;

  private final Dao<RegisteredPlayer, String> playerDao;
  private final Player proxyPlayer;
  private final BayMcAuth plugin;
  private final AtomicBoolean ended = new AtomicBoolean();

  private final long joinTime = System.currentTimeMillis();
  private final BossBar bossBar = BossBar.bossBar(
      Component.empty(),
      1.0F,
      bossbarColor,
      bossbarOverlay
  );

  @Nullable
  private RegisteredPlayer playerInfo;

  private volatile ScheduledFuture<?> authMainTask;

  private LimboPlayer player;
  private volatile int attempts = Settings.IMP.MAIN.LOGIN_ATTEMPTS;
  private volatile boolean totpState;
  private String tempPassword;

  public AuthSessionHandler(Dao<RegisteredPlayer, String> playerDao, Player proxyPlayer, BayMcAuth plugin, @Nullable RegisteredPlayer playerInfo) {
    this.playerDao = playerDao;
    this.proxyPlayer = proxyPlayer;
    this.plugin = plugin;
    this.playerInfo = playerInfo;
  }

  @Override
  public void onSpawn(Limbo server, LimboPlayer player) {
    this.player = player;

    if (Settings.IMP.MAIN.DISABLE_FALLING) {
      this.player.disableFalling();
    } else {
      this.player.enableFalling();
    }

    if (this.playerInfo == null) {
      try {
        String ip = this.proxyPlayer.getRemoteAddress().getAddress().getHostAddress();
        List<RegisteredPlayer> alreadyRegistered = this.playerDao.queryForEq(RegisteredPlayer.IP_FIELD, ip);
        if (alreadyRegistered != null) {
          int sizeOfValidRegistrations = alreadyRegistered.size();
          if (Settings.IMP.MAIN.IP_LIMIT_VALID_TIME > 0) {
            for (RegisteredPlayer registeredPlayer : alreadyRegistered.stream()
                .filter(registeredPlayer -> registeredPlayer.getRegDate() < System.currentTimeMillis() - Settings.IMP.MAIN.IP_LIMIT_VALID_TIME)
                .collect(Collectors.toList())) {
              registeredPlayer.setIP("");
              this.playerDao.update(registeredPlayer);
              --sizeOfValidRegistrations;
            }
          }

          if (sizeOfValidRegistrations >= Settings.IMP.MAIN.IP_LIMIT_REGISTRATIONS) {
            this.proxyPlayer.disconnect(ipLimitKick);
            return;
          }
        }
      } catch (SQLException e) {
        this.proxyPlayer.disconnect(databaseErrorKick);
        throw new SQLRuntimeException(e);
      }
    } else {
      if (!this.proxyPlayer.getUsername().equals(this.playerInfo.getNickname())) {
        this.proxyPlayer.disconnect(BayMcAuth.getSerializer().deserialize(
            wrongNicknameCaseKick,
            Placeholder.unparsed("expected_name", this.playerInfo.getNickname()),
            Placeholder.unparsed("current_name", this.proxyPlayer.getUsername())));
        return;
      }

      try {
        if (!this.trackAuthentication(this.playerInfo)) {
          this.invalidateForPasswordChange();
          return;
        }
      } catch (SQLRuntimeException e) {
        this.disconnectIfPending(databaseErrorKick);
        throw e;
      }
    }

    boolean bossBarEnabled = Settings.IMP.MAIN.ENABLE_BOSSBAR;
    int authTime = Settings.IMP.MAIN.AUTH_TIME;
    float multiplier = 1000.0F / authTime;
    this.authMainTask = this.player.getScheduledExecutor().scheduleWithFixedDelay(() -> {
      if (this.ended.get()) {
        return;
      }
      if (System.currentTimeMillis() - this.joinTime > authTime) {
        this.proxyPlayer.disconnect(timesUp);
      } else {
        if (bossBarEnabled) {
          float secondsLeft = (authTime - (System.currentTimeMillis() - this.joinTime)) / 1000.0F;
          this.bossBar.name(BayMcAuth.getSerializer().deserialize(
              Settings.IMP.MAIN.STRINGS.BOSSBAR, Formatter.number("seconds", (int) secondsLeft)));
          // 进度值可能略大于 1, 例如 1.0000001
          this.bossBar.progress(Math.min(1.0F, secondsLeft * multiplier));
        }
      }
    }, 0, 1, TimeUnit.SECONDS);

    if (this.ended.get()) {
      this.authMainTask.cancel(false);
      return;
    }

    if (bossBarEnabled) {
      this.proxyPlayer.showBossBar(this.bossBar);
    }

    this.sendMessage(true);
  }

  @Override
  public void onChat(String message) {
    if (this.ended.get()) {
      return;
    }
    if (!BayMcAuth.RATELIMITER.attempt(this.proxyPlayer.getRemoteAddress().getAddress())) {
      this.proxyPlayer.sendMessage(AuthSessionHandler.ratelimited);
      return;
    }

    String[] args = message.split(" ");
    if (args.length != 0 && this.checkArgsLength(args.length)) {
      Command command = Command.parse(args[0]);
      if (command == Command.REGISTER && !this.totpState && this.playerInfo == null) {
        String password = args[1];
        if (this.checkPasswordsRepeat(args) && this.checkPasswordLength(password) && this.checkPasswordStrength(password)) {
          this.saveTempPassword(password);
          RegisteredPlayer registeredPlayer = new RegisteredPlayer(this.proxyPlayer).setPassword(password);

          try {
            this.playerDao.create(registeredPlayer);
            this.playerInfo = registeredPlayer;
          } catch (SQLException e) {
            this.proxyPlayer.disconnect(databaseErrorKick);
            throw new SQLRuntimeException(e);
          }

          this.proxyPlayer.sendMessage(registerSuccessful);
          if (registerSuccessfulTitle != null) {
            this.proxyPlayer.showTitle(registerSuccessfulTitle);
          }

          this.plugin.getServer().getEventManager()
              .fire(new PostRegisterEvent(this::finishAuth, this.player, this.playerInfo, this.tempPassword))
              .thenAcceptAsync(this::finishAuth);
        }

        // {@code return} 必须放在这里, 因为以下密码检查方法返回 false 时已调用 Player#sendMessage 发送提示
        // 这些方法包括 AuthSessionHandler#checkPasswordsRepeat, AuthSessionHandler#checkPasswordLength
        // 和 AuthSessionHandler#checkPasswordStrength
        // 如果不在这里返回, AuthSessionHandler#sendMessage 会额外发送一条提示
        return;
      } else if (command == Command.LOGIN && !this.totpState && this.playerInfo != null) {
        String password = args[1];
        this.saveTempPassword(password);

        if (password.length() > 0 && checkPassword(password, this.playerInfo)) {
          if (this.playerInfo.getTotpToken().isEmpty()) {
            this.finishLogin();
          } else {
            this.totpState = true;
            this.sendMessage(true);
          }
        } else if (--this.attempts != 0) {
          this.proxyPlayer.sendMessage(loginWrongPassword[this.attempts - 1]);
          this.checkBruteforceAttempts();
        } else {
          this.proxyPlayer.disconnect(loginWrongPasswordKick);
        }

        return;
      } else if (command == Command.TOTP && this.totpState && this.playerInfo != null) {
        if (TOTP_CODE_VERIFIER.isValidCode(this.playerInfo.getTotpToken(), args[1])) {
          this.finishLogin();
          return;
        } else {
          this.checkBruteforceAttempts();
        }
      }
    }

    this.sendMessage(false);
  }

  private void checkBruteforceAttempts() {
    this.plugin.incrementBruteforceAttempts(this.proxyPlayer.getRemoteAddress().getAddress());
    if (this.plugin.getBruteforceAttempts(this.proxyPlayer.getRemoteAddress().getAddress()) >= Settings.IMP.MAIN.BRUTEFORCE_MAX_ATTEMPTS) {
      this.proxyPlayer.disconnect(loginWrongPasswordKick);
    }
  }

  private void saveTempPassword(String password) {
    this.tempPassword = password;
  }

  @Override
  public void onDisconnect() {
    this.ended.set(true);
    this.tempPassword = null;
    if (this.authMainTask != null) {
      this.authMainTask.cancel(true);
    }

    this.proxyPlayer.hideBossBar(this.bossBar);
    this.plugin.removeAuthenticatingPlayer(this.proxyPlayer.getUsername(), this);
  }

  private void sendMessage(boolean sendTitle) {
    if (this.totpState) {
      this.proxyPlayer.sendMessage(totp);
      if (sendTitle && totpTitle != null) {
        this.proxyPlayer.showTitle(totpTitle);
      }
    } else if (this.playerInfo == null) {
      this.proxyPlayer.sendMessage(register);
      if (sendTitle && registerTitle != null) {
        this.proxyPlayer.showTitle(registerTitle);
      }
    } else {
      this.proxyPlayer.sendMessage(login[this.attempts - 1]);
      if (sendTitle && loginTitle != null) {
        this.proxyPlayer.showTitle(loginTitle);
      }
    }
  }

  private boolean checkArgsLength(int argsLength) {
    if (this.playerInfo == null && Settings.IMP.MAIN.REGISTER_NEED_REPEAT_PASSWORD) {
      return argsLength == 3;
    } else {
      return argsLength == 2;
    }
  }

  private boolean checkPasswordsRepeat(String[] args) {
    if (!Settings.IMP.MAIN.REGISTER_NEED_REPEAT_PASSWORD || args[1].equals(args[2])) {
      return true;
    } else {
      this.proxyPlayer.sendMessage(registerDifferentPasswords);
      return false;
    }
  }

  private boolean checkPasswordLength(String password) {
    int length = password.length();
    if (length > Settings.IMP.MAIN.MAX_PASSWORD_LENGTH) {
      this.proxyPlayer.sendMessage(registerPasswordTooLong);
      return false;
    } else if (length < Settings.IMP.MAIN.MIN_PASSWORD_LENGTH) {
      this.proxyPlayer.sendMessage(registerPasswordTooShort);
      return false;
    } else {
      return true;
    }
  }

  private boolean checkPasswordStrength(String password) {
    if (Settings.IMP.MAIN.CHECK_PASSWORD_STRENGTH && this.plugin.getUnsafePasswords().contains(password)) {
      this.proxyPlayer.sendMessage(registerPasswordUnsafe);
      return false;
    } else {
      return true;
    }
  }

  public void finishLogin() {
    if (this.ended.get()) {
      return;
    }
    this.plugin.getServer().getEventManager()
        .fire(new PostAuthorizationEvent(this::finishAuth, this.player, this.playerInfo, this.tempPassword))
        .thenAcceptAsync(this::finishAuth);
  }

  private void finishAuth(TaskEvent event) {
    if (this.ended.get() || event.getResult() == TaskEvent.Result.WAIT) {
      return;
    }
    if (event.getResult() == TaskEvent.Result.CANCEL) {
      this.disconnectIfPending(event.getReason());
      return;
    }

    this.completeAuthentication(event instanceof PostAuthorizationEvent);
  }

  private boolean trackAuthentication(RegisteredPlayer account) {
    Lock lock = this.plugin.getAuthenticationLock(account.getLowercaseNickname());
    lock.lock();
    try {
      if (this.ended.get() || !matchesCurrentPassword(this.playerDao, account)) {
        return false;
      }
      this.plugin.addAuthenticatingPlayer(this.proxyPlayer.getUsername(), this);
      return true;
    } finally {
      lock.unlock();
    }
  }

  public boolean endPendingAuthentication() {
    if (!this.ended.compareAndSet(false, true)) {
      return false;
    }

    this.tempPassword = null;
    this.plugin.removeAuthenticatingPlayer(this.proxyPlayer.getUsername(), this);
    return true;
  }

  public void disconnectForPasswordChange() {
    this.proxyPlayer.disconnect(passwordChangedKick);
  }

  private void invalidateForPasswordChange() {
    if (this.endPendingAuthentication()) {
      this.disconnectForPasswordChange();
    }
  }

  private void disconnectIfPending(Component reason) {
    if (this.endPendingAuthentication()) {
      this.proxyPlayer.disconnect(reason);
    }
  }

  private void completeAuthentication(boolean passwordLogin) {
    RegisteredPlayer account = this.playerInfo;
    if (account == null) {
      return;
    }

    boolean passwordIsCurrent;
    Lock lock = this.plugin.getAuthenticationLock(account.getLowercaseNickname());
    try {
      lock.lock();
      try {
        if (this.ended.get()) {
          return;
        }
        // 等待双重验证或异步事件完成时密码也可能改变, 放行前重新确认
        passwordIsCurrent = matchesCurrentPassword(this.playerDao, account);
        if (passwordIsCurrent) {
          this.plugin.updateLoginData(this.proxyPlayer);
          if (!this.endPendingAuthentication()) {
            return;
          }
          this.plugin.cacheAuthUser(this.proxyPlayer);
        }
      } finally {
        lock.unlock();
      }
    } catch (SQLException | SQLRuntimeException e) {
      LOGGER.error("完成玩家认证时访问数据库失败", e);
      this.disconnectIfPending(databaseErrorKick);
      return;
    }

    if (!passwordIsCurrent) {
      this.invalidateForPasswordChange();
      return;
    }

    if (passwordLogin) {
      this.proxyPlayer.sendMessage(loginSuccessful);
      if (loginSuccessfulTitle != null) {
        this.proxyPlayer.showTitle(loginSuccessfulTitle);
      }
      this.plugin.clearBruteforceAttempts(this.proxyPlayer.getRemoteAddress().getAddress());
    }
    if (Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.CLEAR_AFTER_LOGIN) {
      this.proxyPlayer.clearTitle();
    }

    this.player.disconnect();
  }

  public static boolean matchesCurrentPassword(Dao<RegisteredPlayer, String> dao, RegisteredPlayer account) {
    RegisteredPlayer current = fetchInfoLowercased(dao, account.getLowercaseNickname());
    return current != null && current.getHash().equals(account.getHash());
  }

  public static void reload() {
    MiniMessage serializer = BayMcAuth.getSerializer();
    AuthSessionHandler.ratelimited = serializer.deserialize(Settings.IMP.MAIN.STRINGS.RATELIMITED);
    bossbarColor = Settings.IMP.MAIN.BOSSBAR_COLOR;
    bossbarOverlay = Settings.IMP.MAIN.BOSSBAR_OVERLAY;
    ipLimitKick = serializer.deserialize(Settings.IMP.MAIN.STRINGS.IP_LIMIT_KICK);
    databaseErrorKick = serializer.deserialize(Settings.IMP.MAIN.STRINGS.DATABASE_ERROR_KICK);
    passwordChangedKick = serializer.deserialize(Settings.IMP.MAIN.STRINGS.PASSWORD_CHANGED_KICK);
    wrongNicknameCaseKick = Settings.IMP.MAIN.STRINGS.WRONG_NICKNAME_CASE_KICK;
    timesUp = serializer.deserialize(Settings.IMP.MAIN.STRINGS.TIMES_UP);
    registerSuccessful = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_SUCCESSFUL);
    if (Settings.IMP.MAIN.STRINGS.REGISTER_SUCCESSFUL_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.REGISTER_SUCCESSFUL_SUBTITLE.isEmpty()) {
      registerSuccessfulTitle = null;
    } else {
      registerSuccessfulTitle = Title.title(
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_SUCCESSFUL_TITLE),
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_SUCCESSFUL_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    int loginAttempts = Settings.IMP.MAIN.LOGIN_ATTEMPTS;
    loginWrongPassword = new Component[loginAttempts];
    for (int i = 0; i < loginAttempts; ++i) {
      loginWrongPassword[i] = serializer.deserialize(
          Settings.IMP.MAIN.STRINGS.LOGIN_WRONG_PASSWORD, Formatter.number("attempts", i + 1));
    }
    loginWrongPasswordKick = serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_WRONG_PASSWORD_KICK);
    totp = serializer.deserialize(Settings.IMP.MAIN.STRINGS.TOTP);
    if (Settings.IMP.MAIN.STRINGS.TOTP_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.TOTP_SUBTITLE.isEmpty()) {
      totpTitle = null;
    } else {
      totpTitle = Title.title(
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.TOTP_TITLE),
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.TOTP_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    register = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER);
    if (Settings.IMP.MAIN.STRINGS.REGISTER_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.REGISTER_SUBTITLE.isEmpty()) {
      registerTitle = null;
    } else {
      registerTitle = Title.title(
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_TITLE),
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    login = new Component[loginAttempts];
    for (int i = 0; i < loginAttempts; ++i) {
      login[i] = serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN, Formatter.number("attempts", i + 1));
    }
    if (Settings.IMP.MAIN.STRINGS.LOGIN_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.LOGIN_SUBTITLE.isEmpty()) {
      loginTitle = null;
    } else {
      loginTitle = Title.title(
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_TITLE, Formatter.number("attempts", loginAttempts)),
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_SUBTITLE, Formatter.number("attempts", loginAttempts)),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
    registerDifferentPasswords = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_DIFFERENT_PASSWORDS);
    registerPasswordTooLong = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_PASSWORD_TOO_LONG);
    registerPasswordTooShort = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_PASSWORD_TOO_SHORT);
    registerPasswordUnsafe = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_PASSWORD_UNSAFE);
    loginSuccessful = serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_SUCCESSFUL);
    if (Settings.IMP.MAIN.STRINGS.LOGIN_SUCCESSFUL_TITLE.isEmpty() && Settings.IMP.MAIN.STRINGS.LOGIN_SUCCESSFUL_SUBTITLE.isEmpty()) {
      loginSuccessfulTitle = null;
    } else {
      loginSuccessfulTitle = Title.title(
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_SUCCESSFUL_TITLE),
          serializer.deserialize(Settings.IMP.MAIN.STRINGS.LOGIN_SUCCESSFUL_SUBTITLE),
          Settings.IMP.MAIN.CRACKED_TITLE_SETTINGS.toTimes()
      );
    }
  }

  public static boolean checkPassword(String password, RegisteredPlayer player) {
    return HASH_VERIFIER.verify(
        password.getBytes(StandardCharsets.UTF_8),
        player.getHash().getBytes(StandardCharsets.UTF_8)
    ).verified;
  }

  public static RegisteredPlayer fetchInfo(Dao<RegisteredPlayer, String> playerDao, UUID uuid) {
    try {
      List<RegisteredPlayer> playerList = playerDao.queryForEq(RegisteredPlayer.PREMIUM_UUID_FIELD, uuid.toString());
      return (playerList != null ? playerList.size() : 0) == 0 ? null : playerList.get(0);
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    }
  }

  public static RegisteredPlayer fetchInfo(Dao<RegisteredPlayer, String> playerDao, String nickname) {
    return AuthSessionHandler.fetchInfoLowercased(playerDao, nickname.toLowerCase(Locale.ROOT));
  }

  public static RegisteredPlayer fetchInfoLowercased(Dao<RegisteredPlayer, String> playerDao, String nickname) {
    try {
      List<RegisteredPlayer> playerList = playerDao.queryForEq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, nickname);
      return (playerList != null ? playerList.size() : 0) == 0 ? null : playerList.get(0);
    } catch (SQLException e) {
      throw new SQLRuntimeException(e);
    }
  }

  /**
   * 请使用 {@link RegisteredPlayer#genHash(String)} 或 {@link RegisteredPlayer#setPassword}
   */
  @Deprecated()
  public static String genHash(String password) {
    return HASHER.hashToString(Settings.IMP.MAIN.BCRYPT_COST, password.toCharArray());
  }


  private enum Command {

    INVALID,
    REGISTER,
    LOGIN,
    TOTP;

    static Command parse(String command) {
      if (Settings.IMP.MAIN.REGISTER_COMMAND.contains(command)) {
        return Command.REGISTER;
      } else if (Settings.IMP.MAIN.LOGIN_COMMAND.contains(command)) {
        return Command.LOGIN;
      } else if (Settings.IMP.MAIN.TOTP_COMMAND.contains(command)) {
        return Command.TOTP;
      } else {
        return Command.INVALID;
      }
    }
  }
}
