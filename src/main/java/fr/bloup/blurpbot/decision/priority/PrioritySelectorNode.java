package fr.bloup.blurpbot.decision.priority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class PrioritySelectorNode implements BehaviorNode {
    private final List<PrioritizedNode> nodes;

    public PrioritySelectorNode(List<PrioritizedNode> nodes) {
        this.nodes = List.copyOf(nodes);
    }

    @Override
    public NodeStatus tick(BotContext context) {
        List<PrioritizedNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparingInt((PrioritizedNode n) -> n.priority(context)).reversed());
        for (PrioritizedNode node : ordered) {
            int p = node.priority(context);
            if (p <= 0) continue;
            if (context.debugFrame() != null) {
                context.debugFrame().setSelectedAction(node.debugLabel());
                context.debugFrame().setSelectedActionPriority(p);
            }
            NodeStatus status = node.tick(context);
            if (status == NodeStatus.PASS) {
                continue;
            }
            if (status == NodeStatus.SUCCESS || status == NodeStatus.RUNNING) {
                return status;
            }
        }
        return NodeStatus.FAILURE;
    }
}

