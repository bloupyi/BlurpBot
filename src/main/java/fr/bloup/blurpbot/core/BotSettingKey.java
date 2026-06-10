package fr.bloup.blurpbot.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum BotSettingKey {
    BEHAVIOR_TREE("behavior-tree"),
    TAB_VISIBLE("tab-visible"),
    SKIN_PROFILE_NAME("skin-profile-name"),
    SKIN_TEXTURE("skin-texture"),
    SKIN_SIGNATURE("skin-signature"),
    ATTACK_RANGE("attack-range"),
    STOP_RANGE("stop-range"),
    ATTACK_COOLDOWN_MS("attack-cooldown-ms"),
    PRIORITY_ATTACK("priority-attack"),
    PRIORITY_MOVE("priority-move"),
    PRIORITY_IDLE("priority-idle"),
    SPRINT_ENABLED("sprint-enabled"),
    KNOCKBACK_ENABLED("knockback-enabled"),
    MOVE_SPEED_SPRINT("move-speed-sprint"),
    MOVE_SPEED_WALK("move-speed-walk"),
    MOVE_SPEED_BACKWARD("move-speed-backward"),
    MOVE_SPEED_CLIMB("move-speed-climb"),
    JUMP_VELOCITY("jump-velocity"),
    STRAFE_JITTER_STRENGTH("strafe-jitter-strength"),
    PATH_MAX_VISITED_NODES("path-max-visited-nodes"),
    PATH_MAX_VISITED_NODES_STUCK("path-max-visited-nodes-stuck"),
    PATH_REPATH_INTERVAL_MS("path-repath-interval-ms"),
    PATH_WALL_PROXIMITY_PENALTY("path-wall-proximity-penalty"),
    PATH_VOID_PENALTY("path-void-penalty"),
    PATH_FALL_RISK_PENALTY("path-fall-risk-penalty"),
    PATH_ENEMY_PROXIMITY_PENALTY("path-enemy-proximity-penalty"),
    PATH_CLIMB_UPWARD_BONUS("path-climb-upward-bonus"),
    PATH_MAX_STEP_UP("path-max-step-up"),
    PATH_MAX_STEP_DOWN("path-max-step-down"),
    PATH_RETRY_ON_FAIL_MS("path-retry-on-fail-ms"),
    STUCK_THRESHOLD_GROUND_TICKS("stuck-threshold-ground-ticks"),
    STUCK_THRESHOLD_CLIMB_TICKS("stuck-threshold-climb-ticks"),
    UNSTUCK_DURATION_MS("unstuck-duration-ms"),
    STEP_SNAP_UP_MAX("step-snap-up-max"),
    STEP_SNAP_DOWN_MAX("step-snap-down-max"),
    EDGE_SLIDE_FORWARD_MIN("edge-slide-forward-min"),
    EDGE_SLIDE_FORWARD_MAX("edge-slide-forward-max"),
    EDGE_SLIDE_LATERAL_MIN("edge-slide-lateral-min"),
    EDGE_SLIDE_LATERAL_MAX("edge-slide-lateral-max"),
    EDGE_SLIDE_SAMPLES("edge-slide-samples"),
    JUMP_INTENT_MIN_DY("jump-intent-min-dy"),
    JUMP_INTENT_MAX_DY("jump-intent-max-dy"),
    JUMP_INTENT_MAX_HORIZ("jump-intent-max-horiz"),
    JUMP_INTENT_TICKS("jump-intent-ticks");

    private final String key;

    BotSettingKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static BotSettingKey fromKey(String raw) {
        if (raw == null) return null;
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (BotSettingKey k : values()) {
            if (k.key.equals(normalized)) return k;
        }
        return null;
    }

    public static List<String> keyNames() {
        return Arrays.stream(values()).map(BotSettingKey::key).toList();
    }
}

