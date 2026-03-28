package fr.bloup.blurpbot.pathfinding;

import fr.bloup.blurpbot.core.BotSettings;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* sur un {@link PathSnapshot} (worker thread). Aucun accès au monde Bukkit.
 */
public final class AStarSnapshotSearch {
    private static final int[][] DIRS = new int[][]{
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private AStarSnapshotSearch() {
    }

    /**
     * @return nœud final pour {@link AStarPathFinder#buildPathFromEnd}, ou {@code null} si aucun chemin exploitable.
     */
    public static PathSearchNode findPath(PathSnapshot snap, PathSearchNode start, PathSearchNode goal, int maxVisitedNodes, BotSettings settings) {
        PriorityQueue<PathSearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, PathSearchNode> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();

        PathSearchNode s = start;
        PathSearchNode g = goal;

        s.g = 0;
        s.h = heuristic(s, g);
        s.f = s.h;
        open.add(s);
        best.put(s.key(), s);
        PathSearchNode bestCandidate = s;
        double bestCandidateScore = candidateScore(s, g);

        int visited = 0;
        while (!open.isEmpty() && visited < maxVisitedNodes) {
            PathSearchNode cur = open.poll();
            if (!closed.add(cur.key())) continue;
            visited++;

            double curScore = candidateScore(cur, g);
            if (curScore < bestCandidateScore) {
                bestCandidateScore = curScore;
                bestCandidate = cur;
            }

            if (distance2D(cur, g) <= 1 && Math.abs(cur.y - g.y) <= 1) {
                return cur;
            }

            for (int[] d : DIRS) {
                PathSearchNode next = stepTo(snap, settings, cur, d[0], d[1]);
                if (next == null) continue;
                if (closed.contains(next.key())) continue;

                double cost = cur.g
                        + movementCost(d[0], d[1])
                        + elevationCost(cur.y, next.y)
                        + wallProximityPenalty(snap, settings, next.x, next.y, next.z)
                        + dangerPenalty(snap, settings, next.x, next.y, next.z, cur.y)
                        + climbProgressAdjustment(snap, settings, cur, next, g);
                PathSearchNode known = best.get(next.key());
                if (known == null || cost < known.g) {
                    if (known == null) known = next;
                    known.g = cost;
                    known.h = heuristic(known, g);
                    known.f = known.g + known.h;
                    known.parent = cur;
                    best.put(known.key(), known);
                    open.add(known);
                }
            }

            for (int dy : new int[]{1, -1}) {
                PathSearchNode vert = stepVertical(snap, cur, dy);
                if (vert == null) continue;
                if (closed.contains(vert.key())) continue;

                double cost = cur.g
                        + 0.85
                        + elevationCost(cur.y, vert.y)
                        + wallProximityPenalty(snap, settings, vert.x, vert.y, vert.z)
                        + dangerPenalty(snap, settings, vert.x, vert.y, vert.z, cur.y)
                        + climbProgressAdjustment(snap, settings, cur, vert, g);
                PathSearchNode known = best.get(vert.key());
                if (known == null || cost < known.g) {
                    if (known == null) known = vert;
                    known.g = cost;
                    known.h = heuristic(known, g);
                    known.f = known.g + known.h;
                    known.parent = cur;
                    best.put(known.key(), known);
                    open.add(known);
                }
            }
        }

        if (bestCandidate != null && bestCandidate.parent != null) {
            if (candidateScore(bestCandidate, g) + 0.15 < candidateScore(s, g)) {
                return bestCandidate;
            }
        }
        return null;
    }

    private static PathSearchNode stepVertical(PathSnapshot snap, PathSearchNode cur, int dy) {
        if (dy == 0) {
            return null;
        }
        int ny = cur.y + dy;
        int minY = snap.worldMinHeight();
        int maxY = snap.worldMaxHeight();
        if (ny <= minY || ny + 1 >= maxY) {
            return null;
        }
        if (!isWalkable(snap, cur.x, ny, cur.z)) {
            return null;
        }
        if (!hasVerticalClimbSupport(snap, cur.x, cur.y, cur.z, ny)) {
            return null;
        }
        if (!canTraverseSpecial(snap, cur.x, cur.y, cur.z, cur.x, ny, cur.z)) {
            return null;
        }
        return new PathSearchNode(cur.x, ny, cur.z);
    }

    private static boolean hasVerticalClimbSupport(PathSnapshot snap, int x, int y, int z, int ny) {
        return climb(snap, x, y, z) || climb(snap, x, y - 1, z) || climb(snap, x, y + 1, z)
                || climb(snap, x, ny, z) || climb(snap, x, ny - 1, z) || climb(snap, x, ny + 1, z);
    }

    private static PathSearchNode stepTo(PathSnapshot snap, BotSettings settings, PathSearchNode cur, int dx, int dz) {
        int nx = cur.x + dx;
        int nz = cur.z + dz;
        if (dx != 0 && dz != 0 && !canPassDiagonal(snap, cur.x, cur.y, cur.z, dx, dz)) {
            return null;
        }

        int dyMin = -settings.getPathMaxStepDown();
        int dyMax = settings.getPathMaxStepUp();
        if (dyMin <= 0 && dyMax >= 0) {
            int ny = cur.y;
            if (isWalkable(snap, nx, ny, nz) && canTraverseSpecial(snap, cur.x, cur.y, cur.z, nx, ny, nz)) {
                return new PathSearchNode(nx, ny, nz);
            }
        }

        PathSearchNode best = null;
        double bestTransitionCost = Double.MAX_VALUE;
        for (int dy = dyMin; dy <= dyMax; dy++) {
            if (dy == 0) {
                continue;
            }
            int ny = cur.y + dy;
            if (!isWalkable(snap, nx, ny, nz)) continue;
            if (!canTraverseSpecial(snap, cur.x, cur.y, cur.z, nx, ny, nz)) continue;
            double transition = Math.abs(dy) + (dy > 0 ? 0.6 : 0.2);
            if (transition < bestTransitionCost) {
                bestTransitionCost = transition;
                best = new PathSearchNode(nx, ny, nz);
            }
        }
        return best;
    }

    private static boolean isWalkable(PathSnapshot snap, int x, int y, int z) {
        int minY = snap.worldMinHeight();
        int maxY = snap.worldMaxHeight();
        if (y <= minY || y + 1 >= maxY) return false;

        int feet = snap.flagAt(x, y, z);
        int head = snap.flagAt(x, y + 1, z);
        int below = snap.flagAt(x, y - 1, z);
        int below2 = snap.flagAt(x, y - 2, z);

        if (bodyPass(feet) && bodyPass(head) && stand(below)) return true;
        if (liquid(feet) && bodyPass(head)) return true;
        if (climbBits(feet) || climbBits(below) || climbBits(below2)) {
            return bodyPass(head) || climbBits(head);
        }
        return false;
    }

    private static boolean bodyPass(int f) {
        return (f & PathSnapshot.F_BODY_PASS) != 0;
    }

    private static boolean stand(int f) {
        return (f & PathSnapshot.F_STANDABLE) != 0;
    }

    private static boolean liquid(int f) {
        return (f & PathSnapshot.F_LIQUID) != 0;
    }

    private static boolean climb(PathSnapshot snap, int x, int y, int z) {
        return (snap.flagAt(x, y, z) & PathSnapshot.F_CLIMB) != 0;
    }

    private static boolean canPassDiagonal(PathSnapshot snap, int x, int y, int z, int dx, int dz) {
        return isWalkable(snap, x + dx, y, z) && isWalkable(snap, x, y, z + dz);
    }

    private static boolean canTraverseSpecial(PathSnapshot snap, int x0, int y0, int z0, int x1, int y1, int z1) {
        int fromFeet = snap.flagAt(x0, y0, z0);
        int toFeet = snap.flagAt(x1, y1, z1);

        if (climbBits(fromFeet) || climbBits(toFeet)) {
            return Math.abs(y1 - y0) <= 1;
        }
        if (liquid(fromFeet) || liquid(toFeet)) {
            return Math.abs(y1 - y0) <= 2;
        }
        return true;
    }

    private static boolean climbBits(int f) {
        return (f & PathSnapshot.F_CLIMB) != 0;
    }

    private static double movementCost(int dx, int dz) {
        return (dx != 0 && dz != 0) ? 1.4142 : 1.0;
    }

    private static double elevationCost(int fromY, int toY) {
        int dy = toY - fromY;
        if (dy > 0) return dy * 0.45;
        if (dy < 0) return Math.abs(dy) * 0.20;
        return 0.0;
    }

    private static boolean quickNearClimbableColumn(PathSnapshot snap, int x, int y, int z) {
        for (int dy = -1; dy <= 1; dy++) {
            int yy = y + dy;
            if (climb(snap, x + 1, yy, z)) return true;
            if (climb(snap, x - 1, yy, z)) return true;
            if (climb(snap, x, yy, z + 1)) return true;
            if (climb(snap, x, yy, z - 1)) return true;
        }
        return false;
    }

    private static double wallProximityPenalty(PathSnapshot snap, BotSettings settings, int x, int y, int z) {
        int feet = snap.flagAt(x, y, z);
        int below = snap.flagAt(x, y - 1, z);
        if (climbBits(feet) || climbBits(below)) {
            return 0.0;
        }
        if (quickNearClimbableColumn(snap, x, y, z)) {
            return 0.0;
        }
        int solids = 0;
        int[][] card = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : card) {
            int ox = d[0];
            int oz = d[1];
            int aroundFeet = snap.flagAt(x + ox, y, z + oz);
            int head = snap.flagAt(x + ox, y + 1, z + oz);
            if (countsAsWallProximitySolid(aroundFeet) || countsAsWallProximitySolid(head)) {
                solids++;
            }
        }
        double factor = 1.0;
        if ((below & PathSnapshot.F_STAIR_SLAB) != 0) {
            factor = 0.45;
        }
        return solids * 2.0 * settings.getPathWallProximityPenalty() * factor;
    }

