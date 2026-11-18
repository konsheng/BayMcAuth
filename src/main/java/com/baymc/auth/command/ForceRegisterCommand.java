package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.model.RegisteredPlayer;
import com.baymc.auth.model.SQLRuntimeException;
import com.j256.ormlite.dao.Dao;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.sql.SQLException;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ForceRegisterCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final String successful;
  private final String notSuccessful;
  private final Component usage;
  private final Component takenNickname;
  private final Component incorrectNickname;

  public ForceRegisterCommand(BayMcAuth plugin, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.playerDao = playerDao;

    this.successful = Settings.IMP.MAIN.STRINGS.FORCE_REGISTER_SUCCESSFUL;
    this.notSuccessful = Settings.IMP.MAIN.STRINGS.FORCE_REGISTER_NOT_SUCCESSFUL;
    this.usage = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.FORCE_REGISTER_USAGE);
    this.takenNickname = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.FORCE_REGISTER_TAKEN_NICKNAME);
    this.incorrectNickname = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.FORCE_REGISTER_INCORRECT_NICKNAME);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (args.length == 2) {
      String nickname = args[0];
      String password = args[1];

      try {
        if (!this.plugin.getNicknameValidationPattern().matcher(nickname).matches()) {
          source.sendMessage(this.incorrectNickname);
          return;
        }

        String lowercaseNickname = nickname.toLowerCase(Locale.ROOT);
        if (this.playerDao.idExists(lowercaseNickname)) {
          source.sendMessage(this.takenNickname);
          return;
        }

        RegisteredPlayer player = new RegisteredPlayer(nickname, "", "").setPassword(password);
        this.playerDao.create(player);

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
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.FORCE_REGISTER
        .hasPermission(invocation.source(), "baymcauth.admin.forceregister");
  }
}
