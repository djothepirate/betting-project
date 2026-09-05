Début : 2026-09-04 23:52:37 UTC

CONTEXT_READY : 2026-09-04 23:53:05 UTC

Base : dépôt Betting Project, HEAD de référence fourni 5a8161e0070632f0b0dbbff760194e57ab4e5b1f ; base PostgreSQL V004 déjà utilisée.
Objectif : revue de couverture de V005 et mini-plan préalable à son application, sans migration exécutée.
Périmètre : V004, V005, contrat stored-snapshot-replay-v1 et implémentation/tests directement utiles. Lecture seule du dépôt ; seule cette note est écrite.
Invariants : préserver les données et preuves V004, ne pas fabriquer d’historique ; namespace global des commandes idempotentes ; demandes durables atomiques, claim concurrent unique ; tentatives et corrélations append-only ; validation des octets et de leur provenance ; reprise après rollback et redémarrage sans faux succès.
État durable visé : ajout de demandes versionnées, tentatives et deux tables de corrélation, plus index sur empreinte des snapshots ; aucun backfill des demandes.
Prochaine action : examiner V005MigrationIT puis les preuves de concurrence, rollback et redémarrage et l’adaptateur JDBC strictement nécessaires ; distinguer tests présents et tests exécutés.

Revue et mini-plan

V005 est additive : quatre tables de replay et des index, dont un index non unique sur l’empreinte des snapshots. Elle ne réécrit aucune preuve V004 et n’invente aucune demande. Cette lecture rend le changement révisable ; elle ne qualifie pas son application à une base utilisée.

| Invariant | Scénario | Preuve écrite / complément nécessaire |
|---|---|---|
| Conservation de V004 | Upgrade prérempli, sans historique fabriqué | `V005MigrationIT.migratesPopulatedV004WithoutInventingReplayHistory` vérifie un snapshot, un reçu, quatre tables vides, neuf index et Flyway 005 réussi. Ajouter mappings, décisions, anomalies, événements, observations et journaux ; comparer identifiants, valeurs, octets et provenance avant/après, au-delà des comptes. |
| Schéma utilisable | Installation neuve et upgrade | Les IT de replay démarrent sur PostgreSQL 17/Testcontainers. Le test de migration vérifie quelques contraintes, sans couvrir toutes les FK, unicités, états et dates. Ajouter des cas négatifs isolés et vérifier SQLSTATE/nom de contrainte : son passage à COMPLETED viole aussi le compteur de tentatives, donc ne prouve pas seul la contrainte de date. |
| Idempotence globale | Doublon identique, autre contenu/type, concurrence | `StoredSnapshotReplayIT.requestByUniqueShaIsIdempotentAndTerminalReplayIsNotAttemptedAgain` couvre répétition et terminal ; `NormalizationReplayRequestServiceTest` couvre le conflit de sélecteur en mémoire. Ajouter collision avec un reçu V004 d’un autre type et créations concurrentes sur PostgreSQL ; vérifier absence de reçu orphelin. |
| Preuve brute fiable | Empreinte ambiguë, octets altérés, compression | Les IT couvrent ambiguïté globale sans écriture et altération terminale sans nouvel effet canonique. Ajouter compression non prise en charge, empreinte enregistrée divergente et conservation exacte des métadonnées historiques. |
| Claim et atomicité | Reprises concurrentes, panne intermédiaire | `simultaneousResumesClaimOneLogicalAttemptOnly` attend une tentative. `failedNormalizationIsDurablyRetryableAndCompletesAfterResume` injecte une erreur de journal d’application puis reprend. Ajouter échecs d’insertion de tentative, corrélations et finalisation : rollback intégral de la transaction d’exécution, demande antérieure conservée. |
| Audit append-only | Réattribution, UPDATE/DELETE des preuves | V005 impose unicité des numéros et des corrélations ; le journal JDBC expose l’ajout. V005 ne crée aucun trigger ni restriction de droits interdisant UPDATE/DELETE : vérifier la protection effective sous le rôle applicatif et décider du niveau requis. |
| Reprise durable | Arrêt après création, avant callback | `ControlApiRestartIT` ferme puis recrée trois contextes Spring sur la même base, reprend PENDING et retrouve le terminal. Ce sont des contextes successifs dans un même test, pas une coupure brutale du processus en transaction ; compléter par interruption après claim et lecture concurrente interdisant un faux succès. |

Le code sépare création transactionnelle et callback après commit ; chaque tentative utilise `REQUIRES_NEW`, compare-and-set et savepoint. Une panne de la seconde transaction doit préserver la première. Aucun worker ne garantit la reprise : elle reste manuelle. Aucun nouvel effet fournisseur n’appelle ici une validation d’outbox.

Contrôles proposés : enrichir d’abord la fixture V004 et les cas SQL ciblés, puis lancer ` .\mvnw.cmd -Pintegration verify` sur une base jetable, sans second `verify` systématique. Rejouer ensuite l’upgrade sur une copie expurgée représentative : comparer données et provenance, valider Flyway, vérifier droits/triggers effectifs, mesurer durée et contention de l’indexation de `raw_snapshot`, éprouver restauration et reprise. Toute correction d’une migration déjà partagée doit être additive.

Contrôles exécutés : uniquement lectures du SQL, du contrat et du code/tests cités. Aucun Maven, test applicatif, réseau, accès à une base ou migration. Aucun résultat historique utilisé. Seule cette note est écrite.

Acceptation limitée : couverture documentée, application non validée. Avant application, obtenir les preuves PostgreSQL au commit retenu et traiter les lacunes ci-dessus. Clarifications opérationnelles restantes : version/volume de la base cible, rôle applicatif et protection append-only attendue, fenêtre disponible pour les verrous. Elles n’empêchent pas de préparer les tests.

Fin : 2026-09-04 23:54:38 UTC
