package com.baymc.auth.listener;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.BayMcAuth.CachedPremiumUser;
import com.baymc.auth.BayMcAuth.PremiumState;
import com.baymc.auth.Settings;
import com.baymc.auth.floodgate.FloodgateApiHolder;
import com.baymc.auth.handler.AuthSessionHandler;
import com.baymc.auth.model.RegisteredPlayer;
import com.baymc.auth.model.SQLRuntimeException;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.util.UuidUtils;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.InitialInboundConnection;
import com.velocitypowered.proxy.connection.client.LoginInboundConnection;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.elytrium.commons.utils.reflection.ReflectionException;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.kyori.adventure.text.Component;

// TODO: 支持自定义事件优先级
public class AuthListener {

  private static final MethodHandle DELEGATE_FIELD;

  private final BayMcAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;
  private final FloodgateApiHolder floodgateApi;
  private final Component errorOccurred;

  public AuthListener(BayMcAuth plugin, Dao<RegisteredPlayer, String> playerDao, FloodgateApiHolder floodgateApi) {
    this.plugin = plugin;
    this.playerDao = playerDao;
    this.floodgateApi = floodgateApi;

    this.errorOccurred = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.ERROR_OCCURRED);
  }

  @Subscribe(order = PostOrder.LATE)
  public void onPreLoginEvent(PreLoginEvent event) {
    // 如果其他插件已拒绝该事件, 则不再处理
    if (!event.getResult().isAllowed()) {
      return;
    }

    try {
      String username = event.getUsername();
      if (!event.getResult().isForceOfflineMode()) {
        if (this.plugin.isPremium(username)) {
          event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());

          try {
            if (!Settings.IMP.MAIN.ONLINE_MODE_NEED_AUTH_STRICT) {
              CachedPremiumUser premiumUser = this.plugin.getPremiumCache(username);
              MinecraftConnection connection = this.getConnection(event.getConnection());
              if (!connection.isClosed() && premiumUser != null && !premiumUser.isForcePremium()
                  && this.plugin.isPremiumInternal(username.toLowerCase(Locale.ROOT)).getState() == PremiumState.UNKNOWN) {
                this.plugin.getPendingLogins().add(username);

                // Velocity 没有适用于此场景的事件, 因此直接向 Netty 注入处理器
                connection.getChannel().closeFuture().addListener(future -> {
                  // 玩家在客户端侧未通过正版验证, 将其标记为离线模式
                  if (this.plugin.getPendingLogins().remove(username)) {
                    this.plugin.setPremiumCacheLowercased(username.toLowerCase(Locale.ROOT), false);
                  }
                });
              }
            }
          } catch (Throwable throwable) {
            throw new IllegalStateException("跟踪玩家认证流程失败", throwable);
          }
        } else {
          event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
        }
      } else {
        try {
          MinecraftConnection connection = this.getConnection(event.getConnection());
          if (!connection.isClosed()) {
            this.plugin.saveForceOfflineMode(username);

            // Velocity 没有适用于此场景的事件, 因此直接向 Netty 注入处理器
            connection.getChannel().closeFuture().addListener(future -> {
              this.plugin.unsetForcedPreviously(username);
            });
          }
        } catch (Throwable throwable) {
          throw new IllegalStateException("跟踪客户端断开连接失败", throwable);
        }
      }
    } catch (Throwable throwable) {
      event.setResult(PreLoginComponentResult.denied(this.errorOccurred));
      throw throwable;
    }
  }

  private MinecraftConnection getConnection(InboundConnection inbound) throws Throwable {
    LoginInboundConnection inboundConnection = (LoginInboundConnection) inbound;
    InitialInboundConnection initialInbound = (InitialInboundConnection) DELEGATE_FIELD.invokeExact(inboundConnection);
    return initialInbound.getConnection();
  }

  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    Runnable postLoginTask = this.plugin.getPostLoginTasks().remove(uuid);
    if (postLoginTask != null) {
      // 玩家客户端完成服务器切换需要少量时间, 因此延迟执行
      this.plugin.getServer().getScheduler()
          .buildTask(this.plugin, postLoginTask)
          .delay(Settings.IMP.MAIN.PREMIUM_AND_FLOODGATE_MESSAGES_DELAY, TimeUnit.MILLISECONDS)
          .schedule();
    }
  }

  @Subscribe
  public void onLoginLimboRegister(LoginLimboRegisterEvent event) {
    // 玩家已完成在线模式认证, 可以确认其拥有正版账号
    if (event.getPlayer().isOnlineMode()) {
      CachedPremiumUser premiumUser = this.plugin.getPremiumCache(event.getPlayer().getUsername());
      if (premiumUser != null) {
        premiumUser.setForcePremium(true);
      }

      this.plugin.getPendingLogins().remove(event.getPlayer().getUsername());
    }

    if (this.plugin.needAuth(event.getPlayer())) {
      event.addOnJoinCallback(() -> this.plugin.authPlayer(event.getPlayer()));
    }
  }

  @Subscribe(order = PostOrder.FIRST)
  public void onGameProfileRequest(GameProfileRequestEvent event) {
    if (Settings.IMP.MAIN.SAVE_UUID && (this.floodgateApi == null || !this.floodgateApi.isFloodgatePlayer(event.getOriginalProfile().getId()))) {
      RegisteredPlayer registeredPlayer = AuthSessionHandler.fetchInfo(this.playerDao, event.getOriginalProfile().getId());

      if (registeredPlayer != null && !registeredPlayer.getUuid().isEmpty()) {
        event.setGameProfile(event.getOriginalProfile().withId(UUID.fromString(registeredPlayer.getUuid())));
        return;
      }
      registeredPlayer = AuthSessionHandler.fetchInfo(this.playerDao, event.getUsername());

      if (registeredPlayer != null) {
        String currentUuid = registeredPlayer.getUuid();

        if (currentUuid.isEmpty()) {
          try {
            registeredPlayer.setUuid(event.getGameProfile().getId().toString());
            this.playerDao.update(registeredPlayer);
          } catch (SQLException e) {
            throw new SQLRuntimeException(e);
          }
        } else {
          event.setGameProfile(event.getOriginalProfile().withId(UUID.fromString(currentUuid)));
        }
      }
    } else if (event.isOnlineMode()) {
      try {
        UpdateBuilder<RegisteredPlayer, String> updateBuilder = this.playerDao.updateBuilder();
        updateBuilder.where().eq(RegisteredPlayer.LOWERCASE_NICKNAME_FIELD, event.getUsername().toLowerCase(Locale.ROOT));
        updateBuilder.updateColumnValue(RegisteredPlayer.HASH_FIELD, "");
        updateBuilder.update();
      } catch (SQLException e) {
        throw new SQLRuntimeException(e);
      }
    }

    if (Settings.IMP.MAIN.FORCE_OFFLINE_UUID) {
      event.setGameProfile(event.getOriginalProfile().withId(UuidUtils.generateOfflinePlayerUuid(event.getUsername())));
    }

    if (!event.isOnlineMode() && !Settings.IMP.MAIN.OFFLINE_MODE_PREFIX.isEmpty()) {
      event.setGameProfile(event.getOriginalProfile().withName(Settings.IMP.MAIN.OFFLINE_MODE_PREFIX + event.getUsername()));
    }

    if (event.isOnlineMode() && !Settings.IMP.MAIN.ONLINE_MODE_PREFIX.isEmpty()) {
      event.setGameProfile(event.getOriginalProfile().withName(Settings.IMP.MAIN.ONLINE_MODE_PREFIX + event.getUsername()));
    }
  }

  static {
    try {
      DELEGATE_FIELD = MethodHandles.privateLookupIn(LoginInboundConnection.class, MethodHandles.lookup())
          .findGetter(LoginInboundConnection.class, "delegate", InitialInboundConnection.class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new ReflectionException("无法获取 Velocity 登录连接字段", e);
    }
  }
}
