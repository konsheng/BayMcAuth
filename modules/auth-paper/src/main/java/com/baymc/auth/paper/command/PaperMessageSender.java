package com.baymc.auth.paper.command;

import com.baymc.auth.common.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PaperMessageSender {
    private volatile Messages messages;

    public PaperMessageSender(Messages messages) {
        this.messages = messages;
    }

    public void setMessages(Messages messages) {
        this.messages = messages;
    }

    public Messages messages() {
        return messages;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        for (Component component : messages.components(key, placeholders)) {
            sender.sendMessage(component);
        }
    }

    public void actionbar(Player player, String key, Map<String, String> placeholders) {
        player.sendActionBar(messages.component(key, placeholders));
    }
}
