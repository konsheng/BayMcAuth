package com.baymc.auth.event;

import com.baymc.auth.model.RegisteredPlayer;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ChangePasswordEvent {

  private final RegisteredPlayer playerInfo;
  @Nullable
  private final String oldPassword;
  private final String oldHash;
  private final String newPassword;
  private final String newHash;

  public ChangePasswordEvent(RegisteredPlayer playerInfo, @Nullable String oldPassword,
                             String oldHash, String newPassword, String newHash) {
    this.playerInfo = playerInfo;
    this.oldPassword = oldPassword;
    this.oldHash = oldHash;
    this.newPassword = newPassword;
    this.newHash = newHash;
  }

  public RegisteredPlayer getPlayerInfo() {
    return this.playerInfo;
  }

  @Nullable
  public String getOldPassword() {
    return this.oldPassword;
  }

  public String getOldHash() {
    return this.oldHash;
  }

  public String getNewPassword() {
    return this.newPassword;
  }

  public String getNewHash() {
    return this.newHash;
  }
}
