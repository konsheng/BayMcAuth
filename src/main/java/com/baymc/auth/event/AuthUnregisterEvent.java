package com.baymc.auth.event;

public class AuthUnregisterEvent {

  private final String nickname;

  public AuthUnregisterEvent(String nickname) {
    this.nickname = nickname;
  }

  public String getNickname() {
    return this.nickname;
  }
}
