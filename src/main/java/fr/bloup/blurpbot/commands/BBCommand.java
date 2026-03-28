package fr.bloup.blurpbot.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.commands.subs.bb.ConfigBotSubCommand;
import fr.bloup.blurpbot.commands.subs.bb.CreateMannequinSubCommand;
import fr.bloup.blurpbot.commands.subs.bb.DebugBotSubCommand;
import fr.bloup.blurpbot.commands.subs.bb.InfoSubCommand;
import fr.bloup.blurpbot.commands.subs.bb.ListMannequinSubCommand;
import fr.bloup.blurpbot.commands.subs.bb.ReloadSubCommand;
import fr.bloup.blurpbot.commands.subs.bb.RemoveMannequinSubCommand;

public class BBCommand extends AbstractCommand {
    public BBCommand(BlurpBot plugin) {
        super(plugin);
        this.registerSubCommand("info", new InfoSubCommand(plugin));
        this.registerSubCommand("create", new CreateMannequinSubCommand(plugin));
        this.registerSubCommand("remove", new RemoveMannequinSubCommand(plugin));
        this.registerSubCommand("list", new ListMannequinSubCommand(plugin));
        this.registerSubCommand("config", new ConfigBotSubCommand(plugin));
        this.registerSubCommand("debug", new DebugBotSubCommand(plugin));
        this.registerSubCommand("reload", new ReloadSubCommand(plugin));
    }

    @Override
    public String getPermission() {
        return "sb.command";
    }

    @Override
    public boolean runCommand(CommandSender sender, Command rootCommand, String label, String[] args) {
        sender.sendMessage(plugin.getMsgPrefix() + "Available commands: info, create, remove, list, config, debug, reload");
        return true;
    }
}
