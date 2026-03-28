# BlurpBot Expressions v1 (`let` + `@...`)

## Objectif

Ce document décrit la **syntaxe v1** implémentée dans BlurpBot pour exprimer des valeurs **runtime** (qui peuvent changer chaque tick) à partir de données du bot.

Dans la V1 actuelle :

- `"$..."` sert uniquement aux **réglages BotSettings** (ex. `$stop-range`, `$attack-cooldown-ms`).
- `"@..."` sert aux **racines runtime / objets dynamiques** (ex. `@closest_entity`) et aux **variables locales** (`let` + `@nom`).
- `let` permet de déclarer des variables **locales à un `node`**.

## 0) Rappel de syntaxe `.bbot`

Un fichier `.bbot` est construit autour de :

- `node <id> { ... leaf ... }`
- `when: sequence { condition ... }` (optionnel)

Un `node` ne peut avoir qu’une seule `leaf` (un seul bloc `leaf`/`run`), mais un même `.bbot` peut avoir plusieurs `node`.

## 1) Les 2 symboles : `$` vs `@`

### 1.1 `$...` (réglages)

`$key` pointe vers une entrée de `BotSettings` (config.yml, `/bb config ...`, etc.).

Exemples :

- `stop_range: $stop-range`
- `cooldown_ms: $attack-cooldown-ms`
- `priority: $priority-attack`

### 1.2 `@...` (runtime)

`@...` pointe vers une **valeur dynamique** fournie au tick par le moteur.

Racines runtime supportées (v1) :

- `@self` : le bot (`LivingEntity`)
- `@closest_player` : premier joueur de la liste triée par distance
- `@closest_entity` : `LivingEntity` vivant le plus proche (hors bot, armor stands exclus)
- `@closest_monster` : `Monster` hostile le plus proche
- `@current_target` : `perception().getTarget()`

## 2) `let <name> = <expr>` (variables locales à un node)

Tu peux déclarer plusieurs variables dans un `node` :

```bbot
node foo {
  priority: 150
  
  let a = @closest_entity.location.x
  let b = @closest_entity.location.z

  leaf move_to_goal { stop_range: $stop-range }
}
```

### 2.1 Utiliser une variable

Tu réutilises la variable via :

- `@a`, `@b`, etc.

Exemple :

```bbot
let stop = @closest_entity.location.x
leaf move_to_goal { stop_range: @stop }
```

## 3) Expressions numériques v1 : `@root.location.<x|y|z|yaw|pitch>`

En V1, les expressions runtime numériques supportées sont :

### 3.1 Forme

```text
@<root>.location.x
@<root>.location.y
@<root>.location.z
@<root>.location.yaw
@<root>.location.pitch
```

Où `<root>` est un des noms listés dans la section `@...`.

### 3.2 Dynamique

La valeur est recalculée au runtime (donc peut changer à chaque tick) en fonction de la racine :

- si la cible la plus proche bouge → `x/y/z` changent

## 4) Où tu peux utiliser ces expressions v1 ?

Les feuilles/conditions actuelles supportent aujourd’hui l’évaluation v1 pour les champs **numériques** suivants :

### 4.1 Conditions

- `target_in_range { range: ... }`
  - accepte `number`, `$<setting-key>`, et `@root.location.{x|y|z|yaw|pitch}`

### 4.2 Feuilles

- `attack_target { cooldown_ms: ... }`
  - accepte `number`, `$<setting-key>`, et `@root.location.{x|y|z|yaw|pitch}`
- `move_to_goal { stop_range: ... }`
  - accepte `number`, `$<setting-key>`, et `@root.location.{x|y|z|yaw|pitch}`
- `set_goal { x: ..., y: ..., z: ..., priority: ... }`
  - pour les `x/y/z` (fixed location) et `priority`, accepte aussi `@root.location.{x|y|z|yaw|pitch}`

### 4.3 Cas particulier : `set_goal.target`

Pour `set_goal`, la clé `target:` attend une **valeur de type “kind”** (un identifiant) :

- `closest_entity`, `closest_player`, `closest_monster`, `current_target`, `location`/`fixed`/`loc`, `none`/`off`/`clear`

Tu peux aussi mettre :

- `target: @<var>` si ta variable `let` contient directement cet identifiant (via une expression littérale).

## 5) Exemples complets

### 5.1 Conserver des coordonnées calculées dans des `let`

```bbot
bot "expr-demo" {
  root priority_selector {

    node set_goal_loc_from_closest {
      priority: 200

      let goalX = @closest_entity.location.x
      let goalY = @closest_entity.location.y
      let goalZ = @closest_entity.location.z

      leaf set_goal {
        target: location
        x: @goalX
        y: @goalY
        z: @goalZ
        priority: 180
      }
    }

    node go_to_goal {
      priority: 150
      when: sequence {
        condition has_active_goal {}
      }
      leaf move_to_goal { stop_range: $stop-range }
    }
  }
}
```

