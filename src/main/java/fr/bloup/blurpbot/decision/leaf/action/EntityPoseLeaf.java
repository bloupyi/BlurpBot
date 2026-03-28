package fr.bloup.blurpbot.decision.leaf.action;

import org.bukkit.entity.Pose;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

/**
 * Pose vanilla (debout, accroupi, nage, etc.) via {@link org.bukkit.entity.LivingEntity#setPose(Pose)}.
 */
public class EntityPoseLeaf implements BehaviorNode {
    private final Pose pose;

    public EntityPoseLeaf(Pose pose) {
        this.pose = pose;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        boolean ok = context.controller().trySetPose(pose);
        return ok ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }
}
