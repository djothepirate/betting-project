# Feuille de route de Betting Project

- **Baseline :** 2 septembre 2026
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

Le commit `743aff7` a été revu avec les CI Windows et Linux vertes, puis fusionné dans `main` par la Pull Request `#3` au commit `6913cea`. ENR-001 est accepté, fusionné et clôturé. CAT-002 est publié dans la Pull Request `#8`. Son correctif fonctionnel `6fb69e2`, produit après une remarque P2 valide, a été revu contre le manifeste SHA-256 correctif, poussé, validé par les quatre checks Windows/Linux et sa discussion de revue est résolue. L'alignement documentaire `a3b471f` est également publié et validé par quatre checks verts. La fusion est autorisée si le HEAD final conserve ces garanties et ne présente aucune nouvelle remarque ni aucun conflit.

## Étape 3 — CAT-002

**Statut d'exécution :** Work Order `MERGE_AUTHORIZED_IF_GREEN` avant fusion effective, puis `ACCEPTED - MERGED - CLOSED` dès que GitHub marque la PR `#8` `MERGED` et que cette version est présente dans `main`. Les lots 0 à 7 restent `COMPLETED` et le lot 8 est `COMPLETED LOCALLY`. Les 36 critères sur 36 sont satisfaits ; la seconde revue humaine du correctif P2 est acquise, le commit fonctionnel `6fb69e2` et l'alignement documentaire `a3b471f` sont publiés avec quatre checks Windows/Linux verts chacun, et la discussion est résolue. Le porteur autorise le closeout final et la fusion si les checks du HEAD final restent verts et si aucune nouvelle remarque ou aucun conflit n'apparaît. L'adaptateur `/internal/catalog` reste strictement interne et lié à la boucle locale ; aucun endpoint public, worker CAT-002 ou appel fournisseur n'est introduit.

### Objectif

Rendre la normalisation sûre en volume et les anomalies réellement opérables depuis `control-api`.

### Travaux

- replay d'un snapshot déjà stocké, ciblé par identifiant ou SHA-256, jamais par chemin HTTP arbitraire ;
- consultation des anomalies, mappings et provenances ; confirmation ou rejet motivé d'un mapping ; replay idempotent des snapshots bloqués ;
- terrain neutre et participants non ordonnés explicitement et séparément modélisés ; seul `participantsUnordered=true` autorise une identité insensible à l'ordre ;
- protection contre les observations anciennes et transitions de statut invalides ;
- autorité `PRIMARY`/`CONTROL` appliquée sans promotion silencieuse ;
- opérations PostgreSQL sûres en concurrence ;
- persistance des jobs déplacée derrière un port applicatif au lot 1, sans SQL ni JDBC dans `operations.application` ;
- projection courante des mappings protégée par version optimiste, décisions humaines confirmées ou rejetées avec identité opérateur, justification expurgée, reçu d'idempotence global et historique append-only ;
- projection courante et journal `OPENED`/`OBSERVED`/`RESOLVED`/`REOPENED` pour le cycle de vie des anomalies ;
- migrations additives `V003`, `V004` et `V005`, sans modification des migrations antérieures ;
- consultations keyset et commandes strictes sous `/internal/catalog`, sans payload brut, reçu d'idempotence ou chemin arbitraire ;
- contrats `catalog-control-api-v1` et `stored-snapshot-replay-v1`, avec écoute limitée à `127.0.0.1` et aucune exposition avant OPS-001.

### Porte d'acceptation

Tests couvrant ordre des observations, contradiction primaire/contrôle, inversion ordonnée/explicitement non ordonnée, neutralité indépendante de l'ordre, deux normalisations concurrentes, décision humaine puis replay, et redémarrage sans perte d'observation.

