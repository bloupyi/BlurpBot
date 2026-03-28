package fr.bloup.blurpbot.perception;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PerceptionUpdater {
    private final double nearbyRange;

    public PerceptionUpdater(double nearbyRange) {
        this.nearbyRange = nearbyRange;
    }

    public void update(LivingEntity entity, BotPerception perception) {
        if (entity == null || !entity.isValid()) {
            perception.setNearbyPlayers(List.of());
            perception.setClosestLivingEntity(null);
            perception.setClosestMonster(null);
            return;
        }

        Location loc = entity.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            perception.setNearbyPlayers(List.of());
            perception.setClosestLivingEntity(null);
            perception.setClosestMonster(null);
            return;
        }

        double rangeSq = nearbyRange * nearbyRange;
        List<Player> players = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (!p.isValid() || p.isDead()) continue;
            if (p.getUniqueId().equals(entity.getUniqueId())) continue;
            if (p.getLocation().distanceSquared(loc) <= rangeSq) {
                players.add(p);
            }
        }

        players.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(loc)));
        perception.setNearbyPlayers(players);

        LivingEntity closestLiving = null;
        LivingEntity closestMonster = null;
        double bestLiving = Double.MAX_VALUE;
        double bestMonster = Double.MAX_VALUE;
        for (Entity e : world.getNearbyEntities(loc, nearbyRange, nearbyRange, nearbyRange)) {
            if (!(e instanceof LivingEntity le)) {
                continue;
            }
            if (le.getUniqueId().equals(entity.getUniqueId()) || le instanceof ArmorStand || le.isDead()) {
                continue;
            }
            double d2 = le.getLocation().distanceSquared(loc);
            if (d2 < bestLiving) {
                bestLiving = d2;
                closestLiving = le;
            }
            if (le instanceof Monster && d2 < bestMonster) {
                bestMonster = d2;
                closestMonster = le;
            }
        }
        perception.setClosestLivingEntity(closestLiving);
        perception.setClosestMonster(closestMonster);
    }
}
