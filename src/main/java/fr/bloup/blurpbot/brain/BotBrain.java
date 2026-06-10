package fr.bloup.blurpbot.brain;

import org.bukkit.Location;
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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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

    /**
     * Commandes API en attente, exécutées au prochain {@link #tick()} dans la fenêtre
     * {@code beginTick}/{@code endTick} du contrôleur (jamais en « force » hors tick).
     */
    private final Queue<Consumer<BotController>> pendingActions = new ConcurrentLinkedQueue<>();
    /**
     * Commande de mouvement en attente (moveTo/stop). « Dernier gagne » : on ne joue qu'UN seul pas
     * physique par tick, même si l'API a été appelée plusieurs fois entre deux ticks.
     */
    private final AtomicReference<Consumer<BotController>> pendingMovement = new AtomicReference<>();

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
        // Les commandes API passent ici, dans la même fenêtre que l'arbre : pas de pas physique forcé.
        boolean manualMovement = drainPendingCommands();
        if (!manualMovement) {
            // Une commande de mouvement manuel a la priorité sur l'IA pour ce tick.
            tree.tick(ctx);
        }
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

    /**
     * File une action (lookAt, rotation, saut, attaque, animation…) exécutée au prochain tick.
     * Plusieurs actions par tick sont jouées dans l'ordre d'arrivée.
     */
    public void enqueueAction(Consumer<BotController> action) {
        if (action != null) {
            pendingActions.add(action);
        }
    }

    /**
     * Pose la commande de mouvement à jouer au prochain tick (« dernier gagne »). Garantit un seul
     * pas physique par tick et fait céder l'arbre de décision ce tick-là.
     */
    public void enqueueMovement(Consumer<BotController> movement) {
        if (movement != null) {
            pendingMovement.set(movement);
        }
    }

    /**
     * Exécute les commandes API en attente contre le contrôleur.
     *
     * @return {@code true} si une commande de mouvement a été jouée (l'arbre doit céder ce tick).
     */
    private boolean drainPendingCommands() {
        Consumer<BotController> action;
        while ((action = pendingActions.poll()) != null) {
            runSafely(action);
        }
        Consumer<BotController> movement = pendingMovement.getAndSet(null);
        if (movement != null) {
            runSafely(movement);
            return true;
        }
        return false;
    }

    private void runSafely(Consumer<BotController> command) {
        try {
            command.accept(controller);
        } catch (Throwable ignored) {
            // Une commande API ne doit jamais casser le tick du brain.
        }
    }

    public BotController getController() {
        return controller;
    }

    public boolean teleport(Location location) {
        return controller.teleport(location);
    }

    public Vector getFakeVelocity() {
        return controller.getFakeVelocity();
    }

    public void setFakeVelocity(Vector velocity) {
        controller.setFakeVelocity(velocity);
    }

    public void addFakeVelocity(Vector velocity) {
        controller.addFakeVelocity(velocity);
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
