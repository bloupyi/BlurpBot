package fr.bloup.blurpbot.decision;

import fr.bloup.blurpbot.brain.BotContext;

public class BehaviorTree {
    private final BehaviorNode root;

    public BehaviorTree(BehaviorNode root) {
        this.root = root;
    }

    public NodeStatus tick(BotContext context) {
        return root.tick(context);
    }
}

