package fr.bloup.blurpbot.goal;

import fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr;
import fr.bloup.blurpbot.bbot.expr.BbotIntExpr;

/**
 * Variante "expression" de {@link GoalTargetSpec} pour la feuille {@code set_goal}.
 * Les coordonnées et la priorité peuvent être calculées au {@code tick}.
 */
public record GoalTargetSpecExpr(
        GoalTargetKind kind,
        BbotIntExpr priorityExpr,
        BbotDoubleExpr xExpr,
        BbotDoubleExpr yExpr,
        BbotDoubleExpr zExpr,
        String worldName
) {
    public static GoalTargetSpecExpr clear() {
        return new GoalTargetSpecExpr(
                GoalTargetKind.NONE,
                ctx -> 0,
                ctx -> Double.NaN,
                ctx -> Double.NaN,
                ctx -> Double.NaN,
                null
        );
    }
}

