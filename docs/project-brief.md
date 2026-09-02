# Cadrage durable de Betting Project

- **Baseline :** décisions du cadrage produit v0.5.1 consolidées le 1er septembre 2026
- **Portée immédiate :** pipeline football prématch fiable
- **Usage :** personnel, avec validation humaine
- **Documents liés :** ADR-001 à ADR-007, `project-status.md`, `roadmap.md`

## Finalité

Betting Project doit collecter, normaliser, qualifier et expliquer des données sportives afin d'assister une décision de pari. PostgreSQL conserve l'état de référence ; les documents, courriels, messages ou autres canaux ne sont que des restitutions.

Le produit doit privilégier la qualité mesurée, la provenance et le replay plutôt que la largeur immédiate de couverture. Il ne place aucun pari automatiquement. Une recommandation future reste soumise à validation humaine.

## Périmètre du prochain jalon

Le prochain jalon majeur livre un pipeline football fiable. Il ne regroupe pas encore les cotes, les probabilités, les valuebets ou la publication.

### Compétitions

| Usage | Compétitions |
|---|---|
| Calendrier actif | Liga Portugal (`PPL`), LaLiga (`PD`), Eredivisie (`DED`) et Championship (`ELC`) |
| Enrichissement complet initial | Liga Portugal et LaLiga, uniquement pour les rencontres priorisées |
| Contrôle hors noyau européen | Saudi Pro League |
| Extension prudente | Autres compétitions en `CALENDAR_ONLY` ou `PILOT` jusqu'à une preuve rejouable |

### Chaîne cible

```text
Planificateur
  → registre de capacités et budget
  → routeur fournisseur
  → adaptateur fournisseur
  → audit d'appel et snapshot brut
  → parsing et replay
  → catalogue canonique
  → contrôle des identités et anomalies
  → sélection quotidienne priorisée
  → enrichissement prématch et post-match
  → validation qualité et quarantaine
```

## Architecture et exploitation

- Le système reste un monolithe modulaire dans un seul dépôt et un cycle de version commun.
- Les profils autorisés sont `control-api`, `batch-worker` et `replay`. Aucun profil live n'est autorisé pendant le prochain jalon.
- Les domaines ne dépendent ni de Spring, ni de JDBC, ni de HTTP, ni des adaptateurs.
- Les effets externes utilisent des clés d'idempotence et l'outbox.
- PostgreSQL est la source de vérité. Les migrations partagées sont immuables ; les corrections sont additives.
- Les tests de persistance utilisent PostgreSQL/Testcontainers, jamais H2.
- Le profil `replay` reste hors réseau, sans DataSource, sans Flyway et sans PostgreSQL ; il rejoue des fichiers ou octets en mémoire. Le rejeu d'un snapshot PostgreSQL passe exclusivement par un cas d'usage interne du `control-api`.
- Aucun microservice, broker externe, Redis ou Kubernetes n'est introduit sans besoin démontré et nouvelle décision.
- Le staging VPS ne commence qu'après acceptation du pipeline local fiable.

## Fournisseurs et registre de capacités

Il n'existe pas de gagnant fournisseur global. Le choix se fait par `fournisseur × compétition × saison × phase × type de donnée` et repose sur une preuve datée, expurgée et rejouable.

La baseline MVP est la suivante :

- Highlightly est `PRIMARY` pour le calendrier étendu et l'enrichissement 2026 dont la capacité a été mesurée ;
- football-data.org est `CONTROL` pour les calendriers et identités de `PPL`, `PD`, `DED` et `ELC` ;
- API-FOOTBALL est `BLOCKED_BY_PLAN` pour le chemin critique gratuit de la saison 2026 ; son retour exige un nouveau test daté ;
- les qualifications UEFA peuvent servir au calendrier, score, statistiques d'équipe et événements, sans dépendance aux compositions ou données joueurs avant réplication des tests ;
- aucune dépendance à SofaScore et aucun appel à ses endpoints privés n'est autorisé.

Les DTO et identifiants fournisseurs restent confinés aux adaptateurs. Une source `CONTROL` n'écrase jamais le canon alimenté par une source `PRIMARY` ; une divergence devient une anomalie visible.

## Quotas et planification