    private static boolean countsAsWallProximitySolid(int f) {
        return (f & PathSnapshot.F_WALL_PROX) != 0;
    }

    private static double dangerPenalty(PathSnapshot snap, BotSettings settings, int x, int y, int z, int fromY) {
        double penalty = 0.0;

        int depthToGround = depthToGround(snap, x, y, z, 8);
        if (depthToGround < 0) {
            penalty += settings.getPathVoidPenalty();
        } else if (depthToGround > 2) {
            penalty += (depthToGround - 2) * 0.22;
        }

        int drop = fromY - y;
        if (drop > 1) {
            penalty += drop * settings.getPathFallRiskPenalty();
        }

        return penalty;
    }

    private static int depthToGround(PathSnapshot snap, int x, int y, int z, int maxDepth) {
        for (int i = 1; i <= maxDepth; i++) {
            int f = snap.flagAt(x, y - i, z);
            if (stand(f)) return i;
        }
        return -1;
    }

    private static double heuristic(PathSearchNode a, PathSearchNode b) {
        return Math.hypot(a.x - b.x, a.z - b.z) + Math.abs(a.y - b.y) * 0.4;
    }

    private static double candidateScore(PathSearchNode a, PathSearchNode goal) {
        double dxz = Math.hypot(a.x - goal.x, a.z - goal.z);
        double dy = Math.abs(a.y - goal.y);
        return dxz + dy * 0.7;
    }

    private static double climbProgressAdjustment(PathSnapshot snap, BotSettings settings, PathSearchNode from, PathSearchNode to, PathSearchNode goal) {
        if (goal.y <= from.y) {
            return 0.0;
        }
        if (to.y <= from.y) {
            return 0.0;
        }
        int fromFeet = snap.flagAt(from.x, from.y, from.z);
        int toFeet = snap.flagAt(to.x, to.y, to.z);
        int toBelow = snap.flagAt(to.x, to.y - 1, to.z);
        if (climbBits(fromFeet) || climbBits(toFeet) || climbBits(toBelow)) {
            return -settings.getPathClimbUpwardBonus();
        }
        return 0.0;
    }

    private static int distance2D(PathSearchNode a, PathSearchNode b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }
}
