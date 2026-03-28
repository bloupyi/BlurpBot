package fr.bloup.blurpbot.decision.composite;

import java.util.List;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class SelectorNode implements BehaviorNode {
    private final List<BehaviorNode> children;

    public SelectorNode(List<BehaviorNode> children) {
        this.children = List.copyOf(children);
    }

    @Override
    public NodeStatus tick(BotContext context) {
        for (BehaviorNode child : children) {
            NodeStatus status = child.tick(context);
            if (status == NodeStatus.SUCCESS || status == NodeStatus.RUNNING) {
                return status;
            }
        }
        return NodeStatus.FAILURE;
    }
}

