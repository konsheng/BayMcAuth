package com.baymc.auth.paper.listener;

import com.baymc.auth.paper.command.PaperMessageSender;
import com.baymc.auth.paper.protection.LoginStateService;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

/*
 * 登录保护监听器
 *
 * <p>在玩家完成认证前限制移动, 交互和命令执行, 并允许配置中的白名单命令
 */
public final class LoginProtectionListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("/baymcauth", "/auth", "/login", "/register", "/2fa", "/resetpassword");
    private final LoginStateService loginStates;
    private final PaperMessageSender messages;

    public LoginProtectionListener(LoginStateService loginStates, PaperMessageSender messages) {
        this.loginStates = loginStates;
        this.messages = messages;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!allowed(event.getPlayer()) && event.getFrom().distanceSquared(event.getTo()) > 0.01D) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!allowed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (allowed(event.getPlayer())) {
            return;
        }
        String lower = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        boolean white = ALLOWED_COMMANDS.stream().anyMatch(command -> lower.equals(command) || lower.startsWith(command + " "));
        if (!white) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "login.required");
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent event) { cancel(event.getPlayer(), event); }
    @EventHandler public void onPlace(BlockPlaceEvent event) { cancel(event.getPlayer(), event); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (!allowed(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onInteractEntity(PlayerInteractEntityEvent event) { if (!allowed(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (!allowed(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onPickup(PlayerPickupItemEvent event) { if (!allowed(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onPortal(PlayerPortalEvent event) { if (!allowed(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) { if (!allowed(event.getPlayer())) event.setCancelled(true); }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !allowed(player)) {
            event.setCancelled(true);
        }
    }

    private void cancel(Player player, org.bukkit.event.Cancellable event) {
        if (!allowed(player)) {
            event.setCancelled(true);
        }
    }

    private boolean allowed(Player player) {
        return loginStates.authenticated(player.getUniqueId());
    }
}
