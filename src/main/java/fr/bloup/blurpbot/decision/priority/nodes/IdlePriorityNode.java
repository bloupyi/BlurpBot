package fr.bloup.blurpbot.decision.priority.nodes;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.NodeStatus;
import fr.bloup.blurpbot.decision.leaf.action.IdleAction;
import fr.bloup.blurpbot.decision.priority.PrioritizedNode;

public class IdlePriorityNode implements PrioritizedNode {
    private final IdleAction idleNode = new IdleAction();

    @Override
    public int priority(BotContext context) {
        return context.settings().getPriorityIdle();
    }

    @Override
    public NodeStatus tick(BotContext context) {
        return idleNode.tick(context);
    }
}

