package com.baymc.auth.paper.listener;

import com.baymc.auth.common.model.AuthStep;
import com.baymc.auth.paper.command.PaperMessageSender;
import com.baymc.auth.paper.protection.LoginStateService;
import com.baymc.auth.paper.scheduler.PaperScheduler;
import com.baymc.auth.paper.service.AuthService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class PlayerConnectionListener implements Listener {
    private final AuthService authService;
    private final LoginStateService loginStates;
    private final PaperMessageSender messages;
    private final PaperScheduler scheduler;
    private final Duration timeout;

    public PlayerConnectionListener(AuthService authService, LoginStateService loginStates, PaperMessageSender messages, PaperScheduler scheduler, Duration timeout) {
        this.authService = authService;
        this.loginStates = loginStates;
        this.messages = messages;
        this.scheduler = scheduler;
        this.timeout = timeout;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.runForPlayer(player, () -> {
            authService.handleJoin(player);
            prompt(player);
            scheduler.runLater(player, () -> timeout(player), Math.max(1L, timeout.toSeconds() * 20L));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authService.remove(event.getPlayer());
    }

    private void prompt(Player player) {
        loginStates.state(player.getUniqueId()).ifPresent(state -> {
            if (state.step() == AuthStep.REGISTER_REQUIRED) {
                messages.send(player, "register.required");
            } else if (state.step() == AuthStep.LOGIN_REQUIRED || state.step() == AuthStep.TOTP_REQUIRED) {
                messages.send(player, "login-prompt.chat");
                messages.actionbar(player, "login-prompt.actionbar", Map.of("time", String.valueOf(timeout.toSeconds()) + "s"));
            }
        });
    }

    private void timeout(Player player) {
        loginStates.state(player.getUniqueId()).ifPresent(state -> {
            if (!state.authenticated() && state.deadline().isBefore(Instant.now().plusSeconds(1))) {
                player.kick(messages.messages().component("login.timeout-kick", Map.of()));
            }
        });
    }
}
