package com.baymc.auth.event;

import com.baymc.auth.model.RegisteredPlayer;
import com.velocitypowered.api.proxy.Player;
import java.util.function.Consumer;

public class PreAuthorizationEvent extends PreEvent {

  private final RegisteredPlayer playerInfo;

  public PreAuthorizationEvent(Consumer<TaskEvent> onComplete, Result result, Player player, RegisteredPlayer playerInfo) {
    super(onComplete, result, player);

    this.playerInfo = playerInfo;
  }

  public RegisteredPlayer getPlayerInfo() {
    return this.playerInfo;
  }
}
