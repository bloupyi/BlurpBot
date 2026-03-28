package fr.bloup.blurpbot.bbot;

import java.util.Map;

import fr.bloup.blurpbot.bbot.BbotModel.ValueExpr;
import fr.bloup.blurpbot.decision.BehaviorNode;

/**
 * Fabrique une condition à partir des arguments du script {@code condition nom { ... }}.
 */
@FunctionalInterface
public interface BbotConditionFactory {
    BehaviorNode build(Map<String, ValueExpr> args) throws BbotParseException;
}
