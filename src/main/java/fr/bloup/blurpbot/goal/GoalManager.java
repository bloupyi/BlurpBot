package fr.bloup.blurpbot.goal;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

import fr.bloup.blurpbot.brain.BotContext;

public class GoalManager {
    private final ScriptedGoal scriptedGoal = new ScriptedGoal();
    private final List<PrioritizedGoal> goals = new ArrayList<>();
    private PrioritizedGoal activeGoal;
    private int activeGoalPriority;

    public GoalManager() {
        register(scriptedGoal);
        register(new ChaseTargetGoal());
    }

    public final void register(PrioritizedGoal goal) {
        goals.add(goal);
    }

    public ScriptedGoal scriptedGoal() {
        return scriptedGoal;
    }

    public void clearScriptedGoal() {
        scriptedGoal.clear();
    }

    public void setScriptedGoal(GoalTargetKind kind, int priority) {
        scriptedGoal.setKind(kind, priority);
    }

    public void setScriptedGoalLocation(Location location, int priority) {
        scriptedGoal.setFixedLocation(location, priority);
    }

    /**
     * Recalcule l’objectif actif (priorité max parmi les buts dont {@link PrioritizedGoal#resolve}
     * n’est pas nul). Peut être appelé plusieurs fois par tick (ex. après {@code set_goal}).
     */
    public void update(BotContext context) {
        activeGoal = null;
        activeGoalPriority = 0;
        for (PrioritizedGoal g : goals) {
            if (g.resolve(context) == null) {
                continue;
            }
            int p = g.priority(context);
            if (activeGoal == null || p > activeGoalPriority) {
                activeGoal = g;
                activeGoalPriority = p;
            }
        }
        if (context.debugFrame() != null) {
            context.debugFrame().setActiveGoal(activeGoal == null ? "none" : activeGoal.getClass().getSimpleName());
            context.debugFrame().setActiveGoalPriority(activeGoalPriority);
        }
    }

    public PrioritizedGoal getActiveGoal() {
        return activeGoal;
    }

    public int getActiveGoalPriority() {
        return activeGoalPriority;
    }
}

