package fr.bloup.blurpbot.decision.priority.nodes;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;
import fr.bloup.blurpbot.decision.priority.PrioritizedNode;

public class ScriptedPriorityNode implements PrioritizedNode {
    private final String label;
    private final PrioritySource prioritySource;
    private final BehaviorNode whenGuard;
    private final BehaviorNode action;

    public ScriptedPriorityNode(
            String label,
            PrioritySource prioritySource,
            BehaviorNode whenGuard,
            BehaviorNode action
    ) {
        this.label = label;
        this.prioritySource = prioritySource;
        this.whenGuard = whenGuard;
        this.action = action;
    }

    @Override
    public String debugLabel() {
        return label;
    }

    @Override
    public int priority(BotContext context) {
        if (whenGuard != null && whenGuard.tick(context) != NodeStatus.SUCCESS) {
            return 0;
        }
        return prioritySource.resolve(context);
    }

    @Override
    public NodeStatus tick(BotContext context) {
        if (whenGuard != null && whenGuard.tick(context) != NodeStatus.SUCCESS) {
            return NodeStatus.FAILURE;
        }
        return action.tick(context);
    }

    public interface PrioritySource {
        int resolve(BotContext context);
    }
}
