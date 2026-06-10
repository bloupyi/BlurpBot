# BlurpBot Plugin API

Ce guide montre comment piloter un bot depuis un autre plugin (goal manuel, settings, skin, etc.).

## Recuperer `BotManager`

```java
BlurpBot blurpBot = JavaPlugin.getPlugin(BlurpBot.class);
BotManager manager = blurpBot.getBotManager();
```

## Detecter un faux joueur (NMS)

Pour savoir si un `Player` est un faux joueur cree par BlurpBot (connexion NMS en memoire, pas un client Minecraft reel) :

```java
import fr.bloup.blurpbot.api.BlurpBotPlayers;

if (BlurpBotPlayers.isFakePlayer(player)) {
    // ex. filtrer des listeners globaux, anti-cheat, messages, etc.
}
```

- `player == null` → `false`.
- Les bots en **fallback zombie** ne sont pas des `Player` : cette methode ne s’applique pas (utiliser `BotManager.getBot(entity.getUniqueId())` si le bot est enregistre par ce plugin).

## Creer un bot

```java
BotSettings settings = blurpBot.getDefaultBotSettingsCopy();
settings.apply("stop-range", "2.1");
BotPlayer bot = manager.createBasicBot(spawnLocation, settings, "aggressive_btree");
UUID botId = bot.getId();
```

## Goal manuel

### Fixer une cible logique

```java
manager.setManualGoal(botId, GoalTargetKind.CLOSEST_PLAYER, 180);
// autres kinds: CLOSEST_ENTITY, CLOSEST_MONSTER, CURRENT_TARGET, NONE
```

### Fixer une location

```java
Location target = new Location(world, 120.5, 64, -33.5);
manager.setManualGoalLocation(botId, target, 200);
```

### Nettoyer le goal script

```java
manager.clearManualGoal(botId);
```

## Modifier des settings a chaud

### Une cle

```java
manager.setSetting(botId, "attack-range", "3.1");
manager.setSetting(botId, BotSettingKey.STOP_RANGE, "2.4");
manager.setSetting(botId, "tab-visible", "false");
```

### Plusieurs settings d'un coup

```java
BotSettings patch = bot.getSettings().copy();
patch.apply("attack-cooldown-ms", "450");
patch.apply("priority-attack", "125");
manager.applySettings(botId, patch, false);
```

## Changer le script de comportement

```java
manager.setSetting(botId, "behavior-tree", "trees/aggressive.bbot");
// BEHAVIOR_TREE declenche automatiquement reloadBehaviorTree()
```

## Skin

```java
manager.setSkin(botId, "MySkinProfile", base64Texture, base64Signature);
```

Note: cette API met a jour les settings de skin du bot courant. Selon l'implementation fake-player NMS, le rendu visuel peut necessiter un respawn pour un refresh complet.

## Manipuler le bot directement (`Bot`)

`BotPlayer` implemente l'interface `Bot`. En conservant la reference retournee par
`createBasicBot` (ou via `manager.getBot(id)`), tu pilotes le bot comme tu veux.

### Modele d'execution : tout passe par les pas du brain

Les commandes de **pilotage** (`moveTo`, `stop`, `lookAt`, `setRotation`, `jump`) et d'**action**
(`attack`, `attackWithCooldown`, `swingHand`, `setPose`) ne s'executent pas immediatement : elles
sont mises en file et jouees au prochain `tick()` du bot, dans la meme fenetre physique que l'arbre
de decision. Aucun pas physique n'est force hors tick. Consequences :

- Un seul pas de mouvement est joue par tick (« dernier gagne ») : appeler `moveTo` plusieurs fois
  entre deux ticks ne telepporte pas le bot, seule la derniere cible compte.
- Un `moveTo`/`stop` fait **ceder l'arbre de decision** pour ce tick (override manuel). Les actions
  (`lookAt`, `attack`…) coexistent avec l'IA.
- Comme l'execution est differee, ces methodes renvoient `void`. Pour decider avant d'agir, utilise
  les **requetes immediates** : `hasLineOfSight(target)`, `isWithinMeleeRange(target, range)`.

Les **mutations d'etat** (`teleport`, `setFakeVelocity`, `addFakeVelocity`, `applyImpulse`,
`setName`, `setHealth`, `setGravity`, `setSneaking`, `setSprinting`) et les **getters** s'appliquent
immediatement.

### Exemple

```java
Bot bot = manager.getBot(botId);

// Pilotage (joue au prochain tick) :
bot.moveTo(target);                 // ou moveTo(target, stopRange)
bot.lookAt(somePlayer);             // vise les yeux
bot.jump();
if (bot.isWithinMeleeRange(somePlayer, 3.0) && bot.hasLineOfSight(somePlayer)) {
    bot.attackWithCooldown(somePlayer, 550);
}

// Teleport + reset navigation/velocite simulee (immediat) :
bot.teleport(new Location(world, 100, 65, 200));

// « Fake velocity » = velocite physique simulee du faux joueur :
bot.setFakeVelocity(new Vector(0, 0.42, 0));   // saut
bot.addFakeVelocity(new Vector(0.5, 0, 0));     // poussee laterale
Vector v = bot.getFakeVelocity();

// Etat / apparence (immediat) :
bot.setName("Garde");
bot.setHealth(20.0);
bot.setSprinting(true);
```

### Par UUID via `BotManager`

```java
manager.teleport(botId, target);
manager.setFakeVelocity(botId, new Vector(0, 0.42, 0));
manager.addFakeVelocity(botId, new Vector(0.5, 0, 0));
manager.applyKnockback(botId, impulse);
```

## Settings / goals via l'objet `Bot`

`Bot` expose aussi:

- `setManualGoal(...)`
- `setManualGoalLocation(...)`
- `clearManualGoal()`
- `setSetting(...)`
- `applySettings(...)`
- `setSkin(...)`
- `reloadBehaviorTree()`

Cela permet un usage direct si tu conserves la reference du bot retourne par `createBasicBot`.
