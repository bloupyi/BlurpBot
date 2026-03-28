package fr.bloup.blurpbot.pathfinding;

import org.bukkit.Location;

import java.util.List;

public interface PathFinder {
    List<Location> findPath(Location start, Location goal, int maxVisitedNodes);
}

