package fr.bloup.blurpbot.pathfinding;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.physics.BlockCollisionHelper;

import java.util.*;

public class AStarPathFinder implements PathFinder {
    private final BotSettings settings;

    private static final int[][] DIRS = new int[][]{
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    public AStarPathFinder(BotSettings settings) {
        this.settings = settings;
    }

    @Override
    public List<Location> findPath(Location start, Location goal, int maxVisitedNodes) {
        if (start.getWorld() == null || goal.getWorld() == null) return List.of();
        if (!start.getWorld().equals(goal.getWorld())) return List.of();
        World world = start.getWorld();

        PathSearchNode s = fromLocation(start);
        PathSearchNode g = fromLocation(goal);
        PriorityQueue<PathSearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Long, PathSearchNode> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();

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
                return buildPath(world, cur);
            }

            for (int[] d : DIRS) {
                PathSearchNode next = stepTo(world, cur, d[0], d[1]);
                if (next == null) continue;
                if (closed.contains(next.key())) continue;

                double cost = cur.g
                        + movementCost(d[0], d[1])
                        + elevationCost(cur.y, next.y)
                        + wallProximityPenalty(world, next.x, next.y, next.z)
                        + dangerPenalty(world, next.x, next.y, next.z, cur.y)
                        + climbProgressAdjustment(world, cur, next, g);
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

            // Même colonne XZ (échelles, lianes) — impossible avec seulement les 8 voisins horizontaux.
            for (int dy : new int[]{1, -1}) {
                PathSearchNode vert = stepVertical(world, cur, dy);
                if (vert == null) continue;
                if (closed.contains(vert.key())) continue;

                double cost = cur.g
                        + 0.85
                        + elevationCost(cur.y, vert.y)
                        + wallProximityPenalty(world, vert.x, vert.y, vert.z)
                        + dangerPenalty(world, vert.x, vert.y, vert.z, cur.y)
                        + climbProgressAdjustment(world, cur, vert, g);
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
        // Fallback: partial path only if we actually got closer to the goal than the start.
        // Otherwise the bot/debug would show a misleading long jump to an arbitrary explored node.
        if (bestCandidate != null && bestCandidate.parent != null) {
            if (candidateScore(bestCandidate, g) + 0.15 < candidateScore(s, g)) {
                return buildPath(world, bestCandidate);
            }
        }
        return List.of();
    }

    private PathSearchNode stepVertical(World world, PathSearchNode cur, int dy) {
        if (dy == 0) {
            return null;
        }
        int ny = cur.y + dy;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        if (ny <= minY || ny + 1 >= maxY) {
            return null;
        }
        if (!isWalkable(world, cur.x, ny, cur.z)) {
            return null;
        }
        if (!hasVerticalClimbSupport(world, cur.x, cur.y, cur.z, ny)) {
            return null;
        }
        if (!canTraverseSpecial(world, cur.x, cur.y, cur.z, cur.x, ny, cur.z)) {
            return null;
        }
        return new PathSearchNode(cur.x, ny, cur.z);
    }

    /**
     * Au moins un bloc échelle/liane sur la colonne (source ou destination) pour éviter le "vol" vertical.
     */
    private boolean hasVerticalClimbSupport(World world, int x, int y, int z, int ny) {
        Block fromFeet = world.getBlockAt(x, y, z);
        Block fromBelow = world.getBlockAt(x, y - 1, z);
        Block fromHead = world.getBlockAt(x, y + 1, z);
        Block toFeet = world.getBlockAt(x, ny, z);
        Block toBelow = world.getBlockAt(x, ny - 1, z);
        Block toHead = world.getBlockAt(x, ny + 1, z);
        return isClimbable(fromFeet) || isClimbable(fromBelow) || isClimbable(fromHead)
                || isClimbable(toFeet) || isClimbable(toBelow) || isClimbable(toHead);
    }

    private PathSearchNode stepTo(World world, PathSearchNode cur, int dx, int dz) {
        int nx = cur.x + dx;
        int nz = cur.z + dz;
        if (dx != 0 && dz != 0 && !canPassDiagonal(world, cur.x, cur.y, cur.z, dx, dz)) {
            return null;
        }

        int dyMin = -settings.getPathMaxStepDown();
        int dyMax = settings.getPathMaxStepUp();
        // Même hauteur : coût de transition minimal (0) — cas dominant en terrain plat.
        if (dyMin <= 0 && dyMax >= 0) {
            int ny = cur.y;
            if (isWalkable(world, nx, ny, nz) && canTraverseSpecial(world, cur.x, cur.y, cur.z, nx, ny, nz)) {
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
            if (!isWalkable(world, nx, ny, nz)) continue;
            if (!canTraverseSpecial(world, cur.x, cur.y, cur.z, nx, ny, nz)) continue;
            double transition = Math.abs(dy) + (dy > 0 ? 0.6 : 0.2);
            if (transition < bestTransitionCost) {
                bestTransitionCost = transition;
                best = new PathSearchNode(nx, ny, nz);
            }
        }
        return best;
    }

    private boolean isWalkable(World world, int x, int y, int z) {
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        if (y <= minY || y + 1 >= maxY) return false;

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        Block below2 = world.getBlockAt(x, y - 2, z);

        // Normal walkable
        if (isBodyPassable(feet) && isBodyPassable(head) && isStandable(below)) return true;
        // Shallow-liquid/swim support
        if (isLiquidLike(feet) && isBodyPassable(head)) return true;
        // Ladder support
        if (isClimbable(feet) || isClimbable(below) || isClimbable(below2)) {
            return isBodyPassable(head) || isClimbable(head);
        }
        return false;
    }

    private boolean canPassDiagonal(World world, int x, int y, int z, int dx, int dz) {
        // Prevent corner-cutting through diagonal gaps touching solid blocks.
        return isWalkable(world, x + dx, y, z) && isWalkable(world, x, y, z + dz);
    }

    private boolean isBodyPassable(Block b) {
        Material t = b.getType();
        return b.isPassable() || isLiquidLike(b) || isClimbable(b) || t == Material.AIR || !t.isSolid();
    }

    private boolean isStandable(Block b) {
        if (b.getType() == Material.AIR) return false;
        return BlockCollisionHelper.blockBlocksMovement(b);
    }

    private boolean isLiquidLike(Block b) {
        return b.isLiquid() || b.getType() == Material.WATER;
    }

    private boolean isClimbable(Block b) {
        Material t = b.getType();
        String n = t.name();
        return n.contains("LADDER") || n.contains("VINE") || n.contains("SCAFFOLDING");
    }

    private boolean canTraverseSpecial(World world, int x0, int y0, int z0, int x1, int y1, int z1) {
        Block fromFeet = world.getBlockAt(x0, y0, z0);
        Block toFeet = world.getBlockAt(x1, y1, z1);

        // Allow climbing transitions on ladders/vines/scaffolding.
        if (isClimbable(fromFeet) || isClimbable(toFeet)) {
            return Math.abs(y1 - y0) <= 1;
        }
        // Water transitions are allowed; prefer not to drop too hard while in water.
        if (isLiquidLike(fromFeet) || isLiquidLike(toFeet)) {
            return Math.abs(y1 - y0) <= 2;
        }
        return true;
    }

    private double movementCost(int dx, int dz) {
        return (dx != 0 && dz != 0) ? 1.4142 : 1.0;
    }

    private double elevationCost(int fromY, int toY) {
        int dy = toY - fromY;
        if (dy > 0) return dy * 0.45;
        if (dy < 0) return Math.abs(dy) * 0.20;
        return 0.0;
    }

    /**
     * Détection échelle proche : remplace {@link BlockCollisionHelper#nearClimbableBlock(World, int, int, int)}
     * (3×3×4 = 36 getBlockAt) par 12 sondes cardinales sur 3 hauteurs — même idée, beaucoup moins cher (Spark).
     */
    private boolean quickNearClimbableColumn(World world, int x, int y, int z) {
        for (int dy = -1; dy <= 1; dy++) {
            int yy = y + dy;
            if (isClimbable(world.getBlockAt(x + 1, yy, z))) return true;
            if (isClimbable(world.getBlockAt(x - 1, yy, z))) return true;
            if (isClimbable(world.getBlockAt(x, yy, z + 1))) return true;
            if (isClimbable(world.getBlockAt(x, yy, z - 1))) return true;
        }
        return false;
    }

    private double wallProximityPenalty(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block below = world.getBlockAt(x, y - 1, z);
        if (isClimbable(feet) || isClimbable(below)) {
            return 0.0;
        }
        if (quickNearClimbableColumn(world, x, y, z)) {
            return 0.0;
        }
        // 4 voisins cardinaux × pieds + tête (8 getBlockAt au lieu de 16 sur la couronne 3×3).
        int solids = 0;
        int[][] card = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : card) {
            int ox = d[0];
            int oz = d[1];
            Block aroundFeet = world.getBlockAt(x + ox, y, z + oz);
            Block head = world.getBlockAt(x + ox, y + 1, z + oz);
            if (countsAsWallProximitySolid(aroundFeet) || countsAsWallProximitySolid(head)) {
                solids++;
            }
        }
        double factor = 1.0;
        if (BlockCollisionHelper.isStairOrSlabLike(below)) {
            factor = 0.45;
        }
        // Même poids qu’avant ~8 cellules utiles sur la couronne ; 4 directions → même échelle de pénalité.
        return solids * 2.0 * settings.getPathWallProximityPenalty() * factor;
    }

    /** Blocs pleins qui resserrent le passage ; pas les marches/dalles (collisions partielles). */
    private boolean countsAsWallProximitySolid(Block b) {
        if (!BlockCollisionHelper.blockBlocksMovement(b)) {
            return false;
        }
        return !BlockCollisionHelper.isStairOrSlabLike(b);
    }

    /**
     * Pénalités légères (blocs uniquement). Ne pas appeler {@link World#getNearbyEntities} ici :
     * c’était exécuté pour chaque nœud exploré → explosion du coût (MSPT) surtout quand le bot est bloqué.
     * La réglure {@code path-enemy-proximity-penalty} reste dans la config pour d’éventuels usages futurs
     * ou tuning hors A* (perception, objectifs).
     */
    private double dangerPenalty(World world, int x, int y, int z, int fromY) {
        double penalty = 0.0;

        // Void / no-ground penalty (discourage edges and drops).
        int depthToGround = depthToGround(world, x, y, z, 8);
        if (depthToGround < 0) {
            penalty += settings.getPathVoidPenalty();
        } else if (depthToGround > 2) {
            penalty += (depthToGround - 2) * 0.22;
        }

        // Falling risk penalty.
        int drop = fromY - y;
        if (drop > 1) {
            penalty += drop * settings.getPathFallRiskPenalty();
        }

        return penalty;
    }

    private int depthToGround(World world, int x, int y, int z, int maxDepth) {
        for (int i = 1; i <= maxDepth; i++) {
            Block b = world.getBlockAt(x, y - i, z);
            if (isStandable(b)) return i;
        }
        return -1;
    }

    private double heuristic(PathSearchNode a, PathSearchNode b) {
        return Math.hypot(a.x - b.x, a.z - b.z) + Math.abs(a.y - b.y) * 0.4;
    }

    /**
     * Lower is better: prioritize nodes close in XZ and Y to goal.
     */
    private double candidateScore(PathSearchNode a, PathSearchNode goal) {
        double dxz = Math.hypot(a.x - goal.x, a.z - goal.z);
        double dy = Math.abs(a.y - goal.y);
        return dxz + dy * 0.7;
    }

    /**
     * Encourage climbing when goal is above and transition advances upward on climbables.
     */
    private double climbProgressAdjustment(World world, PathSearchNode from, PathSearchNode to, PathSearchNode goal) {
        if (goal.y <= from.y) {
            return 0.0;
        }
        if (to.y <= from.y) {
            return 0.0;
        }
        Block fromFeet = world.getBlockAt(from.x, from.y, from.z);
        Block toFeet = world.getBlockAt(to.x, to.y, to.z);
        Block toBelow = world.getBlockAt(to.x, to.y - 1, to.z);
        if (isClimbable(fromFeet) || isClimbable(toFeet) || isClimbable(toBelow)) {
            return -settings.getPathClimbUpwardBonus();
        }
        return 0.0;
    }

    private int distance2D(PathSearchNode a, PathSearchNode b) {
        return Math.abs(a.x - b.x) + Math.abs(a.z - b.z);
    }

    private PathSearchNode fromLocation(Location loc) {
        return new PathSearchNode(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Construit les waypoints sur le thread principal. {@link BlockCollisionHelper#floorFeetY} par nœud
     * aligne les Y sur dalles/trappes/marches — nécessaire pour éviter sauts en boucle côté contrôleur
     * ({@code updateJumpIntentFromPath} / steering). Coût surtout au repath, pas chaque tick.
     */
    public List<Location> buildPathFromEnd(World world, PathSearchNode end) {
        return buildPath(world, end);
    }

    private List<Location> buildPath(World world, PathSearchNode end) {
        List<Location> reversed = new ArrayList<>();
        PathSearchNode n = end;
        while (n != null) {
            double feetY = BlockCollisionHelper.floorFeetY(world, n.x + 0.5, n.z + 0.5, n.y + 2.5);
            if (feetY < -1000) {
                feetY = n.y;
            }
            reversed.add(new Location(world, n.x + 0.5, feetY, n.z + 0.5));
            n = n.parent;
        }
        Collections.reverse(reversed);
        List<Location> simplified = reversed.size() < 3 ? reversed : simplify(reversed);
        return densifyLongSegments(simplified, world, 3.2);
    }

    /**
     * Collinear simplification removes intermediate nodes on long straight runs, leaving only endpoints.
     * That breaks debug visualization (huge gaps) and makes steering lookahead unreliable.
     */
    private List<Location> densifyLongSegments(List<Location> path, World world, double maxHoriz) {
        if (path.size() < 2) {
            return path;
        }
        List<Location> dense = new ArrayList<>();
        dense.add(path.getFirst());
        for (int i = 1; i < path.size(); i++) {
            Location a = dense.get(dense.size() - 1);
            Location b = path.get(i);
            double dx = b.getX() - a.getX();
            double dz = b.getZ() - a.getZ();
            double horiz = Math.hypot(dx, dz);
            if (horiz > maxHoriz) {
                int steps = (int) Math.ceil(horiz / maxHoriz);
                for (int s = 1; s < steps; s++) {
                    double t = s / (double) steps;
                    double ix = a.getX() + dx * t;
                    double iz = a.getZ() + dz * t;
                    double iy = a.getY() + (b.getY() - a.getY()) * t;
                    double feetY = BlockCollisionHelper.floorFeetY(world, ix, iz, iy + 2.5);
                    if (feetY < -1000) {
                        feetY = iy;
                    }
                    dense.add(new Location(world, ix, feetY, iz));
                }
            }
            dense.add(b);
        }
        return dense;
    }

    private List<Location> simplify(List<Location> path) {
        if (path.size() < 3) return path;
        List<Location> out = new ArrayList<>();
        out.add(path.getFirst());
        Vector lastDir = null;
        for (int i = 1; i < path.size(); i++) {
            Location prev = path.get(i - 1);
            Location cur = path.get(i);
            Vector full = cur.toVector().subtract(prev.toVector());
            Vector horiz = full.clone().setY(0);
            boolean verticalOnly = horiz.lengthSquared() < 1.0e-8 && Math.abs(full.getY()) > 1.0e-3;
            if (verticalOnly) {
                out.add(prev);
                lastDir = null;
                continue;
            }
            if (horiz.lengthSquared() < 1.0e-8) {
                out.add(prev);
                continue;
            }
            Vector dir = horiz.normalize();
            if (lastDir == null || dir.dot(lastDir) < 0.999) {
                out.add(prev);
                lastDir = dir;
            }
        }
        out.add(path.getLast());
        return out;
    }
}

