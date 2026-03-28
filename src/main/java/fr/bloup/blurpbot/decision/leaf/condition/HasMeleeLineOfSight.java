package fr.bloup.blurpbot.decision.leaf.condition;

import org.bukkit.entity.Player;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class HasMeleeLineOfSight implements BehaviorNode {
    @Override
    public NodeStatus tick(BotContext context) {
        Player target = context.perception().getTarget();
        if (target == null) {
            return NodeStatus.FAILURE;
        }
        return context.controller().hasMeleeLineOfSight(target)
                ? NodeStatus.SUCCESS
                : NodeStatus.FAILURE;
    }
}
