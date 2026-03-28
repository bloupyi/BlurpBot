package fr.bloup.blurpbot.decision.leaf.action;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

/**
 * Anime un coup de main (joueur uniquement ; zombie ignoré → {@link NodeStatus#FAILURE}).
 */
public class SwingHandLeaf implements BehaviorNode {
    private final boolean mainHand;

    public SwingHandLeaf(boolean mainHand) {
        this.mainHand = mainHand;
    }

    @Override
    public NodeStatus tick(BotContext context) {
        boolean ok = context.controller().trySwingHand(mainHand);
        return ok ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }
}
