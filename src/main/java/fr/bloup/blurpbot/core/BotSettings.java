package fr.bloup.blurpbot.core;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BotSettings {
    /** Relative path under the plugin data folder (e.g. trees/default.bbot). */
    private String behaviorTreeFile = "trees/default.bbot";
    /**
     * Optional Mojang profile name used only to fetch/apply skin data.
     * This is independent from the fake player visible name.
     */
    private boolean tabVisible = true;
    private String skinProfileName;
    /** Base64 texture payload for the GameProfile "textures" property (optional). */
    private String skinTexture;
    /** Optional signature matching skinTexture (for signed textures). */
    private String skinSignature;
    private double attackRange = 2.6;
    private double stopRange = 2.4;
    private long attackCooldownMs = 550L;
    private int priorityAttack = 100;
    private int priorityMove = 50;
    private int priorityIdle = 1;
    private boolean sprintEnabled = true;
    private double moveSpeedSprint = 0.280;
    private double moveSpeedWalk = 0.216;
    private double moveSpeedBackward = 0.11;
    private double moveSpeedClimb = 0.11;
    private double jumpVelocity = 0.48;
    private double strafeJitterStrength = 0.08;
    private int pathMaxVisitedNodes = 1400;
    /** Budget A* réduit quand le bot est considéré comme bloqué (évite d’exploser le MSPT en boucle). */
    private int pathMaxVisitedNodesStuck = 480;
    private long pathRepathIntervalMs = 350L;
    private double pathWallProximityPenalty = 0.14;
    private double pathVoidPenalty = 2.5;
    private double pathFallRiskPenalty = 0.7;
    private double pathEnemyProximityPenalty = 0.35;
    private double pathClimbUpwardBonus = 0.38;
    private int pathMaxStepUp = 1;
    private int pathMaxStepDown = 2;
    private long pathRetryOnFailMs = 280L;
    private int stuckThresholdGroundTicks = 4;
    private int stuckThresholdClimbTicks = 28;
    private long unstuckDurationMs = 850L;
    private double stepSnapUpMax = 0.66;
    private double stepSnapDownMax = 0.24;
    private double edgeSlideForwardMin = 0.08;
    private double edgeSlideForwardMax = 0.20;
    private double edgeSlideLateralMin = 0.10;
    private double edgeSlideLateralMax = 0.22;
    private int edgeSlideSamples = 3;
    private double jumpIntentMinDy = 0.48;
    private double jumpIntentMaxDy = 1.35;
    private double jumpIntentMaxHoriz = 1.75;
    private int jumpIntentTicks = 8;

    public BotSettings copy() {
        BotSettings out = new BotSettings();
        out.behaviorTreeFile = this.behaviorTreeFile;
        out.tabVisible = this.tabVisible;
        out.skinProfileName = this.skinProfileName;
        out.skinTexture = this.skinTexture;
        out.skinSignature = this.skinSignature;
        out.attackRange = this.attackRange;
        out.stopRange = this.stopRange;
        out.attackCooldownMs = this.attackCooldownMs;
        out.priorityAttack = this.priorityAttack;
        out.priorityMove = this.priorityMove;
        out.priorityIdle = this.priorityIdle;
        out.sprintEnabled = this.sprintEnabled;
        out.moveSpeedSprint = this.moveSpeedSprint;
        out.moveSpeedWalk = this.moveSpeedWalk;
        out.moveSpeedBackward = this.moveSpeedBackward;
        out.moveSpeedClimb = this.moveSpeedClimb;
        out.jumpVelocity = this.jumpVelocity;
        out.strafeJitterStrength = this.strafeJitterStrength;
        out.pathMaxVisitedNodes = this.pathMaxVisitedNodes;
        out.pathMaxVisitedNodesStuck = this.pathMaxVisitedNodesStuck;
        out.pathRepathIntervalMs = this.pathRepathIntervalMs;
        out.pathWallProximityPenalty = this.pathWallProximityPenalty;
        out.pathVoidPenalty = this.pathVoidPenalty;
        out.pathFallRiskPenalty = this.pathFallRiskPenalty;
        out.pathEnemyProximityPenalty = this.pathEnemyProximityPenalty;
        out.pathClimbUpwardBonus = this.pathClimbUpwardBonus;
        out.pathMaxStepUp = this.pathMaxStepUp;
        out.pathMaxStepDown = this.pathMaxStepDown;
        out.pathRetryOnFailMs = this.pathRetryOnFailMs;
        out.stuckThresholdGroundTicks = this.stuckThresholdGroundTicks;
        out.stuckThresholdClimbTicks = this.stuckThresholdClimbTicks;
        out.unstuckDurationMs = this.unstuckDurationMs;
        out.stepSnapUpMax = this.stepSnapUpMax;
        out.stepSnapDownMax = this.stepSnapDownMax;
        out.edgeSlideForwardMin = this.edgeSlideForwardMin;
        out.edgeSlideForwardMax = this.edgeSlideForwardMax;
        out.edgeSlideLateralMin = this.edgeSlideLateralMin;
        out.edgeSlideLateralMax = this.edgeSlideLateralMax;
        out.edgeSlideSamples = this.edgeSlideSamples;
        out.jumpIntentMinDy = this.jumpIntentMinDy;
        out.jumpIntentMaxDy = this.jumpIntentMaxDy;
        out.jumpIntentMaxHoriz = this.jumpIntentMaxHoriz;
        out.jumpIntentTicks = this.jumpIntentTicks;
        return out;
    }

    /** Copie tous les champs depuis un autre réglage (même instance référencée par le contrôleur). */
    public void copyStateFrom(BotSettings o) {
        if (o == null) {
            return;
        }
        this.behaviorTreeFile = o.behaviorTreeFile;
        this.tabVisible = o.tabVisible;
        this.skinProfileName = o.skinProfileName;
        this.skinTexture = o.skinTexture;
        this.skinSignature = o.skinSignature;
        this.attackRange = o.attackRange;
        this.stopRange = o.stopRange;
        this.attackCooldownMs = o.attackCooldownMs;
        this.priorityAttack = o.priorityAttack;
        this.priorityMove = o.priorityMove;
        this.priorityIdle = o.priorityIdle;
        this.sprintEnabled = o.sprintEnabled;
        this.moveSpeedSprint = o.moveSpeedSprint;
        this.moveSpeedWalk = o.moveSpeedWalk;
        this.moveSpeedBackward = o.moveSpeedBackward;
        this.moveSpeedClimb = o.moveSpeedClimb;
        this.jumpVelocity = o.jumpVelocity;
        this.strafeJitterStrength = o.strafeJitterStrength;
        this.pathMaxVisitedNodes = o.pathMaxVisitedNodes;
        this.pathMaxVisitedNodesStuck = o.pathMaxVisitedNodesStuck;
        this.pathRepathIntervalMs = o.pathRepathIntervalMs;
        this.pathWallProximityPenalty = o.pathWallProximityPenalty;
        this.pathVoidPenalty = o.pathVoidPenalty;
        this.pathFallRiskPenalty = o.pathFallRiskPenalty;
        this.pathEnemyProximityPenalty = o.pathEnemyProximityPenalty;
        this.pathClimbUpwardBonus = o.pathClimbUpwardBonus;
        this.pathMaxStepUp = o.pathMaxStepUp;
        this.pathMaxStepDown = o.pathMaxStepDown;
        this.pathRetryOnFailMs = o.pathRetryOnFailMs;
        this.stuckThresholdGroundTicks = o.stuckThresholdGroundTicks;
        this.stuckThresholdClimbTicks = o.stuckThresholdClimbTicks;
        this.unstuckDurationMs = o.unstuckDurationMs;
        this.stepSnapUpMax = o.stepSnapUpMax;
        this.stepSnapDownMax = o.stepSnapDownMax;
        this.edgeSlideForwardMin = o.edgeSlideForwardMin;
        this.edgeSlideForwardMax = o.edgeSlideForwardMax;
        this.edgeSlideLateralMin = o.edgeSlideLateralMin;
        this.edgeSlideLateralMax = o.edgeSlideLateralMax;
        this.edgeSlideSamples = o.edgeSlideSamples;
        this.jumpIntentMinDy = o.jumpIntentMinDy;
        this.jumpIntentMaxDy = o.jumpIntentMaxDy;
        this.jumpIntentMaxHoriz = o.jumpIntentMaxHoriz;
        this.jumpIntentTicks = o.jumpIntentTicks;
    }

    public boolean apply(BotSettingKey key, String value) {
        if (key == null) return false;
        try {
            switch (key) {
                case BEHAVIOR_TREE -> this.behaviorTreeFile = value;
                case TAB_VISIBLE -> this.tabVisible = parseBooleanStrict(value);
                case SKIN_PROFILE_NAME -> this.skinProfileName = normalizeNullable(value);
                case SKIN_TEXTURE -> this.skinTexture = normalizeNullable(value);
                case SKIN_SIGNATURE -> this.skinSignature = normalizeNullable(value);
                case ATTACK_RANGE -> this.attackRange = Double.parseDouble(value);
                case STOP_RANGE -> this.stopRange = Double.parseDouble(value);
                case ATTACK_COOLDOWN_MS -> this.attackCooldownMs = Long.parseLong(value);
                case PRIORITY_ATTACK -> this.priorityAttack = Integer.parseInt(value);
                case PRIORITY_MOVE -> this.priorityMove = Integer.parseInt(value);
                case PRIORITY_IDLE -> this.priorityIdle = Integer.parseInt(value);
                case SPRINT_ENABLED -> this.sprintEnabled = parseBooleanStrict(value);
                case MOVE_SPEED_SPRINT -> this.moveSpeedSprint = Double.parseDouble(value);
                case MOVE_SPEED_WALK -> this.moveSpeedWalk = Double.parseDouble(value);
                case MOVE_SPEED_BACKWARD -> this.moveSpeedBackward = Double.parseDouble(value);
                case MOVE_SPEED_CLIMB -> this.moveSpeedClimb = Double.parseDouble(value);
                case JUMP_VELOCITY -> this.jumpVelocity = Double.parseDouble(value);
                case STRAFE_JITTER_STRENGTH -> this.strafeJitterStrength = Double.parseDouble(value);
                case PATH_MAX_VISITED_NODES -> this.pathMaxVisitedNodes = Integer.parseInt(value);
                case PATH_MAX_VISITED_NODES_STUCK -> this.pathMaxVisitedNodesStuck = Integer.parseInt(value);
                case PATH_REPATH_INTERVAL_MS -> this.pathRepathIntervalMs = Long.parseLong(value);
                case PATH_WALL_PROXIMITY_PENALTY -> this.pathWallProximityPenalty = Double.parseDouble(value);
                case PATH_VOID_PENALTY -> this.pathVoidPenalty = Double.parseDouble(value);
                case PATH_FALL_RISK_PENALTY -> this.pathFallRiskPenalty = Double.parseDouble(value);
                case PATH_ENEMY_PROXIMITY_PENALTY -> this.pathEnemyProximityPenalty = Double.parseDouble(value);
                case PATH_CLIMB_UPWARD_BONUS -> this.pathClimbUpwardBonus = Double.parseDouble(value);
                case PATH_MAX_STEP_UP -> this.pathMaxStepUp = Integer.parseInt(value);
                case PATH_MAX_STEP_DOWN -> this.pathMaxStepDown = Integer.parseInt(value);
                case PATH_RETRY_ON_FAIL_MS -> this.pathRetryOnFailMs = Long.parseLong(value);
                case STUCK_THRESHOLD_GROUND_TICKS -> this.stuckThresholdGroundTicks = Integer.parseInt(value);
                case STUCK_THRESHOLD_CLIMB_TICKS -> this.stuckThresholdClimbTicks = Integer.parseInt(value);
                case UNSTUCK_DURATION_MS -> this.unstuckDurationMs = Long.parseLong(value);
                case STEP_SNAP_UP_MAX -> this.stepSnapUpMax = Double.parseDouble(value);
                case STEP_SNAP_DOWN_MAX -> this.stepSnapDownMax = Double.parseDouble(value);
                case EDGE_SLIDE_FORWARD_MIN -> this.edgeSlideForwardMin = Double.parseDouble(value);
                case EDGE_SLIDE_FORWARD_MAX -> this.edgeSlideForwardMax = Double.parseDouble(value);
                case EDGE_SLIDE_LATERAL_MIN -> this.edgeSlideLateralMin = Double.parseDouble(value);
                case EDGE_SLIDE_LATERAL_MAX -> this.edgeSlideLateralMax = Double.parseDouble(value);
                case EDGE_SLIDE_SAMPLES -> this.edgeSlideSamples = Integer.parseInt(value);
                case JUMP_INTENT_MIN_DY -> this.jumpIntentMinDy = Double.parseDouble(value);
                case JUMP_INTENT_MAX_DY -> this.jumpIntentMaxDy = Double.parseDouble(value);
                case JUMP_INTENT_MAX_HORIZ -> this.jumpIntentMaxHoriz = Double.parseDouble(value);
                case JUMP_INTENT_TICKS -> this.jumpIntentTicks = Integer.parseInt(value);
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public boolean apply(String key, String value) {
        return apply(BotSettingKey.fromKey(key), value);
    }

    private boolean parseBooleanStrict(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new NumberFormatException("Expected true/false");
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

