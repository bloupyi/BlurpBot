package fr.bloup.blurpbot.physics;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Player-like collision using {@link org.bukkit.util.VoxelShape} from blocks (slabs, stairs, etc.).
 */
public final class BlockCollisionHelper {
    public static final double HITBOX_RADIUS = 0.27;
    public static final double HITBOX_HEIGHT = 1.80;

    private BlockCollisionHelper() {
    }

    public static BoundingBox playerBounds(Location feet) {
        double x = feet.getX();
        double z = feet.getZ();
        double y = feet.getY();
        return new BoundingBox(
                x - HITBOX_RADIUS, y, z - HITBOX_RADIUS,
                x + HITBOX_RADIUS, y + HITBOX_HEIGHT, z + HITBOX_RADIUS
        );
    }

    /**
     * Blocks that should block player movement (herbe, fleurs = passables → faux).
     */
    public static boolean blockBlocksMovement(Block block) {
        if (block.getType().isAir()) {
            return false;
        }
        if (block.isPassable()) {
            return false;
        }
        org.bukkit.util.VoxelShape shape = block.getCollisionShape();
        if (shape == null || shape.getBoundingBoxes().isEmpty()) {
            return false;
        }
        return true;
    }

    public static boolean overlapsBlockCollision(BoundingBox player, Block block) {
        if (!blockBlocksMovement(block)) {
            return false;
        }
        org.bukkit.util.VoxelShape shape = block.getCollisionShape();
        for (BoundingBox bb : shape.getBoundingBoxes()) {
            BoundingBox world = bb.shift(block.getX(), block.getY(), block.getZ());
            if (world.overlaps(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collision approximativement un cube plein 1×1×1 (bloc classique à gravir en sautant).
     * Dalles, escaliers, trappes, etc. → faux.
     */
    public static boolean isFullCubeCollisionBlock(Block block) {
        if (!blockBlocksMovement(block)) {
            return false;
        }
        org.bukkit.util.VoxelShape shape = block.getCollisionShape();
        if (shape == null || shape.getBoundingBoxes().isEmpty()) {
            return false;
        }
        for (BoundingBox bb : shape.getBoundingBoxes()) {
            if (bb.getWidthX() >= 0.99 && bb.getWidthZ() >= 0.99 && bb.getHeight() >= 0.99
                    && bb.getMinX() <= 1.0e-3 && bb.getMinY() <= 1.0e-3 && bb.getMinZ() <= 1.0e-3) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collision walkable "de pas" (dalles, escaliers, trappes au sol, etc.) : priorité à la marche plutôt qu'au saut.
     */
    public static boolean isWalkableNonFullCollisionBlock(Block block) {
        if (!blockBlocksMovement(block)) {
            return false;
        }
        if (isFullCubeCollisionBlock(block)) {
            return false;
        }
        String n = block.getType().name();
        // Glass panes/blocks are not "step up like a slab" — ray UP hits can look like a walkable lip but block forward motion.
        if (n.contains("GLASS")) {
            return false;
        }
        if (n.contains("SLAB") || n.contains("STAIRS") || n.contains("TRAPDOOR")) {
            return true;
        }
        // Generic fallback: any non-full solid shape is treated as potentially walkable step geometry.
        org.bukkit.util.VoxelShape shape = block.getCollisionShape();
        if (shape == null || shape.getBoundingBoxes().isEmpty()) {
            return false;
        }
        for (BoundingBox bb : shape.getBoundingBoxes()) {
            if (bb.getHeight() < 0.99) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the player volume intersects any non-empty collision shape in the AABB footprint.
     */
    public static boolean worldVolumeOccupied(World world, BoundingBox player) {
        int minX = (int) Math.floor(player.getMinX());
        int maxX = (int) Math.floor(player.getMaxX());
        int minY = (int) Math.floor(player.getMinY());
        int maxY = (int) Math.floor(player.getMaxY());
        int minZ = (int) Math.floor(player.getMinZ());
        int maxZ = (int) Math.floor(player.getMaxZ());

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (!blockBlocksMovement(block)) {
                        continue;
                    }
                    // Ladders/vines: thin collision; entity moves inside like vanilla (not scaffolding — solid faces).
                    if (isWalkThroughClimbable(block)) {
                        continue;
                    }
                    if (isWaterAt(block)) {
                        continue;
                    }
                    if (overlapsBlockCollision(player, block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Feet Y on top of the first solid collision below {@code fromY} (handles slabs/stairs).
     */
    public static double floorFeetY(World world, double x, double z, double fromY) {
        Location start = new Location(world, x, fromY, z);
        // true = ignorer herbe / décor passable pour trouver le vrai sol (dalle, pierre…)
        RayTraceResult ray = world.rayTraceBlocks(
                start,
                new Vector(0, -1, 0),
                64.0,
                FluidCollisionMode.NEVER,
                true
        );
        if (ray == null || ray.getHitPosition() == null) {
            return -9999;
        }
        return ray.getHitPosition().getY() + 0.02;
    }

    public static boolean isWaterAt(Block block) {
        if (block.getType() == Material.WATER) {
            return true;
        }
        if (block.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged()) {
            return true;
        }
        return false;
    }

    public static boolean bodyTouchesWater(Location feet) {
        World w = feet.getWorld();
        if (w == null) {
            return false;
        }
        BoundingBox box = playerBounds(feet);
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX());
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ());
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block b = w.getBlockAt(bx, by, bz);
                    if (isWaterAt(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isClimbableBlock(Block b) {
        Material t = b.getType();
        String n = t.name();
        return n.contains("LADDER") || n.contains("VINE") || n.contains("SCAFFOLDING");
    }

    /**
     * Bloc escalable dans un voisinage 3×3 (plusieurs hauteurs) — utile pour ne pas
     * appliquer la pénalité « mur » du pathfinder ni l'évitement latéral à l'approche d'une échelle.
     */
    public static boolean nearClimbableBlock(World world, int blockX, int blockY, int blockZ) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block b = world.getBlockAt(blockX + ox, blockY + dy, blockZ + oz);
                    if (isClimbableBlock(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean nearClimbableBlock(Location feet) {
        if (feet.getWorld() == null) {
            return false;
        }
        return nearClimbableBlock(feet.getWorld(), feet.getBlockX(), feet.getBlockY(), feet.getBlockZ());
    }

    /** Marches / dalles : montée sans saut (collision partielle). */
    public static boolean isStairOrSlabLike(Block b) {
        String n = b.getType().name();
        return n.contains("STAIRS") || n.contains("SLAB");
    }

    /**
     * Boîte au niveau des pieds : collision uniquement par escaliers/dalles (pas mur plein).
     * Utilisé pour ne pas déclencher un saut auto sur une marche.
     */
    public static boolean footBoxCollidesOnlyWithStairSlabShapes(World world, BoundingBox footBox) {
        boolean any = false;
        int minX = (int) Math.floor(footBox.getMinX());
        int maxX = (int) Math.floor(footBox.getMaxX());
        int minY = (int) Math.floor(footBox.getMinY());
        int maxY = (int) Math.floor(footBox.getMaxY());
        int minZ = (int) Math.floor(footBox.getMinZ());
        int maxZ = (int) Math.floor(footBox.getMaxZ());
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (!blockBlocksMovement(block)) {
                        continue;
                    }
                    if (isWalkThroughClimbable(block)) {
                        continue;
                    }
                    if (isWaterAt(block)) {
                        continue;
                    }
                    if (!overlapsBlockCollision(footBox, block)) {
                        continue;
                    }
                    if (!isStairOrSlabLike(block)) {
                        return false;
                    }
                    any = true;
                }
            }
        }
        return any;
    }

    /**
     * Direction horizontale (normalisée) pour rester "collé" à une échelle/liane quand le waypoint est au-dessus.
     */
    public static Vector horizontalTowardOverlappingClimbable(Location feet) {
        World w = feet.getWorld();
        if (w == null) {
            return new Vector(0, 0, 0);
        }
        BoundingBox box = playerBounds(feet);
        double fx = feet.getX();
        double fz = feet.getZ();
        double best = Double.MAX_VALUE;
        Vector bestDir = new Vector(0, 0, 0);
        int minX = (int) Math.floor(box.getMinX()) - 1;
        int maxX = (int) Math.floor(box.getMaxX()) + 1;
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY()) + 1;
        int minZ = (int) Math.floor(box.getMinZ()) - 1;
        int maxZ = (int) Math.floor(box.getMaxZ()) + 1;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = w.getBlockAt(bx, by, bz);
                    if (!isClimbableBlock(block)) {
                        continue;
                    }
                    if (!overlapsClimbableShape(box, block)) {
                        continue;
                    }
                    double cx = bx + 0.5;
                    double cz = bz + 0.5;
                    double dx = cx - fx;
                    double dz = cz - fz;
                    double d2 = dx * dx + dz * dz;
                    if (d2 < best && d2 > 1.0e-8) {
                        best = d2;
                        bestDir = new Vector(dx, 0, dz);
                    }
                }
            }
        }
        if (bestDir.lengthSquared() < 1.0e-8) {
            return new Vector(0, 0, 0);
        }
        return bestDir.normalize();
    }

    /** Blocks where the player can overlap the collision (ladder / vines), unlike full blocks. */
    public static boolean isWalkThroughClimbable(Block b) {
        Material t = b.getType();
        if (t == Material.LADDER) {
            return true;
        }
        String n = t.name();
        return n.contains("VINE");
    }

    /**
     * Chevauchement avec la hitbox brute (échelle/liane sont souvent {@link Block#isPassable()} → ignorées par {@link #blockBlocksMovement}).
     */
    public static boolean overlapsClimbableShape(BoundingBox player, Block block) {
        if (!isClimbableBlock(block)) {
            return false;
        }
        org.bukkit.util.VoxelShape shape = block.getCollisionShape();
        if (shape == null || shape.getBoundingBoxes().isEmpty()) {
            return false;
        }
        for (BoundingBox bb : shape.getBoundingBoxes()) {
            BoundingBox world = bb.shift(block.getX(), block.getY(), block.getZ());
            if (world.overlaps(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if player AABB overlaps a climbable block's collision (ladder/vine/scaffolding).
     */
    public static boolean touchingClimbable(Location feet) {
        World w = feet.getWorld();
        if (w == null) {
            return false;
        }
        BoundingBox box = playerBounds(feet);
        int minX = (int) Math.floor(box.getMinX()) - 1;
        int maxX = (int) Math.floor(box.getMaxX()) + 1;
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY()) + 1;
        int minZ = (int) Math.floor(box.getMinZ()) - 1;
        int maxZ = (int) Math.floor(box.getMaxZ()) + 1;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = w.getBlockAt(bx, by, bz);
                    if (overlapsClimbableShape(box, block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
