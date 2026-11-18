package com.baymc.auth.event;

import com.velocitypowered.api.proxy.Player;
import java.util.function.Consumer;

public class PreRegisterEvent extends PreEvent {

  public PreRegisterEvent(Consumer<TaskEvent> onComplete, Result result, Player player) {
    super(onComplete, result, player);
  }
}
