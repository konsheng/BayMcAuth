package com.baymc.auth.command;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import java.util.function.BiFunction;

public enum CommandPermissionState {
  FALSE((source, permission) -> false),
  TRUE((source, permission) -> source.getPermissionValue(permission) != Tristate.FALSE),
  PERMISSION(PermissionSubject::hasPermission);

  private final BiFunction<PermissionSubject, String, Boolean> hasPermissionFunction;

  CommandPermissionState(BiFunction<PermissionSubject, String, Boolean> hasPermissionFunction) {
    this.hasPermissionFunction = hasPermissionFunction;
  }

  public boolean hasPermission(PermissionSubject permissionSubject, String permission) {
    return this.hasPermissionFunction.apply(permissionSubject, permission);
  }
}
