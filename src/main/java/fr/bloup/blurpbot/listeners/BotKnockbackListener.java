package fr.bloup.blurpbot.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import fr.bloup.blurpbot.api.BotManager;

public class BotKnockbackListener implements Listener {
    private final BotManager botManager;

    public BotKnockbackListener(BotManager botManager) {
        this.botManager = botManager;
    }

    @EventHandler
    public void onBotDamaged(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();
        if (victim == null || damager == null) return;

        Vector away = victim.getLocation().toVector().subtract(damager.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0e-6) return;

        double horizontal = 0.40; // close to vanilla base KB
        double vertical = 0.36;

        if (damager instanceof Player player && player.isSprinting()) {
            horizontal += 0.05;
            vertical += 0.02;
        }

        if (damager instanceof LivingEntity living) {
            Vector attackerMomentum = living.getVelocity().clone().setY(0).multiply(0.35);
            away.add(attackerMomentum);
        }

        away.normalize().multiply(horizontal);
        away.setY(vertical);

        botManager.applyKnockback(victim.getUniqueId(), away);
    }
}

