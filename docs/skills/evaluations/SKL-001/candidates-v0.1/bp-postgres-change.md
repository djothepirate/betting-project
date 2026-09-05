---
name: bp-postgres-change
description: "Concevoir ou relire une migration et une modification persistante du Betting Project : transactions, concurrence, idempotence, upgrade prérempli et reprise. PostgreSQL/Testcontainers reste la preuve d'exécution."
---

# Modifier une persistance PostgreSQL
Résoudre les références depuis la racine du dépôt et lire AGENTS.md. Examiner le WO, les ports applicatifs, l'adaptateur JDBC et les migrations réellement concernés. Les frontières sont décrites dans docs/architecture/module-boundaries.md.

## Concevoir ou relire
- Décrire l'état durable avant/après, les invariants, contraintes/index, clé d'idempotence et namespace, puis la frontière transactionnelle.
- Examiner nominal, doublon identique, collision de contenu, concurrence et panne au milieu d'une transaction. Choisir verrous, unicité ou compare-and-set selon l'invariant ; ne pas ajouter un verrou général sans besoin.
- Préserver les preuves append-only et les migrations partagées. Une correction de migration partagée est additive. Ne pas inventer d'historique lors d'un backfill.
- Un effet externe utilise l'outbox ; préciser quand le résultat peut être acquitté et ce que le redémarrage doit conserver. Un lecteur ne doit pas pouvoir observer un faux succès avant commit.

## Validation adaptée
Prévoir installation neuve et upgrade depuis la version antérieure préremplie ; contrôler données/provenance, contraintes et triggers pertinents, pas seulement le démarrage Flyway. Pour une évolution de replay, lire docs/contracts/stored-snapshot-replay-v1.md et les tests V005MigrationIT, StoredSnapshotReplayIT ou ControlApiRestartIT selon le sujet.

Tester concurrence/rollback/redémarrage lorsqu'ils sont touchés. Les tests de persistance utilisent PostgreSQL/Testcontainers via le profil integration. Une analyse des tests existants donne une couverture documentée, pas un résultat d'exécution nouveau.

## Sortie
Donner changement ou revue, matrice invariant → scénario → preuve existante/manquante, migrations impactées et commandes appropriées. Ne pas modifier une base réelle ni exécuter une purge pour une simple demande de revue.

