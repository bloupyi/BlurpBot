package fr.bloup.blurpbot.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import fr.bloup.blurpbot.core.BotPlayer;
import fr.bloup.blurpbot.core.BotSettingKey;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.debug.BotDebugFrame;
import fr.bloup.blurpbot.goal.GoalTargetKind;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotManager {
    private final Map<UUID, BotPlayer> bots = new ConcurrentHashMap<>();
    private JavaPlugin plugin;

    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void tickAll() {
        bots.values().forEach(Bot::tick);
    }

    public Collection<BotPlayer> getBots() {
        return bots.values();
    }

    public BotPlayer createBasicBot(Location spawnLocation) {
        return createBasicBot(spawnLocation, new BotSettings());
    }

    public BotPlayer createBasicBot(Location spawnLocation, BotSettings settings) {
        return createBasicBot(spawnLocation, settings, null);
    }

    public BotPlayer createBasicBot(Location spawnLocation, BotSettings settings, String settingsProfile) {
        BotPlayer bot = BotPlayer.spawnBasic(spawnLocation, settings, plugin, settingsProfile);
        bots.put(bot.getId(), bot);
        return bot;
    }

    public BotPlayer getBot(UUID id) {
        return bots.get(id);
    }

    public void applyKnockback(UUID botEntityId, Vector impulse) {
        BotPlayer bot = bots.get(botEntityId);
        if (bot == null) return;
        bot.applyImpulse(impulse);
    }

    public boolean setDebug(UUID id, boolean enabled, UUID viewerId) {
        BotPlayer bot = bots.get(id);
        if (bot == null) return false;
        bot.setDebugEnabled(enabled, viewerId);
        return true;
    }

    public void pushDebugInfo() {
        for (BotPlayer bot : bots.values()) {
            if (!bot.isDebugEnabled()) continue;
            UUID viewerId = bot.getDebugViewerId();
            if (viewerId == null) continue;
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) continue;

            BotDebugFrame frame = bot.getBrain().getLastDebugFrame();
            String msg = String.format(
                    "SB Debug | action=%s(%d) goal=%s(%d) path=%d@%d stuck=%d",
                    frame.getSelectedAction(),
                    frame.getSelectedActionPriority(),
                    frame.getActiveGoal(),
                    frame.getActiveGoalPriority(),
                    frame.getPathSize(),
                    frame.getPathIndex(),
                    frame.getStuckTicks()
            );
            viewer.sendActionBar(Component.text(msg));

            // Draw remaining path ahead of the bot (yellow), not only the first nodes (avoids huge visual gaps).
            List<Location> pts = frame.getPathPoints();
            int start = Math.max(0, frame.getPathIndex() - 1);
            int remaining = Math.max(0, pts.size() - start);
            int maxPoints = Math.min(remaining, 48);
            for (int j = 0; j < maxPoints; j++) {
                Location p = pts.get(start + j);
                if (p.getWorld() == null || !p.getWorld().equals(viewer.getWorld())) continue;
                viewer.spawnParticle(Particle.END_ROD, p.clone().add(0, 0.1, 0), 1, 0, 0, 0, 0);
            }
            if (frame.getWaypoint() != null && frame.getWaypoint().getWorld() != null && frame.getWaypoint().getWorld().equals(viewer.getWorld())) {
                viewer.spawnParticle(Particle.HAPPY_VILLAGER, frame.getWaypoint().clone().add(0, 0.35, 0), 4, 0.08, 0.02, 0.08, 0);
            }
            if (frame.getUnstuckTarget() != null && frame.getUnstuckTarget().getWorld() != null && frame.getUnstuckTarget().getWorld().equals(viewer.getWorld())) {
                viewer.spawnParticle(Particle.FLAME, frame.getUnstuckTarget().clone().add(0, 0.35, 0), 3, 0.08, 0.02, 0.08, 0);
            }
        }
    }

    public boolean removeBot(UUID id) {
        BotPlayer bot = bots.remove(id);
        if (bot == null) return false;
        LivingEntity entity = bot.getEntity();
        if (entity != null) {
            if (entity instanceof Player playerEntity) {
                removeFakePlayerFromServer(playerEntity);
            }
            if (entity.isValid()) {
                entity.remove();
            }
        }
        return true;
    }

    public boolean clearManualGoal(UUID id) {
        BotPlayer bot = bots.get(id);
        if (bot == null) return false;
        bot.clearManualGoal();
        return true;
    }

    public boolean setManualGoal(UUID id, GoalTargetKind targetKind, int priority) {
        BotPlayer bot = bots.get(id);
        if (bot == null) return false;
        bot.setManualGoal(targetKind, priority);
        return true;
    }

    public boolean setManualGoalLocation(UUID id, Location location, int priority) {
        BotPlayer bot = bots.get(id);
        if (bot == null) return false;
        bot.setManualGoalLocation(location, priority);
        return true;
    }

    public boolean setSetting(UUID id, BotSettingKey key, String value) {
        BotPlayer bot = bots.get(id);
        if (bot == null) return false;
        boolean ok = bot.setSetting(key, value);
        if (ok && key == BotSettingKey.BEHAVIOR_TREE) {
            bot.reloadBehaviorTree();
        }
        return ok;
    }

    public boolean setSetting(UUID id, String key, String value) {
        BotSettingKey parsed = BotSettingKey.fromKey(key);
        if (parsed == null) {
            return false;
        }
        return setSetting(id, parsed, value);
    }

    public boolean applySettings(UUID id, BotSettings settings, boolean reloadBehaviorTree) {
        BotPlayer bot = bots.get(id);
        if (bot == null || settings == null) return false;
        bot.applySettings(settings);
        if (reloadBehaviorTree) {
            bot.reloadBehaviorTree();
        }
        return true;
    }

    /**
     * Updates skin-related settings. For NMS fake players, visual skin refresh generally needs respawn.
     */
    public boolean setSkin(UUID id, String profileName, String texture, String signature) {
        BotPlayer bot = bots.get(id);
        if (bot == null) return false;
        bot.setSkin(profileName, texture, signature);
        return true;
    }

    private void removeFakePlayerFromServer(Player playerEntity) {
        try {
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object playerList = minecraftServer.getClass().getMethod("getPlayerList").invoke(minecraftServer);
            Object handle = playerEntity.getClass().getMethod("getHandle").invoke(playerEntity);

            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            playerList.getClass().getMethod("remove", serverPlayerClass).invoke(playerList, handle);
        } catch (Throwable ignored) {
            // Best effort cleanup. We still call entity.remove() afterwards.
        }
    }

    public void clearAll() {
        for (UUID id : List.copyOf(bots.keySet())) {
            removeBot(id);
        }
    }
}
