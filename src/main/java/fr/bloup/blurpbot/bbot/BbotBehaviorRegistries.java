package fr.bloup.blurpbot.bbot;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fr.bloup.blurpbot.decision.leaf.action.AttackTarget;
import fr.bloup.blurpbot.decision.leaf.action.EntityPoseLeaf;
import fr.bloup.blurpbot.decision.leaf.action.IdleAction;
import fr.bloup.blurpbot.decision.leaf.action.LookAtLeaf;
import fr.bloup.blurpbot.decision.leaf.action.MoveToTarget;
import fr.bloup.blurpbot.decision.leaf.action.SetGoalAction;
import fr.bloup.blurpbot.decision.leaf.action.SwingHandLeaf;
import fr.bloup.blurpbot.decision.leaf.action.TeleportLeaf;
import fr.bloup.blurpbot.decision.leaf.condition.HasActiveGoalCondition;
import fr.bloup.blurpbot.decision.leaf.condition.HasMeleeLineOfSight;
import fr.bloup.blurpbot.decision.leaf.condition.IsTargetInRange;
import fr.bloup.blurpbot.goal.ActiveGoalResolver;

/**
 * Registres des noms de feuilles et de conditions → fabriques. Les noms sont en
 * <strong>snake_case</strong> comme dans le script ; la recherche est insensible à la casse.
 * <p>
 * Pour ajouter une feuille ou une condition : appeler {@link #registerLeaf(String, BbotLeafFactory)}
 * ou {@link #registerCondition(String, BbotConditionFactory)} au démarrage du plugin (avant le
 * premier chargement d’un .bbot), ou enregistrer ici dans le bloc {@code static} pour le cœur du plugin.
 */
public final class BbotBehaviorRegistries {

    private static final Map<String, BbotLeafFactory> LEAVES = new ConcurrentHashMap<>();
    private static final Map<String, BbotConditionFactory> CONDITIONS = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    private BbotBehaviorRegistries() {
    }

    private static void registerDefaults() {
        registerLeaf("attack_target", args -> new AttackTarget(BbotArgParsing.resolveCooldownMsExpr(args)));
        registerLeaf("move_to_goal", args -> new MoveToTarget(new ActiveGoalResolver(), BbotArgParsing.resolveStopRangeExpr(args)));
        registerLeaf("idle", args -> new IdleAction());
        registerLeaf("swing_hand", args -> new SwingHandLeaf(BbotArgParsing.resolveSwingHandMain(args)));
        registerLeaf("entity_pose", args -> new EntityPoseLeaf(BbotArgParsing.resolvePose(args)));
        registerLeaf("set_goal", args -> new SetGoalAction(BbotArgParsing.resolveSetGoalSpecExpr(args)));
        registerLeaf("teleport", args -> new TeleportLeaf(
                BbotArgParsing.resolveRequiredDoubleExprStrict(args, "x"),
                BbotArgParsing.resolveRequiredDoubleExprStrict(args, "y"),
                BbotArgParsing.resolveRequiredDoubleExprStrict(args, "z"),
                BbotArgParsing.resolveOptionalDoubleExprStrict(args, "yaw"),
                BbotArgParsing.resolveOptionalDoubleExprStrict(args, "pitch"),
                BbotArgParsing.resolveOptionalWorldName(args)
        ));
        registerLeaf("look_at", args -> {
            String target = BbotArgParsing.resolveOptionalEntityTargetRoot(args, "target");
            if (target != null && !target.isBlank()) {
                return new LookAtLeaf(target, null, null, null);
            }
            return new LookAtLeaf(
                    null,
                    BbotArgParsing.resolveRequiredDoubleExprStrict(args, "x", "look_at"),
                    BbotArgParsing.resolveRequiredDoubleExprStrict(args, "y", "look_at"),
                    BbotArgParsing.resolveRequiredDoubleExprStrict(args, "z", "look_at")
            );
        });

        registerCondition("target_in_range", args -> new IsTargetInRange(BbotArgParsing.resolveRangeExpr(args, "range")));
        registerCondition("has_melee_los", args -> new HasMeleeLineOfSight());
        registerCondition("has_active_goal", args -> new HasActiveGoalCondition());
    }

    /**
     * Enregistre ou remplace une feuille (nom script : ex. {@code my_action}).
     */
    public static void registerLeaf(String name, BbotLeafFactory factory) {
        LEAVES.put(normalize(name), factory);
    }

    /**
     * Enregistre ou remplace une condition (nom script : ex. {@code target_in_range}).
     */
    public static void registerCondition(String name, BbotConditionFactory factory) {
        CONDITIONS.put(normalize(name), factory);
    }

    public static BbotLeafFactory getLeafFactory(String name) {
        return LEAVES.get(normalize(name));
    }

    public static BbotConditionFactory getConditionFactory(String name) {
        return CONDITIONS.get(normalize(name));
    }

    public static Set<String> registeredLeafNames() {
        return Collections.unmodifiableSet(LEAVES.keySet());
    }

    public static Set<String> registeredConditionNames() {
        return Collections.unmodifiableSet(CONDITIONS.keySet());
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
