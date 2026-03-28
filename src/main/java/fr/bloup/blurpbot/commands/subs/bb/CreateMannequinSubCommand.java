package fr.bloup.blurpbot.commands.subs.bb;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.api.BotManager;
import fr.bloup.blurpbot.commands.PermissionedCommand;
import fr.bloup.blurpbot.core.BotPlayer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CreateMannequinSubCommand implements TabExecutor, PermissionedCommand {
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

        BotManager manager = plugin.getBotManager();
        String profile = args.length > 0 ? args[0] : null;
        try {
            if (profile != null && !plugin.hasProfile(profile)) {
                sender.sendMessage(plugin.getMsgPrefix() + "Unknown profile '" + profile + "'.");
                return true;
            }
            BotPlayer bot = manager.createBasicBot(
                    player.getLocation(),
                    plugin.getProfileBotSettingsCopy(profile),
                    profile
            );
            sender.sendMessage(plugin.getMsgPrefix() + "Bot created: " + bot.getId());
            if (profile != null) {
                sender.sendMessage(plugin.getMsgPrefix() + "Profile applied: " + profile);
            }
            if (!(bot.getEntity() instanceof Player)) {
                sender.sendMessage(plugin.getMsgPrefix() + "Player-bot NMS path is disabled for stability on Paper 1.21, fallback mob used.");
            }
        } catch (Exception ex) {
            sender.sendMessage(plugin.getMsgPrefix() + "Bot spawn failed: " + ex.getClass().getSimpleName());
            plugin.getLOGGER().severe("Failed to create bot: " + ex.getMessage());
            ex.printStackTrace();
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return plugin.getProfileNames().stream().filter(p -> p.startsWith(args[0])).toList();
        }
        return List.of();
    }
}

