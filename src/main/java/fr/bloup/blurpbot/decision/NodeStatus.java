package fr.bloup.blurpbot.decision;

/**
 * Résultat d’un {@link BehaviorNode#tick}.
 * <p>
 * {@link #PASS} est réservé au {@link fr.bloup.blurpbot.decision.priority.PrioritySelectorNode} : l’action
 * a réussi mais on veut essayer le nœud prioritaire suivant dans le même tick (ex. après {@code set_goal}).
 */
public enum NodeStatus {
    SUCCESS,
    FAILURE,
    RUNNING,
    /**
     * Succès logique : essayer le nœud suivant du {@code priority_selector} (ne pas confondre avec {@link #FAILURE}).
     */
    PASS
}
