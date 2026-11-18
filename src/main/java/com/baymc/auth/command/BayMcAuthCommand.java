package com.baymc.auth.command;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.Settings;
import com.baymc.auth.config.ConfigurationException;
import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public class BayMcAuthCommand extends RatelimitedCommand {

  private final BayMcAuth plugin;

  public BayMcAuthCommand(BayMcAuth plugin) {
    this.plugin = plugin;
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      String argument = args[0];
      return Arrays.stream(Subcommand.values())
          .filter(command -> command.hasPermission(source))
          .map(Subcommand::getCommand)
          .filter(str -> str.regionMatches(true, 0, argument, 0, argument.length()))
          .collect(Collectors.toList());
    } else {
      return ImmutableList.of();
    }
  }

  @Override
  public void execute(CommandSource source, String[] args) {
    int argsAmount = args.length;
    if (argsAmount > 0) {
      try {
        Subcommand subcommand = Subcommand.valueOf(args[0].toUpperCase(Locale.ROOT));
        if (!subcommand.hasPermission(source)) {
          this.showHelp(source);
          return;
        }

        subcommand.executor.execute(this, source, args);
      } catch (ConfigurationException e) {
        source.sendMessage(BayMcAuth.getSerializer().deserialize(
            Settings.IMP.MAIN.STRINGS.RELOAD_FAILED, Placeholder.unparsed("reason", e.getMessage())));
      } catch (IllegalArgumentException e) {
        this.showHelp(source);
      }
    } else {
      this.showHelp(source);
    }
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.HELP
        .hasPermission(invocation.source(), "baymcauth.commands.help");
  }

  private void showHelp(CommandSource source) {
    Settings.MAIN.STRINGS messages = Settings.IMP.MAIN.STRINGS;
    MiniMessage serializer = BayMcAuth.getSerializer();
    source.sendMessage(serializer.deserialize(messages.HELP_HEADER));
    source.sendMessage(serializer.deserialize(messages.HELP_COPYRIGHT));
    source.sendMessage(serializer.deserialize(messages.HELP_WEBSITE));
    source.sendMessage(Component.empty());

    List<Subcommand> availableSubcommands = Arrays.stream(Subcommand.values())
        .filter(command -> command.hasPermission(source))
        .collect(Collectors.toList());

    if (availableSubcommands.size() > 0) {
      source.sendMessage(serializer.deserialize(messages.HELP_AVAILABLE_SUBCOMMANDS));
      availableSubcommands.forEach(command -> source.sendMessage(command.getMessageLine()));
    } else {
      source.sendMessage(serializer.deserialize(messages.HELP_NO_AVAILABLE_SUBCOMMANDS));
    }
  }

  private enum Subcommand {
    RELOAD(() -> Settings.IMP.MAIN.STRINGS.HELP_RELOAD_DESCRIPTION, () -> Settings.IMP.MAIN.COMMAND_PERMISSION_STATE.RELOAD,
        (BayMcAuthCommand parent, CommandSource source, String[] args) -> {
          parent.plugin.reload();
          source.sendMessage(BayMcAuth.getSerializer().deserialize(Settings.IMP.MAIN.STRINGS.RELOAD));
        });

    private final String command;
    private final Supplier<String> description;
    private final Supplier<CommandPermissionState> permissionState;
    private final SubcommandExecutor executor;

    Subcommand(Supplier<String> description, Supplier<CommandPermissionState> permissionState, SubcommandExecutor executor) {
      this.permissionState = permissionState;
      this.command = this.name().toLowerCase(Locale.ROOT);
      this.description = description;
      this.executor = executor;
    }

    public boolean hasPermission(CommandSource source) {
      return this.permissionState.get().hasPermission(source, "baymcauth.admin." + this.command);
    }

    public Component getMessageLine() {
      return Component.textOfChildren(
          Component.text("  /baymcauth " + this.command, NamedTextColor.GREEN),
          Component.text(" - ", NamedTextColor.DARK_GRAY),
          BayMcAuth.getSerializer().deserialize(this.description.get())
      );
    }

    public String getCommand() {
      return this.command;
    }
  }

  private interface SubcommandExecutor {
    void execute(BayMcAuthCommand parent, CommandSource source, String[] args);
  }
}
