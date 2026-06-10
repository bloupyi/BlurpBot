package fr.bloup.blurpbot.bbot;

import java.util.List;
import java.util.Map;

public final class BbotModel {
    private BbotModel() {
    }

    public record Document(int version, List<SetDirective> sets, BotBlock bot) {
    }

    public record SetDirective(String key, String value) {
    }

    public record BotBlock(String name, RootBlock root) {
    }

    public record RootBlock(List<NodeBlock> nodes) {
    }

    public record LetDirective(String name, ValueExpr expr) {
    }

    public record NodeBlock(String id, List<LetDirective> lets, ValueExpr priority, WhenBlock when, List<LeafBlock> leaves) {
    }

    public record WhenBlock(List<ConditionBlock> conditions) {
    }

    public record ConditionBlock(String name, Map<String, ValueExpr> args) {
    }

    public record LeafBlock(String name, Map<String, ValueExpr> args) {
    }

    public enum DynamicLocationAxis {
        X, Y, Z,
        /** Location yaw in Bukkit coordinates (degrees). */
        YAW,
        /** Location pitch in Bukkit coordinates (degrees). */
        PITCH
    }

    public enum ArithmeticOp {
        ADD, SUB, MUL, DIV
    }

    public sealed interface ValueExpr
            permits ValueExpr.NumberVal, ValueExpr.StringVal, ValueExpr.SettingRef,
                    ValueExpr.DynamicVarRef, ValueExpr.DynamicLocationComponent,
                    ValueExpr.UnaryNeg, ValueExpr.BinaryOp {
        record NumberVal(double value) implements ValueExpr {
        }

        record StringVal(String value) implements ValueExpr {
        }

        record SettingRef(String key) implements ValueExpr {
        }

        /**
         * Variable / identifier runtime : {@code @name}.
         * <p>
         * In practice, {@code @name} is first expanded from {@code let name = ...} inside the current node,
         * or interpreted as a runtime root (e.g. {@code @closest_entity}) for non-numeric contexts (like {@code set_goal target}).
         */
        record DynamicVarRef(String name) implements ValueExpr {
        }

        /**
         * Numeric expression : {@code @root.location.x/y/z}.
         */
        record DynamicLocationComponent(String root, DynamicLocationAxis axis) implements ValueExpr {
        }

        record UnaryNeg(ValueExpr value) implements ValueExpr {
        }

        record BinaryOp(ValueExpr left, ArithmeticOp op, ValueExpr right) implements ValueExpr {
        }
    }
}
