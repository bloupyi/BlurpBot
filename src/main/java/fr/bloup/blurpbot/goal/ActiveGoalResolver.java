package fr.bloup.blurpbot.goal;

import org.bukkit.Location;

import fr.bloup.blurpbot.brain.BotContext;

public class ActiveGoalResolver implements Goal {
    @Override
    public Location resolve(BotContext context) {
        GoalManager manager = context.goalManager();
        if (manager == null || manager.getActiveGoal() == null) return null;
        return manager.getActiveGoal().resolve(context);
    }
}

