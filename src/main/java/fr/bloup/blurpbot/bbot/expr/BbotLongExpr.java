package fr.bloup.blurpbot.bbot.expr;

import fr.bloup.blurpbot.brain.BotContext;

@FunctionalInterface
public interface BbotLongExpr {
    long eval(BotContext ctx);
}

