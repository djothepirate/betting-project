# Historique des évolutions

Ce fichier recense les lots fonctionnels du dépôt. Il ne remplace ni les critères d'acceptation détaillés des Work Orders, ni l'historique Git.

## En préparation — DEVX-001

### Préparé dans le lot

- correction du Maven Wrapper Windows avec Maven 3.9.16 et vérification de la distribution par SHA-256 ;
- normalisation LF du wrapper POSIX pour les checkouts Windows utilisés depuis WSL2 ;
- propagation fiable des codes de sortie dans la validation Windows ;
- exécution obligatoire des tests PostgreSQL/Testcontainers dans la validation complète ;
- contrôles de non-régression des scripts et contrôle de secrets étendu au working tree, à l'index Git, aux blobs `HEAD` et aux commits intermédiaires du diff ;
- CI Windows/Linux sans étape, configuration, clé ni appel fournisseur ;
- mémoire durable du projet : cadrage, état courant, feuille de route et procédures actualisées.

### Gouvernance

Le lot reste en cours tant que toutes ses validations n'ont pas été consignées dans `docs/work-orders/DEVX-001.md` et que le porteur ne l'a pas revu. Aucun commit, push, Pull Request ou fusion n'est implicite.

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

## Lots de benchmark hors de `main`

INV-01, CAL-01 et ENR-001 ont produit des mesures et preuves conservées hors Git. ENR-001 n'est pas encore intégré : son collecteur reste un outil de benchmark contrôlé et ne constitue pas le connecteur de production du futur pipeline.
