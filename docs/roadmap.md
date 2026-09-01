# Feuille de route de Betting Project

- **Baseline :** 1er septembre 2026
- **Priorité 0 :** pipeline football prématch fiable
- **Ordre obligatoire :** DEVX-001 → ENR-001 → CAT-002 → MVP-001 → ENR-002 → OPS-001

## Principes de séquencement

Chaque lot possède une branche, un Work Order, des validations hors réseau fournisseur et une revue humaine. Un lot aval ne commence pas en supposant la fusion du précédent. Commit, push, Pull Request, fusion, protection de branche et déploiement sont des décisions distinctes conformément à ADR-005.

Les charges de benchmark et les connecteurs de production restent séparés : les preuves ENR-001 orientent le registre de capacités, mais MVP-001 crée des adaptateurs Java de production dédiés.

## Vue d'ensemble

| Ordre | Work Order | But | Porte de sortie principale |
|---:|---|---|---|
| 0 | Sauvegarde ENR-001 | Sécuriser code, diff et preuves historiques | Restauration à blanc et manifeste SHA-256 validés — réalisée le 1er septembre 2026 |
| 1 | DEVX-001 | Éliminer les faux verts et versionner la mémoire du projet | Accepté, fusionné par la Pull Request `#2` et clôturé |
| 2 | ENR-001 | Finaliser le benchmark d'enrichissement comme lot séparé | Accepté, fusionné par la Pull Request `#3` au commit `6913cea` et clôturé |
| 3 | CAT-002 | Durcir le canon et rendre les anomalies opérables | ordre, autorité, concurrence, mappings et replay administrables |
| 4 | MVP-001 | Planifier, router et collecter le calendrier | faux fournisseurs, budgets, jobs et replay complets hors réseau |
| 5 | ENR-002 | Produire l'enrichissement et la qualité | pilote local de sept jours conforme |
| 6 | OPS-001 | Déployer un staging VPS sûr | image, secrets, sauvegarde/restauration et observabilité validés |

## Étape 1 — DEVX-001

### Objectif

Fiabiliser le Maven Wrapper, les scripts Windows, les contrôles de secrets et la CI, puis ramener le cadrage et l'état du projet dans Git.

### Porte d'acceptation

- Maven Wrapper 3.9.16 fonctionnel sous Windows avec un `.m2` ordinaire et sous Linux ;
- somme SHA-256 de distribution vérifiée ;
- échec immédiat si Maven, un test, le scan ou Testcontainers échoue ;
- 17 tests standards et 11 tests d'intégration PostgreSQL réussis ;
- CI Windows et Linux hors réseau fournisseur ;
- documentation de CAT-001, du cadrage, du statut et de la roadmap à jour ;
- revue humaine avant toute opération Git distante.

La protection de branche et le caractère obligatoire des checks GitHub restent hors lot jusqu'à autorisation explicite.

## Étape 2 — ENR-001

### Objectif

Réconcilier le diff historique, préserver le collecteur comme outil de benchmark contrôlé et versionner les rapports, scripts, fixtures représentatives et index expurgés nécessaires. Les payloads complets restent hors Git.

### Décisions clôturées

- sélection fournisseur par capacité, compétition, saison, phase et type de donnée ;
- Highlightly `PRIMARY` pour les capacités 2026 mesurées ;
- football-data.org `CONTROL` sur `PPL`, `PD`, `DED` et `ELC` ;
- API-FOOTBALL `BLOCKED_BY_PLAN` pour le chemin critique gratuit 2026 ;
- compositions facultatives, quotas prudents, second snapshot post-match et règles de qualité décrites dans `project-brief.md` ;
- aucune dépendance à SofaScore et aucun pari automatique.

### Porte d'acceptation

- 57 tests standards et 12 tests PostgreSQL via le wrapper corrigé ;
- 127 preuves ENR rejouées hors réseau avec empreintes conformes ;
- manifeste valide de 12 rencontres principales, 4 réserves et 1 contrôle transversal ;
- contrôle de secrets, `git diff --check`, architecture et fixtures propres ;
- aucun appel fournisseur pendant la revue finale ;
- chaque critère ENR-001 est prouvé ou marqué `NON_APPLICABLE` avec justification ;
- revue, commit, push, Pull Request et fusion autorisés séparément.

Le cas `Hirnyk` conserve deux preuves distinctes : ambiguïté historique sans rapprochement textuel, et mapping explicite de la référence exacte `highlightly:TEAM:5522923` vers `FC Kryvbas Kryvyi Rih` comme `CONFIRMED_HISTORICAL_REBRAND_ALIAS`.

Le commit `743aff7` a été revu avec les CI Windows et Linux vertes, puis fusionné dans `main` par la Pull Request `#3` au commit `6913cea`. ENR-001 est accepté, fusionné et clôturé ; CAT-002 est le prochain Work Order à ouvrir dans la feuille de route.

## Étape 3 — CAT-002

### Objectif

Rendre la normalisation sûre en volume et les anomalies réellement opérables depuis `control-api`.

### Travaux

- replay d'un snapshot déjà stocké, ciblé par identifiant ou SHA-256, jamais par chemin HTTP arbitraire ;
- consultation des anomalies, mappings et provenances ; confirmation ou rejet motivé d'un mapping ; replay idempotent des snapshots bloqués ;
- terrain neutre et participants non ordonnés explicitement modélisés ;
- protection contre les observations anciennes et transitions de statut invalides ;
- autorité `PRIMARY`/`CONTROL` appliquée sans promotion silencieuse ;
- opérations PostgreSQL sûres en concurrence ;
- persistance des jobs déplacée derrière un port applicatif ;
- migrations additives à partir de `V003`, sans modification de `V001` ou `V002`.

