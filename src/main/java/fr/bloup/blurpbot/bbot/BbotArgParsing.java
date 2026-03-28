package fr.bloup.blurpbot.bbot;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Pose;
import org.bukkit.entity.LivingEntity;

import fr.bloup.blurpbot.bbot.BbotModel.ValueExpr;
import fr.bloup.blurpbot.bbot.BbotModel.DynamicLocationAxis;
import fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr;
import fr.bloup.blurpbot.bbot.expr.BbotIntExpr;
import fr.bloup.blurpbot.brain.BotContext;
import fr.bloup.blurpbot.goal.GoalTargetSpec;
import fr.bloup.blurpbot.goal.GoalTargetKind;
import fr.bloup.blurpbot.goal.GoalTargetSpecExpr;

/**
 * Parsing des arguments {@code clé: valeur} des blocs .bbot (feuilles / conditions).
 */
public final class BbotArgParsing {

    private BbotArgParsing() {
    }

    public static double resolveRange(Map<String, ValueExpr> args, String key) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            return -1;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return n.value();
        }
        if (v instanceof ValueExpr.SettingRef) {
            return -1;
        }
        throw new BbotParseException(1, "range must be a number or $attack-range");
    }

    public static long resolveCooldownMs(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr v = args.get("cooldown_ms");
        if (v == null) {
            return -1;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return (long) n.value();
        }
        if (v instanceof ValueExpr.SettingRef) {
            return -1;
        }
        throw new BbotParseException(1, "cooldown_ms must be a number or $attack-cooldown-ms");
    }

    public static double resolveStopRange(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr v = args.get("stop_range");
        if (v == null) {
            return -1;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return n.value();
        }
        if (v instanceof ValueExpr.SettingRef) {
            return -1;
        }
        throw new BbotParseException(1, "stop_range must be a number or $stop-range");
    }

    /**
     * Expression runtime pour {@code when/leaf} numériques.
     * <p>
     * Règles v1 :
     * <ul>
     *     <li>number → constant</li>
     *     <li>$... (BotSettings) → valeur sentinelle {@code -1} (utilise ensuite la valeur par défaut côté action)</li>
     *     <li>{@code @root.location.x|y|z} → valeur évaluée à chaque tick</li>
     * </ul>
     */
    public static fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr resolveRangeExpr(Map<String, ValueExpr> args, String key) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            return ctx -> -1;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return ctx -> n.value();
        }
        if (v instanceof ValueExpr.SettingRef) {
            return ctx -> -1;
        }
        if (v instanceof ValueExpr.DynamicLocationComponent c) {
            return ctx -> evalDynamicLocationAxis(ctx, c.root(), c.axis(), -1);
        }
        throw new BbotParseException(1, key + " must be a number, $setting, or @root.location.{x|y|z}");
    }

    public static fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr resolveStopRangeExpr(Map<String, ValueExpr> args) throws BbotParseException {
        return resolveRangeExpr(args, "stop_range");
    }

    public static fr.bloup.blurpbot.bbot.expr.BbotLongExpr resolveCooldownMsExpr(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr v = args.get("cooldown_ms");
        if (v == null) {
            return ctx -> -1L;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return ctx -> (long) n.value();
        }
        if (v instanceof ValueExpr.SettingRef) {
            return ctx -> -1L;
        }
        if (v instanceof ValueExpr.DynamicLocationComponent c) {
            return ctx -> (long) evalDynamicLocationAxis(ctx, c.root(), c.axis(), -1);
        }
        throw new BbotParseException(1, "cooldown_ms must be a number, $setting, or @root.location.{x|y|z}");
    }

    /**
     * Résolution stricte d'une expression numérique en V1 (utile pour les coordonnées de teleport).
     * <p>
     * Accepte :
     * - number
     * - @root.location.{x|y|z|yaw|pitch}
     * <p>
     * Refuse :
     * - $settings (BotSettings) dans ce contexte
     */
    public static fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr resolveRequiredDoubleExprStrict(Map<String, ValueExpr> args, String key) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            throw new BbotParseException(1, "teleport requires " + key + ": <number or @root.location.{x|y|z|yaw|pitch}>");
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return ctx -> n.value();
        }
        if (v instanceof ValueExpr.DynamicLocationComponent c) {
            return ctx -> evalDynamicLocationAxis(ctx, c.root(), c.axis(), Double.NaN);
        }
        if (v instanceof ValueExpr.SettingRef) {
            throw new BbotParseException(1, "teleport " + key + " cannot be a $setting ; use number or @root.location.{x|y|z|yaw|pitch}");
        }
        if (v instanceof ValueExpr.DynamicVarRef) {
            throw new BbotParseException(1, "teleport " + key + " refers to unknown/undeclared @var ; check your let declarations");
        }
        throw new BbotParseException(1, "teleport " + key + " must be a number or @root.location.{x|y|z|yaw|pitch}");
    }

    public static fr.bloup.blurpbot.bbot.expr.BbotDoubleExpr resolveOptionalDoubleExprStrict(Map<String, ValueExpr> args, String key) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return ctx -> n.value();
        }
        if (v instanceof ValueExpr.DynamicLocationComponent c) {
            return ctx -> evalDynamicLocationAxis(ctx, c.root(), c.axis(), Double.NaN);
        }
        if (v instanceof ValueExpr.SettingRef) {
            throw new BbotParseException(1, "teleport " + key + " cannot be a $setting ; use number or @root.location.{x|y|z|yaw|pitch}");
        }
        if (v instanceof ValueExpr.DynamicVarRef) {
            throw new BbotParseException(1, "teleport " + key + " refers to unknown/undeclared @var ; check your let declarations");
        }
        throw new BbotParseException(1, "teleport " + key + " must be a number or @root.location.{x|y|z|yaw|pitch}");
    }

    public static String resolveOptionalWorldName(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr v = args.get("world");
        if (v == null) {
            return null;
        }
        if (v instanceof ValueExpr.StringVal sv) {
            return sv.value();
        }
        throw new BbotParseException(1, "teleport world must be a string identifier");
    }

    public static GoalTargetSpecExpr resolveSetGoalSpecExpr(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr targetExpr = args.get("target");
        if (targetExpr == null) {
            throw new BbotParseException(1, "set_goal requires target: <kind>");
        }

        String rawTarget = goalTargetIdentifierExpr(targetExpr);
        GoalTargetKind kind = parseGoalTargetKind(rawTarget);

        BbotIntExpr priorityExpr = resolveOptionalPriorityIntExpr(args, "priority", 150);

        if (kind == GoalTargetKind.NONE) {
            return GoalTargetSpecExpr.clear();
        }

        if (kind == GoalTargetKind.LOCATION_FIXED) {
            BbotDoubleExpr xExpr = requireDoubleExpr(args, "x", Double.NaN);
            BbotDoubleExpr yExpr = requireDoubleExpr(args, "y", Double.NaN);
            BbotDoubleExpr zExpr = requireDoubleExpr(args, "z", Double.NaN);
            String world = optionalStringArg(args, "world");
            return new GoalTargetSpecExpr(kind, priorityExpr, xExpr, yExpr, zExpr, world);
        }

        // Les coordonnées ne sont pas utilisées pour les autres kinds.
        return new GoalTargetSpecExpr(kind, priorityExpr, ctx -> 0, ctx -> 0, ctx -> 0, null);
    }

    private static BbotIntExpr resolveOptionalPriorityIntExpr(Map<String, ValueExpr> args, String key, int defaultVal) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            return ctx -> defaultVal;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            int i = (int) n.value();
            return ctx -> i;
        }
        if (v instanceof ValueExpr.DynamicLocationComponent c) {
            return ctx -> (int) evalDynamicLocationAxis(ctx, c.root(), c.axis(), -1);
        }
        if (v instanceof ValueExpr.SettingRef) {
            throw new BbotParseException(1, key + " must be a number or @root.location.{x|y|z}, not a BotSetting");
        }
        throw new BbotParseException(1, key + " must be a number or @root.location.{x|y|z}");
    }

    private static BbotDoubleExpr requireDoubleExpr(Map<String, ValueExpr> args, String key, double missingValue) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            throw new BbotParseException(1, "set_goal requires " + key + " for fixed locations");
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return ctx -> n.value();
        }
        if (v instanceof ValueExpr.DynamicLocationComponent c) {
            return ctx -> evalDynamicLocationAxis(ctx, c.root(), c.axis(), missingValue);
        }
        if (v instanceof ValueExpr.SettingRef) {
            throw new BbotParseException(1, key + " cannot be a BotSetting in v1, use @root.location.{x|y|z}");
        }
        throw new BbotParseException(1, key + " must be a number or @root.location.{x|y|z}");
    }

    private static String goalTargetIdentifierExpr(ValueExpr v) throws BbotParseException {
        if (v instanceof ValueExpr.StringVal sv) {
            return sv.value();
        }
        if (v instanceof ValueExpr.DynamicVarRef dv) {
            return dv.name();
        }
        if (v instanceof ValueExpr.SettingRef ref) {
            // Compatibilité : ancien format pouvait utiliser $closest-entity (non-recognized comme setting).
            return ref.key();
        }
        throw new BbotParseException(1, "set_goal target must be an identifier/string or @dynamic-root");
    }

    private static double evalDynamicLocationAxis(BotContext ctx, String rootRaw, DynamicLocationAxis axis, double missingValue) {
        String root = normalizeRoot(rootRaw);
        LivingEntity e = switch (root) {
            case "self" -> ctx.controller().getEntity();
            case "closest_player" -> ctx.perception().getClosestPlayer();
            case "closest_entity" -> ctx.perception().getClosestLivingEntity();
            case "closest_monster" -> ctx.perception().getClosestMonster();
            case "current_target" -> ctx.perception().getTarget();
            default -> null;
        };
        if (e == null || !e.isValid()) {
            return missingValue;
        }
        double x = e.getLocation().getX();
        double y = e.getLocation().getY();
        double z = e.getLocation().getZ();
        double yaw = e.getLocation().getYaw();
        double pitch = e.getLocation().getPitch();
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
            case YAW -> yaw;
            case PITCH -> pitch;
        };
    }

    private static String normalizeRoot(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replace('-', '_');
    }

    public static boolean resolveSwingHandMain(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr v = args.get("hand");
        if (v == null) {
            return true;
        }
        String s = stringFromValueExpr(v).toLowerCase(Locale.ROOT);
        return switch (s) {
            case "main", "right", "primary" -> true;
            case "off", "off_hand", "left", "secondary" -> false;
            default -> throw new BbotParseException(1, "swing_hand hand must be main|off, got: " + s);
        };
    }

    /**
     * Feuille {@code set_goal} : {@code target:} ident / chaîne / {@code $closest-player} (référence dynamique, pas un réglage BotSettings).
     */
    public static GoalTargetSpec resolveSetGoalSpec(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr targetExpr = args.get("target");
        if (targetExpr == null) {
            throw new BbotParseException(1, "set_goal requires target: <kind>");
        }
        String raw = goalTargetIdentifier(targetExpr);
        GoalTargetKind kind = parseGoalTargetKind(raw);
        int priority = resolveOptionalInt(args, "priority", 150);
        if (kind == GoalTargetKind.NONE) {
            return GoalTargetSpec.clear();
        }
        if (kind == GoalTargetKind.LOCATION_FIXED) {
            double x = requireNumberArg(args, "x");
            double y = requireNumberArg(args, "y");
            double z = requireNumberArg(args, "z");
            String world = optionalStringArg(args, "world");
            return new GoalTargetSpec(kind, priority, x, y, z, world);
        }
        return new GoalTargetSpec(kind, priority, 0, 0, 0, null);
    }

    private static String goalTargetIdentifier(ValueExpr v) throws BbotParseException {
        if (v instanceof ValueExpr.StringVal sv) {
            return sv.value();
        }
        if (v instanceof ValueExpr.SettingRef ref) {
            return ref.key();
        }
        throw new BbotParseException(1, "set_goal target must be an identifier, string, or $dynamic-ref");
    }

    private static GoalTargetKind parseGoalTargetKind(String raw) throws BbotParseException {
        if (raw == null || raw.isBlank()) {
            throw new BbotParseException(1, "empty set_goal target");
        }
        String n = raw.toLowerCase(Locale.ROOT).trim().replace('-', '_');
        n = switch (n) {
            case "closestplayer" -> "closest_player";
            case "closestentity" -> "closest_entity";
            case "closestmonster" -> "closest_monster";
            case "currenttarget" -> "current_target";
            default -> n;
        };
        return switch (n) {
            case "none", "clear", "off" -> GoalTargetKind.NONE;
            case "closest_player" -> GoalTargetKind.CLOSEST_PLAYER;
            case "closest_entity" -> GoalTargetKind.CLOSEST_ENTITY;
            case "closest_monster" -> GoalTargetKind.CLOSEST_MONSTER;
            case "current_target" -> GoalTargetKind.CURRENT_TARGET;
            case "location", "fixed", "loc" -> GoalTargetKind.LOCATION_FIXED;
            default -> throw new BbotParseException(1, "Unknown set_goal target: " + raw);
        };
    }

    private static int resolveOptionalInt(Map<String, ValueExpr> args, String key, int defaultVal) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            return defaultVal;
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return (int) n.value();
        }
        throw new BbotParseException(1, key + " must be a number");
    }

    private static double requireNumberArg(Map<String, ValueExpr> args, String key) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            throw new BbotParseException(1, "set_goal with target location requires " + key + ":");
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return n.value();
        }
        throw new BbotParseException(1, key + " must be a number");
    }

    private static String optionalStringArg(Map<String, ValueExpr> args, String key) throws BbotParseException {
        ValueExpr v = args.get(key);
        if (v == null) {
            return null;
        }
        return stringFromValueExpr(v);
    }

    public static Pose resolvePose(Map<String, ValueExpr> args) throws BbotParseException {
        ValueExpr v = args.get("pose");
        if (v == null) {
            throw new BbotParseException(1, "entity_pose requires pose: <name>");
        }
        String raw = stringFromValueExpr(v).trim();
        String norm = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Pose.valueOf(norm);
        } catch (IllegalArgumentException ex) {
            throw new BbotParseException(1, "Unknown Pose: " + raw);
        }
    }

    public static String stringFromValueExpr(ValueExpr v) throws BbotParseException {
        if (v instanceof ValueExpr.StringVal sv) {
            return sv.value();
        }
        if (v instanceof ValueExpr.NumberVal n) {
            return String.valueOf((long) n.value());
        }
        throw new BbotParseException(1, "Expected literal string/number for this argument");
    }
}
