package com.baymc.auth.event;

import com.baymc.auth.model.RegisteredPlayer;
import java.util.function.Consumer;
import net.elytrium.limboapi.api.player.LimboPlayer;

public class PostRegisterEvent extends PostEvent {

  public PostRegisterEvent(Consumer<TaskEvent> onComplete, LimboPlayer player, RegisteredPlayer playerInfo, String password) {
    super(onComplete, player, playerInfo, password);
  }
}
