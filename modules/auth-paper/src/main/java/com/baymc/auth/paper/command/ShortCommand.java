package com.baymc.auth.paper.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public final class ShortCommand implements CommandExecutor {
    private final BayMcAuthCommand main;
    private final String subcommand;

    public ShortCommand(BayMcAuthCommand main, String subcommand) {
        this.main = main;
        this.subcommand = subcommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("2fa".equals(subcommand) && args.length > 0) {
            return main.execute(sender, subcommand, args);
        }
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = subcommand;
        System.arraycopy(args, 0, forwarded, 1, args.length);
        return main.onCommand(sender, command, label, forwarded);
    }
}