### 5.2 Fusionner `$stop-range` + runtime `let`

Ici, `stop_range` reste un réglage, mais les coordonnées de `set_goal` sont runtime :

```bbot
node go {
  priority: 150

  let x = @current_target.location.x
  let y = @current_target.location.y
  let z = @current_target.location.z

  when: sequence {
    condition has_active_goal {}
  }

  leaf set_goal {
    target: location
    x: @x
    y: @y
    z: @z
    priority: 180
  }
}
```

## 6) Limites (V1)

- Pas d’opérations arithmétiques (`+`, `-`, etc.) en V1.
- Pas de propriétés autres que `location.x/y/z` dans les expressions numériques v1.
- `set_goal.target` n’est pas une “expression d’objet” : c’est un **kind** (un identifiant).

## 7) Comment ajouter une nouvelle expression / un nouvel objet (modulaire)

La règle d’or : ajouter un nouveau support = 3 étapes :

1) **Décrire l’AST** (modèle interne)
2) **Parser la syntaxe** (lexer/parser)
3) **Évaluer au runtime** (resolveur utilisé par les feuilles/conditions)

Dans le code, ces étapes se répartissent typiquement comme suit :

- **AST (modèle)** : `fr.bloup.blurpbot.bbot.BbotModel`
- **Parsing de la syntaxe** : `fr.bloup.blurpbot.bbot.BbotParser` (+ tokens dans `BbotTokenizer`)
- **Expansion `let`** : `fr.bloup.blurpbot.bbot.BbotTreeBuilder` (remplace `@var` par l’expression déclarée)
- **Évaluation runtime (numérique)** : `fr.bloup.blurpbot.bbot.BbotArgParsing` (`resolveRangeExpr`, `resolveStopRangeExpr`, etc.)
- **Branchement dans le moteur** : `fr.bloup.blurpbot.bbot.BbotBehaviorRegistries` (les fabriques appellent les bons `resolve*Expr`)

### 7.1 Ajouter une nouvelle racine runtime

Exemple : tu veux `@closest_monster2` (alias).

Dans la V1, l’évaluation numérique des expressions `@root.location.x/y/z/yaw` est centralisée dans :

- `fr.bloup.blurpbot.bbot.BbotArgParsing#evalDynamicLocationAxis(...)`

Tu ajoutes un nouveau `case "closest_monster2" -> ...` qui renvoie l’entité Bukkit à utiliser.

À faire aussi si la “racine” correspond à une notion que tu n’as pas encore en perception :
- soit tu utilises une donnée déjà exposée via `BotContext` / `BotPerception`
- soit tu ajoutes un champ + getter dans la perception (ex. `getClosestMonster2()`), puis tu l’exposes dans le resolver.

### 7.2 Ajouter une nouvelle propriété de localisation

Exemple : accepter `location.pitch` au lieu de seulement `x/y/z`.

Étapes :

1) Ajouter la nouvelle valeur dans `BbotModel.DynamicLocationAxis` (ex. `PITCH`)
2) Étendre le parsing dans `BbotParser.parseValueExpr()` :
   - le code qui reconnaît `@<root>.location.<axis>`
   - ajoute un `case` pour mapper `"pitch"` -> `DynamicLocationAxis.PITCH`
3) Étendre `evalDynamicLocationAxis(...)` dans `BbotArgParsing` :
   - ajouter un `case PITCH -> entity.getLocation().getPitch()`

> Remarque : pour rester “facile”, la V1 ne supporte que des axes numériques d’une `location` (pas d’autres propriétés pour le moment).

### 7.2.1 Exemple concret (déjà présent) : `@closest_entity.location.yaw`

- `BbotModel.DynamicLocationAxis`: ajouter `YAW`
- `BbotParser`: mapper `yaw` -> `DynamicLocationAxis.YAW`
- `BbotArgParsing`: `case YAW -> entity.getLocation().getYaw()`

### 7.3 Ajouter une “vraie” expression non-location

Exemple futur : `@current_target.health` ou une distance.

Étapes :

1) Ajouter une nouvelle variante dans `BbotModel.ValueExpr` (ex. `DynamicHealthComponent(root)`)
2) Étendre `BbotParser.parseValueExpr()` pour reconnaître la syntaxe et créer cette variante
3) Ajouter un résolveur dans `BbotArgParsing` :
   - soit un `eval...` spécifique
   - soit un resolver générique qui sait comment convertir la nouvelle `ValueExpr` en `BbotDoubleExpr`
4) Brancher :
   - soit dans les fabriques des feuilles/conditions concernées (`BbotBehaviorRegistries`)
   - soit en généralisant les fonctions `resolveRangeExpr`, etc. pour accepter la nouvelle `ValueExpr`

## 8) Résumé rapide (à retenir)

- `$...` = réglage `BotSettings`
- `@...` = runtime root ou variable locale
- `let name = expr` dans un `node`
- expression numérique v1 : `@root.location.x|y|z`

