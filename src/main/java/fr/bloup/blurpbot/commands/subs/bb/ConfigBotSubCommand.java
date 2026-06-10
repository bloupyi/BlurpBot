package fr.bloup.blurpbot.commands.subs.bb;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import fr.bloup.blurpbot.BlurpBot;
import fr.bloup.blurpbot.commands.PermissionedCommand;
import fr.bloup.blurpbot.core.BotPlayer;
import fr.bloup.blurpbot.core.BotSettingKey;
import fr.bloup.blurpbot.core.BotSettings;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ConfigBotSubCommand implements TabExecutor, PermissionedCommand {
    private static final List<String> KEYS = BotSettingKey.keyNames();
    private final BlurpBot plugin;

    @Override
    public String getPermission() {
        return "bb.admin";
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMsgPrefix() + "Usage: /sb config <uuid> [key] [value]");
            return true;
        }

        UUID id;
        try {
            id = UUID.fromString(args[0]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(plugin.getMsgPrefix() + "Invalid UUID.");
            return true;
        }

        BotPlayer bot = plugin.getBotManager().getBot(id);
        if (bot == null) {
            sender.sendMessage(plugin.getMsgPrefix() + "Bot not found.");
            return true;
        }

        BotSettings settings = bot.getSettings();
        if (args.length == 1) {
            sender.sendMessage(plugin.getMsgPrefix() + "Bot " + id + " settings:");
            sender.sendMessage(" - attack-range=" + settings.getAttackRange());
            sender.sendMessage(" - stop-range=" + settings.getStopRange());
            sender.sendMessage(" - attack-cooldown-ms=" + settings.getAttackCooldownMs());
            sender.sendMessage(" - priority-attack=" + settings.getPriorityAttack());
            sender.sendMessage(" - priority-move=" + settings.getPriorityMove());
            sender.sendMessage(" - priority-idle=" + settings.getPriorityIdle());
            sender.sendMessage(" - behavior-tree=" + settings.getBehaviorTreeFile());
            sender.sendMessage(" - tab-visible=" + settings.isTabVisible());
            sender.sendMessage(" - skin-profile-name=" + settings.getSkinProfileName());
            sender.sendMessage(" - skin-texture=" + (settings.getSkinTexture() == null ? "<unset>" : "<set>"));
            sender.sendMessage(" - skin-signature=" + (settings.getSkinSignature() == null ? "<unset>" : "<set>"));
            sender.sendMessage(" - sprint-enabled=" + settings.isSprintEnabled());
            sender.sendMessage(" - knockback-enabled=" + settings.isKnockbackEnabled());
            sender.sendMessage(" - move-speed-sprint=" + settings.getMoveSpeedSprint());
            sender.sendMessage(" - move-speed-walk=" + settings.getMoveSpeedWalk());
            sender.sendMessage(" - move-speed-backward=" + settings.getMoveSpeedBackward());
            sender.sendMessage(" - move-speed-climb=" + settings.getMoveSpeedClimb());
            sender.sendMessage(" - jump-velocity=" + settings.getJumpVelocity());
            sender.sendMessage(" - strafe-jitter-strength=" + settings.getStrafeJitterStrength());
            sender.sendMessage(" - path-max-visited-nodes=" + settings.getPathMaxVisitedNodes());
            sender.sendMessage(" - path-max-visited-nodes-stuck=" + settings.getPathMaxVisitedNodesStuck());
            sender.sendMessage(" - path-repath-interval-ms=" + settings.getPathRepathIntervalMs());
            sender.sendMessage(" - path-wall-proximity-penalty=" + settings.getPathWallProximityPenalty());
            sender.sendMessage(" - path-void-penalty=" + settings.getPathVoidPenalty());
            sender.sendMessage(" - path-fall-risk-penalty=" + settings.getPathFallRiskPenalty());
            sender.sendMessage(" - path-enemy-proximity-penalty=" + settings.getPathEnemyProximityPenalty());
            sender.sendMessage(" - path-climb-upward-bonus=" + settings.getPathClimbUpwardBonus());
            sender.sendMessage(" - path-max-step-up=" + settings.getPathMaxStepUp());
            sender.sendMessage(" - path-max-step-down=" + settings.getPathMaxStepDown());
            sender.sendMessage(" - path-retry-on-fail-ms=" + settings.getPathRetryOnFailMs());
            sender.sendMessage(" - stuck-threshold-ground-ticks=" + settings.getStuckThresholdGroundTicks());
            sender.sendMessage(" - stuck-threshold-climb-ticks=" + settings.getStuckThresholdClimbTicks());
            sender.sendMessage(" - unstuck-duration-ms=" + settings.getUnstuckDurationMs());
            sender.sendMessage(" - step-snap-up-max=" + settings.getStepSnapUpMax());
            sender.sendMessage(" - step-snap-down-max=" + settings.getStepSnapDownMax());
            sender.sendMessage(" - edge-slide-forward-min=" + settings.getEdgeSlideForwardMin());
            sender.sendMessage(" - edge-slide-forward-max=" + settings.getEdgeSlideForwardMax());
            sender.sendMessage(" - edge-slide-lateral-min=" + settings.getEdgeSlideLateralMin());
            sender.sendMessage(" - edge-slide-lateral-max=" + settings.getEdgeSlideLateralMax());
            sender.sendMessage(" - edge-slide-samples=" + settings.getEdgeSlideSamples());
            sender.sendMessage(" - jump-intent-min-dy=" + settings.getJumpIntentMinDy());
            sender.sendMessage(" - jump-intent-max-dy=" + settings.getJumpIntentMaxDy());
            sender.sendMessage(" - jump-intent-max-horiz=" + settings.getJumpIntentMaxHoriz());
            sender.sendMessage(" - jump-intent-ticks=" + settings.getJumpIntentTicks());
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMsgPrefix() + "Usage: /sb config <uuid> <key> <value>");
            return true;
        }

        String key = args[1];
        BotSettingKey settingKey = BotSettingKey.fromKey(key);
        String value = args[2];
        boolean ok = settings.apply(settingKey, value);
        if (!ok) {
            sender.sendMessage(plugin.getMsgPrefix() + "Invalid key/value. Keys: " + String.join(", ", KEYS));
            return true;
        }

        sender.sendMessage(plugin.getMsgPrefix() + "Updated bot " + id + ": " + key + "=" + value + " (temporary)");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return plugin.getBotManager().getBots().stream()
                    .map(BotPlayer::getId)
                    .map(UUID::toString)
                    .filter(id -> id.startsWith(args[0]))
                    .toList();
        }
        if (args.length == 2) {
            return KEYS.stream().filter(k -> k.startsWith(args[1])).toList();
        }
        return List.of();
    }
}

