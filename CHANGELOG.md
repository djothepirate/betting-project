# Historique des évolutions

Ce fichier recense les lots fonctionnels du dépôt. Il ne remplace ni les critères d'acceptation détaillés des Work Orders, ni l'historique Git.

## Prêt pour revue humaine — ENR-001

### Finalisé dans le lot

- reconstruction du diff historique depuis une sauvegarde restaurable et revalidée, sans modifier le checkout source ;
- conservation du collecteur comme outil manuel de benchmark, distinct des futurs adaptateurs de production ;
- index expurgé de 127 appels et vérificateur du corpus strictement hors réseau et en lecture seule ;
- sept fixtures synthétiques représentatives des familles réellement appelées, avec manifeste de provenance et tests ;
- garde de quota prudente, verrou d'exécution exclusif et écritures atomiques des preuves et replays ;
- conservation séparée de l'ambiguïté historique `Hirnyk` et du mapping explicite de `highlightly:TEAM:5522923` ;
- baseline MVP acceptée par capacité, compétition, saison, phase et type de donnée.

### Validation

- 57 tests standards réussis ;
- 12 tests PostgreSQL/Testcontainers réussis ;
- 127 preuves sur 127 vérifiées, empreintes conformes et replays `PASS` ;
- manifeste valide de 12 rencontres principales, 4 réserves et 1 contrôle transversal ;
- validation Windows complète et contrôle de secrets réussis, sans appel fournisseur.

### Gouvernance

Le lot est finalisé dans le working tree de `codex/enr-001` et prêt pour revue humaine. Il n'est pas encore commité, publié ou fusionné ; ces opérations exigent des autorisations distinctes.

## 2026-09-01 — DEVX-001

### Ajouté

- correction du Maven Wrapper Windows avec Maven 3.9.16 et vérification de la distribution par SHA-256 ;
- normalisation LF du wrapper POSIX pour les checkouts Windows utilisés depuis WSL2 ;
- propagation fiable des codes de sortie dans la validation Windows ;
- exécution obligatoire des tests PostgreSQL/Testcontainers dans la validation complète ;
- contrôles de non-régression des scripts et contrôle de secrets étendu au working tree, à l'index Git, aux blobs `HEAD` et aux commits intermédiaires du diff ;
- CI Windows/Linux sans étape, configuration, clé ni appel fournisseur ;
- mémoire durable du projet : cadrage, état courant, feuille de route et procédures actualisées.

### Validation et gouvernance

Le lot a été revu, publié et fusionné dans `main` par la Pull Request `#2`, après correction du défaut P2 signalé en revue et validation des CI Windows et Linux. DEVX-001 est accepté, fusionné et clôturé.

## 2026-08-11 — CAT-001

### Ajouté

- catalogue canonique des compétitions, saisons, équipes et rencontres ;
- mappings fournisseurs, anomalies structurées et provenance des observations ;
- normalisation idempotente et replay de snapshots de calendrier ;
- migration additive Flyway `V002` ;
- tests PostgreSQL/Testcontainers du catalogue et des contraintes d'architecture.

### Validation

- 17 tests standards ;
- 11 tests PostgreSQL/Testcontainers ;
- validations Windows 11 et Ubuntu/WSL2 ;
- lot fusionné dans `main` puis clôturé après autorisation humaine.

## 2026-08-11 — BOOT-001

### Ajouté

- socle Java 25, Spring Boot 4.1 et Maven Wrapper 3.9.16 ;
- monolithe modulaire et profils `control-api`, `batch-worker` et `replay` ;
- PostgreSQL, Flyway `V001`, jobs persistants et outbox minimaux ;
- replay hors réseau, règles d'architecture, ADR, runbooks et contrôles de secrets initiaux.

### Validation

BOOT-001 a été accepté, fusionné dans `main` et clôturé avant CAT-001.

## Corpus de benchmark hors de Git

INV-01, CAL-01 et ENR-001 ont produit des mesures et payloads complets conservés hors Git. ENR-001 versionne seulement les rapports, l'index expurgé, les empreintes, les fixtures synthétiques, le collecteur contrôlé et le vérificateur ; il ne constitue pas le connecteur de production du futur pipeline.
