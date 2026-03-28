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

## Utiliser l'objet `Bot` directement

`BotPlayer` implemente l'interface `Bot`, qui expose aussi:

- `setManualGoal(...)`
- `setManualGoalLocation(...)`
- `clearManualGoal()`
- `setSetting(...)`
- `applySettings(...)`
- `setSkin(...)`
- `reloadBehaviorTree()`

Cela permet un usage direct si tu conserves la reference du bot retourne par `createBasicBot`.
