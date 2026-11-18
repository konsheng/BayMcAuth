package com.baymc.auth.listener;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.backend.Endpoint;
import com.baymc.auth.backend.type.LongDatabaseEndpoint;
import com.baymc.auth.backend.type.StringDatabaseEndpoint;
import com.baymc.auth.backend.type.StringEndpoint;
import com.baymc.auth.backend.type.UnknownEndpoint;
import com.baymc.auth.model.RegisteredPlayer;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.protocol.packet.PluginMessagePacket;
import io.netty.buffer.Unpooled;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.function.Function;

public class BackendEndpointsListener {

  // 保留旧通信通道, 兼容现有后端扩展
  public static final ChannelIdentifier API_CHANNEL = MinecraftChannelIdentifier.create("limboauth", "backend_api");

  public static final Map<String, Function<BayMcAuth, Endpoint>> TYPES = Map.ofEntries(
      new SimpleEntry<>("available_endpoints", plugin -> new StringEndpoint(plugin, "available_endpoints",
          username -> String.join(",", Settings.IMP.MAIN.BACKEND_API.ENABLED_ENDPOINTS.stream()
              .filter(BackendEndpointsListener.TYPES::containsKey).toList()))),
      new SimpleEntry<>("premium_state", auth -> new StringEndpoint(auth, "premium_state",
          username -> auth.isPremiumInternal(username).getState().name())),
      new SimpleEntry<>("hash", plugin -> new StringDatabaseEndpoint(plugin, "hash", RegisteredPlayer::getHash)),
      new SimpleEntry<>("totp_token", plugin -> new StringDatabaseEndpoint(plugin, "totp_token", RegisteredPlayer::getTotpToken)),
      new SimpleEntry<>("reg_date", plugin -> new LongDatabaseEndpoint(plugin, "reg_date", RegisteredPlayer::getRegDate)),
      new SimpleEntry<>("uuid", plugin -> new StringDatabaseEndpoint(plugin, "uuid", RegisteredPlayer::getUuid)),
      new SimpleEntry<>("premium_uuid", plugin -> new StringDatabaseEndpoint(plugin, "premium_uuid", RegisteredPlayer::getPremiumUuid)),
      new SimpleEntry<>("ip", plugin -> new StringDatabaseEndpoint(plugin, "ip", RegisteredPlayer::getIP)),
      new SimpleEntry<>("login_ip", plugin -> new StringDatabaseEndpoint(plugin, "login_ip", RegisteredPlayer::getLoginIp)),
      new SimpleEntry<>("login_date", plugin -> new LongDatabaseEndpoint(plugin, "login_date", RegisteredPlayer::getLoginDate))
  );

  private final BayMcAuth plugin;

  public BackendEndpointsListener(BayMcAuth plugin) {
    this.plugin = plugin;

    plugin.getServer().getChannelRegistrar().register(API_CHANNEL);
  }

  @Subscribe
  public void onRequest(PluginMessageEvent event) {
    if (event.getIdentifier() != API_CHANNEL) {
      return;
    }

    event.setResult(ForwardResult.handled());
    if (!(event.getSource() instanceof VelocityServerConnection server) || !server.isActive()) {
      return;
    }

    Endpoint endpoint;
    ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
    String dataType = in.readUTF();
    Function<BayMcAuth, Endpoint> typeFunc = TYPES.get(dataType);
    if (typeFunc == null) {
      endpoint = new UnknownEndpoint(this.plugin, dataType);
    } else {
      endpoint = typeFunc.apply(this.plugin);
      endpoint.read(in);
    }

    // 不要使用 ServerConnection::sendPluginMessage, 延迟响应容易导致异常
    // 1) 玩家可能已退出服务器, 此时调用 ServerConnection::sendPluginMessage 会抛出异常
    // 2) 无法可靠判断 ServerConnection 是否仍未关闭
    MinecraftConnection connection = server.getConnection();
    if (connection != null && !connection.isClosed()) {
      ByteArrayDataOutput output = ByteStreams.newDataOutput();
      endpoint.write(output);
      connection.write(new PluginMessagePacket(API_CHANNEL.getId(), Unpooled.wrappedBuffer(output.toByteArray())));
    }
  }
}
