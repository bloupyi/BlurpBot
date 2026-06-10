package fr.bloup.blurpbot.api;

import fr.bloup.blurpbot.core.BotSettingKey;
import fr.bloup.blurpbot.core.BotSettings;
import fr.bloup.blurpbot.goal.GoalTargetKind;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;

import java.util.UUID;

public interface Bot {
    UUID getId();
    LivingEntity getEntity();
    BotSettings getSettings();
    void tick();

    // ----------------------------------------------------------------------------------------
    // État / requêtes
    // ----------------------------------------------------------------------------------------

    /** Position courante du bot (monde + coordonnées + orientation), ou {@code null} si l'entité est invalide. */
    Location getLocation();

    /** Monde courant du bot, ou {@code null} si l'entité est invalide. */
    World getWorld();

    /** {@code true} si l'entité du bot existe encore et n'est pas morte/supprimée. */
    boolean isValid();

    /** Nom (custom name) courant du bot, ou {@code null}. */
    String getName();

    double getHealth();
    double getMaxHealth();

    /** Vélocité Bukkit réelle de l'entité (distincte de {@link #getFakeVelocity()}). */
    Vector getVelocity();

    boolean isOnGround();
    boolean isSneaking();
    boolean isSprinting();

    // ----------------------------------------------------------------------------------------
    // Mouvement / navigation
    //
    // Les commandes de pilotage (moveTo/stop/lookAt/setRotation/jump) et les actions plus bas ne
    // s'exécutent PAS immédiatement : elles sont mises en file et jouées au prochain pas du brain
    // ({@code tick()}), dans la même fenêtre physique que l'arbre de décision. Aucun pas physique
    // n'est donc forcé hors tick. Un {@code moveTo}/{@code stop} fait céder l'arbre ce tick-là
    // (override manuel) et un seul pas de mouvement est joué par tick (« dernier gagne »).
    // ----------------------------------------------------------------------------------------

    /**
     * Téléporte le bot et remet à zéro sa navigation / vélocité simulée pour repartir proprement.
     * Appliqué immédiatement (mutation d'état, pas un pas physique).
     *
     * @return {@code true} si la téléportation a été appliquée.
     */
    boolean teleport(Location location);

    /** Pilote le bot vers {@code target} au prochain tick, avec le {@code stop-range} des réglages. */
    void moveTo(Location target);

    /** Pilote le bot vers {@code target} au prochain tick, s'arrêtant à {@code stopRange} blocs. */
    void moveTo(Location target, double stopRange);

    /** Stoppe le déplacement horizontal et purge l'état de cheminement (au prochain tick). */
    void stop();

    /** Oriente le bot vers une position au prochain tick (avec sync tête/corps NMS). */
    void lookAt(Location target);

    /** Oriente le bot vers une entité au prochain tick (vise les yeux pour les êtres vivants). */
    void lookAt(Entity target);

    /** Fixe l'orientation (yaw/pitch en degrés) au prochain tick. */
    void setRotation(float yaw, float pitch);

    /** Arme un saut : décolle au prochain pas physique si le bot est au sol. */
    void jump();

    // ----------------------------------------------------------------------------------------
    // Vélocité simulée / impulsions
    // ----------------------------------------------------------------------------------------

    /**
     * Vélocité « fake » : la vélocité physique simulée appliquée chaque tick au faux joueur
     * (x/z horizontaux + y vertical), distincte de la vélocité Bukkit de l'entité.
     */
    Vector getFakeVelocity();

    /** Remplace la vélocité simulée (voir {@link #getFakeVelocity()}). */
    void setFakeVelocity(Vector velocity);

    /** Ajoute un delta à la vélocité simulée (voir {@link #getFakeVelocity()}). */
    void addFakeVelocity(Vector velocity);

    /** Applique une impulsion type knockback (priorise la physique externe sur quelques ticks). */
    void applyImpulse(Vector impulse);

    // ----------------------------------------------------------------------------------------
    // Actions / combat
    // ----------------------------------------------------------------------------------------

    /** Attaque une cible au prochain tick si la ligne de vue mêlée est dégagée. */
    void attack(Entity target);

    /** Comme {@link #attack(Entity)} mais soumis à un cooldown propre au bot. */
    void attackWithCooldown(Entity target, long cooldownMs);

    /** Animation de bras au prochain tick (faux joueur uniquement). */
    void swingHand(boolean mainHand);

    /** Change la pose d'affichage au prochain tick (debout, accroupi, nage…). */
    void setPose(Pose pose);

    /** Requête immédiate : {@code true} s'il existe une ligne de vue mêlée dégagée vers la cible. */
    boolean hasLineOfSight(Entity target);

    /** Requête immédiate : {@code true} si la cible est à portée mêlée {@code attackRange} (œil → hitbox). */
    boolean isWithinMeleeRange(Entity target, double attackRange);

    // ----------------------------------------------------------------------------------------
    // Apparence / état mutable
    // ----------------------------------------------------------------------------------------

    /** Change le nom affiché (et le nom de player list pour les faux joueurs). */
    void setName(String name);

    /** Définit les PV du bot (clampé à [0, maxHealth]). @return {@code true} si appliqué. */
    boolean setHealth(double health);

    void setSneaking(boolean sneaking);
    void setSprinting(boolean sprinting);

    /** Active/désactive la gravité Bukkit de l'entité. */
    void setGravity(boolean gravity);

    // ----------------------------------------------------------------------------------------
    // Buts scriptés (déjà existants)
    // ----------------------------------------------------------------------------------------

    void clearManualGoal();
    void setManualGoal(GoalTargetKind kind, int priority);
    void setManualGoalLocation(Location location, int priority);

    boolean setSetting(BotSettingKey key, String value);
    boolean setSetting(String key, String value);
    void applySettings(BotSettings settings);
    void setSkin(String profileName, String texture, String signature);
    void reloadBehaviorTree();
}
