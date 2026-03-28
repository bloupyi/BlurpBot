package fr.bloup.blurpbot.decision.composite;

import java.util.List;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class SequenceNode implements BehaviorNode {
    private final List<BehaviorNode> children;

    public SequenceNode(List<BehaviorNode> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public NodeStatus tick(BotContext context) {
        for (BehaviorNode child : children) {
            NodeStatus s = child.tick(context);
            if (s != NodeStatus.SUCCESS && s != NodeStatus.PASS) {
                return s;
            }
        }
        return NodeStatus.SUCCESS;
    }
}
