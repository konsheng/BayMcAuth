package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.event.AuthUnregisterEvent;
import com.baymc.auth.model.RegisteredPlayer;
import com.baymc.auth.model.SQLRuntimeException;
import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import net.elytrium.commons.velocity.commands.SuggestUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ForceUnregisterCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;
  private final ProxyServer server;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final Component kick;
  private final String successful;
  private final String notSuccessful;
  private final Component usage;

  public ForceUnregisterCommand(BayMcAuth plugin, ProxyServer server, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.server = server;
    this.playerDao = playerDao;

    MiniMessage serializer = BayMcAuth.getSerializer();
    this.kick = serializer.deserialize(Settings.IMP.MAIN.STRINGS.FORCE_UNREGISTER_KICK);
    this.successful = Settings.IMP.MAIN.STRINGS.FORCE_UNREGISTER_SUCCESSFUL;
    this.notSuccessful = Settings.IMP.MAIN.STRINGS.FORCE_UNREGISTER_NOT_SUCCESSFUL;
    this.usage = serializer.deserialize(Settings.IMP.MAIN.STRINGS.FORCE_UNREGISTER_USAGE);
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    return SuggestUtils.suggestPlayers(this.server, invocation.arguments(), 0);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (args.length == 1) {
      String playerNick = args[0];
      String usernameLowercased = playerNick.toLowerCase(Locale.ROOT);

      try {
        this.plugin.getServer().getEventManager().fireAndForget(new AuthUnregisterEvent(playerNick));
        this.playerDao.deleteById(usernameLowercased);
        this.plugin.removePlayerFromCacheLowercased(usernameLowercased);
        this.server.getPlayer(playerNick).ifPresent(player -> player.disconnect(this.kick));
        source.sendMessage(BayMcAuth.getSerializer().deserialize(this.successful, Placeholder.unparsed("player", playerNick)));
      } catch (SQLException e) {
        source.sendMessage(BayMcAuth.getSerializer().deserialize(this.notSuccessful, Placeholder.unparsed("player", playerNick)));
        throw new SQLRuntimeException(e);
      }
    } else {
      source.sendMessage(this.usage);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.FORCE_UNREGISTER
        .hasPermission(invocation.source(), "baymcauth.admin.forceunregister");
  }
}
