package fr.bloup.blurpbot.goal;

import org.bukkit.Location;

import fr.bloup.blurpbot.brain.BotContext;

public class MoveToLocationGoal implements Goal {
    private final Location target;

    public MoveToLocationGoal(Location target) {
        this.target = target;
    }

    @Override
    public Location resolve(BotContext context) {
        return target;
    }
}

