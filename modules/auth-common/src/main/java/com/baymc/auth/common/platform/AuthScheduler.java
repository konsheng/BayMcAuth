package com.baymc.auth.common.platform;

import java.util.concurrent.CompletableFuture;

/*
 * 平台调度接口
 *
 * <p>屏蔽 Paper 和 Folia 调度差异, 供业务按玩家或全局执行任务
 */
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
