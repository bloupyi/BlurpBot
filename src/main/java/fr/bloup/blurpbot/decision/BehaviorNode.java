package fr.bloup.blurpbot.decision;

import fr.bloup.blurpbot.brain.BotContext;

public interface BehaviorNode {
    NodeStatus tick(BotContext context);
}