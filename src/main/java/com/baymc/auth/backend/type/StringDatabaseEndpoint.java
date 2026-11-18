package com.baymc.auth.backend.type;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.handler.AuthSessionHandler;
import com.baymc.auth.model.RegisteredPlayer;
import java.util.function.Function;

public class StringDatabaseEndpoint extends StringEndpoint {

  public StringDatabaseEndpoint(BayMcAuth plugin, String type, Function<RegisteredPlayer, String> function) {
    super(plugin, type, username -> {
      RegisteredPlayer player = AuthSessionHandler.fetchInfo(plugin.getPlayerDao(), username);
      if (player == null) {
        return "";
      } else {
        return function.apply(player);
      }
    });
  }
}
