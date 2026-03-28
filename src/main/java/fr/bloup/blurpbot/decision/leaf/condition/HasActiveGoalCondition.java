package fr.bloup.blurpbot.decision.leaf.condition;

import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.NodeStatus;

public class HasActiveGoalCondition implements BehaviorNode {
    @Override
    public NodeStatus tick(BotContext context) {
        if (context.goalManager() == null || context.goalManager().getActiveGoal() == null) {
            return NodeStatus.FAILURE;
        }
        return NodeStatus.SUCCESS;
    }
}
