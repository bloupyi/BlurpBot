package fr.bloup.blurpbot.decision.leaf.action;

import org.bukkit.entity.Player;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;
import fr.bloup.blurpbot.bbot.expr.BbotLongExpr;

public class AttackTarget implements BehaviorNode {
    private final BbotLongExpr cooldownExpr;

    public AttackTarget(BbotLongExpr cooldownExpr) {
        this.cooldownExpr = cooldownExpr;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        Player target = context.perception().getTarget();
        if (target == null) return NodeStatus.FAILURE;
        context.controller().lookAt(target.getLocation());
        long cooldownMs = cooldownExpr.eval(context);
        long effectiveCooldown = cooldownMs > 0 ? cooldownMs : context.settings().getAttackCooldownMs();
        boolean didAttack = context.controller().attackWithCooldown(target, effectiveCooldown);
        return didAttack ? NodeStatus.SUCCESS : NodeStatus.RUNNING;
    }
}

