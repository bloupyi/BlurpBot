# Script d’arbre de comportement `.bbot`

Ce document décrit le format **BlurpBot** pour les fichiers `.bbot` : syntaxe, blocs supportés, intégration config et commandes.

## Emplacement et chargement

- Fichier relatif au **dossier du plugin** (ex. `plugins/BlurpBot/trees/default.bbot`).
- Chemin défini par `**behavior-tree`** dans `config.yml` (`bot.defaults.behavior-tree`, surcharge possible dans un **profil** sous `bot.profiles.<nom>`.
- Au premier lancement, `trees/default.bbot` et `trees/aggressive.bbot` peuvent être extraits du JAR si absents.
- Si le fichier sur disque est absent ou invalide, le plugin charge le **même** `trees/default.bbot` **depuis le JAR** (source unique avec les resources). En dernier recours seulement : arbre minimal idle.

## Structure générale

Un fichier `.bbot` contient :

1. (optionnel) une ligne `**version`** ;
2. (optionnel) des directives `**set**` ;
3. un bloc `**bot "nom"**` avec l’arbre.

Les commentaires commencent par `#` jusqu’à la fin de la ligne.

## `version`

```text
version 1
```

Numéro de version du format, réservé pour la compatibilité future. Aujourd’hui il est parsé mais **n’impose pas** de comportement particulier.

## `set` — écrire dans les réglages au chargement

```text
set <clé-kebab> <valeur>
```

- Applique une valeur aux `**BotSettings**` au moment du chargement du fichier (comme `config.yml` ou `/bb config <uuid> <clé> <valeur>`).
- Les clés reconnues sont celles de l’énumération interne (voir [Clés de réglage](#clés-de-réglage-botsettings)).
- Exemple : `set priority-attack 110`

## `bot "nom"` — nom d’affichage

```text
bot "MonBot" {
  ...
}
```

Le **nom** entre guillemets (ou identifiant sans guillemets) est utilisé pour le **nom affiché** de l’entité (nom personnalisé / liste pour un faux joueur), avec tronçature à 32 caractères si besoin. Mis à jour aussi après `**/bb reload`** si le script change.

## `root priority_selector` — racine de décision

```text
root priority_selector {
  node ...
  node ...
}
```

- `**root**` : point d’entrée du comportement.
- `**priority_selector**` : à chaque tick serveur, tous les `**node**` sont évalués :
  - chaque nœud calcule une **priorité** (nombre ou `$priority-*`) ;
  - si un bloc `**when`** est présent, ses **conditions** doivent réussir (séquence) sinon la priorité effective devient **0** ;
  - les nœuds sont triés par priorité **décroissante** ;
  - les nœuds sont essayés par priorité décroissante jusqu’à ce qu’une action renvoie **`SUCCESS`** ou **`RUNNING`** (fin du tick pour ce sélecteur) ;
  - **`PASS`** (ex. feuille **`set_goal`**) signifie « OK mais continuer » : le sélecteur essaie le nœud suivant **dans le même tick**.

En résumé : **choisir l’action la plus prioritaire possible** parmi les nœuds valides ; **`PASS`** ne « gagne » pas le tick, il délègue au nœud suivant.

## Bloc `node`

```text
node <id> {
  priority: <valeur>
  when: sequence { ... }   # optionnel
  leaf <nom> { ... }       # 1+ fois (ou: run <nom> { ... })
}
```

- `**id**` : libellé de debug (ex. `attack`, `move`).
- `**priority:**`  
  - nombre entier (ex. `100`) ;  
  - ou référence `**$priority-attack**`, `**$priority-move**`, `**$priority-idle**` (lus depuis les réglages **à l’exécution**).
- `**when:`** (optionnel) : une `**sequence**` de `**condition**` (toutes doivent passer). Si absent, le nœud est toujours “éligible” côté garde (seule la priorité compte).
- `**leaf**` / `**run**` : actions exécutées dans l’ordre d’apparition quand ce nœud est sélectionné (stop si une action renvoie `RUNNING`).
- `**let <name> = <expr>**` (optionnel, dans un `node`) : déclare une variable locale pour ce nœud.
  - `expr` v1 (numérique) : `number`, `@root.location.x|y|z|yaw|pitch`, `@<name>` (variable déjà déclarée), `$<setting-key>` (si l’argument supporte déjà les références settings ; ex. `range`, `stop_range`, `cooldown_ms`) et calculs `+ - * /` avec parenthèses.
  - racines runtime v1 : `@self`, `@closest_player`, `@closest_entity`, `@closest_monster`, `@current_target`.

## Conditions (`when: sequence { ... }`)

Syntaxe :

```text
when: sequence {
  condition <nom> { ... }
  condition <nom> { ... }
}
```


| Nom               | Rôle                                      | Arguments                                                                            |
| ----------------- | ----------------------------------------- | ------------------------------------------------------------------------------------ |
| `target_in_range` | Cible présente et dans la portée de mêlée | `range:` nombre ou `$attack-range` (référence → utilise `attack-range` des réglages) |
| `has_melee_los`   | Ligne de vue mêlée vers la cible          | (aucun)                                                                              |
| `has_active_goal` | Un objectif actif (goal manager)          | (aucun)                                                                              |


## Feuilles (`leaf`)


| Nom             | Rôle                                              | Arguments                                                                                     |
| --------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `attack_target` | Regarder / attaquer la cible avec cooldown        | `cooldown_ms:` nombre ou `$attack-cooldown-ms`                                                |
| `move_to_goal`  | Se déplacer vers l’objectif actif (pathfinding)   | `stop_range:` nombre ou `$stop-range`                                                         |
| `idle`          | Arrêt / idle                                      | `{}`                                                                                          |
| `swing_hand`    | Animation de coup de main (joueur NMS uniquement) | `hand:` `main` ou `off` (défaut `main`) — sinon **échec** (ex. zombie)                        |
| `entity_pose`   | Pose d’affichage vanilla (`LivingEntity#setPose`) | `pose:` nom d’énum **Bukkit** (ex. `SWIMMING`, `CROUCHING`, `STANDING`) — ident ou `"chaîne"` |
| `set_goal`      | Fixe l’objectif de déplacement (priorité scriptée, défaut 150) | `target:` + optionnel `priority:` ; voir [Objectif scripté (`set_goal`)](#objectif-scripté-set_goal) |
| `teleport`      | Téléporte le bot à une position calculée runtime | `x:`, `y:`, `z:` (number ou `@root.location.{x|y|z|yaw|pitch}`) ; optionnel `world:`, `yaw:`, `pitch:` |
| `look_at`       | Oriente le bot vers une entité ou une location | soit `target:` (`self`, `closest_entity`, `closest_player`, `closest_monster`, `current_target`), soit `x:`, `y:`, `z:` (expressions numériques) |


Les références `**$...`** sur les feuilles (cooldown, portée d’arrêt, etc.) utilisent en général les valeurs **runtime** des **réglages** (`BotSettings`). **Exception** : sur `**set_goal**`, la syntaxe `**$...**` sert aussi aux **cibles dynamiques** (`$closest-player`, etc.) — ce ne sont pas des clés de `config.yml` / `/bb config`.

### Objectif scripté (`set_goal`)

Le **GoalManager** choisit un objectif parmi : objectif **scripté** (cette feuille) et **chasse** de la cible de perception. L’objectif scripté a par défaut la priorité **150** (au-dessus du chase à 100).

- **`target:`** — ident, chaîne entre guillemets, ou **`$nom`** (voir ci‑dessous). Obligatoire.
- **`priority:`** — entier optionnel (défaut **150**).

Valeurs de **`target:`** (snake_case ou kebab-case dans `$...`) :

| Valeur | Rôle |
|--------|------|
| `closest_player` / **`$closest-player`** | Joueur le plus proche (même ordre que la cible de perception triée par distance). |
| `closest_entity` / **`$closest-entity`** | Entité vivante la plus proche (hors le bot ; armor stands exclus). |
| `closest_monster` / **`$closest-monster`** | Monstre hostile (`Monster`) le plus proche. |
| `current_target` (ou **`$current-target`**) | Cible courante de perception (`getTarget()`). |
| `location` / `fixed` / `loc` | Point fixe : `x:`, `y:`, `z:` (nombres) ; `world:` optionnel (nom monde Bukkit ; défaut = monde de l’entité bot). |
| `none` / `clear` / `off` | Désactive l’objectif scripté (les autres objectifs reprennent le dessus). |

La feuille **`set_goal`** met à jour l’objectif scripté, appelle ensuite **`GoalManager.update`** pour recalculer l’objectif actif **dans le même tick**, puis renvoie le statut **`PASS`** (succès + « essayer le nœud prioritaire suivant » dans le `priority_selector`, sans confondre avec un échec). Place **`set_goal`** dans un nœud **plus prioritaire** que **`move_to_goal`**.

**Animations :** `swing_hand` n’a d’effet que si le bot est un **Player** (faux joueur). `entity_pose` fonctionne aussi sur le **zombie** de secours ; les poses comme `SWIMMING` sont surtout visibles dans l’eau.

## Valeurs : littéraux et `$clé`

- **Nombre** : `2.5`, `100`
- **Chaîne** : `"texte"`
- **Identifiant** (sans guillemets) : `main`, `SWIMMING`, etc. — utile pour `hand:` et `pose:`
- **Référence réglage** : `$attack-range`, `$priority-move`, etc.  
  - Pour `**priority:`**, seules les clés `**priority-attack**`, `**priority-move**`, `**priority-idle**` sont acceptées en `$...`.
- **Référence runtime / expression (v1)** : `@self`, `@closest_entity`, `@current_target`, ou `@<name>` (variable `let`) ; pour les valeurs numériques : `@root.location.x|y|z`.

## `set` vs `$`

- `**set clé valeur**` : écrit dans le profil / réglages **au chargement** du `.bbot` (et lors du reload si le fichier est relu).
- `**$clé`** dans les nœuds : **lecture** du réglage **à chaque tick** (ou à l’usage de la condition / feuille), donc modifiable en jeu via `/bb config` sans changer le fichier.

## Rechargement

- `**/bb reload`** (permission `bb.admin`) : recharge `config.yml`, le cache des défauts, relit **chaque** `.bbot` et met à jour les bots déjà spawn (réglages + arbre + nom affiché depuis `bot "..."`).

## Exemple minimal

```text
version 1

set priority-attack 100
set priority-move 50
set priority-idle 1

bot "default" {
  root priority_selector {
    node attack {
      priority: $priority-attack
      when: sequence {
        condition target_in_range { range: $attack-range }
        condition has_melee_los {}
      }
      leaf attack_target { cooldown_ms: $attack-cooldown-ms }
    }
    node move {
      priority: $priority-move
      when: sequence {
        condition has_active_goal {}
      }
      leaf move_to_goal { stop_range: $stop-range }
    }
    node idle {
      priority: $priority-idle
      leaf idle {}
    }
  }
}
```

## Clés de réglage (`BotSettings`)

Clés reconnues (kebab-case, comme dans `config.yml`) — utilisables avec `**set**` et souvent `**$**` dans le script :

- `behavior-tree`
- `attack-range`, `stop-range`, `attack-cooldown-ms`
- `priority-attack`, `priority-move`, `priority-idle`
- `sprint-enabled`
- `move-speed-sprint`, `move-speed-walk`, `move-speed-backward`, `move-speed-climb`
- `jump-velocity`, `strafe-jitter-strengthé`
- `path-max-visited-nodes`, `path-repath-interval-ms`
- `path-wall-proximity-penalty`, `path-void-penalty`, `path-fall-risk-penalty`, `path-enemy-proximity-penalty`, `path-climb-upward-bonus`
- `path-max-step-up`, `path-max-step-down`, `path-retry-on-fail-ms`
- `stuck-threshold-ground-ticks`, `stuck-threshold-climb-ticks`, `unstuck-duration-ms`
- `step-snap-up-max`, `step-snap-down-max`
- `edge-slide-forward-min`, `edge-slide-forward-max`, `edge-slide-lateral-min`, `edge-slide-lateral-max`, `edge-slide-samples`
- `jump-intent-min-dy`, `jump-intent-max-dy`, `jump-intent-max-horiz`, `jump-intent-ticks`

Pour la liste exacte à jour, voir `fr.bloup.blurpbot.core.BotSettingKey` dans les sources.

## Limites actuelles

- Un seul bloc `**bot**` par fichier.
- Pas d’**import** de fichiers `.bbot` entre eux.
- Pas de **sélecteur / séquence** génériques en racine : seulement `**priority_selector`** + `**node**` comme ci-dessus.

## Pour les développeurs (étendre le moteur)

- **Conditions / feuilles** (`.bbot`) : `**docs/bbot-extend-nodes.md`**
- **Nouveaux réglages** (`BotSettingKey`, YAML, `/bb config`) : `**docs/bbot-extend-settings.md`**

