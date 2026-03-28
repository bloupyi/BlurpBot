# Guide développeur — ajouter une condition ou une feuille `.bbot`

Ce guide explique comment étendre le moteur **Java** pour de nouveaux blocs utilisables dans les scripts `.bbot` : `condition ...` et `leaf ...`.

## Concepts

- Toute logique d’arbre implémente **`BehaviorNode`** (`tick(BotContext)` → `NodeStatus`).
- Les **conditions** et les **feuilles** sont des `BehaviorNode` comme les autres.
- Dans un `priority_selector` scripté, chaque nœud est un **`ScriptedPriorityNode`** :
  - le bloc **`when`** est une **`SequenceNode`** de conditions ;
  - **`priority()`** et **`tick()`** réévaluent le `when` (deux appels par tick si le nœud est candidat) — évite les effets de bord lourds dans les conditions.

### `NodeStatus`

| Valeur | Usage typique |
|--------|----------------|
| `SUCCESS` | Condition OK ; action terminée avec succès |
| `FAILURE` | Condition KO ; action impossible |
| `RUNNING` | Action en cours (ex. déplacement, attaque sur cooldown) |
| `PASS` | Succès + enchaînement : uniquement dans le `priority_selector`, essayer le nœud prioritaire suivant (ex. **`set_goal`** puis **`move_to_goal`**) |

Pour une **condition** dans un `sequence`, seul **`SUCCESS`** fait continuer la chaîne. Dans une **`sequence`** de **feuilles** (composite Java), **`PASS`** est traité comme **`SUCCESS`** pour avancer.

### `BotContext`

Accès utile (non exhaustif) :

- `perception()` — cible, etc.
- `controller()` — déplacement, attaque, LOS, …
- `settings()` — `BotSettings` (portées, vitesses, …)
- `goalManager()` — objectifs actifs
- `debugFrame()` — peut être `null` si debug désactivé

Référence : `fr.bloup.blurpbot.brain.BotContext`.

## Emplacement des classes

| Rôle | Paquet suggéré |
|------|----------------|
| Condition | `fr.bloup.blurpbot.decision.leaf.condition` |
| Action (feuille) | `fr.bloup.blurpbot.decision.leaf.action` |

Les noms de classes Java sont en PascalCase ; les noms **dans le script** sont en **`snake_case`** (ex. classe `LowHealth`, script `low_health`).

## 1. Nouvelle condition

### Exemple minimal (sans argument)

Référence : `HasActiveGoalCondition`.

1. Créer une classe implémentant **`BehaviorNode`**.
2. Dans `tick`, retourner **`SUCCESS`** ou **`FAILURE`** (pas d’effet secondaire si possible).
3. Enregistrer le nom dans **`BbotBehaviorRegistries.registerCondition`** (voir §3).

```java
// Exemple : condition "has_active_goal" { }
public NodeStatus tick(BotContext context) {
    if (context.goalManager() == null || context.goalManager().getActiveGoal() == null) {
        return NodeStatus.FAILURE;
    }
    return NodeStatus.SUCCESS;
}
```

### Avec arguments dans le script

Référence : `IsTargetInRange` + `BbotArgParsing.resolveRange`.

Script :

```text
condition my_check { threshold: 0.5 }
condition my_check { threshold: $attack-range }
```

1. Ajouter un constructeur à ta condition avec les paramètres nécessaires.
2. Dans la fabrique **`BbotConditionFactory`**, lire les arguments via le paramètre **`args`** (`Map<String, ValueExpr>`), comme le ferait `buildCondition` après parsing du bloc `condition`.
3. Parser les valeurs :
   - littéral numérique → `ValueExpr.NumberVal` ;
   - `$clé` → `ValueExpr.SettingRef` (souvent mappé vers `context.settings()` au tick, ou résolu au build comme pour `range`).

Pour les clés reconnues par `BotSettings`, tu peux t’aligner sur **`BotSettingKey.fromKey(...)`**.

4. Ajouter une méthode statique du style **`resolveXxx(Map<String, ValueExpr> args)`** dans **`BbotArgParsing`** (ou réutiliser une méthode existante) si la logique de parsing se répète.

**Important :** le parseur n’accepte pour l’instant que **nombre**, **chaîne** et **`$ident`** (pas d’expressions arbitraires).

## 2. Nouvelle feuille (action)

### Exemple minimal

Référence : `IdleAction`.

