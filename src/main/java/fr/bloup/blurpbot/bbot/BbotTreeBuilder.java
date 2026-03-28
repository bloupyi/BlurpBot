package fr.bloup.blurpbot.bbot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import fr.bloup.blurpbot.bbot.BbotModel.ConditionBlock;
import fr.bloup.blurpbot.bbot.BbotModel.Document;
import fr.bloup.blurpbot.bbot.BbotModel.LetDirective;
import fr.bloup.blurpbot.bbot.BbotModel.LeafBlock;
import fr.bloup.blurpbot.bbot.BbotModel.NodeBlock;
import fr.bloup.blurpbot.bbot.BbotModel.ValueExpr;
import fr.bloup.blurpbot.bbot.BbotModel.WhenBlock;
import fr.bloup.blurpbot.core.BotSettingKey;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.decision.BehaviorNode;
import fr.bloup.blurpbot.decision.BehaviorTree;
import fr.bloup.blurpbot.decision.composite.SequenceNode;
import fr.bloup.blurpbot.decision.priority.PrioritySelectorNode;
import fr.bloup.blurpbot.decision.priority.PrioritizedNode;
import fr.bloup.blurpbot.decision.priority.nodes.ScriptedPriorityNode;

public final class BbotTreeBuilder {

    private BbotTreeBuilder() {
    }

    public static BehaviorTree build(Document doc) throws BbotParseException {
        List<PrioritizedNode> out = new ArrayList<>();
        for (NodeBlock nb : doc.bot().root().nodes()) {
            out.add(buildNode(nb));
        }
        return new BehaviorTree(new PrioritySelectorNode(out));
    }

    private static ScriptedPriorityNode.PrioritySource buildPrioritySource(ValueExpr expr) throws BbotParseException {
        if (expr instanceof ValueExpr.NumberVal n) {
            int p = (int) n.value();
            return ctx -> p;
        }
        if (expr instanceof ValueExpr.SettingRef ref) {
            BotSettingKey k = BotSettingKey.fromKey(ref.key());
            if (k == null || !isPriorityKey(k)) {
                throw new BbotParseException(1, "priority must be a number or $priority-attack / $priority-move / $priority-idle, got $" + ref.key());
            }
            return ctx -> priorityFromSettings(ctx.settings(), k);
        }
        throw new BbotParseException(1, "priority must be a number or setting ref, got string literal");
    }

    private static boolean isPriorityKey(BotSettingKey k) {
        return k == BotSettingKey.PRIORITY_ATTACK || k == BotSettingKey.PRIORITY_MOVE || k == BotSettingKey.PRIORITY_IDLE;
    }

    private static int priorityFromSettings(BotSettings s, BotSettingKey k) {
        return switch (k) {
            case PRIORITY_ATTACK -> s.getPriorityAttack();
            case PRIORITY_MOVE -> s.getPriorityMove();
            case PRIORITY_IDLE -> s.getPriorityIdle();
            default -> 0;
        };
    }

    private static PrioritizedNode buildNode(NodeBlock nb) throws BbotParseException {
        ScriptedPriorityNode.PrioritySource prio = buildPrioritySource(nb.priority());
        Map<String, ValueExpr> lets = nb.lets().stream()
                .collect(Collectors.toMap(LetDirective::name, LetDirective::expr, (a, b) -> b));

        BehaviorNode when = buildWhen(nb.when(), lets);
        List<BehaviorNode> leafNodes = new ArrayList<>();
        for (LeafBlock leaf : nb.leaves()) {
            leafNodes.add(buildLeaf(leaf, lets));
        }
        BehaviorNode action = leafNodes.size() == 1 ? leafNodes.get(0) : new SequenceNode(leafNodes);
        return new ScriptedPriorityNode(nb.id(), prio, when, action);
    }

    private static BehaviorNode buildWhen(WhenBlock when, Map<String, ValueExpr> lets) throws BbotParseException {
        if (when == null || when.conditions().isEmpty()) {
            return null;
        }
        List<BehaviorNode> children = new ArrayList<>();
        for (ConditionBlock c : when.conditions()) {
            children.add(buildCondition(c, lets));
        }
        return new SequenceNode(children);
    }

    private static BehaviorNode buildCondition(ConditionBlock c, Map<String, ValueExpr> lets) throws BbotParseException {
        BbotConditionFactory factory = BbotBehaviorRegistries.getConditionFactory(c.name());
        if (factory == null) {
            throw new BbotParseException(1, "Unknown condition: " + c.name());
        }
        return factory.build(expandLetsInArgs(c.args(), lets));
    }

    private static BehaviorNode buildLeaf(LeafBlock leaf, Map<String, ValueExpr> lets) throws BbotParseException {
        BbotLeafFactory factory = BbotBehaviorRegistries.getLeafFactory(leaf.name());
        if (factory == null) {
            throw new BbotParseException(1, "Unknown leaf: " + leaf.name());
        }
        return factory.build(expandLetsInArgs(leaf.args(), lets));
    }

    private static Map<String, ValueExpr> expandLetsInArgs(Map<String, ValueExpr> args, Map<String, ValueExpr> lets) {
        return args.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> expandLetsValue(e.getValue(), lets, new HashSet<>()), (a, b) -> b));
    }

    private static ValueExpr expandLetsValue(ValueExpr v, Map<String, ValueExpr> lets, Set<String> visiting) {
        if (v instanceof ValueExpr.DynamicVarRef dv) {
            if (!lets.containsKey(dv.name())) {
                return v;
            }
            if (!visiting.add(dv.name())) {
                throw new IllegalArgumentException("let cycle detected: @" + dv.name());
            }
            ValueExpr expanded = expandLetsValue(lets.get(dv.name()), lets, visiting);
            visiting.remove(dv.name());
            return expanded;
        }
        // For v1, dynamic locations are already fully evaluable runtime expressions.
        return v;
    }
}
