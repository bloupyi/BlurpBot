package fr.bloup.blurpbot.core;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.api.Bot;
import fr.bloup.blurpbot.bbot.BotBehaviorTreeLoader;
import fr.bloup.blurpbot.brain.BotBrain;
import fr.bloup.blurpbot.controller.BotController;
import fr.bloup.blurpbot.decision.BehaviorTree;
import fr.bloup.blurpbot.goal.GoalTargetKind;
import fr.bloup.blurpbot.nms.NmsFakePlayerFactory;

import java.util.UUID;
import java.util.logging.Level;

@Getter
public class BotPlayer implements Bot {
    private static final boolean ENABLE_EXPERIMENTAL_NMS_PLAYER = true;

    private final UUID id;
    private final LivingEntity entity;
    private final BotBrain brain;
    private final BotSettings settings;
    private final JavaPlugin plugin;
    /** Profil config.yml utilisé à la création (null = défauts seuls). */
    private final String settingsProfile;
    private boolean debugEnabled;
    private UUID debugViewerId;

    private BotPlayer(UUID id, LivingEntity entity, BotBrain brain, BotSettings settings, JavaPlugin plugin, String settingsProfile) {
        this.id = id;
        this.entity = entity;
        this.brain = brain;
        this.settings = settings;
        this.plugin = plugin;
        this.settingsProfile = settingsProfile;
    }

    public static BotPlayer spawnBasic(Location spawnLocation, BotSettings settings, JavaPlugin plugin) {
        return spawnBasic(spawnLocation, settings, plugin, null);
    }

