package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.handler.AuthSessionHandler;
import com.velocitypowered.api.command.CommandSource;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class ForceLoginCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;

  private final String successful;
  private final String unknownPlayer;
  private final Component usage;

  public ForceLoginCommand(BayMcAuth plugin) {
    this.plugin = plugin;

    this.successful = Settings.IMP.MAIN.STRINGS.FORCE_LOGIN_SUCCESSFUL;
    this.unknownPlayer = Settings.IMP.MAIN.STRINGS.FORCE_LOGIN_UNKNOWN_PLAYER;
    this.usage = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.FORCE_LOGIN_USAGE);
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    if (args.length == 1) {
      String nickname = args[0];

      AuthSessionHandler handler = this.plugin.getAuthenticatingPlayer(nickname);
      if (handler == null) {
        source.sendMessage(BayMcAuth.getSerializer().deserialize(this.unknownPlayer, Placeholder.unparsed("player", nickname)));
        return;
      }

      handler.finishLogin();
      source.sendMessage(BayMcAuth.getSerializer().deserialize(this.successful, Placeholder.unparsed("player", nickname)));
    } else {
      source.sendMessage(this.usage);
    }
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.FORCE_LOGIN
        .hasPermission(invocation.source(), "baymcauth.admin.forcelogin");
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    if (invocation.arguments().length > 1) {
      return super.suggest(invocation);
    }

    String nickname = invocation.arguments().length == 0 ? "" : invocation.arguments()[0];
    List<String> suggest = new ArrayList<>();
    for (String username : this.plugin.getAuthenticatingPlayers().keySet()) {
      if (username.startsWith(nickname)) {
        suggest.add(username);
      }
    }

    return suggest;
  }
}
