package fr.bloup.blurpbot.listeners;

import fr.bloup.blurpbot.nms.NmsFakePlayerFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Hides join message for in-memory fake players spawned by BlurpBot.
 */
public class BotJoinMessageListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (NmsFakePlayerFactory.isInMemoryFakePlayer(event.getPlayer())) {
            event.joinMessage(null);
        }
    }
}
