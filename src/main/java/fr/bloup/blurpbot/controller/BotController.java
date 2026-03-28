package fr.bloup.blurpbot.controller;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.nms.NmsFakePlayerFactory;
import fr.bloup.blurpbot.pathfinding.AStarPathFinder;
import fr.bloup.blurpbot.pathfinding.AStarSnapshotSearch;
import fr.bloup.blurpbot.pathfinding.PathSearchNode;
import fr.bloup.blurpbot.pathfinding.PathSnapshot;
import fr.bloup.blurpbot.pathfinding.PathfindingAsync;
import fr.bloup.blurpbot.physics.BlockCollisionHelper;

public class BotController {
    private final LivingEntity entity;
    private final BotSettings settings;
    private long lastAttackAtMs = 0L;
    private long nextStrafeSwitchAtMs = 0L;
    private int strafeSign = 1;
    private Vector simulatedHorizontalVelocity = new Vector(0, 0, 0);
    private double simulatedVerticalVelocity = 0.0;
    private boolean movedThisTick = false;
    private int knockbackPriorityTicks = 0;
    private final AStarPathFinder pathFinder;
    private final JavaPlugin plugin;
    /** Incrémenté à chaque nouvelle recherche async ; les callbacks obsolètes sont ignorés. */
    private volatile int pathSearchGeneration = 0;
    private volatile int pathInflight = 0;
    private List<Location> currentPath = List.of();
    private int currentPathIndex = 0;
    private long nextRepathAtMs = 0L;
    private Location lastGoal = null;
    private Location lastProgressLoc = null;
    private int stuckTicks = 0;
    private Location unstuckTarget = null;
    private long unstuckUntilMs = 0L;
    private int jumpIntentTicks = 0;
    private int manualJumpTicks = 0;
    /** True while steering toward a lateral unstuck detour — no obstacle/path jumps (avoids super-jumps with NMS velocity). */
    private boolean lateralUnstuckMode = false;
    /**
     * While resolving jump arc substeps: do not stack {@link #tryHorizontalStepUp} / snap onto the same tick's jump impulse
     * (otherwise two vertical boosts in one physics pass → "super jump").
     */
    private boolean suppressWalkStepDuringJumpArc = false;
    /** At most one ground jump impulse per server tick (guards path physics + endTick physics both firing). */
    private boolean jumpConsumedThisTick = false;
    /**
     * Ticks remaining where we ignore upward velocity samples from the entity (NMS + client sync)
     * and cap vertical speed — must cover the whole jump arc, not only takeoff ({@link #manualJumpTicks} was too short → super-jumps).
     */
    private int upwardVelocityGuardTicks = 0;

    /**
     * Sondes en avant pour anticiper un mur avant d'être "collé" : une seule distance (~0,5–0,7)
     * laisse le volume libre tant qu'on est encore loin du bloc plein.
     */
    private static final double[] JUMP_OBSTACLE_PROBE_DISTANCES = {
            0.35, 0.52, 0.68, 0.85, 1.02, 1.2, 1.38, 1.55
    };
    /** Max step-up that should be crossed without jumping (slabs, trapdoors on floor, stairs slope). */
    private static final double MAX_WALK_UP_WITHOUT_JUMP = 0.62;
    private static final double[] STEP_UP_TEST_HEIGHTS = {0.20, 0.32, 0.45, 0.58, 0.62};

    /**
     * Raycasts horizontaux : si on touche le dessus d'un bloc (face UP), on monte en marchant, pas en sautant.
     * Plusieurs hauteurs car une dalle peut être touchée sur le côté à basse hauteur.
     */
    private static final double[] JUMP_RAY_HEIGHT_OFFSETS = {0.05, 0.12, 0.22, 0.35, 0.48, 0.58};
    /**
     * Légère élévation de la hitbox pour les tests de collision : évite les faux positifs
     * quand les pieds sont exactement sur le plan supérieur d'un bloc (herbe → verre, etc.).
     */
    private static final double FEET_COLLISION_EPSILON = 0.02;
    /**
     * Au-delà de cet écart pieds/sol (sonde centrale), on n'est pas « au sol » pour la gravité.
     * ~0,07 bloc évite de figer le bot en l'air (lévitation) après dalles/trappes tout en tolérant le bruit NMS.
     */
    private static final double PHYSICS_ON_GROUND_MAX_GAP = 0.07;
    /**
     * Au-delà de cet écart, le micro-snap vertical ne s'applique pas (chute libre gérée par la vélocité verticale).
     */
    private static final double SNAP_SKIP_GAP_ABOVE = 0.48;

    public BotController(LivingEntity entity, BotSettings settings) {
        this(entity, settings, null);
    }

    public BotController(LivingEntity entity, BotSettings settings, JavaPlugin plugin) {
        this.entity = entity;
        this.settings = settings;
        this.plugin = plugin;
        this.pathFinder = new AStarPathFinder(settings);
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public void beginTick() {
        movedThisTick = false;
        lateralUnstuckMode = false;
        jumpConsumedThisTick = false;
    }

    public void endTick() {
        if (!entity.isValid()) return;
        if (entity instanceof Player && !movedThisTick) {
            movePlayerWithSimulatedPhysics(new Vector(0, 0, 0));
        }
    }

    public void stop() {
        if (!entity.isValid()) return;
        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(0, v.getY(), 0));
    }

