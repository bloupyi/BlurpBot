package fr.bloup.blurpbot.commands.subs.bb;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.commands.PermissionedCommand;
import fr.bloup.blurpbot.core.BotPlayer;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class RemoveMannequinSubCommand implements TabExecutor, PermissionedCommand {
    private final BlurpBot plugin;

    @Override
    public String getPermission() {
        return "bb.admin";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMsgPrefix() + "Usage: /sb remove <uuid>");
            return true;
        }

        UUID id;
        try {
            id = UUID.fromString(args[0]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(plugin.getMsgPrefix() + "Invalid UUID.");
            return true;
        }

        boolean removed = plugin.getBotManager().removeBot(id);
        sender.sendMessage(plugin.getMsgPrefix() + (removed ? "Bot removed." : "Bot not found."));
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
        return List.of();
    }
}

