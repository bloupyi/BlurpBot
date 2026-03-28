package fr.bloup.blurpbot.decision.leaf.condition;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class HasTarget implements BehaviorNode {
    @Override
    public NodeStatus tick(BotContext context) {
        return (context.perception().getTarget() != null) ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }
}

