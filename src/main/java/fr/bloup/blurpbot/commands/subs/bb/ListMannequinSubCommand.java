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

@RequiredArgsConstructor
public class ListMannequinSubCommand implements TabExecutor, PermissionedCommand {
    private final BlurpBot plugin;

    @Override
    public String getPermission() {
        return "bb.command";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        var bots = plugin.getBotManager().getBots();
        sender.sendMessage(plugin.getMsgPrefix() + "Bots: " + bots.size());
        for (BotPlayer bot : bots) {
            sender.sendMessage(" - " + bot.getId());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}

