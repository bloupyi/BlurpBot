package fr.bloup.blurpbot.decision.leaf.condition;

import org.bukkit.entity.Player;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;
import fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr;

public class IsTargetInRange implements BehaviorNode {
    private final BbotDoubleExpr rangeExpr;

    public IsTargetInRange(BbotDoubleExpr rangeExpr) {
        this.rangeExpr = rangeExpr;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        Player target = context.perception().getTarget();
        if (target == null) return NodeStatus.FAILURE;
        double range = rangeExpr.eval(context);
        double effectiveRange = range > 0 ? range : context.settings().getAttackRange();
        return context.controller().isTargetWithinMeleeRange(target, effectiveRange)
                ? NodeStatus.SUCCESS
                : NodeStatus.FAILURE;
    }
}