    public static BotPlayer spawnBasic(Location spawnLocation, BotSettings settings, JavaPlugin plugin, String settingsProfile) {
        String displayName = BotBehaviorTreeLoader.readBotNameOrDefault(plugin, settings, "SB_Bot");
        if (ENABLE_EXPERIMENTAL_NMS_PLAYER) {
            try {
                Player fakePlayer = NmsFakePlayerFactory.spawnFakePlayer(spawnLocation, displayName, settings);
                applyDisplayName(fakePlayer, displayName);
                applyTabVisibility(fakePlayer, settings);
                UUID id = fakePlayer.getUniqueId();
                BotBrain brain = BotBrain.basic(fakePlayer, settings, plugin);
                return new BotPlayer(id, fakePlayer, brain, settings, plugin, normalizeProfile(settingsProfile));
            } catch (Throwable ex) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "[bot] NMS fake player spawn failed; using zombie fallback", ex);
                }
            }
        }

        Zombie zombie = spawnLocation.getWorld().spawn(spawnLocation, Zombie.class, z -> {
            z.setAdult();
            z.setAI(false);
            z.setSilent(true);
            z.setRemoveWhenFarAway(false);
            z.setCustomNameVisible(true);
        });
        UUID id = zombie.getUniqueId();
        applyDisplayName(zombie, displayName + "-" + id.toString().substring(0, 6));
        BotBrain brain = BotBrain.basic(zombie, settings, plugin);
        return new BotPlayer(id, zombie, brain, settings, plugin, normalizeProfile(settingsProfile));
    }

    private static String normalizeProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return null;
        }
        return profile;
    }

    /**
     * Recharge config + arbre .bbot pour ce bot (profil mémorisé) sans respawn.
     */
    public void reloadFromConfig(BlurpBot plugin) {
        BotSettings template = plugin.getProfileBotSettingsCopy(settingsProfile);
        BehaviorTree tree = BotBehaviorTreeLoader.loadOrBuiltin(plugin, template);
        String displayName = BotBehaviorTreeLoader.readBotNameOrDefault(plugin, template, "SB_Bot");
        this.settings.copyStateFrom(template);
        this.brain.setBehaviorTree(tree);
        if (entity != null && entity.isValid()) {
            applyDisplayName(entity, displayName);
            applyTabVisibility(entity, this.settings);
        }
    }

    private static void applyDisplayName(LivingEntity entity, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String trimmed = name.length() > 32 ? name.substring(0, 32) : name;
        entity.setCustomName(trimmed);
        entity.setCustomNameVisible(true);
        if (entity instanceof Player p) {
            try {
                p.setPlayerListName(trimmed);
            } catch (Throwable ignored) {
                // Best effort for fake player implementations.
            }
        }
    }

    private static void applyTabVisibility(LivingEntity entity, BotSettings settings) {
        if (entity instanceof Player p && settings != null) {
            NmsFakePlayerFactory.applyTabVisibility(p, settings.isTabVisible());
        }
    }

    @Override
    public void tick() {
        brain.tick();
    }

    @Override
    public void clearManualGoal() {
        brain.getGoalManager().clearScriptedGoal();
    }

    @Override
    public void setManualGoal(GoalTargetKind kind, int priority) {
        brain.getGoalManager().setScriptedGoal(kind, priority);
    }

    @Override
    public void setManualGoalLocation(Location location, int priority) {
        brain.getGoalManager().setScriptedGoalLocation(location, priority);
    }

    @Override
    public boolean setSetting(BotSettingKey key, String value) {
        boolean ok = settings.apply(key, value);
        if (ok && key == BotSettingKey.TAB_VISIBLE) {
            applyTabVisibility(entity, settings);
        }
        return ok;
    }

    @Override
    public boolean setSetting(String key, String value) {
        BotSettingKey parsed = BotSettingKey.fromKey(key);
        if (parsed == null) {
            return false;
        }
        return setSetting(parsed, value);
    }

    @Override
    public void applySettings(BotSettings settings) {
        this.settings.copyStateFrom(settings);
        applyTabVisibility(entity, this.settings);
    }

    @Override
    public void setSkin(String profileName, String texture, String signature) {
        settings.setSkinProfileName(profileName);
        settings.setSkinTexture(texture);
        settings.setSkinSignature(signature);
    }

    @Override
    public void reloadBehaviorTree() {
        BehaviorTree tree = BotBehaviorTreeLoader.loadOrBuiltin(plugin, settings);
        String displayName = BotBehaviorTreeLoader.readBotNameOrDefault(plugin, settings, "SB_Bot");
        this.brain.setBehaviorTree(tree);
        if (entity != null && entity.isValid()) {
            applyDisplayName(entity, displayName);
        }
    }

    // ----------------------------------------------------------------------------------------
    // État / requêtes
    // ----------------------------------------------------------------------------------------

    @Override
    public Location getLocation() {
        return (entity != null && entity.isValid()) ? entity.getLocation() : null;
    }

    @Override
    public World getWorld() {
        return entity != null ? entity.getWorld() : null;
    }

    @Override
    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    @Override
    public String getName() {
        return entity != null ? entity.getCustomName() : null;
    }

    @Override
    public double getHealth() {
        return entity != null ? entity.getHealth() : 0.0;
    }

    @Override
    public double getMaxHealth() {
        if (entity == null) {
            return 0.0;
        }
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : entity.getHealth();
    }

    @Override
    public Vector getVelocity() {
        return (entity != null && entity.isValid()) ? entity.getVelocity() : new Vector(0, 0, 0);
    }

    @Override
    public boolean isOnGround() {
        return entity != null && entity.isValid() && entity.isOnGround();
    }

    @Override
    public boolean isSneaking() {
        return entity instanceof Player p && p.isSneaking();
    }

    @Override
    public boolean isSprinting() {
        return entity instanceof Player p && p.isSprinting();
    }

    // ----------------------------------------------------------------------------------------
    // Mouvement / navigation
    // ----------------------------------------------------------------------------------------

    @Override
    public boolean teleport(Location location) {
        return brain.teleport(location);
    }

    @Override
    public void moveTo(Location target) {
        moveTo(target, settings.getStopRange());
    }

    @Override
    public void moveTo(Location target, double stopRange) {
        if (target == null) {
            return;
        }
        Location snapshot = target.clone();
        brain.enqueueMovement(c -> c.moveTo(snapshot, stopRange));
    }

    @Override
    public void stop() {
        brain.enqueueMovement(BotController::stop);
    }

    @Override
    public void lookAt(Location target) {
        if (target == null) {
            return;
        }
        Location snapshot = target.clone();
        brain.enqueueAction(c -> c.lookAt(snapshot));
    }

    @Override
    public void lookAt(Entity target) {
        if (target == null) {
            return;
        }
        // Résolution de la position à l'exécution (la cible peut bouger d'ici le prochain tick).
        brain.enqueueAction(c -> {
            if (!target.isValid()) {
                return;
            }
            Location at = (target instanceof LivingEntity living) ? living.getEyeLocation() : target.getLocation();
            c.lookAt(at);
        });
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        brain.enqueueAction(c -> c.setRotation(yaw, pitch));
    }

    @Override
    public void jump() {
        brain.enqueueAction(BotController::requestJump);
    }

    // ----------------------------------------------------------------------------------------
    // Vélocité simulée / impulsions
    // ----------------------------------------------------------------------------------------

    @Override
    public Vector getFakeVelocity() {
        return brain.getFakeVelocity();
    }

    @Override
    public void setFakeVelocity(Vector velocity) {
        brain.setFakeVelocity(velocity);
    }

    @Override
    public void addFakeVelocity(Vector velocity) {
        brain.addFakeVelocity(velocity);
    }

    @Override
    public void applyImpulse(Vector impulse) {
        brain.applyImpulse(impulse);
    }

    // ----------------------------------------------------------------------------------------
    // Actions / combat
    // ----------------------------------------------------------------------------------------

    @Override
    public void attack(Entity target) {
        attackWithCooldown(target, 0L);
    }

    @Override
    public void attackWithCooldown(Entity target, long cooldownMs) {
        if (target == null) {
            return;
        }
        brain.enqueueAction(c -> c.attackWithCooldown(target, cooldownMs));
    }

    @Override
    public void swingHand(boolean mainHand) {
        brain.enqueueAction(c -> c.trySwingHand(mainHand));
    }

    @Override
    public void setPose(Pose pose) {
        if (pose == null) {
            return;
        }
        brain.enqueueAction(c -> c.trySetPose(pose));
    }

    @Override
    public boolean hasLineOfSight(Entity target) {
        return brain.getController().hasMeleeLineOfSight(target);
    }

    @Override
    public boolean isWithinMeleeRange(Entity target, double attackRange) {
        return brain.getController().isTargetWithinMeleeRange(target, attackRange);
    }

    // ----------------------------------------------------------------------------------------
    // Apparence / état mutable
    // ----------------------------------------------------------------------------------------

    @Override
    public void setName(String name) {
        if (entity != null && entity.isValid()) {
            applyDisplayName(entity, name);
        }
    }

    @Override
    public boolean setHealth(double health) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        try {
            double max = getMaxHealth();
            double clamped = Math.max(0.0, Math.min(health, max > 0 ? max : health));
            entity.setHealth(clamped);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void setSneaking(boolean sneaking) {
        if (entity instanceof Player p) {
            p.setSneaking(sneaking);
        }
    }

    @Override
    public void setSprinting(boolean sprinting) {
        if (entity instanceof Player p) {
            p.setSprinting(sprinting);
        }
    }

    @Override
    public void setGravity(boolean gravity) {
        if (entity != null) {
            entity.setGravity(gravity);
        }
    }

    public void setDebugEnabled(boolean debugEnabled, UUID viewerId) {
        this.debugEnabled = debugEnabled;
        this.debugViewerId = debugEnabled ? viewerId : null;
        brain.setDebugEnabled(debugEnabled);
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public UUID getDebugViewerId() {
        return debugViewerId;
    }

}
