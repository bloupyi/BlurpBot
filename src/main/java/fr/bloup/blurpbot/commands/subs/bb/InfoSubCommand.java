package fr.bloup.blurpbot.commands.subs.bb;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.bloup.blurpbot.BlurpBot;

import java.util.List;

@RequiredArgsConstructor
public class InfoSubCommand implements TabExecutor {
    private final BlurpBot plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        Player player = (Player) commandSender;

        player.sendMessage(" ");
        player.sendMessage(plugin.getMsgPrefix() + "Version: §6" + plugin.getDescription().getVersion());
        player.sendMessage(plugin.getMsgPrefix() + "Created by §6bloup§f/§6bloupyi");
        player.sendMessage(" ");

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String @NotNull [] strings) {
        return List.of();
    }
}
