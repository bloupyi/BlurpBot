package fr.bloup.blurpbot.perception;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public class BotPerception {
    private List<Player> nearbyPlayers = List.of();
    private Player target;
    private LivingEntity closestLivingEntity;
    private LivingEntity closestMonster;
    private boolean underAttack;

    public List<Player> getNearbyPlayers() {
        return nearbyPlayers;
    }

    public void setNearbyPlayers(List<Player> nearbyPlayers) {
        this.nearbyPlayers = (nearbyPlayers == null) ? List.of() : List.copyOf(nearbyPlayers);
        this.target = this.nearbyPlayers.isEmpty() ? null : this.nearbyPlayers.getFirst();
    }

    /**
     * Joueur le plus proche dans la portée de perception (identique à {@link #getTarget()}).
     */
    public Player getClosestPlayer() {
        return target;
    }

    public void setClosestLivingEntity(LivingEntity closestLivingEntity) {
        this.closestLivingEntity = closestLivingEntity;
    }

    public LivingEntity getClosestLivingEntity() {
        return closestLivingEntity;
    }

    public void setClosestMonster(LivingEntity closestMonster) {
        this.closestMonster = closestMonster;
    }

    public LivingEntity getClosestMonster() {
        return closestMonster;
    }

    public Player getTarget() {
        return target;
    }

    public boolean isUnderAttack() {
        return underAttack;
    }

    public void setUnderAttack(boolean underAttack) {
        this.underAttack = underAttack;
    }
}
