package fr.bloup.blurpbot.goal;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import fr.bloup.blurpbot.brain.BotContext;

/**
 * Objectif prioritaire défini par le script (feuille {@code set_goal}). Bat le chase (100) par défaut
 * lorsqu’il est actif et résolu.
 */
public final class ScriptedGoal implements PrioritizedGoal {

    private static final int DEFAULT_PRIORITY = 150;

    private GoalTargetKind kind = GoalTargetKind.NONE;
    private int priorityValue = DEFAULT_PRIORITY;
    private Location fixedLocation;

    public void clear() {
        this.kind = GoalTargetKind.NONE;
        this.priorityValue = 0;
        this.fixedLocation = null;
    }

    public void setKind(GoalTargetKind kind, int priority) {
        if (kind == null || kind == GoalTargetKind.NONE) {
            clear();
            return;
        }
        this.kind = kind;
        this.priorityValue = priority > 0 ? priority : DEFAULT_PRIORITY;
        this.fixedLocation = null;
    }

    public void setFixedLocation(Location location, int priority) {
        if (location == null || location.getWorld() == null) {
            clear();
            return;
        }
        this.kind = GoalTargetKind.LOCATION_FIXED;
        this.priorityValue = priority > 0 ? priority : DEFAULT_PRIORITY;
        this.fixedLocation = location.clone();
    }

    @Override
    public Location resolve(BotContext context) {
        return switch (kind) {
            case NONE -> null;
            case CLOSEST_PLAYER -> {
                Player p = context.perception().getClosestPlayer();
                yield p != null ? p.getLocation() : null;
            }
            case CLOSEST_ENTITY -> {
                LivingEntity e = context.perception().getClosestLivingEntity();
                yield e != null ? e.getLocation() : null;
            }
            case CLOSEST_MONSTER -> {
                LivingEntity e = context.perception().getClosestMonster();
                yield e != null ? e.getLocation() : null;
            }
            case CURRENT_TARGET -> {
                Player t = context.perception().getTarget();
                yield t != null ? t.getLocation() : null;
            }
            case LOCATION_FIXED -> fixedLocation != null ? fixedLocation.clone() : null;
        };
    }

    @Override
    public int priority(BotContext context) {
        if (kind == GoalTargetKind.NONE) {
            return 0;
        }
        return priorityValue;
    }

    /**
     * Applique la spec ; la location fixe est résolue avec le monde du bot si {@code worldName} est absent.
     */
    public void apply(GoalTargetSpec spec, BotContext context) {
        this.kind = spec.kind();
        this.fixedLocation = null;
        if (spec.kind() == GoalTargetKind.NONE) {
            this.priorityValue = 0;
            return;
        }
        this.priorityValue = spec.priority() > 0 ? spec.priority() : DEFAULT_PRIORITY;
        if (spec.kind() == GoalTargetKind.LOCATION_FIXED) {
            World w = resolveWorld(spec.worldName(), context);
            if (w == null) {
                this.kind = GoalTargetKind.NONE;
                this.priorityValue = 0;
                return;
            }
            this.fixedLocation = new Location(w, spec.x(), spec.y(), spec.z());
        }
    }

    private static World resolveWorld(String worldName, BotContext context) {
        if (worldName != null && !worldName.isBlank()) {
            return context.controller().getEntity().getServer().getWorld(worldName);
        }
        return context.controller().getEntity().getWorld();
    }
}
