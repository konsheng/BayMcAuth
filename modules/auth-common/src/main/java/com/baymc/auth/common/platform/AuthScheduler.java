package com.baymc.auth.common.platform;

import java.util.concurrent.CompletableFuture;

public interface AuthScheduler<P> {
    void runGlobal(Runnable task);

    void runForPlayer(P player, Runnable task);

    void runAsync(Runnable task);

    default <T> void afterAsync(P player, CompletableFuture<T> future, java.util.function.Consumer<T> success, java.util.function.Consumer<Throwable> failure) {
        future.whenComplete((value, throwable) -> runForPlayer(player, () -> {
            if (throwable == null) {
                success.accept(value);
            } else {
                failure.accept(throwable);
            }
        }));
    }
}
