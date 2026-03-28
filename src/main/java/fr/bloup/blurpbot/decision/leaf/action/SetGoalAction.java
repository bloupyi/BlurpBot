package fr.bloup.blurpbot.decision.leaf.action;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;
import fr.bloup.blurpbot.goal.GoalTargetSpec;
import fr.bloup.blurpbot.goal.GoalTargetSpecExpr;

/**
 * Active l’objectif scripté du {@link fr.bloup.blurpbot.goal.GoalManager} pour les ticks suivants,
 * puis {@linkplain fr.bloup.blurpbot.goal.GoalManager#update recalcule} tout de suite l’objectif actif
 * (même tick : {@code has_active_goal}, {@code move_to_goal}, etc.).
 * <p>
 * Renvoie {@link NodeStatus#PASS} pour que le {@code priority_selector} enchaîne le nœud suivant
 * dans le même tick sans utiliser {@link NodeStatus#FAILURE} (l’opération a bien réussi).
 */
public final class SetGoalAction implements BehaviorNode {

    private final GoalTargetSpecExpr specExpr;

    public SetGoalAction(GoalTargetSpecExpr specExpr) {
        this.specExpr = specExpr;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        var gm = context.goalManager();
        GoalTargetSpec spec = buildSpec(context);
        gm.scriptedGoal().apply(spec, context);
        gm.update(context);
        return NodeStatus.PASS;
    }

    private GoalTargetSpec buildSpec(BotContext context) {
        var kind = specExpr.kind();
        if (kind == fr.bloup.blurpbot.goal.GoalTargetKind.NONE) {
            return GoalTargetSpec.clear();
        }

        int priority = specExpr.priorityExpr().eval(context);
        if (kind == fr.bloup.blurpbot.goal.GoalTargetKind.LOCATION_FIXED) {
            double x = specExpr.xExpr().eval(context);
            double y = specExpr.yExpr().eval(context);
            double z = specExpr.zExpr().eval(context);
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                return GoalTargetSpec.clear();
            }
            return new GoalTargetSpec(kind, priority, x, y, z, specExpr.worldName());
        }

        return new GoalTargetSpec(kind, priority, 0, 0, 0, null);
    }
}

