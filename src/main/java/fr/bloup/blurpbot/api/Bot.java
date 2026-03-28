package fr.bloup.blurpbot.api;

import fr.bloup.blurpbot.core.BotSettingKey;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.goal.GoalTargetKind;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.UUID;

public interface Bot {
    UUID getId();
    LivingEntity getEntity();
    BotSettings getSettings();
    void tick();

    void clearManualGoal();
    void setManualGoal(GoalTargetKind kind, int priority);
    void setManualGoalLocation(Location location, int priority);

    boolean setSetting(BotSettingKey key, String value);
    boolean setSetting(String key, String value);
    void applySettings(BotSettings settings);
    void setSkin(String profileName, String texture, String signature);
    void reloadBehaviorTree();
}
