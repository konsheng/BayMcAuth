package com.baymc.auth.floodgate;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;

/**
 * 可选 Floodgate 功能的持有类, Velocity 的结构要求通过持有类接入可选插件
 */
public class FloodgateApiHolder {

  private final FloodgateApi floodgateApi;

  public FloodgateApiHolder() {
    this.floodgateApi = FloodgateApi.getInstance();
  }

  public boolean isFloodgatePlayer(UUID uuid) {
    return this.floodgateApi.isFloodgatePlayer(uuid);
  }

  public int getPrefixLength() {
    return this.floodgateApi.getPlayerPrefix().length();
  }
}
