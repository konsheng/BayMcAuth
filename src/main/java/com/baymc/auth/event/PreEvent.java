package com.baymc.auth.event;

import com.velocitypowered.api.proxy.Player;
import java.util.function.Consumer;

public abstract class PreEvent extends TaskEvent {

  private final Player player;

  protected PreEvent(Consumer<TaskEvent> onComplete, Result result, Player player) {
    super(onComplete, result);

    this.player = player;
  }

  public Player getPlayer() {
    return this.player;
  }
}