1. Implémenter **`BehaviorNode`**.
2. Utiliser `context.controller()`, `perception()`, etc.
3. Retourner **`SUCCESS`** si l’action est “faite” pour ce tick, **`RUNNING`** si elle doit continuer, **`FAILURE`** si impossible.
4. Enregistrer dans **`BbotBehaviorRegistries.registerLeaf`** (voir §3).

Exemples existants : `swing_hand` / `EntityPoseLeaf` (animations / poses), `AttackTarget`, `IdleAction`.

### Avec arguments

Référence : `AttackTarget` / `MoveToTarget` + `resolveCooldownMs` / `resolveStopRange`.

1. Constructeur avec paramètres (ex. `long cooldownMs`).
2. Dans la fabrique **`BbotLeafFactory`**, lire `args` et convertir comme pour les conditions.
3. Convention d’arguments : **`snake_case`** dans le `.bbot` (`cooldown_ms`, `stop_range`) pour rester cohérent avec l’existant.

## 3. Registre : `BbotBehaviorRegistries`

Fichier : `fr.bloup.blurpbot.bbot.BbotBehaviorRegistries`.

Au lieu d’un gros `switch` dans `BbotTreeBuilder`, chaque **nom** de script (`snake_case`) est associé à une **fabrique** :

- **`BbotConditionFactory`** — `BehaviorNode build(Map<String, ValueExpr> args) throws BbotParseException`
- **`BbotLeafFactory`** — même signature

Les **feuilles et conditions par défaut** du plugin sont enregistrées dans le bloc `static { registerDefaults(); }`. Pour une extension (autre plugin, ou init au démarrage), appeler **`registerCondition`** / **`registerLeaf`** **avant** le premier chargement d’un `.bbot`.

**Condition** — exemple :

```java
BbotBehaviorRegistries.registerCondition("your_condition", args ->
    new YourCondition(BbotArgParsing.resolveSomething(args)));
```

**Feuille** — exemple :

```java
BbotBehaviorRegistries.registerLeaf("your_leaf", args ->
    new YourLeafAction(BbotArgParsing.resolveCooldownMs(args)));
```

**Construction de l’arbre** : `BbotTreeBuilder.buildCondition` / `buildLeaf` font uniquement un **lookup** (`getConditionFactory` / `getLeafFactory`) puis `factory.build(args)`. Si le nom n’est pas enregistré → `Unknown condition/leaf`.

Les clés sont **normalisées en minuscules** (`Locale.ROOT`) : `Attack_Target` et `attack_target` pointent vers la même entrée.

**Parsing d’arguments partagé** : `fr.bloup.blurpbot.bbot.BbotArgParsing` (méthodes statiques `resolveRange`, `resolveCooldownMs`, etc.).

## 4. Documentation utilisateur

Mettre à jour **`docs/bbot-script.md`** :

- table des **conditions** (nom script + arguments) ;
- table des **feuilles** (nom + arguments).

Les joueurs / admins ne voient que les noms **`snake_case`** du script.

## 5. Arbres intégrés (hors `.bbot`)

Le fallback **`BotBehaviorTreeLoader`** charge **`trees/default.bbot`** depuis le **classpath** (même fichier que sous `src/main/resources/`). Plus de duplication Java dédiée (anciennes classes attack/move supprimées).  
Si la ressource embarquée est absente ou invalide : arbre minimal **idle** uniquement (`IdlePriorityNode`).

## 6. Tests rapides

1. `mvn compile` (ou `mvn test` si tu ajoutes des tests).
2. Placer un `.bbot` de test avec `condition your_condition {}` / `leaf your_leaf {}`.
3. **`/bb reload`** après modification du `.bbot` ou du code.
4. Vérifier les logs serveur : préfixe **`[bbot]`** en cas d’erreur de parse ou de `Unknown condition/leaf`.

## 7. Pièges courants

- **Effets de bord dans `when`** : exécuté pour `priority()` puis pour `tick()` — garder les conditions **idempotentes** ou très légères.
- **Noms** : le script utilise **`snake_case`** ; l’enregistrement au registre utilise la même chaîne (la recherche est **insensible à la casse**).
- **Arguments optionnels** : gérer `args.get("key") == null` avec une valeur par défaut cohérente (comme `-1` pour “utiliser le réglage par défaut” côté action).

## Voir aussi

- **`docs/bbot-script.md`** — référence utilisateur du `.bbot`
- **`docs/bbot-extend-settings.md`** — ajouter un nouveau réglage (`BotSettingKey` / `BotSettings`)
