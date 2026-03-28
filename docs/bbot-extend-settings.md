# Guide développeur — ajouter un nouveau réglage (`BotSettings`)

Un **réglage** est une valeur stockée dans **`BotSettings`**, adressable par une **clé kebab-case** (ex. `attack-range`), modifiable via :

- `config.yml` (`bot.defaults`, profils),
- `/sb config <uuid> <clé> <valeur>`,
- directives **`set`** dans un `.bbot`,
- références **`$clé`** dans le script (lecture à l’exécution).

Ce guide liste les fichiers à modifier pour ajouter un nouveau champ de façon cohérente.

## 1. `BotSettingKey`

Fichier : `fr.bloup.blurpbot.core.BotSettingKey`.

Ajouter une constante avec la **clé publique** en kebab-case :

```java
MY_NEW_TUNING("my-new-tuning"),
```

- Le **nom de l’énum** est en `UPPER_SNAKE_CASE`.
- La **chaîne** est celle utilisée partout côté config / commandes / `.bbot`.

`fromKey()` et `keyNames()` (tab completion `/sb config`) se mettent à jour automatiquement.

## 2. `BotSettings`

Fichier : `fr.bloup.blurpbot.core.BotSettings`.

1. **Champ** avec valeur par défaut raisonnable (Lombok `@Getter` / `@Setter` sont déjà sur la classe).
2. **`copy()`** — copier le champ vers l’objet cloné.
3. **`copyStateFrom(BotSettings o)`** — copier le champ depuis `o`.

## 3. `apply(BotSettingKey key, String value)`

Toujours dans **`BotSettings`**, ajouter un **`case`** dans le `switch` de `apply` :

| Type Java | Parsing typique |
|-----------|-----------------|
| `double` | `Double.parseDouble(value)` |
| `int` | `Integer.parseInt(value)` |
| `long` | `Long.parseLong(value)` |
| `boolean` | `parseBooleanStrict(value)` (déjà présent ; `true` / `false` uniquement) |
| `String` | affectation directe `this.xxx = value` (comme `BEHAVIOR_TREE`) |

En cas de **`NumberFormatException`**, `apply` retourne **`false`** (clé ou valeur invalide).

La surcharge **`apply(String key, String value)`** délègue à `BotSettingKey.fromKey` — rien à changer si la clé est enregistrée dans l’énum.

## 4. Configuration YAML

**Optionnel mais recommandé** : ajouter une valeur par défaut sous `bot.defaults` dans **`src/main/resources/config.yml`**.

`BlurpBot.mergeBotSectionIntoSettings` fusionne les clés du YAML dans `BotSettings` via **`apply(key, String.valueOf(...))`** : aucun code Java supplémentaire n’est nécessaire pour la lecture des **defaults** et **profils**, tant que la clé correspond à `BotSettingKey`.

## 5. Commande `/sb config`

Fichier : `fr.bloup.blurpbot.commands.subs.sb.ConfigBotSubCommand`.

- La **liste des clés** pour l’auto-complétion vient déjà de **`BotSettingKey.keyNames()`**.
- Pour l’affichage **sans argument** (`/sb config <uuid>`), ajouter une ligne du type :

```java
sender.sendMessage(" - my-new-tuning=" + settings.getMyNewTuning());
```

(même ordre / style que les lignes existantes).

## 6. Utiliser le réglage dans le code métier

Injecter ou lire **`context.settings()`** (IA, contrôleur, pathfinding, etc.) :

```java
double v = context.settings().getMyNewTuning();
```

Pas besoin de toucher au parseur `.bbot` pour que **`set my-new-tuning 1.5`** et **`$my-new-tuning`** fonctionnent, **sauf** si tu veux un comportement spécial côté **`priority:`** (voir ci-dessous).

## 7. Cas particulier : `priority:` dans un `.bbot`

Dans **`BbotTreeBuilder.buildPrioritySource`**, seules les références **`$priority-attack`**, **`$priority-move`**, **`$priority-idle`** sont autorisées pour un **`priority:`** dynamique.

Si tu ajoutes un **nouveau réglage de priorité** (ex. `priority-flee`), il faut aussi :

1. l’ajouter à **`BotSettingKey`** et **`BotSettings`** comme ci-dessus ;
2. étendre **`isPriorityKey`** et **`priorityFromSettings`** dans **`BbotTreeBuilder`** pour ce cas.

Les autres réglages (double, int, etc.) utilisés uniquement dans des **`leaf`** / **`condition`** via **`$clé`** passent par **`BotSettingKey.fromKey`** dans ton code d’instanciation des nœuds (comme aujourd’hui pour `attack-range`, etc.).

## 8. Checklist rapide

| Étape | Fichier / action |
|-------|------------------|
| Clé stable kebab-case | `BotSettingKey` |
| Champ + défaut + getters Lombok | `BotSettings` |
| `copy()` + `copyStateFrom()` | `BotSettings` |
| `case` dans `apply` | `BotSettings` |
| Défaut YAML (optionnel) | `config.yml` |
| Ligne d’affichage `/sb config` | `ConfigBotSubCommand` |
| Logique gameplay | contrôleur, pathfinding, feuilles, … |

## 9. Vérifications

1. `mvn compile`
2. `/sb config <uuid> my-new-tuning <valeur>` puis relire avec `/sb config <uuid>`
3. **`/bb reload`** après changement de `config.yml` ou du `.bbot` avec **`set`**

## Voir aussi

- **`docs/bbot-script.md`** — clés utilisables dans les `.bbot` (`set`, `$`)
- **`docs/bbot-extend-nodes.md`** — nouvelles conditions / feuilles Java
