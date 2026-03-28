package fr.bloup.blurpbot.bbot;

import java.util.Map;

import fr.bloup.blurpbot.bbot.BbotModel.ValueExpr;
import fr.bloup.blurpbot.decision.BehaviorNode;

/**
 * Fabrique une feuille d’action à partir des arguments du script {@code leaf nom { ... }}.
 */
@FunctionalInterface
public interface BbotLeafFactory {
    BehaviorNode build(Map<String, ValueExpr> args) throws BbotParseException;
}