    public void moveTo(Location loc, double stopRange) {
        if (!entity.isValid()) return;
        if (entity instanceof Player) {
            moveToWithPath(loc, stopRange);
            return;
        }

        Location cur = entity.getLocation();
        if (cur.getWorld() == null || loc.getWorld() == null) return;
        if (!cur.getWorld().equals(loc.getWorld())) return;

        double distSq = cur.distanceSquared(loc);
        if (distSq <= stopRange * stopRange) {
            stop();
            return;
        }

        Vector dir = loc.toVector().subtract(cur.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) {
            stop();
            return;
        }
        dir.normalize();
        applyStrafeJitter(dir, cur.distanceSquared(loc));
        dir = avoidObstacles(dir);
        faceDirection(dir);
        double speed = computeMoveSpeed(cur.distanceSquared(loc));
        dir.multiply(speed);
        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(dir.getX(), v.getY(), dir.getZ()));
    }

    public void lookAt(Location loc) {
        if (!entity.isValid()) return;
        Location cur = entity.getLocation();

        Vector dir = loc.toVector().subtract(cur.toVector());
        if (dir.lengthSquared() < 1.0e-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.toDegrees(-Math.atan2(dir.getY(), Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ())));
        applyEntityRotation(yaw, pitch);
    }

    public void attack(Entity target) {
        if (!entity.isValid() || target == null || !target.isValid()) return;
        if (!hasMeleeLineOfSight(target)) return;
        doMeleeAttack(target);
    }

    public boolean attackWithCooldown(Entity target, long cooldownMs) {
        long now = System.currentTimeMillis();
        if (now - lastAttackAtMs < cooldownMs) return false;
        if (!hasMeleeLineOfSight(target)) return false;
        doMeleeAttack(target);
        lastAttackAtMs = now;
        return true;
    }

    private void doMeleeAttack(Entity target) {
        if (entity instanceof Player p) {
            p.swingMainHand();
            p.attack(target);
        }
    }

    /**
     * Animation de bras (joueur uniquement). Les mobs sans {@link Player} ne supportent pas l’API Bukkit.
     */
    public boolean trySwingHand(boolean mainHand) {
        if (!entity.isValid()) {
            return false;
        }
        if (entity instanceof Player p) {
            if (mainHand) {
                p.swingMainHand();
            } else {
                p.swingOffHand();
            }
            return true;
        }
        return false;
    }

    /**
     * Pose d’affichage (debout, accroupi, nage, etc.).
     */
    public boolean trySetPose(Pose pose) {
        if (!entity.isValid() || pose == null) {
            return false;
        }
        try {
            entity.setPose(pose);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Bloc plein entre le bot et la cible (herbe / passables ignorés comme en jeu).
     */
    public boolean hasMeleeLineOfSight(Entity target) {
        if (!entity.isValid() || target == null || !target.isValid()) {
            return false;
        }
        return hasMeleeLOSFrom(entity.getEyeLocation(), target);
    }

    /**
     * Melee range from bot eye to nearest point of target hitbox (not feet-to-feet).
     */
    public boolean isTargetWithinMeleeRange(Entity target, double attackRange) {
        if (!entity.isValid() || target == null || !target.isValid()) {
            return false;
        }
        Location eye = entity.getEyeLocation();
        BoundingBox bb = target.getBoundingBox();
        double cx = Math.max(bb.getMinX(), Math.min(bb.getMaxX(), eye.getX()));
        double cy = Math.max(bb.getMinY(), Math.min(bb.getMaxY(), eye.getY()));
        double cz = Math.max(bb.getMinZ(), Math.min(bb.getMaxZ(), eye.getZ()));
        double dx = eye.getX() - cx;
        double dy = eye.getY() - cy;
        double dz = eye.getZ() - cz;
        return (dx * dx + dy * dy + dz * dz) <= (attackRange * attackRange);
    }

    /**
     * Cherche un point au sol autour de la cible d'où une attaque au corps à corps a la LOS (pour repath).
     */
    public Location findMeleeFlankLocation(Entity target, double attackRange) {
        if (!entity.isValid() || target == null || !target.isValid()) {
            return null;
        }
        World w = entity.getWorld();
        if (w == null || !w.equals(target.getWorld())) {
            return null;
        }
        Location tgt = target.getLocation();
        double radius = Math.min(Math.max(attackRange - 0.35, 1.2), 2.6);
        for (double r : new double[]{radius, radius * 0.72, radius * 0.45}) {
            for (int i = 0; i < 16; i++) {
                double angle = (i / 16.0) * 2.0 * Math.PI;
                double dx = Math.cos(angle) * r;
                double dz = Math.sin(angle) * r;
                double cx = tgt.getX() + dx;
                double cz = tgt.getZ() + dz;
                double fy = BlockCollisionHelper.floorFeetY(w, cx, cz, tgt.getY() + 3.0);
                if (fy < -500) {
                    continue;
                }
                Location feet = new Location(w, cx, fy, cz);
                Location eye = feet.clone().add(0, 1.62, 0);
                if (hasMeleeLOSFrom(eye, target)) {
                    return feet;
                }
            }
        }
        return null;
    }

    private boolean hasMeleeLOSFrom(Location eye, Entity target) {
        World w = eye.getWorld();
        if (w == null || !w.equals(target.getWorld())) {
            return false;
        }
        Vector to = target.getBoundingBox().getCenter().subtract(eye.toVector());
        double dist = to.length();
        if (dist < 1.0e-4) {
            return true;
        }
        Vector dir = to.clone().normalize();
        RayTraceResult res = w.rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
        if (res == null || res.getHitPosition() == null) {
            return true;
        }
        double hitDist = eye.toVector().distance(res.getHitPosition());
        return hitDist >= dist - 0.28;
    }

    public void applyExternalImpulse(Vector impulse) {
        if (impulse == null) return;
        simulatedHorizontalVelocity.add(new Vector(impulse.getX(), 0, impulse.getZ()));
        simulatedVerticalVelocity += impulse.getY();
        knockbackPriorityTicks = Math.max(knockbackPriorityTicks, 7);
    }

    /** Vitesses cibles (blocs/tick) proches du joueur vanilla (~0,216 marche, ~0,280 sprint). */
    private double computeMoveSpeed(double distSq) {
        if (settings.isSprintEnabled()) {
            return settings.getMoveSpeedSprint();
        }
        if (distSq > 16.0) return settings.getMoveSpeedWalk();
        return 0.18;
    }

    private double computePlayerMoveSpeed(Vector desiredMoveDir) {
        Vector dir = desiredMoveDir.clone().setY(0);
        if (dir.lengthSquared() < 1.0e-6) return 0.0;
        dir.normalize();

        if (BlockCollisionHelper.touchingClimbable(entity.getLocation())) {
            return settings.getMoveSpeedClimb();
        }

        float yawRad = (float) Math.toRadians(entity.getLocation().getYaw());
        Vector forward = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        double align = forward.dot(dir);

        // Minecraft-like feel:
        // - looking mostly in movement direction => sprint
        // - sideways/backwards => slower walk
        if (align > 0.72) {
            return settings.isSprintEnabled() ? settings.getMoveSpeedSprint() : settings.getMoveSpeedWalk();
        }
        if (align < -0.25) return settings.getMoveSpeedBackward();
        return settings.getMoveSpeedWalk();
    }

    private void applyStrafeJitter(Vector dir, double distSq) {
        if (distSq < 4.0) return;
        long now = System.currentTimeMillis();
        if (now >= nextStrafeSwitchAtMs) {
            strafeSign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            nextStrafeSwitchAtMs = now + ThreadLocalRandom.current().nextLong(350, 950);
        }
        Vector right = new Vector(-dir.getZ(), 0, dir.getX());
        dir.add(right.multiply(settings.getStrafeJitterStrength() * strafeSign)).normalize();
    }

    private Vector avoidObstacles(Vector desiredDir) {
        Location base = entity.getLocation();
        if (!isBlocked(base, desiredDir)) return desiredDir;

        Vector left = rotate90(desiredDir, true).normalize();
        if (!isBlocked(base, left)) return left;

        Vector right = rotate90(desiredDir, false).normalize();
        if (!isBlocked(base, right)) return right;

        return desiredDir;
    }

    private boolean isBlocked(Location base, Vector dir) {
        if (dir.lengthSquared() < 1.0e-8) {
            return false;
        }
        // If we can step-up this shape, it's not a true blocking obstacle.
        if (canStepUpWithoutJump(base, dir)) {
            return false;
        }
        Location ahead = base.clone().add(dir.clone().normalize().multiply(0.7));
        World w = base.getWorld();
        if (w == null) {
            return false;
        }
        return BlockCollisionHelper.worldVolumeOccupied(w, BlockCollisionHelper.playerBounds(ahead));
    }

    private Vector rotate90(Vector dir, boolean left) {
        return left ? new Vector(dir.getZ(), 0, -dir.getX()) : new Vector(-dir.getZ(), 0, dir.getX());
    }

    private void movePlayerWithSimulatedPhysics(Vector horizontalDelta) {
        suppressWalkStepDuringJumpArc = false;
        Location cur = entity.getLocation();
        Location next = cur.clone();
        captureExternalVelocity();

        boolean inWater = BlockCollisionHelper.bodyTouchesWater(cur);

        Vector waterFlow = inWater ? estimateWaterFlow(cur) : new Vector(0, 0, 0);

        Vector desired = horizontalDelta.clone();
        if (inWater) {
            desired.multiply(0.32);
        }
        // Stay glued into the ladder column: waypoint lookahead can pull sideways off the shaft.
        if (BlockCollisionHelper.touchingClimbable(cur) && desired.lengthSquared() > 1.0e-8) {
            Vector into = BlockCollisionHelper.horizontalTowardOverlappingClimbable(cur);
            if (into.lengthSquared() > 1.0e-8) {
                double len = Math.hypot(desired.getX(), desired.getZ());
                Vector h = new Vector(desired.getX(), 0, desired.getZ()).normalize();
                Vector blended = into.clone().multiply(0.72).add(h.multiply(0.28));
                blended.normalize();
                desired.setX(blended.getX() * len);
                desired.setZ(blended.getZ() * len);
            }
        }

        boolean climbing = BlockCollisionHelper.touchingClimbable(cur)
                && desired.lengthSquared() > 1.0e-6;

        // Input affects velocity over time (player-like), and can partially counter knockback.
        double inputFactor;
        if (knockbackPriorityTicks > 4) {
            inputFactor = 0.10;
        } else if (knockbackPriorityTicks > 0) {
            inputFactor = 0.22;
        } else {
            inputFactor = 0.38;
        }
        if (inWater) {
            inputFactor *= 0.48;
        }
        if (climbing) {
            inputFactor *= 0.88;
        }

        simulatedHorizontalVelocity.setX(lerp(simulatedHorizontalVelocity.getX(), desired.getX(), inputFactor));
        simulatedHorizontalVelocity.setZ(lerp(simulatedHorizontalVelocity.getZ(), desired.getZ(), inputFactor));

        if (inWater) {
            simulatedHorizontalVelocity.add(waterFlow);
            simulatedHorizontalVelocity.multiply(0.90);
        }

        // Ne PAS tester le sol sous (pos + vélocité) : au bord d'un bloc ça échoue toujours → onGround faux → jamais de saut.
        boolean onGround = isOnGroundForJump(cur);
        boolean jumpNeededAhead = !lateralUnstuckMode
                && (jumpIntentTicks > 0 || needsJumpForObstacle(cur, desired));

        if (climbing) {
            simulatedVerticalVelocity = Math.max(simulatedVerticalVelocity, 0.0);
            // ~0,118 bloc/tick en montée échelle (ordre de grandeur vanilla)
            simulatedVerticalVelocity += 0.118;
            if (simulatedVerticalVelocity > 0.14) {
                simulatedVerticalVelocity = 0.14;
            }
        }

        // Still moving up from our own impulse or NMS: do not arm another "ground" jump (coyote / hitbox noise).
        if (onGround && jumpNeededAhead && !inWater && !climbing && !jumpConsumedThisTick
                && simulatedVerticalVelocity <= 0.12) {
            // Fixed jump impulse: avoid stacking previous positive Y into "super jumps".
            if (simulatedVerticalVelocity > 0.0) {
                simulatedVerticalVelocity = 0.0;
            }
            simulatedVerticalVelocity = settings.getJumpVelocity();
            jumpIntentTicks = 0;
            manualJumpTicks = 18;
            upwardVelocityGuardTicks = 18;
            jumpConsumedThisTick = true;
            // Substeps must not add step-up / ground snap on top of this impulse (double vertical in one tick).
            suppressWalkStepDuringJumpArc = true;
        } else if (inWater) {
            simulatedVerticalVelocity -= 0.025;
            simulatedVerticalVelocity *= 0.98;
            simulatedVerticalVelocity += 0.038; // buoyancy / swim
            if (simulatedVerticalVelocity < -0.28) {
                simulatedVerticalVelocity = -0.28;
            }
        } else if (!onGround || simulatedVerticalVelocity != 0.0) {
            if (climbing) {
                simulatedVerticalVelocity -= 0.02;
            } else {
                // Minecraft-like gravity approximation while airborne/in motion.
                simulatedVerticalVelocity -= 0.08;
                simulatedVerticalVelocity *= 0.98;
                if (simulatedVerticalVelocity < -3.92) {
                    simulatedVerticalVelocity = -3.92;
                }
            }
        } else if (onGround && simulatedVerticalVelocity < 0.0) {
            simulatedVerticalVelocity = 0.0;
        }

        double totalY = simulatedVerticalVelocity;
        Vector totalMove = new Vector(
                simulatedHorizontalVelocity.getX(),
                totalY,
                simulatedHorizontalVelocity.getZ()
        );
        int steps = Math.max(1, (int) Math.ceil(totalMove.length() / 0.10));
        Vector step = totalMove.clone().multiply(1.0 / steps);

        for (int i = 0; i < steps; i++) {
            Location combined = next.clone().add(step.getX(), step.getY(), step.getZ());
            if (canOccupy(combined)) {
                next = combined;
                continue;
            }
            boolean goingUp = step.getY() > 1.0e-6;
            if (goingUp) {
                Location verticalNext = next.clone().add(0, step.getY(), 0);
                if (canOccupy(verticalNext)) {
                    next = verticalNext;
                } else {
                    simulatedVerticalVelocity = 0.0;
                }
                applyHorizontalSubstep(next, step);
            } else {
                applyHorizontalSubstep(next, step);
                if (Math.abs(step.getY()) > 1.0e-6) {
                    Location verticalNext = next.clone().add(0, step.getY(), 0);
                    if (canOccupy(verticalNext)) {
                        next = verticalNext;
                    } else {
                        simulatedVerticalVelocity = 0.0;
                    }
                }
            }
        }

        // Hitbox-based ground penetration fix (not a snap): only correct when already intersecting ground.
        if (simulatedVerticalVelocity <= 0.0) {
            double feetGap = groundGapFromHitboxForPhysics(next);
            if (feetGap < -0.02) {
                next.setY(next.getY() - feetGap);
                simulatedVerticalVelocity = 0.0;
            }
        }

        // Sans mouvement horizontal, aucun snap dans applyHorizontalSubstep — corrige la légère lévitation
        // après trappes/dalles (raycast vs position NMS) même debout.
        if (!BlockCollisionHelper.touchingClimbable(next)) {
            double down = Math.max(settings.getStepSnapDownMax(), 0.24);
            snapFeetToNearbyGround(next, settings.getStepSnapUpMax(), down);
        }

        // Safety against broken states below world.
        int minY = next.getWorld() != null ? next.getWorld().getMinHeight() : -64;
        if (next.getY() < minY - 8) {
            next.setY(minY + 2.0);
            simulatedVerticalVelocity = 0.0;
        }

        next.setYaw(cur.getYaw());
        next.setPitch(cur.getPitch());
        entity.teleport(next);
        applyFriction(hasSolidGroundForPhysics(next), BlockCollisionHelper.bodyTouchesWater(next));
        if (jumpIntentTicks > 0) {
            jumpIntentTicks--;
        }
        if (manualJumpTicks > 0) {
            manualJumpTicks--;
        }
        if (upwardVelocityGuardTicks > 0) {
            upwardVelocityGuardTicks--;
        }
        if (knockbackPriorityTicks > 0) knockbackPriorityTicks--;
        suppressWalkStepDuringJumpArc = false;
    }

    private void applyHorizontalSubstep(Location next, Vector step) {
        Location horizontalNext = next.clone().add(step.getX(), 0, step.getZ());
        if (canOccupy(horizontalNext)) {
            if (!suppressWalkStepDuringJumpArc) {
                snapFeetToNearbyGround(horizontalNext, settings.getStepSnapUpMax(), settings.getStepSnapDownMax());
            }
            next.set(horizontalNext.getX(), horizontalNext.getY(), horizontalNext.getZ());
            return;
        }
        Location stepped = tryHorizontalStepUp(next, step);
        if (stepped != null) {
            next.set(stepped.getX(), stepped.getY(), stepped.getZ());
            return;
        }
        Location xOnly = next.clone().add(step.getX(), 0, 0);
        Location xStepped = tryHorizontalStepUp(next, new Vector(step.getX(), 0, 0));
        if (xStepped != null) {
            next.set(xStepped.getX(), xStepped.getY(), xStepped.getZ());
            simulatedHorizontalVelocity.setZ(0.0);
            return;
        }
        if (canOccupy(xOnly)) {
            next.set(xOnly.getX(), xOnly.getY(), xOnly.getZ());
            simulatedHorizontalVelocity.setZ(0.0);
            return;
        }
        Location zOnly = next.clone().add(0, 0, step.getZ());
        Location zStepped = tryHorizontalStepUp(next, new Vector(0, 0, step.getZ()));
        if (zStepped != null) {
            next.set(zStepped.getX(), zStepped.getY(), zStepped.getZ());
            simulatedHorizontalVelocity.setX(0.0);
            return;
        }
        if (canOccupy(zOnly)) {
            next.set(zOnly.getX(), zOnly.getY(), zOnly.getZ());
            simulatedHorizontalVelocity.setX(0.0);
            return;
        }
        Location edgeSlide = tryEdgeSlide(next, step);
        if (edgeSlide != null) {
            next.set(edgeSlide.getX(), edgeSlide.getY(), edgeSlide.getZ());
            return;
        }
        simulatedHorizontalVelocity.setX(0.0);
        simulatedHorizontalVelocity.setZ(0.0);
    }

    /**
     * Resolve side/corner snag by trying tiny lateral slides (with optional micro step-up).
     */
    private Location tryEdgeSlide(Location base, Vector step) {
        if (suppressWalkStepDuringJumpArc) {
            return null;
        }
        Vector horiz = new Vector(step.getX(), 0, step.getZ());
        if (horiz.lengthSquared() < 1.0e-8) {
            return null;
        }
        Vector dir = horiz.clone().normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        int samples = Math.max(1, settings.getEdgeSlideSamples());
        double fMin = settings.getEdgeSlideForwardMin();
        double fMax = settings.getEdgeSlideForwardMax();
        double sMin = settings.getEdgeSlideLateralMin();
        double sMax = settings.getEdgeSlideLateralMax();
        for (int fi = 0; fi < samples; fi++) {
            double f = (samples == 1) ? fMax : (fMin + (fMax - fMin) * fi / (samples - 1.0));
            for (int si = 0; si < samples; si++) {
                double s = (samples == 1) ? sMax : (sMin + (sMax - sMin) * si / (samples - 1.0));
                for (int sign : new int[]{1, -1}) {
                    Location cand = base.clone()
                            .add(dir.getX() * f + side.getX() * s * sign, 0, dir.getZ() * f + side.getZ() * s * sign);
                    if (canOccupy(cand)) {
                        snapFeetToNearbyGround(cand, settings.getStepSnapUpMax(), settings.getStepSnapDownMax());
                        return cand;
                    }
                    Location stepped = tryHorizontalStepUp(base, new Vector(
                            dir.getX() * f + side.getX() * s * sign, 0, dir.getZ() * f + side.getZ() * s * sign
                    ));
                    if (stepped != null) {
                        return stepped;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Try a vanilla-like "step-up" when horizontal movement is blocked.
     * This avoids jumping on slabs/stairs/trapdoors that are walkable by collision shape.
     */
    private Location tryHorizontalStepUp(Location base, Vector step) {
        if (suppressWalkStepDuringJumpArc) {
            return null;
        }
        if (step.lengthSquared() < 1.0e-10) {
            return null;
        }
        for (double up : STEP_UP_TEST_HEIGHTS) {
            Location candidate = base.clone().add(step.getX(), up, step.getZ());
            if (!canOccupy(candidate)) {
                continue;
            }
            snapFeetToNearbyGround(candidate, settings.getStepSnapUpMax(), settings.getStepSnapDownMax());
            if (hasSolidGround(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Sol pour autoriser le saut : le joueur NMS peut avoir un Y légèrement décalé du raycast.
     * <p>
     * Utilise une sonde au centre des pieds (pas le max sur toute la hitbox) : sinon, une dalle
     * sous un seul coin peut encore donner un « sol » alors que le centre est déjà au-dessus du vide
     * (pas de chute, effet de lévitation).
     */
    private boolean isOnGroundForJump(Location loc) {
        if (hasSolidGroundForPhysics(loc)) {
            return true;
        }
        if (entity instanceof Player) {
            return false;
        }
        return entity instanceof LivingEntity le && le.isOnGround();
    }

    /**
     * Saut : uniquement si l'obstacle n'est pas franchissable "à pied" selon la hitbox de collision.
     */
    private boolean needsJumpForObstacle(Location base, Vector dir) {
        if (dir == null || !isFiniteVector(dir) || dir.lengthSquared() < 1.0e-8) {
            return false;
        }
        World w = base.getWorld();
        if (w == null) {
            return false;
        }
        Vector d = dir.clone().normalize();
        double floorHere = BlockCollisionHelper.floorFeetY(w, base.getX(), base.getZ(), base.getY() + 0.45);
        if (floorHere < -1000) {
            return false;
        }
        // Direct physics check first: if we can step-up with collision, never jump.
        if (canStepUpWithoutJump(base, d)) {
            return false;
        }
        // Surface devant (dalle, marche, escalier) : raycast horizontal sur la face UP + hauteur vs sol actuel.
        if (forwardRayHitsTopSurfaceToStepOn(w, base, d, floorHere)) {
            return false;
        }
        double riseAhead = riseAtForwardSample(w, base, d, floorHere, 0.52);
        if (riseAhead < -2048) {
            return false;
        }
        // Tout ce qui reste dans la hauteur de step-up se franchit sans saut (universel par hitbox).
        if (riseAhead > 0.15 && riseAhead <= MAX_WALK_UP_WITHOUT_JUMP) {
            return false;
        }
        // ~0,62–1,25 : sauter uniquement pour un vrai bloc plein devant.
        if (riseAhead > MAX_WALK_UP_WITHOUT_JUMP && riseAhead <= 1.25) {
            return needsJumpAheadAabb(base, dir, 0.52);
        }
        for (double f : JUMP_OBSTACLE_PROBE_DISTANCES) {
            Location fwdFeet = base.clone().add(d.getX() * f, 0, d.getZ() * f);
            if (!canOccupy(fwdFeet) && canOccupy(fwdFeet.clone().add(0, 1.0, 0))) {
                if (canStepUpWithoutJump(base, d)) {
                    continue;
                }
                double riseAtProbe = riseAtForwardSample(w, base, d, floorHere, f);
                if (riseAtProbe > 0.15 && riseAtProbe <= MAX_WALK_UP_WITHOUT_JUMP) {
                    continue;
                }
                if (riseAtProbe > MAX_WALK_UP_WITHOUT_JUMP && riseAtProbe <= 1.25
                        && !needsJumpAheadAabb(base, dir, f)) {
                    continue;
                }
                if (forwardRayHitsTopSurfaceToStepOn(w, base, d, floorHere)) {
                    continue;
                }
                if (needsJumpAheadAabb(base, dir, f)) {
                    return true;
                }
                continue;
            }
            if (needsJumpAheadAabb(base, dir, f)) {
                if (canStepUpWithoutJump(base, d)) {
                    continue;
                }
                double riseAtProbe = riseAtForwardSample(w, base, d, floorHere, f);
                if (riseAtProbe > 0.15 && riseAtProbe <= MAX_WALK_UP_WITHOUT_JUMP) {
                    continue;
                }
                if (riseAtProbe > MAX_WALK_UP_WITHOUT_JUMP && riseAtProbe <= 1.25
                        && !needsJumpAheadAabb(base, dir, f)) {
                    continue;
                }
                if (forwardRayHitsTopSurfaceToStepOn(w, base, d, floorHere)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * True when moving forward can be resolved by a small step-up (<= MAX_WALK_UP_WITHOUT_JUMP).
     * Must compare real floor heights: otherwise a 1-block ledge can look "steppable" (air above the lip)
     * while {@link #needsJumpForObstacle} would incorrectly skip the jump.
     */
    private boolean canStepUpWithoutJump(Location base, Vector normalizedDir) {
        Vector h = normalizedDir.clone().setY(0);
        if (h.lengthSquared() < 1.0e-8) {
            return false;
        }
        h.normalize();
        World w = base.getWorld();
        if (w == null) {
            return false;
        }
        double floorHere = BlockCollisionHelper.floorFeetY(w, base.getX(), base.getZ(), base.getY() + 0.45);
        if (floorHere < -1000) {
            return false;
        }
        double[] forwardSamples = {0.35, 0.52, 0.70};
        for (double f : forwardSamples) {
            Location ahead = base.clone().add(h.getX() * f, 0, h.getZ() * f);
            if (canOccupy(ahead)) {
                continue;
            }
            for (double up : STEP_UP_TEST_HEIGHTS) {
                Location stepped = ahead.clone().add(0, up, 0);
                if (!canOccupy(stepped)) {
                    continue;
                }
                double steppedFloor = BlockCollisionHelper.floorFeetY(w, stepped.getX(), stepped.getZ(), stepped.getY() + 0.45);
                if (steppedFloor < -1000) {
                    continue;
                }
                double rise = steppedFloor - floorHere;
                if (rise > MAX_WALK_UP_WITHOUT_JUMP + 0.02) {
                    continue;
                }
                if (hasSolidGround(stepped)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Rayon horizontal : touche la face UP d'une surface qu'on peut emprunter sans saut (pas le sol plat devant au même Y).
     * Utilise le dénivelé par rapport à {@code floorHere} et la forme de collision (cube plein = sauter pour monter).
     */
    private boolean forwardRayHitsTopSurfaceToStepOn(World w, Location base, Vector d, double floorHere) {
        Location start = base.clone().add(d.getX() * 0.18, 0.0, d.getZ() * 0.18);
        for (double dy : JUMP_RAY_HEIGHT_OFFSETS) {
            RayTraceResult hit = w.rayTraceBlocks(
                    start.clone().add(0, dy, 0),
                    d,
                    0.95,
                    FluidCollisionMode.NEVER,
                    true
            );
            if (hit == null || hit.getHitBlockFace() != BlockFace.UP || hit.getHitPosition() == null) {
                continue;
            }
            Block hitBlock = hit.getHitBlock();
            if (hitBlock == null) {
                continue;
            }
            double deltaY = hit.getHitPosition().getY() - floorHere;
            // Sol plat au même niveau : même hauteur de surface (évite faux positifs sur terrain plat).
            if (deltaY <= 0.04) {
                continue;
            }
            if (deltaY <= MAX_WALK_UP_WITHOUT_JUMP) {
                return true;
            }
            // Entre ~0,62 et ~1,15 : vraies marches/dalles (pas le verre, barres, etc.).
            if (deltaY <= 1.18 && BlockCollisionHelper.isWalkableNonFullCollisionBlock(hitBlock)) {
                return true;
            }
        }
        return false;
    }

    private double riseAtForwardSample(World w, Location base, Vector normalizedDir, double floorHere, double forward) {
        double sx = base.getX() + normalizedDir.getX() * forward;
        double sz = base.getZ() + normalizedDir.getZ() * forward;
        double floorAhead = BlockCollisionHelper.floorFeetY(w, sx, sz, base.getY() + 0.45);
        if (floorAhead < -1000) {
            return -9999;
        }
        return floorAhead - floorHere;
    }

    /**
     * @param forward distance horizontale depuis les pieds (même Y que {@code base})
     */
    private boolean needsJumpAheadAabb(Location base, Vector dir, double forward) {
        if (dir == null || !isFiniteVector(dir) || !Double.isFinite(forward) || forward <= 0.0) {
            return false;
        }
        World w = base.getWorld();
        if (w == null) {
            return false;
        }
        Vector n = dir.clone();
        if (n.lengthSquared() < 1.0e-8) {
            return false;
        }
        n.normalize();
        if (!isFiniteVector(n)) {
            return false;
        }
        Location ahead = base.clone().add(n.multiply(forward));
        if (!Double.isFinite(ahead.getX()) || !Double.isFinite(ahead.getY()) || !Double.isFinite(ahead.getZ())) {
            return false;
        }
        BoundingBox lower = new BoundingBox(
                ahead.getX() - 0.2, ahead.getY(), ahead.getZ() - 0.2,
                ahead.getX() + 0.2, ahead.getY() + 0.55, ahead.getZ() + 0.2
        );
        BoundingBox upper = new BoundingBox(
                ahead.getX() - 0.2, ahead.getY() + 0.55, ahead.getZ() - 0.2,
                ahead.getX() + 0.2, ahead.getY() + 1.78, ahead.getZ() + 0.2
        );
        boolean footBlocked = BlockCollisionHelper.worldVolumeOccupied(w, lower);
        boolean headFree = !BlockCollisionHelper.worldVolumeOccupied(w, upper);
        if (!footBlocked || !headFree) {
            return false;
        }
        // Jump for full blocks, glass panes, fences, etc. — not slab/stair/trapdoor walkable geometry.
        return hasFootObstacleRequiringJump(w, lower);
    }

    /**
     * Foot-level volume blocked by something that cannot be crossed by a small step-up (slab/stair-like).
     */
    private boolean hasFootObstacleRequiringJump(World world, BoundingBox footBox) {
        int minX = (int) Math.floor(footBox.getMinX());
        int maxX = (int) Math.floor(footBox.getMaxX());
        int minY = (int) Math.floor(footBox.getMinY());
        int maxY = (int) Math.floor(footBox.getMaxY());
        int minZ = (int) Math.floor(footBox.getMinZ());
        int maxZ = (int) Math.floor(footBox.getMaxZ());
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block b = world.getBlockAt(bx, by, bz);
                    if (!BlockCollisionHelper.blockBlocksMovement(b)) {
                        continue;
                    }
                    if (BlockCollisionHelper.isWalkThroughClimbable(b) || BlockCollisionHelper.isWaterAt(b)) {
                        continue;
                    }
                    if (!BlockCollisionHelper.overlapsBlockCollision(footBox, b)) {
                        continue;
                    }
                    if (BlockCollisionHelper.isWalkableNonFullCollisionBlock(b)) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFiniteVector(Vector v) {
        return Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ());
    }


    /**
     * Approximate horizontal water current from neighbouring fluid levels (vanilla-like direction).
     */
    private Vector estimateWaterFlow(Location feet) {
        World w = feet.getWorld();
        if (w == null) {
            return new Vector(0, 0, 0);
        }
        Vector flux = new Vector(0, 0, 0);
        for (Block b : new Block[]{feet.getBlock(), feet.clone().add(0, 1, 0).getBlock()}) {
            if (b.getType() != Material.WATER) {
                continue;
            }
            Levelled lvl = (Levelled) b.getBlockData();
            int my = lvl.getLevel();
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST}) {
                Block adj = b.getRelative(face);
                if (adj.getType() != Material.WATER) {
                    continue;
                }
                int adjL = ((Levelled) adj.getBlockData()).getLevel();
                int d = adjL - my;
                if (d != 0) {
                    flux.add(new Vector(face.getModX() * d, 0, face.getModZ() * d));
                }
            }
        }
        if (flux.lengthSquared() < 1.0e-10) {
            return new Vector(0, 0, 0);
        }
        flux.normalize();
        flux.multiply(0.068);
        return flux;
    }

    private void moveToWithPath(Location goal, double stopRange) {
        Location cur = entity.getLocation();
        if (cur.getWorld() == null || goal.getWorld() == null) return;
        if (!cur.getWorld().equals(goal.getWorld())) return;

        if (cur.distanceSquared(goal) <= stopRange * stopRange && hasDirectTravel(cur, goal)) {
            return;
        }

        long now = System.currentTimeMillis();
        updateStuckState(cur);
        boolean onClimbable = BlockCollisionHelper.touchingClimbable(cur);
        boolean goalMoved = lastGoal == null || !sameGoalCell(lastGoal, goal);
        boolean pathEmpty = currentPath.isEmpty() || currentPathIndex >= currentPath.size();
        int stuckThreshold = onClimbable ? settings.getStuckThresholdClimbTicks() : settings.getStuckThresholdGroundTicks();
        boolean forceRepathForStuck = stuckTicks >= stuckThreshold;
        if (pathEmpty || goalMoved || now >= nextRepathAtMs || forceRepathForStuck) {
            int maxNodes = settings.getPathMaxVisitedNodes();
            if (stuckTicks >= stuckThreshold) {
                maxNodes = Math.min(maxNodes, settings.getPathMaxVisitedNodesStuck());
            }
            final int maxNodesFinal = maxNodes;
            final boolean pathWasEmpty = pathEmpty;
            final boolean repathStuck = forceRepathForStuck;
            final int stuckAtSubmit = stuckTicks;
            final int stuckThresholdSnapshot = stuckThreshold;

            boolean waitForAsync = pathInflight > 0 && !goalMoved;
            if (!waitForAsync) {
                // Boîte couloir : latéral, arrière, avant max (grille), puis marge verticale — évolue à chaque repath.
                PathSnapshot snap = plugin != null ? PathSnapshot.tryCapture(cur, goal, 12, 8, 52, 8) : null;
                if (plugin != null && snap != null) {
                    pathSearchGeneration++;
                    final int gen = pathSearchGeneration;
                    pathInflight++;
                    final Location curSnap = cur.clone();
                    final Location goalSnap = goal.clone();
                    PathfindingAsync.executor().execute(() -> {
                        final PathSearchNode[] pathEndBox = new PathSearchNode[1];
                        try {
                            PathSearchNode s = new PathSearchNode(curSnap.getBlockX(), curSnap.getBlockY(), curSnap.getBlockZ());
                            PathSearchNode g = new PathSearchNode(goalSnap.getBlockX(), goalSnap.getBlockY(), goalSnap.getBlockZ());
                            pathEndBox[0] = AStarSnapshotSearch.findPath(snap, s, g, maxNodesFinal, settings);
                        } catch (Throwable t) {
                            pathEndBox[0] = null;
                        }
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            pathInflight--;
                            if (!entity.isValid()) {
                                return;
                            }
                            if (gen != pathSearchGeneration) {
                                return;
                            }
                            World w = entity.getWorld();
                            if (w == null) {
                                return;
                            }
                            long nowCb = System.currentTimeMillis();
                            PathSearchNode pathEnd = pathEndBox[0];
                            List<Location> candidatePath;
                            if (pathEnd != null) {
                                List<Location> built = pathFinder.buildPathFromEnd(w, pathEnd);
                                candidatePath = (built == null || built.isEmpty()) ? List.of() : built;
                            } else {
                                candidatePath = List.of();
                            }
                            if (!candidatePath.isEmpty()) {
                                currentPath = candidatePath;
                                currentPathIndex = currentPath.size() > 1 ? 1 : 0;
                            } else if (pathWasEmpty || repathStuck) {
                                currentPath = List.of();
                                currentPathIndex = 0;
                            }
                            lastGoal = goalSnap.clone();
                            long delay = candidatePath.isEmpty() ? settings.getPathRetryOnFailMs() : settings.getPathRepathIntervalMs();
                            if (candidatePath.isEmpty() && stuckAtSubmit >= stuckThresholdSnapshot) {
                                delay = Math.max(delay, 450L + (long) Math.min(stuckAtSubmit, 50) * 35L);
                            }
                            nextRepathAtMs = nowCb + delay;
                        });
                    });
                    lastGoal = goal.clone();
                    nextRepathAtMs = now + 50L;
                } else {
                    List<Location> candidatePath = pathFinder.findPath(cur, goal, maxNodes);
                    if (!candidatePath.isEmpty()) {
                        currentPath = candidatePath;
                        currentPathIndex = currentPath.size() > 1 ? 1 : 0;
                    } else if (pathEmpty || forceRepathForStuck) {
                        currentPath = List.of();
                        currentPathIndex = 0;
                    }
                    lastGoal = goal.clone();
                    long delay = candidatePath.isEmpty() ? settings.getPathRetryOnFailMs() : settings.getPathRepathIntervalMs();
                    if (candidatePath.isEmpty() && stuckTicks >= stuckThreshold) {
                        delay = Math.max(delay, 450L + (long) Math.min(stuckTicks, 50) * 35L);
                    }
                    nextRepathAtMs = now + delay;
                }
            }
        }

        if (unstuckTarget != null) {
            if (cur.distanceSquared(unstuckTarget) < 0.55 * 0.55 || now >= unstuckUntilMs) {
                unstuckTarget = null;
                // Rebuild path from the new escaped position to avoid going back to stale waypoints.
                currentPath = List.of();
                currentPathIndex = 0;
                nextRepathAtMs = 0L;
                stuckTicks = 0;
                lastProgressLoc = cur.clone();
            }
        }

        Location waypoint = goal;
        if (!currentPath.isEmpty() && currentPathIndex < currentPath.size()) {
            waypoint = currentPath.get(currentPathIndex);
            if (cur.distanceSquared(waypoint) < 0.70 * 0.70) {
                currentPathIndex++;
                if (currentPathIndex < currentPath.size()) {
                    waypoint = currentPath.get(currentPathIndex);
                }
            }
        }

        // Skip stale waypoints that are now behind us (common after unstuck detours).
        while (!currentPath.isEmpty() && currentPathIndex < currentPath.size()) {
            Location wp = currentPath.get(currentPathIndex);
            Vector toWp = wp.toVector().subtract(cur.toVector()).setY(0);
            Vector toGoal = goal.toVector().subtract(cur.toVector()).setY(0);
            if (toWp.lengthSquared() < 1.0e-6 || toGoal.lengthSquared() < 1.0e-6) break;
            if (toWp.dot(toGoal) < 0) {
                currentPathIndex++;
                waypoint = (currentPathIndex < currentPath.size()) ? currentPath.get(currentPathIndex) : goal;
            } else {
                break;
            }
        }

        Vector toWaypoint = waypoint.toVector().subtract(cur.toVector());
        toWaypoint.setY(0);
        boolean jumpTowardWaypoint = toWaypoint.lengthSquared() > 1.0e-6
                && needsJumpForObstacle(cur, toWaypoint.clone().normalize());
        // Drop a lateral detour if a normal forward jump toward the waypoint is what we need.
        if (unstuckTarget != null && (jumpTowardWaypoint || jumpIntentTicks > 0)) {
            unstuckTarget = null;
        }

        // Lateral unstuck only after we know the navigation waypoint: do not steal control when a forward jump would work.
        if (!onClimbable && stuckTicks >= settings.getStuckThresholdGroundTicks()
                && (unstuckTarget == null || now >= unstuckUntilMs)) {
            if (!jumpTowardWaypoint && jumpIntentTicks <= 0) {
                unstuckTarget = findUnstuckTarget(cur, goal);
                unstuckUntilMs = now + settings.getUnstuckDurationMs();
            }
        }

        Location navTarget = (unstuckTarget != null) ? unstuckTarget : waypoint;
        lateralUnstuckMode = unstuckTarget != null;
        Location steeringTarget = chooseSteeringTarget(cur, navTarget);
        if (!lateralUnstuckMode) {
            updateJumpIntentFromPath(cur, waypoint);
        }

        boolean onLadder = BlockCollisionHelper.touchingClimbable(cur);
        boolean nearLadderShaft = BlockCollisionHelper.nearClimbableBlock(cur);
        Vector dir = steeringTarget.toVector().subtract(cur.toVector());
        dir.setY(0);
        // Waypoint au-dessus : direction horizontale nulle sans poussée vers l'échelle → pas d'entrée W.
        if (dir.lengthSquared() < 1.0e-6 && onLadder) {
            Vector into = BlockCollisionHelper.horizontalTowardOverlappingClimbable(cur);
            if (into.lengthSquared() > 1.0e-6) {
                dir = into;
            }
        }
        if (dir.lengthSquared() < 1.0e-6) return;
        faceToward(cur, steeringTarget);
        dir.normalize();
        boolean immediateJumpAhead = needsJumpForObstacle(cur, dir);
        if (!onLadder && !nearLadderShaft && jumpIntentTicks <= 0 && !immediateJumpAhead) {
            applyStrafeJitter(dir, cur.distanceSquared(goal));
            dir = avoidObstacles(dir);
        } else if (immediateJumpAhead && !lateralUnstuckMode) {
            // Keep a straight line into the obstacle when jump is needed.
            jumpIntentTicks = Math.max(jumpIntentTicks, 2);
        }
        double speed = computePlayerMoveSpeed(dir);
        if (speed <= 0.0) return;
        dir.multiply(speed);
        movedThisTick = true;
        movePlayerWithSimulatedPhysics(dir);
    }

    /**
     * Path-driven jump intent: if next waypoint is a real upward step close in front,
     * request a short jump window. This is more reliable than local collision-only heuristics.
     */
    private void updateJumpIntentFromPath(Location cur, Location waypoint) {
        if (waypoint == null || cur.getWorld() == null || waypoint.getWorld() == null || !cur.getWorld().equals(waypoint.getWorld())) {
            return;
        }
        Vector toWp = waypoint.toVector().subtract(cur.toVector());
        double dy = toWp.getY();
        double horiz = toWp.clone().setY(0).length();
        if (dy > settings.getJumpIntentMinDy() && dy <= settings.getJumpIntentMaxDy() && horiz <= settings.getJumpIntentMaxHoriz()) {
            jumpIntentTicks = Math.max(jumpIntentTicks, settings.getJumpIntentTicks());
        }
    }

    private boolean sameGoalCell(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public List<Location> getCurrentPathSnapshot() {
        return List.copyOf(currentPath);
    }

    public int getCurrentPathIndex() {
        return currentPathIndex;
    }

    public int getStuckTicks() {
        return stuckTicks;
    }

    public Location getUnstuckTarget() {
        return unstuckTarget == null ? null : unstuckTarget.clone();
    }

    private Location chooseSteeringTarget(Location cur, Location fallback) {
        if (BlockCollisionHelper.touchingClimbable(cur)) {
            return fallback;
        }
        if (currentPath.isEmpty() || currentPathIndex >= currentPath.size()) {
            return fallback;
        }
        int maxLookahead = Math.min(currentPath.size() - 1, currentPathIndex + 6);
        for (int i = maxLookahead; i >= currentPathIndex; i--) {
            Location cand = currentPath.get(i);
            if (hasDirectTravel(cur, cand)) {
                return cand;
            }
        }
        return fallback;
    }

    /**
     * Segment horizontal uniquement, avec Y recollé au sol à chaque pas (comme la marche réelle).
     * L'interpolation 3D droite vers la cible pouvait déclarer "libre" un trajet alors que les pieds
     * ne suivent pas le terrain (plateforme au même niveau : herbe → verre, etc.).
     */
    private boolean hasDirectTravel(Location from, Location to) {
        World w = from.getWorld();
        if (w == null || !w.equals(to.getWorld())) {
            return false;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double lenH = Math.hypot(dx, dz);
        if (lenH < 1.0e-6) {
            return true;
        }
        double ux = dx / lenH;
        double uz = dz / lenH;
        double rayTop = Math.max(from.getY(), to.getY()) + 4.0;
        double stepLen = 0.22;
        int steps = Math.max(1, (int) Math.ceil(lenH / stepLen));
        for (int i = 1; i <= steps; i++) {
            double t = Math.min(lenH, (i / (double) steps) * lenH);
            double px = from.getX() + ux * t;
            double pz = from.getZ() + uz * t;
            double floorY = BlockCollisionHelper.floorFeetY(w, px, pz, rayTop);
            if (floorY < -1000) {
                return false;
            }
            Location probe = new Location(w, px, floorY + 0.05, pz);
            if (!canOccupy(probe)) {
                return false;
            }
        }
        return true;
    }

    private void updateStuckState(Location cur) {
        if (lastProgressLoc == null) {
            lastProgressLoc = cur.clone();
            stuckTicks = 0;
            return;
        }
        if (BlockCollisionHelper.touchingClimbable(cur)) {
            double dy = Math.abs(cur.getY() - lastProgressLoc.getY());
            if (dy > 0.08) {
                stuckTicks = 0;
                lastProgressLoc = cur.clone();
                return;
            }
            stuckTicks = Math.max(0, stuckTicks - 1);
            return;
        }
        double movedSq = cur.distanceSquared(lastProgressLoc);
        if (movedSq < 0.03 * 0.03) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastProgressLoc = cur.clone();
        }
    }

    private Location findUnstuckTarget(Location cur, Location goal) {
        if (cur.getWorld() == null) return null;
        Vector toGoal = goal.toVector().subtract(cur.toVector()).setY(0);
        if (toGoal.lengthSquared() < 1.0e-6) return null;
        toGoal.normalize();

        Vector[] dirs = new Vector[]{
                toGoal.clone(),
                rotate90(toGoal, true).normalize(),
                rotate90(toGoal, false).normalize(),
                toGoal.clone().multiply(-1)
        };

        Location best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Vector d : dirs) {
            // Ground-level lateral move.
            Location flat = cur.clone().add(d.getX() * 0.75, 0, d.getZ() * 0.75);
            if (canOccupy(flat) && hasSolidGround(flat)) {
                double score = -flat.distanceSquared(goal);
                if (score > bestScore) {
                    bestScore = score;
                    best = flat;
                }
            }

            // Step-up candidate: stand on the adjacent block top if free.
            Location stepUp = cur.clone().add(d.getX() * 0.75, 1.0, d.getZ() * 0.75);
            Location belowStep = stepUp.clone().add(0, -1, 0);
            if (canOccupy(stepUp) && canSupportStep(belowStep.getBlock())) {
                double score = -stepUp.distanceSquared(goal) + 0.35;
                if (score > bestScore) {
                    bestScore = score;
                    best = stepUp;
                }
            }
        }
        return best;
    }

    private boolean hasSolidGround(Location loc) {
        double feetGap = groundGapFromHitbox(loc);
        // Allow small uncertainty from voxel shapes (slabs/stairs edges) for step-up validation.
        return feetGap > -0.08 && feetGap <= 0.26;
    }

    /**
     * Sol pour gravité / friction / snap : sonde verticale au centre des pieds uniquement.
     * Évite le faux « au sol » quand la sonde max sur 9 points touche encore une dalle sous un coin
     * alors que le centre est déjà au-dessus du vide.
     */
    private boolean hasSolidGroundForPhysics(Location loc) {
        double feetGap = groundGapFromHitboxForPhysics(loc);
        return feetGap > -0.08 && feetGap <= PHYSICS_ON_GROUND_MAX_GAP;
    }

    /**
     * Feet-to-ground vertical gap based on the player's hitbox footprint.
     * Positive: feet above ground, zero: on ground, negative: penetrating ground.
     */
    private double groundGapFromHitbox(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return Double.POSITIVE_INFINITY;
        }
        BoundingBox box = BlockCollisionHelper.playerBounds(loc);
        double feetY = box.getMinY();
        double[] xs = {
                (box.getMinX() + box.getMaxX()) * 0.5,
                box.getMinX() + 0.03,
                box.getMaxX() - 0.03
        };
        double[] zs = {
                (box.getMinZ() + box.getMaxZ()) * 0.5,
                box.getMinZ() + 0.03,
                box.getMaxZ() - 0.03
        };
        double bestFloor = -9999;
        for (double x : xs) {
            for (double z : zs) {
                double y = BlockCollisionHelper.floorFeetY(w, x, z, feetY + 0.55);
                if (y > bestFloor) {
                    bestFloor = y;
                }
            }
        }
        if (bestFloor < -2048) {
            return Double.POSITIVE_INFINITY;
        }
        return feetY - bestFloor;
    }

    /**
     * Écart pieds/sol au centre de la hitbox (gravité, snap vertical). Voir {@link #hasSolidGroundForPhysics}.
     */
    private double groundGapFromHitboxForPhysics(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return Double.POSITIVE_INFINITY;
        }
        BoundingBox box = BlockCollisionHelper.playerBounds(loc);
        double feetY = box.getMinY();
        double cx = (box.getMinX() + box.getMaxX()) * 0.5;
        double cz = (box.getMinZ() + box.getMaxZ()) * 0.5;
        double floorY = BlockCollisionHelper.floorFeetY(w, cx, cz, feetY + 0.55);
        if (floorY < -2048) {
            return Double.POSITIVE_INFINITY;
        }
        return feetY - floorY;
    }

    /**
     * Minecraft-like micro-snap around nearby surfaces for smoother walk/step transitions.
     * Descente : si l'écart au sol dépasse {@code maxDown}, on descend quand même d'au plus {@code maxDown}
     * par tick (avant : aucun snap → lévitation au-dessus du sol réel).
     */
    private void snapFeetToNearbyGround(Location loc, double maxUp, double maxDown) {
        double gap = groundGapFromHitboxForPhysics(loc);
        if (!Double.isFinite(gap)) {
            return;
        }
        if (gap >= SNAP_SKIP_GAP_ABOVE) {
            return;
        }
        double adjust = -gap; // + => move up, - => move down
        if (adjust > maxUp) {
            return;
        }
        if (adjust < -maxDown) {
            adjust = -maxDown;
        }
        if (Math.abs(adjust) < 1.0e-3) {
            return;
        }
        Location adjusted = loc.clone().add(0, adjust, 0);
        if (canOccupy(adjusted)) {
            loc.setY(adjusted.getY());
        }
    }

    private boolean canOccupy(Location loc) {
        World w = loc.getWorld();
        if (w == null) {
            return false;
        }
        Location feet = loc.clone().add(0, FEET_COLLISION_EPSILON, 0);
        return !BlockCollisionHelper.worldVolumeOccupied(w, BlockCollisionHelper.playerBounds(feet));
    }

    /** Bloc sur lequel on peut vraiment se tenir (pas herbe/fleurs passables). */
    private boolean canSupportStep(Block b) {
        return BlockCollisionHelper.blockBlocksMovement(b);
    }

    private void captureExternalVelocity() {
        Vector v = entity.getVelocity();
        if (v == null) return;
        if (v.lengthSquared() < 1.0e-6) return;

        // Clamp to sane values so huge velocities do not destabilize step-solver.
        double x = Math.max(-1.2, Math.min(1.2, v.getX()));
        double y = Math.max(-0.9, Math.min(0.5, v.getY()));
        double z = Math.max(-1.2, Math.min(1.2, v.getZ()));
        if (Math.abs(y) < 0.03) {
            y = 0.0;
        }
        // Prevent "super jumps": ignore extra positive Y from self-motion while our own jump is in flight,
        // but preserve real knockback impulses.
        if ((manualJumpTicks > 0 || upwardVelocityGuardTicks > 0)
                && knockbackPriorityTicks <= 0 && y > 0.0) {
            y = 0.0;
        }
        // Lateral unstuck: NMS often feeds spurious upward velocity while sliding along walls — never stack it.
        if (lateralUnstuckMode && knockbackPriorityTicks <= 0 && y > 0.0) {
            y = 0.0;
        }
        if (hasSolidGroundForPhysics(entity.getLocation()) && y > 0.0) {
            y = 0.0;
        }

        simulatedHorizontalVelocity.setX(simulatedHorizontalVelocity.getX() + x);
        simulatedHorizontalVelocity.setZ(simulatedHorizontalVelocity.getZ() + z);
        simulatedVerticalVelocity += y;
        if ((manualJumpTicks > 0 || upwardVelocityGuardTicks > 0)
                && knockbackPriorityTicks <= 0 && simulatedVerticalVelocity > settings.getJumpVelocity()) {
            simulatedVerticalVelocity = settings.getJumpVelocity();
        }
        if (lateralUnstuckMode && knockbackPriorityTicks <= 0 && simulatedVerticalVelocity > settings.getJumpVelocity()) {
            simulatedVerticalVelocity = settings.getJumpVelocity();
        }
        if (lateralUnstuckMode && knockbackPriorityTicks <= 0 && hasSolidGroundForPhysics(entity.getLocation())
                && simulatedVerticalVelocity > 0.0) {
            simulatedVerticalVelocity = 0.0;
        }
        entity.setVelocity(new Vector(0, 0, 0));
    }

    private void applyFriction(boolean onGround, boolean inWater) {
        double friction = onGround ? 0.62 : 0.91;
        if (inWater) {
            friction = onGround ? 0.78 : 0.84;
        }
        simulatedHorizontalVelocity.setX(simulatedHorizontalVelocity.getX() * friction);
        simulatedHorizontalVelocity.setZ(simulatedHorizontalVelocity.getZ() * friction);
        if (Math.abs(simulatedHorizontalVelocity.getX()) < 1.0e-3) simulatedHorizontalVelocity.setX(0.0);
        if (Math.abs(simulatedHorizontalVelocity.getZ()) < 1.0e-3) simulatedHorizontalVelocity.setZ(0.0);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void faceDirection(Vector dir) {
        if (dir.lengthSquared() < 1.0e-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float currentPitch = entity.getLocation().getPitch();
        // During path movement we want a mostly horizontal gaze, not stale up/down pitch.
        float targetPitch = 0.0f;
        float smoothedPitch = currentPitch + (targetPitch - currentPitch) * 0.35f;
        applyEntityRotation(yaw, smoothedPitch);
    }

    private void faceToward(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) return;
        faceDirection(dir);
    }

    private void applyEntityRotation(float yaw, float pitch) {
        entity.setRotation(yaw, pitch);
        if (entity instanceof Player player) {
            NmsFakePlayerFactory.syncHeadBodyRotation(player, yaw, pitch);
        }
    }

    // Legacy non-player jump injector removed: it conflicted with collision-based step-up logic.
}
