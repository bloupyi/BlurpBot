package fr.bloup.blurpbot.commands.subs.bb;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.commands.PermissionedCommand;

import java.util.List;

@RequiredArgsConstructor
public class ReloadSubCommand implements TabExecutor, PermissionedCommand {
    private final BlurpBot plugin;

    @Override
    public String getPermission() {
        return "bb.admin";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        int updated = plugin.reloadBotRuntimeState();
        sender.sendMessage(plugin.getMsgPrefix() + "Configuration et scripts rechargés (" + updated + " bot(s) mis à jour).");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
