package fr.bloup.blurpbot.goal;

import fr.bloup.blurpbot.brain.BotContext;

public interface PrioritizedGoal extends Goal {
    int priority(BotContext context);
}

