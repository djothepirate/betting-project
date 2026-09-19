# MVP-001 — Lot 6 : qualification intégrée et préparation de revue

## Reprise — 2026-09-19

- Activation explicite par la demande « Réaliser le lot 6 ».
- Base de ce lot : `6843ff833c1c74de26f6bfc6c7b71a11e3d64b5e`, worktree propre.
- Base du Work Order conservée : `5e4b05b5a1a7cfa66850643db31d9612192e7281`.
- Branche : `feature/V0.1.0-RC01-CODEX-MVP-001` ; cible : `feature/V0.1.0-RC01`.
- La CI `35411773181` est verte sur la base du lot 6 ; la cible distante est encore au SHA
  de départ et aucune Pull Request de cette branche n'existe au début de la reprise.

## Objectif et frontières

Qualifier le candidat complet, consolider les preuves de migration, de reprise et de collecte
simulée, finaliser les contrats/runbooks et préparer la revue humaine de la PR. La revue technique
ne remplace pas celle du porteur : les deux critères de revue humaine et de fusion restent ouverts.
Le commit/push des travaux qualifiés et la PR de clôture suivent les autorisations MVP-001 ; aucune
fusion, activation fournisseur ou livraison du train n'est exécutée implicitement.

Les migrations V001–V011 restent immuables. Aucune V012 n'est prévue. Les tests utilisent des
fournisseurs en mémoire ou loopback et PostgreSQL/Testcontainers ; registre réel vide, clients et
boucle worker désactivés. Le plafond sept du lot 5 est prévisionnel, pas un droit d'enrichissement.

## Plan de qualification

1. Vérifier les preuves existantes et renforcer seulement une lacune d'acceptation démontrée,
   notamment la conservation des données V008 après la chaîne V009–V011.
2. Relire les frontières budget/envoi, jobs/preuves/replay, autorité et API interne.
3. Exécuter les tests ciblés pertinents puis `mvnw.cmd -Pintegration verify`, le scan et les
   contrôles d'intégrité. Les totaux historiques 615/239 ne sont pas une nouvelle exécution.
4. Produire le rapport de revue et synchroniser la mémoire du dépôt, sans déclarer le WO fusionné.
5. Publier le candidat qualifié, vérifier sa CI et ouvrir la PR vers le train pour revue humaine.

## Qualification réellement exécutée

Le parcours V008 peuplée → dernière migration est déjà porté par `V009MigrationIT` : la cible
Flyway finale n'y est pas figée à V009 et applique V009, V010 puis V011. Les UUID, faits et dates
de l'équipe, du mapping, de l'audit et de l'outbox préexistants sont vérifiés. `V010MigrationIT`
conserve séparément budget, brut et audit d'une V009 peuplée ; `V011MigrationIT` conserve les
preuves calendrier et l'ancien job d'une V010 peuplée, sans inventer claim ou historique.
Les trois suites couvrent aussi l'installation neuve et les contraintes. Aucune lacune requérant
une nouvelle migration, fixture ou test n'a été constatée pour cette porte.

Revue technique ciblée des chemins budget/envoi, normalisation/autorité, dérivation/replay,
claims/fencing, API et profils : pas de correctif fonctionnel nécessaire. Les tests à haut risque
sont inclus dans la suite intégrée complète plutôt que relancés une première fois isolément.

Exécution Windows terminée le **19 septembre 2026 à 10:32:46 Europe/Paris** :

- `mvnw.cmd -Pintegration verify` : **615 tests standards et 239 tests PostgreSQL/Testcontainers**,
  zéro échec, erreur ou omission, `BUILD SUCCESS`, code 0, durée 2 min 23 s ;
- 76 rapports Surefire et 25 rapports Failsafe régénérés pendant ce run ; le résumé Failsafe
  confirme 239 terminés, aucun timeout ni flaky test ;
- Java 25.0.4, Maven 3.9.16, Spring Boot 4.1.1/Spring 7.0.9, Docker Desktop par named pipe,
  PostgreSQL 17.10 ; les versions du README ont été alignées sur le POM/classpath déjà présents ;
- les suites incluent les trois profils, ArchUnit, upgrades, concurrence, redémarrages réels
  de contextes/pools, collecte simulée jusqu'au contrôle API et non-régressions CAT-002/INT-001 ;
- `scripts/check-no-secrets.cmd` : `PASS (All)`, code 0 ; aucun secret à forte confiance ou
  endpoint privé SofaScore détecté ;
