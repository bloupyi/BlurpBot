package fr.bloup.blurpbot.brain;

import fr.bloup.blurpbot.controller.BotController;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.debug.BotDebugFrame;
import fr.bloup.blurpbot.goal.GoalManager;
import fr.bloup.blurpbot.perception.BotPerception;

public record BotContext(
        BotPerception perception,
        BotController controller,
        BotSettings settings,
        GoalManager goalManager,
        BotDebugFrame debugFrame
) {}
