package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.event.ChangePasswordEvent;
import com.baymc.auth.handler.AuthSessionHandler;
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
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ForceChangePasswordCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;
  private final ProxyServer server;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final String message;
  private final String successful;
  private final String notSuccessful;
  private final String notRegistered;
  private final Component usage;

  public ForceChangePasswordCommand(BayMcAuth plugin, ProxyServer server, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.server = server;
    this.playerDao = playerDao;

    this.message = Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_MESSAGE;
    this.successful = Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_SUCCESSFUL;
    this.notSuccessful = Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_SUCCESSFUL;
    this.notRegistered = Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_NOT_REGISTERED;
    this.usage = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.FORCE_CHANGE_PASSWORD_USAGE);
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    return SuggestUtils.suggestPlayers(this.server, invocation.arguments(), 0);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (args.length == 2) {
      String nickname = args[0];
      String nicknameLowercased = args[0].toLowerCase(Locale.ROOT);
      String newPassword = args[1];

      try {
        RegisteredPlayer registeredPlayer = AuthSessionHandler.fetchInfoLowercased(this.playerDao, nicknameLowercased);

        if (registeredPlayer == null) {
          source.sendMessage(BayMcAuth.getSerializer().deserialize(
              this.notRegistered, Placeholder.unparsed("player", nickname)));
          return;
        }

        final String oldHash = registeredPlayer.getHash();
        final String newHash = RegisteredPlayer.genHash(newPassword);

        if (!this.plugin.updatePassword(this.playerDao, registeredPlayer, newHash)) {
          source.sendMessage(BayMcAuth.getSerializer().deserialize(
              this.notRegistered, Placeholder.unparsed("player", nickname)));
          return;
        }

        this.server.getPlayer(nickname)
            .ifPresent(player -> player.sendMessage(BayMcAuth.getSerializer().deserialize(
                this.message, Placeholder.unparsed("password", newPassword))));

        this.plugin.getServer().getEventManager().fireAndForget(new ChangePasswordEvent(registeredPlayer, null, oldHash, newPassword, newHash));

        source.sendMessage(BayMcAuth.getSerializer().deserialize(this.successful, Placeholder.unparsed("player", nickname)));
      } catch (SQLException e) {
        source.sendMessage(BayMcAuth.getSerializer().deserialize(this.notSuccessful, Placeholder.unparsed("player", nickname)));
        throw new SQLRuntimeException(e);
      }
    } else {
      source.sendMessage(this.usage);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.FORCE_CHANGE_PASSWORD
        .hasPermission(invocation.source(), "baymcauth.admin.forcechangepassword");
  }
}
