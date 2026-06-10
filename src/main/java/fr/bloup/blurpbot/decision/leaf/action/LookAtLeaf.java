package fr.bloup.blurpbot.decision.leaf.action;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import fr.bloup.blurpbot.bbot.BbotArgParsing;
import fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr;
import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

/**
 * Oriente le bot vers une cible runtime (entité) ou vers une location explicite (x/y/z).
 */
public final class LookAtLeaf implements BehaviorNode {
    private final String targetRoot;
    private final BbotDoubleExpr xExpr;
    private final BbotDoubleExpr yExpr;
    private final BbotDoubleExpr zExpr;

    public LookAtLeaf(String targetRoot, BbotDoubleExpr xExpr, BbotDoubleExpr yExpr, BbotDoubleExpr zExpr) {
        this.targetRoot = targetRoot;
        this.xExpr = xExpr;
        this.yExpr = yExpr;
        this.zExpr = zExpr;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        if (targetRoot != null && !targetRoot.isBlank()) {
            LivingEntity target = BbotArgParsing.resolveRuntimeEntity(context, targetRoot);
            if (target == null || !target.isValid()) {
                return NodeStatus.FAILURE;
            }
            context.controller().lookAt(target.getLocation());
            return NodeStatus.SUCCESS;
        }
        Location cur = context.controller().getEntity().getLocation();
        if (cur.getWorld() == null || xExpr == null || yExpr == null || zExpr == null) {
            return NodeStatus.FAILURE;
        }
        Location look = new Location(
                cur.getWorld(),
                xExpr.eval(context),
                yExpr.eval(context),
                zExpr.eval(context)
        );
        context.controller().lookAt(look);
        return NodeStatus.SUCCESS;
    }
}

