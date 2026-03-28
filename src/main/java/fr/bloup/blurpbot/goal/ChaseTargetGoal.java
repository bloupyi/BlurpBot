package fr.bloup.blurpbot.goal;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.bloup.blurpbot.brain.BotContext;

public class ChaseTargetGoal implements PrioritizedGoal {
    @Override
    public Location resolve(BotContext context) {
        Player target = context.perception().getTarget();
        if (target == null) {
            return null;
        }
        if (context.controller().hasMeleeLineOfSight(target)) {
            return target.getLocation();
        }
        Location flank = context.controller().findMeleeFlankLocation(target, context.settings().getAttackRange());
        return flank != null ? flank : target.getLocation();
    }

    @Override
    public int priority(BotContext context) {
        return context.perception().getTarget() != null ? 100 : 0;
    }
}

