package fr.bloup.blurpbot.decision.priority;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.NodeStatus;

public interface PrioritizedNode {
    int priority(BotContext context);
    NodeStatus tick(BotContext context);

    default String debugLabel() {
        return getClass().getSimpleName();
    }
}

