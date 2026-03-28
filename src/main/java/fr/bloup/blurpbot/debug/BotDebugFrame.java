package fr.bloup.blurpbot.debug;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

import java.util.List;

@Getter
@Setter
public class BotDebugFrame {
    private String selectedAction = "none";
    private int selectedActionPriority = 0;
    private String activeGoal = "none";
    private int activeGoalPriority = 0;
    private int pathSize = 0;
    private int pathIndex = 0;
    private int stuckTicks = 0;
    private Location waypoint;
    private Location unstuckTarget;
    private List<Location> pathPoints = List.of();
}

