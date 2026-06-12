package com.baymc.auth.paper.scheduler;

import com.baymc.auth.common.platform.AuthScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/*
 * Paper/Folia 调度适配
 *
 * <p>运行时优先使用 Folia Region Scheduler 和 Global Region Scheduler
 * 普通 Paper 环境回退到 Bukkit Scheduler
 */
public final class PaperScheduler implements AuthScheduler<Player> {
    private final Plugin plugin;
    private final boolean folia;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = hasMethod(Bukkit.class, "getGlobalRegionScheduler");
    }

    @Override
    public void runGlobal(Runnable task) {
        if (folia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class).invoke(scheduler, plugin, task);
                return;
            } catch (ReflectiveOperationException ignored) {
                // 反射失败时使用 Paper 调度器
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        if (folia) {
            try {
                Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
                scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class)
                    .invoke(scheduler, plugin, task, null, 1L);
                return;
            } catch (ReflectiveOperationException ignored) {
                // 反射失败时使用 Paper 调度器
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runLater(Player player, Runnable task, long ticks) {
        if (folia) {
            try {
                Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
                scheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class)
                    .invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), null, ticks);
                return;
            } catch (ReflectiveOperationException ignored) {
                // 反射失败时使用 Paper 调度器
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    private boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
