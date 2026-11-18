package com.baymc.auth.event;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public abstract class TaskEvent {

  private static Component DEFAULT_REASON;

  private final Consumer<TaskEvent> onComplete;

  private Result result = Result.NORMAL;
  private Component reason = DEFAULT_REASON;

  public TaskEvent(Consumer<TaskEvent> onComplete) {
    this.onComplete = onComplete;
  }

  public TaskEvent(Consumer<TaskEvent> onComplete, Result result) {
    this.onComplete = onComplete;
    this.result = result;
  }

  public void complete(@NotNull Result result) {
    if (this.result != Result.WAIT) {
      return;
    }

    this.result = result;
    this.onComplete.accept(this);
  }

  public void completeAndCancel(@NotNull Component reason) {
    if (this.result != Result.WAIT) {
      return;
    }

    this.cancel(reason);
    this.onComplete.accept(this);
  }

  public void cancel(@NotNull Component reason) {
    this.result = Result.CANCEL;
    this.reason = reason;
  }

  public void setResult(@NotNull Result result) {
    this.result = result;
  }

  public Result getResult() {
    return this.result;
  }

  public Component getReason() {
    return this.reason;
  }

  public static void reload() {
    DEFAULT_REASON = BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.EVENT_CANCELLED);
  }

  public enum Result {

    CANCEL,
    BYPASS,
    NORMAL,
    WAIT
  }
}