Les portes locales des lots 2 à 7 sont franchies : `cal01-fixture-v3` reste strict, V003 applique chronologie et journal, V004 protège les décisions humaines et historise le cycle des anomalies, et V005 ajoute les demandes et tentatives durables depuis une V004 peuplée. Le lot 6 sélectionne les snapshots par UUID ou SHA-256 unique, vérifie leur preuve avant parsing, reprend après arrêt et relie `CONFIRM` ou `REJECT` au rejeu après commit. Le lot 7 ajoute les lectures paginées, les décisions et rejeux manuels, les erreurs RFC 9457 et les projections expurgées sous le seul profil `control-api`, sans V006. Le lot 8 ajoute une preuve réelle de reprise au travers de trois contextes Spring successifs. Après la remarque P2 de la PR `#8`, la clé d'exécution accepte les valeurs fournisseur littérales `*`, `?` et `%`, tandis que les affectations classpath continuent de les refuser comme jokers. La baseline validée est de 219 tests standards et 89 tests PostgreSQL/Testcontainers. Le correctif fonctionnel `6fb69e2` et l'alignement documentaire `a3b471f` sont publiés et validés par quatre checks chacun ; la discussion est résolue. La fusion est autorisée lorsque le HEAD final conserve des checks verts, sans nouvelle remarque ni conflit ; la présence de cette version dans `main` clôt CAT-002 et ouvre ensuite MVP-001.

## Étape 4 — MVP-001

**État au 19 septembre 2026, après l'ouverture du 6 septembre :** [MVP-001](work-orders/MVP-001.md)
reste `ACTIVE - IMPLEMENTATION`, lots 0 à 5 terminés localement. Le
[rapport du lot 5](work-orders/MVP-001-lot5-execution.md) qualifie les consultations internes et
la sélection prévisionnelle PPL/PD après calendrier, sur le budget et les jobs qualifiés.
Le lot 6 est prochain et reste `PENDING`. La branche
`feature/V0.1.0-RC01-CODEX-MVP-001` part de
`feature/V0.1.0-RC01@5e4b05b5a1a7cfa66850643db31d9612192e7281` ; sa PR de clôture
ciblera `feature/V0.1.0-RC01`. Quinze critères sur vingt sont démontrés : registre fermé,
routage, protection du canon, budget, pagination, audit/replay, temps/ordre source et isolation
des échecs fournisseurs, claim/fencing, reprise et absence de double effet logique, incidents
consultables, API bornée et sélection priorisée plafonnée à sept. La prévision ne réserve ni
n'envoie d'appel d'enrichissement ; le coût par rencontre est explicitement fourni.
La baseline réelle reste inactive. Qualification du lot 5 : **615 tests
standards et 239 tests PostgreSQL/Testcontainers**, sans échec ni omission. Les preuves antérieures
restent historiques ; chaque lot fait l'objet d'un commit, d'un push et d'une vérification de
sa CI sur le SHA publié. Le blocage Actions historique a été dépassé : la base `51e85de` a
des CI Windows et Linux/PostgreSQL vertes (run `35404160290`, tentative 2). La qualification
du nouveau SHA reste obligatoire. Aucun appel fournisseur réel n'est effectué ; les nouvelles
routes sont internes, sous `control-api` loopback. V001–V011 sont inchangées, sans V012.
La boucle worker livrée reste opt-in et désactivée, comme les clients ;
leurs tests HTTP restent strictement loopback ou simulés.

### Objectif

Implémenter le registre de capacités, le routage, les budgets, les adaptateurs de production et les jobs persistants de collecte calendrier.

### Contrats minimaux

La [clarification COV-002](benchmark/mvp-001-cov002-clarification-20260906.md) prépare la révision
du périmètre cible vers calendrier quotidien top 5 + UEFA et alimentation J7 optionnelle.
La journée mesurée ne garantit pas quinze enrichissements exhaustifs sous 100 appels.
Le budget du lot 2 reste indépendant de la largeur du calendrier ; les compléments J7 métier,
la traduction des observations T0/T+45 et les fréquences nécessitent un cadrage explicite avant
réalisation des lots concernés. Aucun live minute ou fournisseur réel n'est activé par cette note.

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
