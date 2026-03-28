package fr.bloup.blurpbot.brain;

import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import fr.bloup.blurpbot.bbot.BotBehaviorTreeLoader;
import fr.bloup.blurpbot.controller.BotController;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.debug.BotDebugFrame;
import fr.bloup.blurpbot.decision.BehaviorTree;
import fr.bloup.blurpbot.goal.GoalManager;
import fr.bloup.blurpbot.perception.BotPerception;
import fr.bloup.blurpbot.perception.PerceptionUpdater;

public class BotBrain {
    private final BotPerception perception;
    private final BotController controller;
    private final PerceptionUpdater perceptionUpdater;
    private BehaviorTree tree;
    private final LivingEntity entity;
    private final BotSettings settings;
    private final GoalManager goalManager;
    private boolean debugEnabled = false;
    private BotDebugFrame lastDebugFrame = new BotDebugFrame();

    private BotBrain(LivingEntity entity, BotPerception perception, BotController controller, PerceptionUpdater perceptionUpdater, BehaviorTree tree, BotSettings settings, GoalManager goalManager) {
        this.entity = entity;
        this.perception = perception;
        this.controller = controller;
        this.perceptionUpdater = perceptionUpdater;
        this.tree = tree;
        this.settings = settings;
        this.goalManager = goalManager;
    }

    public static BotBrain basic(LivingEntity entity, BotSettings settings) {
        return basic(entity, settings, null);
    }

    public static BotBrain basic(LivingEntity entity, BotSettings settings, JavaPlugin plugin) {
        BotPerception perception = new BotPerception();
        BotController controller = new BotController(entity, settings, plugin);
        PerceptionUpdater updater = new PerceptionUpdater(16.0);
        GoalManager goalManager = new GoalManager();

        BehaviorTree tree = BotBehaviorTreeLoader.loadOrBuiltin(plugin, settings);

        return new BotBrain(entity, perception, controller, updater, tree, settings, goalManager);
    }

    public void tick() {
        controller.beginTick();
        perceptionUpdater.update(entity, perception);
        BotDebugFrame frame = debugEnabled ? new BotDebugFrame() : null;
        BotContext ctx = new BotContext(perception, controller, settings, goalManager, frame);
        goalManager.update(ctx);
        tree.tick(ctx);
        controller.endTick();
        if (debugEnabled && frame != null) {
            var path = controller.getCurrentPathSnapshot();
            frame.setPathPoints(path);
            frame.setPathSize(path.size());
            frame.setPathIndex(controller.getCurrentPathIndex());
            frame.setStuckTicks(controller.getStuckTicks());
            frame.setUnstuckTarget(controller.getUnstuckTarget());
            if (!path.isEmpty()) {
                int idx = Math.max(0, Math.min(controller.getCurrentPathIndex(), path.size() - 1));
                frame.setWaypoint(path.get(idx));
            }
            this.lastDebugFrame = frame;
        }
    }

    public void applyImpulse(Vector impulse) {
        controller.applyExternalImpulse(impulse);
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public BotDebugFrame getLastDebugFrame() {
        return lastDebugFrame;
    }

    public GoalManager getGoalManager() {
        return goalManager;
    }

    public void setBehaviorTree(BehaviorTree newTree) {
        this.tree = newTree != null ? newTree : BotBehaviorTreeLoader.builtinDefault(settings);
    }
}
