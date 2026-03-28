package fr.bloup.blurpbot.commands;

import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import fr.bloup.blurpbot.BlurpBot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractCommand implements TabExecutor {

    private final Map<String, TabExecutor> subCommands = new HashMap<>();
    protected final BlurpBot plugin;

    public abstract String getPermission();

    public abstract boolean runCommand(CommandSender sender, Command rootCommand, String label, String[] args);

    public void registerSubCommand(String label, TabExecutor subCommand) {
        subCommands.put(label.toLowerCase(), subCommand);
    }

    protected String getUsage(String label) {
        return ChatColor.RED + "Usage: /" + label + " < " + String.join(" | ", this.subCommands.keySet()) + " >";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(this.getPermission()) && !sender.isOp()) {
            sender.sendMessage("Don't have permission to execute this command.");
            return true;
        }

        if (args.length > 0) {
            TabExecutor child = subCommands.get(args[0].toLowerCase());
            if (child != null) {
                if (child instanceof PermissionedCommand permCmd) {
                    if (!sender.hasPermission(permCmd.getPermission()) && !sender.isOp()) {
                        sender.sendMessage("Don't have permission to execute this command.");
                        return true;
                    }
                }
                return child.onCommand(sender, command, args[0], removeHead(args));
            }
        }
        return runCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            TabExecutor child = subCommands.get(args[0].toLowerCase());
            if (child != null) {
                return child.onTabComplete(sender, command, args[0], removeHead(args));
            }
            return subCommands.entrySet().stream()
                    .filter(entry -> {
                        TabExecutor exec = entry.getValue();
                        if (exec instanceof PermissionedCommand permCmd) {
                            return sender.hasPermission(permCmd.getPermission()) || sender.isOp();
                        }
                        return true;
                    })
                    .map(Map.Entry::getKey)
                    .filter(cmd -> cmd.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        return subCommands.entrySet().stream()
                .filter(entry -> {
                    TabExecutor exec = entry.getValue();
                    if (exec instanceof PermissionedCommand permCmd) {
                        return sender.hasPermission(permCmd.getPermission()) || sender.isOp();
                    }
                    return true;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String[] removeHead(String[] list) {
        String[] newList = new String[list.length - 1];
        System.arraycopy(list, 1, newList, 0, newList.length);
        return newList;
    }
}