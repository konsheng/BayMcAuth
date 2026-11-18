package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class DestroySessionCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;

  private final Component successful;
  private final Component notPlayer;

  public DestroySessionCommand(BayMcAuth plugin) {
    this.plugin = plugin;

    MiniMessage serializer = BayMcAuth.getSerializer();
    this.successful = serializer.deserialize(Settings.IMP.MAIN.STRINGS.DESTROY_SESSION_SUCCESSFUL);
    this.notPlayer = serializer.deserialize(Settings.IMP.MAIN.STRINGS.NOT_PLAYER);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (source instanceof Player) {
      this.plugin.removePlayerFromCache(((Player) source).getUsername());
      source.sendMessage(this.successful);
    } else {
      source.sendMessage(this.notPlayer);
    }
  }

  @Override
  public boolean hasPermission(SimpleCommand.Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.DESTROY_SESSION
        .hasPermission(invocation.source(), "baymcauth.commands.destroysession");
  }
}
