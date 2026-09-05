# T3-A — Revue préparatoire de V005

Début UTC : 2026-09-04 23:43:52 UTC

## CONTEXT_READY

Horodatage UTC : 2026-09-04 23:44:30 UTC

- Base de référence communiquée : HEAD `5a8161e0070632f0b0dbbff760194e57ab4e5b1f` ; cible opérationnelle : PostgreSQL déjà utilisé au niveau V004, contenu réel non inspecté.
- Objectif : déterminer si les preuves disponibles suffisent avant V005 et préparer la validation manquante, sans appliquer la migration.
- Périmètre : V004, V005, V005MigrationIT, contrat stored-snapshot-replay-v1 et code/tests de replay directement utiles. Lecture seule ; aucun réseau, Maven, test applicatif, accès à .env ou secret, ni mutation Git.
- Invariants : préserver les preuves et données V004 ; ne fabriquer aucun historique ; garantir références, idempotence, intégrité des payloads et atomicité des tentatives ; réserver le replay durable à control-api avec reprise manuelle.
- Constat initial : V005 ajoute quatre tables et neuf index nommés ; un index porte sur raw_snapshot existant. Le test de migration ne peuple qu'un snapshot et un reçu puis contrôle certains objets et refus SQL.
- Prochaine action : confronter ces assertions aux tests de replay et distinguer migration, cohérence applicative et risques d'exploitation sur une base peuplée.

## Note de revue

V005 est additive et cohérente avec l'absence de reconstruction d'historique. Les sources fournissent une base utile, mais ne suffisent pas à accepter son application à une base V004 utilisée. Les « preuves existantes » ci-dessous sont des assertions lues, sans résultat d'exécution vérifié.

| Invariant | Scénario | Preuve existante ou manquante |
|---|---|---|
| Préserver V004 sans inventer d'historique | Migration d'une base peuplée | `V005MigrationIT.migratesPopulatedV004WithoutInventingReplayHistory` conserve les comptes d'un snapshot et d'un reçu, puis vérifie quatre tables vides et Flyway 005 réussi. Il manque les valeurs/octet-à-octet, mappings, décisions, anomalies, événements, observations et journaux existants. |
| Schéma et références cohérents | Objets, FK, unicités, états invalides | V005 définit FK, contraintes, neuf index explicites. Le test compte les noms d'objets et quelques refus SQL ; il ne contrôle ni leurs définitions ni l'ensemble des FK/unicités. Le refus de passage à COMPLETED viole aussi le compteur de tentatives ; les assertions SQLException n'identifient pas la contrainte visée. |
| Idempotence et sélection exacte | Même clé, hash ambigu, décision/snapshot | `StoredSnapshotReplayIT` couvre répétition, ambiguïté globale et décisions confirmées/rejetées ; `NormalizationReplayRequestServiceTest` couvre le conflit de contenu. Manquent un scénario post-migration avec anciens reçus et des refus SQL isolés pour chaque unicité. |
| Payload intact, aucun effet canonique doublé | Payload altéré, reprise, sélection par UUID | `StoredSnapshotReplayIT` vérifie échec terminal sur altération, absence de nouvelles applications/corrélations et déduplication des effets. Le service vérifie compression identity et empreintes ; le test de migration utilise une empreinte artificielle, donc ne démontre pas le rejeu d'une preuve V004 valide. |
| Tentative atomique et reprenable | Concurrence, échec, redémarrage | `StoredSnapshotReplayIT` couvre deux reprises simultanées et une erreur de normalisation avec savepoint. `ControlApiRestartIT` ferme trois contextes/pools dans la même JVM : preuve de relecture durable, pas de destruction réelle du processus pendant une tentative. |
| Audit exact et séparation des profils | Corrélations, historique, activation | Les IT vérifient des corrélations précises ; V005 empêche de rattacher deux fois un journal. L'adaptateur de tentative insère seulement, mais V005 n'interdit pas UPDATE/DELETE. Les tests de profils déclarent l'absence des composants durables hors control-api. |

Point de compatibilité à résoudre : V004 accepte des reçus `REPLAY_REQUEST`, et le test en conserve un sans demande. Dans `NormalizationReplayRequestService.resolveIdempotent`, une clé ancienne dont type et empreinte correspondent conduit à une exception si sa demande manque. Cela ne justifie aucun remplissage rétroactif : inventorier ces reçus sans divulguer leurs clés et définir leur réponse attendue.

Mini-plan proposé, à exécuter ensuite :

1. Préparer une restauration isolée et représentative de V004, expurgée ; vérifier version PostgreSQL, historique/checksums Flyway, droits, volume de raw_snapshot et espace disponible. Fixer une fenêtre et des limites mesurables de durée/verrouillage : l'index initial utilise CREATE INDEX sans CONCURRENTLY sur la table existante.
2. Comparer avant/après comptes, identités, empreintes et valeurs des preuves et objets V004. Cibler explicitement 005 : le test actuel migre vers « latest ». Vérifier définitions d'index, FK, contraintes et absence de nouveaux historiques ; relancer Flyway pour constater l'absence de seconde application.
3. Exécuter les tests existants PostgreSQL/Testcontainers, puis ajouter des cas SQL indépendants identifiant SQLSTATE/contrainte : références absentes, doublons, origine/décision, compteurs, dates, erreurs et résultats. Tester l'interruption de migration et la restauration.
4. Valider un vrai replay de snapshot antérieur, la politique des anciens reçus, et injecter des échecs d'ajout de tentative, corrélation et finalisation. Vérifier rollback total de la tentative, maintien de la demande/décision déjà commitée, puis reprise. Tester un arrêt brutal pendant la transaction et expliciter la portée applicative de l'append-only.

Acceptation limitée : aucune donnée V004 altérée, schéma attendu, zéro historique fabriqué, preuves transactionnelles concluantes et interruption mesurée compatible avec la fenêtre convenue. Clarifications opérationnelles nécessaires : version/volume cibles, fenêtre disponible et existence de reçus REPLAY_REQUEST antérieurs.

Contrôles exécutés : lecture statique uniquement. Aucun test, migration, accès réseau ou base. Seul fichier écrit : T3-A.md ; aucune source du dépôt modifiée.

Fin UTC : 2026-09-04 23:46:33 UTC

