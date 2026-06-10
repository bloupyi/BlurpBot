package fr.bloup.blurpbot.decision.leaf.action;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr;
import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

/**
 * Téléporte le bot à une position donnée (coordonnées calculées runtime).
 *
 * Arguments :
 * - {@code x}, {@code y}, {@code z} : nombres ou expressions {@code @root.location.{x|y|z|yaw|pitch}}
 * - {@code world} (optionnel) : nom du monde Bukkit
 * - {@code yaw}, {@code pitch} (optionnels) : orientation (sinon orientation actuelle du bot)
 */
public final class TeleportLeaf implements BehaviorNode {

    private final BbotDoubleExpr xExpr;
    private final BbotDoubleExpr yExpr;
    private final BbotDoubleExpr zExpr;
    private final BbotDoubleExpr yawExpr;
    private final BbotDoubleExpr pitchExpr;
    private final String worldName;

    public TeleportLeaf(
            BbotDoubleExpr xExpr,
            BbotDoubleExpr yExpr,
            BbotDoubleExpr zExpr,
            BbotDoubleExpr yawExpr,
            BbotDoubleExpr pitchExpr,
            String worldName
    ) {
        this.xExpr = xExpr;
        this.yExpr = yExpr;
        this.zExpr = zExpr;
        this.yawExpr = yawExpr;
        this.pitchExpr = pitchExpr;
        this.worldName = worldName;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        LivingEntity ent = context.controller().getEntity();
        World w = resolveWorld(ent);
        if (w == null) return NodeStatus.FAILURE;

        double x = xExpr.eval(context);
        double y = yExpr.eval(context);
        double z = zExpr.eval(context);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return NodeStatus.FAILURE;

        float curYaw = ent.getLocation().getYaw();
        float curPitch = ent.getLocation().getPitch();
        float yaw = yawExpr != null ? (float) yawExpr.eval(context) : curYaw;
        float pitch = pitchExpr != null ? (float) pitchExpr.eval(context) : curPitch;
        if (yawExpr != null && Float.isNaN(yaw)) return NodeStatus.FAILURE;
        if (pitchExpr != null && Float.isNaN(pitch)) return NodeStatus.FAILURE;

        Location to = new Location(w, x, y, z, yaw, pitch);
        return context.controller().teleport(to) ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }

    private World resolveWorld(LivingEntity ent) {
        if (worldName == null || worldName.isBlank()) {
            return ent.getWorld();
        }
        return ent.getServer().getWorld(worldName);
    }
}

