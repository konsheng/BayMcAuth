package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public abstract class RatelimitedCommand implements SimpleCommand {

  private final Component ratelimited;

  public RatelimitedCommand() {
    this.ratelimited = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.RATELIMITED);
  }

  @Override
  public final void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    if (source instanceof Player) {
      if (!BayMcAuth.RATELIMITER.attempt(((Player) source).getRemoteAddress().getAddress())) {
        source.sendMessage(this.ratelimited);
        return;
      }
    }

    this.execute(source, invocation.arguments());
  }

  protected abstract void execute(CommandSource source, String[] args);
}