- Le budget opérationnel Highlightly est de 80 appels avec une réserve incompressible de 20.
- Le calendrier est consommé avant le calcul de la capacité d'enrichissement.
- Le plafond conservateur est de sept rencontres enrichies par jour.
- Le planificateur retient la valeur la plus prudente entre compteur fournisseur, compteur local et budget projet.
- Aucun appel ne doit entamer la réserve.
- Aucune remise à zéro n'est supposée à minuit Europe/Paris ; une fenêtre incertaine reste contrainte.
- Les erreurs HTTP 401, 403 ou 429 désactivent le fournisseur pour la fenêtre courante et ouvrent un incident exploitable.

## Enrichissement et qualité

Les compositions sont facultatives et non bloquantes. Leur état est explicite : `ABSENT`, `INCOMPLETE`, `COMPLETE`, `COMPLETE_LATE` ou `UNKNOWN`.

Les vérifications admissibles sont planifiées à T-60, T-45, T-30 et T-15. T-5 n'est autorisé que pour une rencontre explicitement prioritaire. Les appels cessent dès qu'une composition est complète et aucune composition prématch ne peut être validée à T0 ou après T0.

La première disponibilité, la dernière vérification admissible et le caractère tardif d'une composition sont persistés. Lorsque les identifiants joueurs divergent entre endpoints, le rapprochement s'appuie sur la provenance d'endpoint, l'équipe, le rôle, le nom et le temps de jeu ; il n'est jamais déduit du seul identifiant joueur Highlightly.

Le post-match comprend un snapshot initial puis, pour les rencontres prioritaires, un second snapshot configurable par défaut vers T+60 minutes.

Les règles suivantes sont invariantes :

- une donnée absente n'est jamais remplacée silencieusement par zéro ;
- les statistiques d'équipe sont associées par `team.id`, jamais par position dans un tableau ;
- aucune jointure inter-endpoints ne repose sur le seul identifiant joueur Highlightly ;
- pour les faits disciplinaires contradictoires, les événements priment sur le box-score joueur ;
- une donnée douteuse est conservée avec sa provenance puis exclue ou mise en quarantaine, pas corrigée arbitrairement ;
- les payloads nécessaires à l'audit restent conservés et rejouables hors réseau.

## Identités et observations canoniques

- Toute observation est conservée, même si elle n'est pas appliquée au canon.
- Une observation antérieure à la dernière observation appliquée ne régresse pas l'état canonique.
- `FINISHED` et `CANCELLED` sont terminaux.
- `SCHEDULED` peut devenir `POSTPONED`, `CANCELLED` ou `FINISHED`.
- `POSTPONED` peut redevenir `SCHEDULED` après reprogrammation, ou devenir `CANCELLED` ou `FINISHED`.
- Toute autre régression de statut ouvre une anomalie au lieu de modifier silencieusement le canon.
- Une référence fournisseur ne devient jamais un identifiant canonique.
- Une identité ambiguë n'est pas rapprochée automatiquement sur le seul nom.
- Le libellé brut `Hirnyk` reste une preuve d'ambiguïté ; seule une référence exacte et un mapping explicite de type `CONFIRMED_HISTORICAL_REBRAND_ALIAS` peuvent la relier historiquement à `FC Kryvbas Kryvyi Rih`.
- L'ordre source des participants est toujours conservé. Une identité insensible à l'ordre n'est permise que lorsque `participantsUnordered=true` est explicitement porté par l'observation ; `neutralVenue=true` seul ne permet jamais une inversion.

## Axes fonctionnels différés

Après acceptation du pipeline fiable, les lots sont envisagés dans cet ordre :

1. `ODD-001` : cotes, bookmakers réels, plateformes et observations horodatées ;
2. `ANL-001` : probabilités versionnées, valuebets, explications et backtests temporels ;
3. `PUB-001` : dossiers, bilans et publication idempotente vers Drive, courriel et Telegram ;
4. `LIVE-001` : nouvelle décision d'architecture et autorisation explicite d'un profil live ;
5. extension multisport : eSports League of Legends/CS2 et MMA, puis éventuellement tennis, basket et rugby après validation des abstractions.

Le football reste la priorité 0. Les autres sports ne deviennent actifs qu'après une décision dédiée.

## Gouvernance

Chaque changement significatif dépend d'un Work Order. Codex prépare un diff borné dans une branche ou un worktree isolé ; le porteur le relit et rejoue les tests avant d'autoriser séparément commit, push, Pull Request et fusion conformément à ADR-005.

Les limites et couvertures fournisseurs actuellement retenues proviennent des mesures du 9 au 17 août 2026. Toute capacité non testée ou devenue incertaine requiert une nouvelle preuve datée et rejouable.