### Porte d'acceptation

Tests couvrant ordre des observations, contradiction primaire/contrôle, inversion neutre/non neutre, deux normalisations concurrentes, décision humaine puis replay, et redémarrage sans perte d'observation.

## Étape 4 — MVP-001

### Objectif

Implémenter le registre de capacités, le routage, les budgets, les adaptateurs de production et les jobs persistants de collecte calendrier.

### Contrats minimaux

- clé du registre : `provider × competition × season × phase × dataType` ;
- données : `CALENDAR`, `MATCH_DETAIL`, `LINEUP`, `TEAM_STATS`, `EVENTS`, `PLAYER_STATS` ;
- statuts : `PRIMARY`, `CONTROL`, `PILOT`, `CALENDAR_ONLY`, `NON_APPLICABLE`, `BLOCKED_BY_PLAN` ;
- ports : collecte calendrier, collecte d'enrichissement, routage, budget/quota, audit fournisseur et stockage/replay des preuves ;
- jobs : `CALENDAR_DISCOVERY`, `PREMATCH_ENRICHMENT`, `POSTMATCH_ENRICHMENT`, `POSTMATCH_RECHECK`, `REPLAY_NORMALIZATION`.

### Comportement attendu

- calendrier quotidien `PPL`, `PD`, `DED` et `ELC` ;
- enrichissement complet limité aux rencontres PPL/LaLiga priorisées ;
- audit de chaque appel avec latence, statut, quota, hash, parseur et brut ;
- réserve de 20 jamais entamée, maximum sept rencontres enrichies après consommation calendrier ;
- incidents exploitables pour HTTP 401/403/429 ;
- claim PostgreSQL atomique, tentatives bornées, backoff, reprise après crash et idempotence ;
- données déjà obtenues conservées même si l'autre fournisseur échoue.

### Porte d'acceptation

Intégration entièrement hors réseau avec faux fournisseurs, couvrant pagination, quotas, réserve, reset incertain, erreurs HTTP, timeouts, arrêt/reprise sans double appel logique, audit, replay et autorité primaire/contrôle. Le `control-api` expose les capacités, les budgets, les jobs et les incidents.

## Étape 5 — ENR-002

### Objectif

Collecter l'enrichissement prématch/post-match de production, rendre la qualité visible et exclure proprement les données douteuses.

### Travaux

- séquencement conditionnel des compositions et suivi de leur disponibilité ;
- statistiques d'équipe, événements et statistiques joueurs post-match ;
- second contrôle différé pour les rencontres prioritaires ;
- schémas profonds et réduits conservés sans invention de champs ;
- quarantaines `MISSING_PLAYER_FULL_NAME`, `ZERO_MINUTE_EXPECTED_METRICS`, `INVALID_SECOND_YELLOW_VALUE`, `MISSING_PLAYER_YELLOW_CARD` et `CROSS_ENDPOINT_PLAYER_ID_MISMATCH` ;
- qualité, fraîcheur, complétude et raisons d'exclusion exposées.

### Porte du jalon « pipeline fiable »

- pilote local de sept jours consécutifs ;
- quatre calendriers collectés et normalisés chaque jour ;
- au moins dix rencontres PPL/LaLiga enrichies ;
- maximum sept enrichissements par jour et zéro atteinte à la réserve ;
- 100 % des appels audités et payloads rejouables ;
- zéro doublon canonique après replay, concurrence ou redémarrage ;
- anomalies visibles ; absence de composition non bloquante ;
- aucun secret, appel live ou usage SofaScore.

## Étape 6 — OPS-001

### Objectif

Déployer le pipeline accepté sur un staging VPS sans exposer prématurément de service.

### Travaux et porte

- image reproductible et non-root ; configuration et secrets externalisés ;
- `control-api` protégé et Actuator isolé sur un port/réseau de management ;
- PostgreSQL restreint avec rôles Flyway et applicatif distincts ;
- rétention, purge, sauvegarde et exercice de restauration ;
- logs structurés, corrélation et métriques fournisseur/jobs/anomalies/fraîcheur/base/outbox ;
- runbooks de déploiement, rollback, incident fournisseur et reprise ;
- aucun endpoint public avant validation de la sécurité, de la sauvegarde et de la restauration.

## Après le pipeline fiable

| Ordre | Axe | Contenu |
|---:|---|---|
| 1 | ODD-001 | Benchmark des fournisseurs de cotes, modèle canonique bookmaker/plateforme/fournisseur de prix/marché/ligne/sélection, couverture des opérateurs ANJ et internationaux réellement utilisés, et prévention des faux consensus. |
| 2 | ANL-001 | Probabilités versionnées, marge/value, explications, backtests temporels et suivi des recommandations. |
| 3 | PUB-001 | Dossiers et bilans reproductibles, publication idempotente Drive/courriel et notification/validation Telegram. |
| 4 | LIVE-001 | Architecture, budget, fréquence, latence, alertes et reprise dédiés ; nouveau profil seulement après autorisation. |
| 5 | Multisport | eSports LoL/CS2 et MMA, puis tennis/basket/rugby après validation des abstractions. |

Aucun de ces axes n'entre dans le prochain jalon technique. Le placement automatique de pari reste exclu.
