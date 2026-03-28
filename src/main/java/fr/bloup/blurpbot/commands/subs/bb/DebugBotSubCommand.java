package fr.bloup.blurpbot.commands.subs.bb;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.commands.PermissionedCommand;
import fr.bloup.blurpbot.core.BotPlayer;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class DebugBotSubCommand implements TabExecutor, PermissionedCommand {
    private final BlurpBot plugin;

    @Override
    public String getPermission() {
        return "bb.admin";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMsgPrefix() + "This command is player-only.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getMsgPrefix() + "Usage: /sb debug <uuid> <on|off>");
            return true;
        }

        UUID id;
        try {
            id = UUID.fromString(args[0]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(plugin.getMsgPrefix() + "Invalid UUID.");
            return true;
        }

        boolean enable;
        if ("on".equalsIgnoreCase(args[1])) enable = true;
        else if ("off".equalsIgnoreCase(args[1])) enable = false;
        else {
            sender.sendMessage(plugin.getMsgPrefix() + "Second argument must be on or off.");
            return true;
        }

        boolean ok = plugin.getBotManager().setDebug(id, enable, player.getUniqueId());
        if (!ok) {
            sender.sendMessage(plugin.getMsgPrefix() + "Bot not found.");
            return true;
        }

        sender.sendMessage(plugin.getMsgPrefix() + "Debug " + (enable ? "enabled" : "disabled") + " for bot " + id);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return plugin.getBotManager().getBots().stream()
                    .map(BotPlayer::getId)
                    .map(UUID::toString)
                    .filter(id -> id.startsWith(args[0]))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("on", "off").stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}

