package fr.bloup.blurpbot;

import fr.bloup.blurpapi.BlurpAPI;
import fr.bloup.blurpbot.api.BotManager;
import fr.bloup.blurpbot.commands.BBCommand;
import fr.bloup.blurpbot.core.BotPlayer;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.listeners.BotJoinMessageListener;
import fr.bloup.blurpbot.listeners.BotKnockbackListener;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class BlurpBot extends JavaPlugin {
    @Getter private final Logger LOGGER = getLogger();
    @Getter private final String msgPrefix = ChatColor.GRAY + "[" + ChatColor.DARK_PURPLE + "BlurpBot" + ChatColor.GRAY + "] " + ChatColor.RESET;

    @Getter private final BotManager botManager = new BotManager();
    private BotSettings defaultBotSettings;
    private BukkitTask botTickTask;
    private BukkitTask botDebugTask;

    private final List<Listener> listeners = new ArrayList<>();

    @Override
    public void onEnable() {
        LOGGER.info("Enabled "+ this.getDescription().getName() + " " +  this.getDescription().getVersion());
        BlurpAPI.init(this);
        saveDefaultConfig();
        reloadConfig();
        loadDefaultBotSettings();
        botManager.setPlugin(this);
        saveResource("trees/default.bbot", false);
        saveResource("trees/aggressive.bbot", false);

        listeners.add(new BotKnockbackListener(botManager));
        listeners.add(new BotJoinMessageListener());
        
        this.registerCommands();
        this.registerListeners();

        botTickTask = Bukkit.getScheduler().runTaskTimer(this, botManager::tickAll, 1L, 1L);
        botDebugTask = Bukkit.getScheduler().runTaskTimer(this, botManager::pushDebugInfo, 1L, 2L);
    }

    @Override
    public void onDisable() {
        if (botTickTask != null) {
            botTickTask.cancel();
            botTickTask = null;
        }
        if (botDebugTask != null) {
            botDebugTask.cancel();
            botDebugTask = null;
        }
        botManager.clearAll();
    }

    private void registerCommands(){
        this.getCommand("bb").setExecutor(new BBCommand(this));
    }

    private void registerListeners(){
        this.listeners.forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, this));
    }

    public BotSettings getDefaultBotSettingsCopy() {
        return defaultBotSettings.copy();
    }

    public BotSettings getProfileBotSettingsCopy(String profileName) {
        BotSettings settings = getDefaultBotSettingsCopy();
        if (profileName == null || profileName.isBlank()) {
            return settings;
        }
        mergeBotSectionIntoSettings(getConfig().getConfigurationSection("bot.profiles." + profileName), settings);
        return settings;
    }

    /**
     * Recharge config.yml, le cache des défauts, et chaque bot (profil + fichier .bbot).
     */
    public int reloadBotRuntimeState() {
        reloadConfig();
        loadDefaultBotSettings();
        int n = 0;
        for (BotPlayer bot : botManager.getBots()) {
            bot.reloadFromConfig(this);
            n++;
        }
        return n;
    }

    private static void mergeBotSectionIntoSettings(ConfigurationSection section, BotSettings target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (!section.isSet(key)) {
                continue;
            }
            Object raw = section.get(key);
            if (raw == null) {
                continue;
            }
            String value = raw instanceof Boolean b ? (b ? "true" : "false") : String.valueOf(raw);
            target.apply(key, value);
        }
    }

    public boolean hasProfile(String profileName) {
        return profileName != null && getConfig().isConfigurationSection("bot.profiles." + profileName);
    }

    public List<String> getProfileNames() {
        ConfigurationSection section = getConfig().getConfigurationSection("bot.profiles");
        if (section == null) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    private void loadDefaultBotSettings() {
        BotSettings s = new BotSettings();
        mergeBotSectionIntoSettings(getConfig().getConfigurationSection("bot.defaults"), s);
        this.defaultBotSettings = s;
    }
}
