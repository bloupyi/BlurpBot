package fr.bloup.blurpbot.decision.leaf.action;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class IdleAction implements BehaviorNode {
    @Override
    public NodeStatus tick(BotContext context) {
        context.controller().stop();
        return NodeStatus.SUCCESS;
    }
}