- `git diff --check` et contrôle séparé des espaces finaux des fichiers non suivis : propres ;
- index vide avant indexation bornée ; aucun diff sous `src`, POM, scripts, CI ou changelog
  depuis `6843ff8` ; registre classpath vide et aucune V012 ;
- les onze SHA-256 sont identiques à ceux du lot 5 ; la table complète est dans le
  [dossier de revue](../reviews/MVP-001-final-review.md).

La suite complète constitue une nouvelle exécution d'acceptation, pas une recopie de 615/239.
Le lot ne change aucun script : pas de Pester, `verify-windows.cmd` ou second build standard local
redondant. Aucun test WSL n'est exécuté ; la qualification Linux est celle de la CI du SHA publié.
Les contrôles de sécurité et de périmètre sont répétés sur le diff final avant publication.

## Documentation et périmètre final

Treize documents constituent le diff propre du lot 6 : Work Order et rapport d'exécution,
dossier final de revue, README/état/roadmap, frontières modulaires, contrats fondations/jobs/replay
stocké, runbooks développement local/WSL/jobs. Le diff cumulé du Work Order comporte 170 fichiers.
Le dernier changement fonctionnel reste exactement `6843ff8` ; aucun changement de production,
test, schéma ou fixture n'est masqué derrière la clôture documentaire.

Les procédures périmées au stade CAT-002 sont actualisées. La preuve de redémarrage est décrite
comme des contextes et pools fermés/réouverts dans une même JVM, pas trois processus OS.
La matrice du dossier de revue couvre les vingt critères, les limites opérationnelles et la
checklist du porteur. Les preuves des campagnes externes et le changelog fusionné sont conservés.

## Sortie de qualification — avant la décision de clôture

Lot 6 : **TECHNICALLY_COMPLETED - AWAITING_HUMAN_REVIEW**.
Work Order : **ACTIVE - AWAITING_HUMAN_REVIEW**, **18/20 critères**. Les trois critères
migrations, qualification et documentation sont désormais prouvés ; les deux critères revue
humaine/CI de tête et fusion restent ouverts.

Le commit et le push documentaires sont couverts par l'autorisation de publier chaque lot
terminé ; la PR de clôture est préparée vers `feature/V0.1.0-RC01`. Après publication, les checks
de son propre SHA sont à lire et à relier dans la description de PR. Ce document ne prétend pas
qu'un commit futur a déjà réussi. Aucun merge, changement de visibilité/protection, livraison
GitLab/tag, activation fournisseur ou démarrage ENR-002 n'est effectué par cette étape.

La fin réelle de MVP-001 exige le diff relu et les tests rejoués par le porteur, les discussions
traitées, la CI de tête verte et l'autorisation distincte de fusion par merge commit vers RC01.
La livraison feature → main reste une étape séparée.

## Acceptation et clôture autorisées — 2026-09-19

Le candidat `871617b2b91740da76380cdc650e7adcebb458c8` est publié dans la
[PR #15](https://github.com/djothepirate/betting-project/pull/15) vers `feature/V0.1.0-RC01`.
Ses runs [push 35432891703](https://github.com/djothepirate/betting-project/actions/runs/35432891703)
et [PR 35432956177](https://github.com/djothepirate/betting-project/actions/runs/35432956177)
sont verts : 615 tests standards sous Windows ; 615 standards et 239 PostgreSQL/Testcontainers
sous Linux, aucun échec, erreur ou test ignoré. Les revues automatiques de code et de sécurité
sont terminées sans remarque ; aucun fil de revue ouvert et aucun conflit.

Le porteur confirme ces checks et autorise la clôture ainsi que la fusion vers RC01. Le closeout
consigne cette décision dans six documents : Work Order, dossier de revue, présent rapport,
README, état et roadmap. Aucun changement de code, test, configuration, migration, POM,
script, workflow ou changelog. Les skills de clôture et de validation imposent des contrôles
documentaires locaux et la vérification des checks du nouveau SHA, sans relancer spontanément
la suite Maven locale pour de la prose. Les résultats CI finaux sont rattachés à la tête de PR.

État avant merge : `MERGE_AUTHORIZED_IF_GREEN`, 19/20. Dès que la PR #15 est `MERGED`
par merge commit dans RC01 : lot 6 `COMPLETED`, MVP-001 `ACCEPTED - MERGED - CLOSED`,
20/20. Le contrôle de l'état distant et du merge commit ferme cette dernière porte ; aucune
fusion à venir n'est présentée ici comme réalisée. ENR-002 reste non activé, clients et registre
réels désactivés ; `main`, GitLab, tags et paramètres de forge restent hors périmètre.
