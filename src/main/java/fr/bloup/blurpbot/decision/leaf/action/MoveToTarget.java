package fr.bloup.blurpbot.decision.leaf.action;

import org.bukkit.Location;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;
import fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr;
import fr.bloup.blurpbot.goal.Goal;

public class MoveToTarget implements BehaviorNode {
    private final Goal goal;
    private final BbotDoubleExpr stopRangeExpr;

    public MoveToTarget(Goal goal, BbotDoubleExpr stopRangeExpr) {
        this.goal = goal;
        this.stopRangeExpr = stopRangeExpr;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        Location targetLoc = goal.resolve(context);
        if (targetLoc == null) return NodeStatus.FAILURE;
        double stopRange = stopRangeExpr.eval(context);
        double effectiveStopRange = stopRange > 0 ? stopRange : context.settings().getStopRange();
        context.controller().moveTo(targetLoc, effectiveStopRange);
        return NodeStatus.RUNNING;
    }
}

