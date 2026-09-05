# T3-B — Revue préparatoire V005

Début : 2026-09-04 23:44:09 UTC.

## CONTEXT_READY — 2026-09-04 23:44:39 UTC

- Base : dépôt à la référence annoncée `5a8161e0070632f0b0dbbff760194e57ab4e5b1f` ; PostgreSQL V004 déjà utilisé, avec snapshots, reçus globaux, décisions de mapping et cycle des anomalies.
- Objectif : préparer la revue et les critères de validation avant l'upgrade V005, sans exécuter la migration.
- Périmètre : V004/V005, contrat de replay stocké, test de migration et classes/tests directement utiles ; lecture seule, sans réseau, Maven, base réelle, secret ni mutation Git.
- État après : quatre tables de demandes/tentatives/corrélations, index de recherche SHA ; aucune création rétroactive d'historique.
- Invariants : conservation des données et provenance existantes ; idempotence globale et unicité décision/snapshot ; intégrité référentielle ; historique append-only ; exécution après commit, claim unique et reprise durable ; absence d'effets canoniques doublonnés.
- Prochaine action : distinguer assertions effectivement présentes, garanties de schéma/application et preuves manquantes pour une base V004 représentative.

La fiche est enregistrée avant l'approfondissement. Aucun contrôle applicatif exécuté.

## Note de revue

**Avis : V005 est additive, mais les preuves lues ne suffisent pas à accepter l'upgrade d'une base utilisée.** Elle crée quatre tables et neuf index explicites, sans backfill. V004 demeure intacte. Les reçus gardent leur namespace global ; les unicités couvrent reçu/demande, décision/snapshot, demande/numéro de tentative et attribution unique des journaux.

| Invariant | Scénario | Preuve existante ou manquante |
|---|---|---|
| Préservation V004 | Upgrade prérempli, aucun faux historique | `V005MigrationIT:33` conserve les comptes d'un snapshot et d'un reçu, vérifie quatre tables vides et Flyway. Manquent décisions, anomalies, observations et comparaison exacte des données/provenance. |
| Schéma valide | Installation neuve, contraintes et index | Tests PostgreSQL 17/Testcontainers présents ; migration IT vérifie surtout les noms d'objets. Ajouter définitions, FK, unicités, états, dates et sélecteurs. |
| Idempotence | Répétition, collision, concurrence | `StoredSnapshotReplayIT:160` couvre la répétition ; `NormalizationReplayRequestServiceTest:154` la collision de sélecteur en mémoire. Manquent créations concurrentes et collision entre types en PostgreSQL. |
| Preuve source intacte | SHA ambigu, payload altéré | `StoredSnapshotReplayIT:195,269` vérifie absence de reçu sur ambiguïté et échec terminal sans nouvel effet. Ajouter compression et empreinte enregistrée incohérente. |
| Audit append-only | Modification/suppression d'une tentative ou corrélation | Adaptateur journal limité à insertion/lecture ; V005 n'ajoute aucun trigger ni restriction de privilèges interdisant UPDATE/DELETE. Protection SQL non démontrée. |
| Atomicité | Panne pendant création ou exécution | `StoredSnapshotReplayIT:527` couvre rollback au savepoint puis reprise ; `MappingDecisionIT:298–408` couvre des pannes antérieures à la création replay. Manquent panne d'insertion demande, journal de tentative, corrélation replay et CAS final. |
| Reprise durable | Double claim, arrêt avant/après commit | `StoredSnapshotReplayIT:487` vérifie une seule tentative. `ControlApiRestartIT:42` recrée trois contextes/pools dans la même JVM ; aucune preuve d'arrêt brutal d'un processus pendant la transaction. |

Les assertions négatives de `V005MigrationIT` demandent seulement `SQLException` : le sélecteur invalide réutilise un reçu déjà associé ; la demande passée à COMPLETED conserve aussi zéro tentative. Ces tests peuvent rester verts si une autre contrainte provoque le rejet. Isoler chaque violation et vérifier SQLSTATE/nom de contrainte. Le snapshot prérempli utilise un hash synthétique sans lien avec ses octets : ce test ne prouve pas sa rejouabilité.

La création est transactionnelle ; le callback intervient après commit et l'exécution utilise `REQUIRES_NEW`, avec claim/CAS, savepoint puis journal/corrélations/état final. Les états terminaux sont protégés par l'application ; les CHECK SQL seuls n'imposent pas toute la machine d'états. La reprise des demandes non terminales reste manuelle.

Mini-plan proposé, non exécuté :

1. Sur copie assainie représentative V004, comparer avant/après UUID, octets/hash, fournisseur, endpoint, réception, connecteur et historiques complets. Vérifier Flyway/checksums, puis absence totale de replay rétrocréé ; tester également une installation vide.
2. Compléter les scénarios manquants de la matrice sur PostgreSQL/Testcontainers, avec vérification depuis une connexion indépendante : aucun succès visible avant commit ; rollback sans perte de la décision déjà commitée ; reprise unique après arrêt brutal.
3. Préparer sauvegarde/restauration vérifiée et fenêtre d'exploitation ; mesurer espace, durée et contention du `CREATE INDEX` non concurrent sur `raw_snapshot`. Exécuter ensuite `.\mvnw.cmd -Pintegration verify` et les contrôles standards `.\mvnw.cmd verify` ; conserver des résultats nouveaux et expurgés.

Acceptation limitée à une répétition réussie sur copie représentative et aux garanties explicitement prouvées. Clarifications avant application : version/volume PostgreSQL, droits SQL effectifs, tolérance aux blocages et portée attendue de l'append-only. Toute correction d'une migration déjà partagée doit être additive.

Exécuté : lecture statique uniquement. Aucun SQL, Maven, test applicatif, réseau ou contrôle de secrets exécuté. Seul fichier écrit : `T3-B.md` ; code et migrations inchangés.

Fin : 2026-09-04 23:46:57 UTC.
