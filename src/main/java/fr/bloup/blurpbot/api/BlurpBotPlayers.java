package fr.bloup.blurpbot.api;

import org.bukkit.entity.Player;

import fr.bloup.blurpbot.nms.NmsFakePlayerFactory;

/**
 * Utilitaires pour distinguer les faux joueurs NMS créés par BlurpBot des joueurs réels.
 */
public final class BlurpBotPlayers {
    private BlurpBotPlayers() {}

    /**
     * Indique si ce {@link Player} est un faux joueur généré par BlurpBot (connexion NMS en mémoire),
     * et non un client Minecraft réel.
     */
    public static boolean isFakePlayer(Player player) {
        return NmsFakePlayerFactory.isInMemoryFakePlayer(player);
    }
}
