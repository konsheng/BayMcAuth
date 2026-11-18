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
import com.velocitypowered.api.proxy.Player;
import java.sql.SQLException;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ChangePasswordCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;
  private final Dao<RegisteredPlayer, String> playerDao;

  private final boolean needOldPass;
  private final Component notRegistered;
  private final Component wrongPassword;
  private final Component unsafePassword;
  private final Component successful;
  private final Component errorOccurred;
  private final Component usage;
  private final Component notPlayer;

  public ChangePasswordCommand(BayMcAuth plugin, Dao<RegisteredPlayer, String> playerDao) {
    this.plugin = plugin;
    this.playerDao = playerDao;

    MiniMessage serializer = BayMcAuth.getSerializer();
    this.needOldPass = Settings.IMP.MAIN.CHANGE_PASSWORD_NEED_OLD_PASSWORD;
    this.notRegistered = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_REGISTERED);
    this.wrongPassword = serializer.deserialize(Settings.IMP.MAIN.STRINGS.WRONG_PASSWORD);
    this.unsafePassword = serializer.deserialize(Settings.IMP.MAIN.STRINGS.REGISTER_PASSWORD_UNSAFE);
    this.successful = serializer.deserialize(Settings.IMP.MAIN.STRINGS.CHANGE_PASSWORD_SUCCESSFUL);
    this.errorOccurred = serializer.deserialize(Settings.IMP.MAIN.STRINGS.ERROR_OCCURRED);
    this.usage = serializer.deserialize(Settings.IMP.MAIN.STRINGS.CHANGE_PASSWORD_USAGE);
    this.notPlayer = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PLAYER);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (source instanceof Player) {
      String usernameLowercase = ((Player) source).getUsername().toLowerCase(Locale.ROOT);
      RegisteredPlayer player = AuthSessionHandler.fetchInfoLowercased(this.playerDao, usernameLowercase);

      if (player == null) {
        source.sendMessage(this.notRegistered);
        return;
      }

      boolean onlineMode = player.getHash().isEmpty();
      boolean needOldPass = this.needOldPass && !onlineMode;
      if (needOldPass) {
        if (args.length < 2) {
          source.sendMessage(this.usage);
          return;
        }

        if (!AuthSessionHandler.checkPassword(args[0], player)) {
          source.sendMessage(this.wrongPassword);
          return;
        }
      } else if (args.length < 1) {
        source.sendMessage(this.usage);
        return;
      }

      final String newPassword = needOldPass ? args[1] : args[0];
      if (Settings.IMP.MAIN.CHECK_PASSWORD_STRENGTH && this.plugin.getUnsafePasswords().contains(newPassword)) {
        source.sendMessage(this.unsafePassword);
        return;
      }

      try {
        final String oldHash = player.getHash();
        final String newHash = RegisteredPlayer.genHash(newPassword);

        if (!this.plugin.updatePassword(this.playerDao, player, newHash)) {
          source.sendMessage(this.notRegistered);
          return;
        }

        this.plugin.getServer().getEventManager().fireAndForget(
            new ChangePasswordEvent(player, needOldPass ? args[0] : null, oldHash, newPassword, newHash));

        source.sendMessage(this.successful);
      } catch (SQLException e) {
        source.sendMessage(this.errorOccurred);
        throw new SQLRuntimeException(e);
      }
    } else {
      source.sendMessage(this.notPlayer);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.CHANGE_PASSWORD
        .hasPermission(invocation.source(), "baymcauth.commands.changepassword");
  }
}
