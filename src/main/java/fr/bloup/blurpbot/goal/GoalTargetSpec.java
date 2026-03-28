package fr.bloup.blurpbot.goal;

/**
 * Valeurs figées au chargement du .bbot pour une feuille {@code set_goal}.
 */
public record GoalTargetSpec(GoalTargetKind kind, int priority, double x, double y, double z, String worldName) {
    public static GoalTargetSpec clear() {
        return new GoalTargetSpec(GoalTargetKind.NONE, 0, 0, 0, 0, null);
    }
}
