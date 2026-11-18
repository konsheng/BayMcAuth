package com.baymc.auth.event;

import com.baymc.auth.model.RegisteredPlayer;
import java.util.function.Consumer;
import net.elytrium.limboapi.api.player.LimboPlayer;

public abstract class PostEvent extends TaskEvent {

  private final LimboPlayer player;
  private final RegisteredPlayer playerInfo;
  private final String password;

  protected PostEvent(Consumer<TaskEvent> onComplete, LimboPlayer player, RegisteredPlayer playerInfo, String password) {
    super(onComplete);

    this.player = player;
    this.playerInfo = playerInfo;
    this.password = password;
  }

  protected PostEvent(Consumer<TaskEvent> onComplete, Result result, LimboPlayer player, RegisteredPlayer playerInfo, String password) {
    super(onComplete, result);

    this.player = player;
    this.playerInfo = playerInfo;
    this.password = password;
  }

  public LimboPlayer getPlayer() {
    return this.player;
  }

  public RegisteredPlayer getPlayerInfo() {
    return this.playerInfo;
  }

  public String getPassword() {
    return this.password;
  }
}
