package fr.bloup.blurpbot.pathfinding;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import fr.bloup.blurpbot.physics.BlockCollisionHelper;

/**
 * Grille figée (construction sur le thread principal uniquement). L’A* async lit ces flags
 * sans appeler {@link World#getBlockAt} depuis un worker.
 * <p>
 * La boîte horizontale suit le bot : couloir le long de la ligne vers l’objectif (Bresenham),
 * avec une marge arrière et une portée avant plafonnée. Les longs trajets se font par repaths
 * successifs au lieu d’une énorme AABB départ–objectif.
 */
public final class PathSnapshot {
    static final int F_BODY_PASS = 1;
    static final int F_STANDABLE = 2;
    static final int F_LIQUID = 4;
    static final int F_CLIMB = 8;
    /** Mur plein pour la pénalité de proximité (collision et pas marche/dalle). */
    static final int F_WALL_PROX = 16;
    static final int F_STAIR_SLAB = 32;

    public final World world;
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    private final byte[] flags;

    private PathSnapshot(World world, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, byte[] flags) {
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.flags = flags;
    }

    /**
     * Capture avec boîte dynamique : couloir depuis {@code cur} vers {@code goal} (évolue à chaque repath).
     *
     * @param lateralHalf demi-largeur perpendiculaire au pas vers l’objectif (blocs)
     * @param behind      nombre de pas en arrière (opposé à l’objectif) sur la grille
     * @param aheadMax    nombre de pas vers l’objectif au plus (la ligne s’arrête avant si l’objectif est atteint)
     */
    public static PathSnapshot tryCapture(Location cur, Location goal, int lateralHalf, int behind, int aheadMax, int marginV) {
        if (cur.getWorld() == null || goal.getWorld() == null || !cur.getWorld().equals(goal.getWorld())) {
            return null;
        }
        World w = cur.getWorld();
        int cx = cur.getBlockX();
        int cy = cur.getBlockY();
        int cz = cur.getBlockZ();
        int gx = goal.getBlockX();
        int gy = goal.getBlockY();
        int gz = goal.getBlockZ();

        lateralHalf = Math.max(4, lateralHalf);
        behind = Math.max(0, behind);
        aheadMax = Math.max(8, aheadMax);

        XzBounds xz = xzBoundsAlongApproach(cx, cz, gx, gz, behind, aheadMax);
        xz.expand(lateralHalf);

        int minX = xz.minX;
        int maxX = xz.maxX;
        int minZ = xz.minZ;
        int maxZ = xz.maxZ;
        int minY = Math.min(cy, gy) - marginV;
        int maxY = Math.max(cy, gy) + marginV;

        int minH = w.getMinHeight();
        int maxH = w.getMaxHeight() - 1;
        minY = Math.max(minY, minH + 1);
        maxY = Math.min(maxY, maxH - 2);

        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int sizeY = maxY - minY + 1;

        final int capX = 64;
        final int capZ = 64;
        final int capY = 28;
        if (sizeX > capX || sizeZ > capZ || sizeY > capY) {
            int hx = capX / 2;
            int hz = capZ / 2;
            int hy = capY / 2;
            minX = cx - hx;
            maxX = cx + (capX - 1 - hx);
            minZ = cz - hz;
            maxZ = cz + (capZ - 1 - hz);
            minY = Math.max(minH + 1, cy - hy);
            maxY = Math.min(maxH - 2, cy + (capY - 1 - hy));
            sizeX = maxX - minX + 1;
            sizeZ = maxZ - minZ + 1;
            sizeY = maxY - minY + 1;
        }

        int total = sizeX * sizeY * sizeZ;
        // Après caps (65³ possible avec moitiés entières), garder une marge au-dessus de 64×64×28.
        if (total <= 0 || total > 150_000) {
            return null;
        }

        byte[] flags = new byte[total];
        // X → Z → Y : meilleure localité des chunks + remplissage séquentiel avec idx = (ix*sizeZ+iz)*sizeY+iy
        for (int x = minX; x <= maxX; x++) {
            int ix = x - minX;
            for (int z = minZ; z <= maxZ; z++) {
                int iz = z - minZ;
                int base = (ix * sizeZ + iz) * sizeY;
                for (int y = minY; y <= maxY; y++) {
                    int iy = y - minY;
                    Block b = w.getBlockAt(x, y, z);
                    flags[base + iy] = (byte) computeCellFlags(b);
                }
            }
        }
        return new PathSnapshot(w, minX, minY, minZ, sizeX, sizeY, sizeZ, flags);
    }

