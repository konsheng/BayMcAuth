package com.baymc.auth.event;

import com.baymc.auth.model.RegisteredPlayer;
import java.util.function.Consumer;
import net.elytrium.limboapi.api.player.LimboPlayer;

public class PostAuthorizationEvent extends PostEvent {

  public PostAuthorizationEvent(Consumer<TaskEvent> onComplete, LimboPlayer player, RegisteredPlayer playerInfo, String password) {
    super(onComplete, player, playerInfo, password);
  }
}
