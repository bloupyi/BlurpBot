package fr.bloup.blurpbot.goal;

import org.bukkit.Location;

import fr.bloup.blurpbot.brain.BotContext;

public interface Goal {
    Location resolve(BotContext context);
}