    /**
     * Raccourci : déduit latéral / arrière / avant à partir d’un seul paramètre horizontal (compat ancienne marge).
     */
    public static PathSnapshot tryCapture(Location cur, Location goal, int marginH, int marginV) {
        int lateral = Math.max(10, (marginH * 2 + 4) / 5);
        int back = Math.max(6, marginH / 4);
        int ahead = Math.min(72, Math.max(marginH * 2, marginH + 20));
        return tryCapture(cur, goal, lateral, back, ahead, marginV);
    }

    /** Enveloppe XZ des cellules visitées le long d’une ligne de grille (Bresenham). */
    private static final class XzBounds {
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        private boolean empty = true;

        void add(int x, int z) {
            if (empty) {
                minX = maxX = x;
                minZ = maxZ = z;
                empty = false;
            } else {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }

        void expand(int pad) {
            if (empty || pad <= 0) {
                return;
            }
            minX -= pad;
            maxX += pad;
            minZ -= pad;
            maxZ += pad;
        }
    }

    /** Couloir : arrière (symétrique de l’objectif) + avant vers l’objectif, sans AABB départ–but entière. */
    private static XzBounds xzBoundsAlongApproach(int cx, int cz, int gx, int gz, int behind, int aheadMax) {
        XzBounds b = new XzBounds();
        int farX = 2 * cx - gx;
        int farZ = 2 * cz - gz;
        bresenhamAppend(cx, cz, farX, farZ, behind + 1, b);
        bresenhamAppend(cx, cz, gx, gz, aheadMax + 1, b);
        return b;
    }

    private static void bresenhamAppend(int x0, int z0, int x1, int z1, int maxCells, XzBounds out) {
        if (maxCells <= 0) {
            return;
        }
        int x = x0;
        int z = z0;
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0);
        int sz = Integer.compare(z1, z0);
        int err = dx - dz;
        for (int n = 0; n < maxCells; n++) {
            out.add(x, z);
            if (x == x1 && z == z1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    /** Aligné sur {@link AStarPathFinder} (isBodyPassable / isStandable / etc.). Un seul appel collision par bloc (MSPT). */
    static int computeCellFlags(Block b) {
        Material t = b.getType();
        boolean blocksMov = BlockCollisionHelper.blockBlocksMovement(b);
        boolean stairSlab = BlockCollisionHelper.isStairOrSlabLike(b);
        int f = 0;
        if (b.isPassable() || isLiquidLikeBlock(b) || isClimbableName(t) || t == Material.AIR || !t.isSolid()) {
            f |= F_BODY_PASS;
        }
        if (t != Material.AIR && blocksMov) {
            f |= F_STANDABLE;
        }
        if (isLiquidLikeBlock(b)) {
            f |= F_LIQUID;
        }
        if (isClimbableName(t)) {
            f |= F_CLIMB;
        }
        if (blocksMov && !stairSlab) {
            f |= F_WALL_PROX;
        }
        if (stairSlab) {
            f |= F_STAIR_SLAB;
        }
        return f;
    }

    private static boolean isLiquidLikeBlock(Block b) {
        return b.isLiquid() || b.getType() == Material.WATER;
    }

    private static boolean isClimbableName(Material t) {
        String n = t.name();
        return n.contains("LADDER") || n.contains("VINE") || n.contains("SCAFFOLDING");
    }

    public boolean containsBlock(int x, int y, int z) {
        return x >= minX && x <= minX + sizeX - 1
                && y >= minY && y <= minY + sizeY - 1
                && z >= minZ && z <= minZ + sizeZ - 1;
    }

    /** Hors grille : traité comme solide (mur) pour ne pas sortir du snapshot par erreur. */
    public int flagAt(int x, int y, int z) {
        if (!containsBlock(x, y, z)) {
            return F_STANDABLE | F_WALL_PROX;
        }
        int ix = x - minX;
        int iy = y - minY;
        int iz = z - minZ;
        return flags[(ix * sizeZ + iz) * sizeY + iy] & 0xFF;
    }

    public int worldMinHeight() {
        return world.getMinHeight();
    }

    public int worldMaxHeight() {
        return world.getMaxHeight() - 1;
    }
}
