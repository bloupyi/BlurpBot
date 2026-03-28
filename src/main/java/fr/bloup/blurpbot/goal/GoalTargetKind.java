package fr.bloup.blurpbot.goal;

/**
 * Cible logique pour {@link ScriptedGoal} (feuille {@code set_goal} dans un .bbot).
 */
public enum GoalTargetKind {
    /** Aucun objectif scripté (comportement comme avant la commande). */
    NONE,
    /** Joueur le plus proche (même notion que la cible de perception triée par distance). */
    CLOSEST_PLAYER,
    /** Entité vivante la plus proche (hors soi ; exclut les armor stands). */
    CLOSEST_ENTITY,
    /** Monstre hostile le plus proche. */
    CLOSEST_MONSTER,
    /** Cible courante de perception (premier joueur proche). */
    CURRENT_TARGET,
    /** Coordonnées fixes (x, y, z + monde optionnel). */
    LOCATION_FIXED
}
