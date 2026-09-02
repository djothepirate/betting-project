# Betting Project

Betting Project est un monolithe modulaire Java/Spring destiné à construire une chaîne football prématch fiable, auditable et rejouable. `main` contient le socle applicatif BOOT-001, le catalogue canonique CAT-001, les garde-fous DEVX-001 et le benchmark football ENR-001 accepté. La collecte planifiée, l'enrichissement de production, les cotes, les valuebets et la publication restent des étapes ultérieures.

Le projet est personnel et piloté par un humain. Il ne place aucun pari automatiquement et le profil live n'est pas autorisé.

## Versions du socle

- Java 25 : Oracle JDK 25.0.4 LTS sous Windows et OpenJDK 25.0.3 sous Ubuntu/WSL2 ; règle de build `[25,26)`
- Apache Maven 3.9.16, imposé par le wrapper
- Spring Boot 4.1.0
- Spring Framework 7.0.8, géré par Spring Boot
- PostgreSQL 17 pour le développement local et les tests d'intégration

## État du dépôt

- BOOT-001 : fusionné et clôturé ;
- CAT-001 : fusionné et clôturé, avec migrations Flyway `V001` et `V002` ;
- DEVX-001 : accepté, fusionné dans `main` par la Pull Request `#2` et clôturé ;
- ENR-001 : accepté, fusionné dans `main` par la Pull Request `#3` au commit de fusion `6913cea` et clôturé ;
- CAT-002 : Work Order `ACTIVE - AWAITING_HUMAN_REVIEW` sur `codex/cat-002` ; les lots 0 à 7 sont `COMPLETED` localement et l'acceptation technique du lot 8 est prête pour la revue humaine liée au manifeste SHA-256 ; 35 critères sur 36 sont satisfaits, le dernier appartenant exclusivement au porteur ; aucun endpoint public, worker CAT-002, commit, push, Pull Request, fusion ou appel fournisseur n'a été effectué ;
- profils autorisés : `control-api`, `batch-worker` et `replay` ;
- PostgreSQL : source de vérité ;
- fournisseurs sportifs : aucun appel requis pour construire, tester ou rejouer le dépôt.

Le détail vérifiable se trouve dans [`docs/project-status.md`](docs/project-status.md) et l'ordre des prochains lots dans [`docs/roadmap.md`](docs/roadmap.md).

## Démarrage rapide sous Windows

1. Copier `.env.example` vers `.env` et remplacer le mot de passe factice.
2. Démarrer PostgreSQL : `docker compose --env-file .env up -d postgres`.
3. Vérifier le build : `.\mvnw.cmd verify`.
4. Définir localement `BETTING_OPERATOR_ID` si les décisions humaines doivent être autorisées, puis démarrer l'API de contrôle : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"`.
5. Démarrer le worker dans un autre terminal : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=batch-worker"`.

Sans profil actif, l'application démarre en mode `replay`, hors réseau et sans PostgreSQL. Les fixtures actuelles restent autonomes ; le rejeu d'un snapshot stocké appartient au `control-api` interne. Celui-ci écoute uniquement sur `127.0.0.1` et ne doit pas être exposé avant OPS-001 ; son contrat et sa procédure locale sont documentés dans [`catalog-control-api-v1.md`](docs/contracts/catalog-control-api-v1.md) et [`catalog-control-api-local.md`](docs/runbooks/catalog-control-api-local.md).

## Vérifications

- Validation standard sans Docker : `.\mvnw.cmd verify`
- Tests PostgreSQL réels avec Testcontainers : `.\mvnw.cmd -Pintegration verify`
- Recherche de secrets dans les fichiers suivis ou non suivis, les sorties locales ignorées `*.log` et `reports/**`, l'index Git, les blobs `HEAD` et chaque commit depuis la base fournie ou `origin/main` : `.\scripts\check-no-secrets.cmd`
- Validation Windows complète, intégration PostgreSQL comprise : `.\scripts\verify-windows.cmd`
- Vérification hors réseau du corpus ENR-001 restauré : `.\scripts\verify-enrichment-evidence.cmd -EvidenceRoot "C:\chemin\vers\enr01"`

La commande `verify` seule n'est pas une validation complète. La validation Windows complète exécute systématiquement le profil `integration` et échoue si Testcontainers ne peut pas joindre un moteur Docker ; la présence de la CLI `docker` n'est pas un prérequis suffisant ni nécessaire à cette détection.

Les fixtures de replay ne contactent aucun fournisseur et ne consomment aucun quota. Pour ENR-001, fusionné dans `main`, les validations du 1er septembre 2026 comptent 57 tests standards, 12 tests PostgreSQL/Testcontainers et 127 preuves externes vérifiées hors réseau. Le candidat de revue du lot 8 de CAT-002 porte la baseline à 216 tests standards et 77 tests PostgreSQL/Testcontainers : outre les contrats et scénarios des lots précédents, un test ferme puis rouvre réellement trois contextes `control-api` successifs sur la même base afin de prouver la reprise durable et la terminalité sans duplication. La validation Windows complète est verte avec un code de sortie `0`. La validation Linux complète avec Testcontainers reste une porte obligatoire de la future Pull Request ; l'absence actuelle d'intégration Docker Desktop dans WSL n'est jamais présentée comme un succès. Aucun endpoint public, worker CAT-002, appel fournisseur ni aucune opération Git de commit, push, Pull Request ou fusion n'a été autorisé ou effectué pour ces travaux locaux. Les nombres historiques de CAT-001 restent 17 tests standards et 11 tests PostgreSQL/Testcontainers.

## Documentation

- Cadrage durable : [`docs/project-brief.md`](docs/project-brief.md)
- État courant : [`docs/project-status.md`](docs/project-status.md)
- Feuille de route : [`docs/roadmap.md`](docs/roadmap.md)
- Historique : [`CHANGELOG.md`](CHANGELOG.md)
- Décisions d'architecture : [`docs/adr/`](docs/adr/)
- Work Orders : [`docs/work-orders/`](docs/work-orders/)
- Procédures : [`docs/runbooks/`](docs/runbooks/)
- Frontières modulaires : [`docs/architecture/module-boundaries.md`](docs/architecture/module-boundaries.md)
- Contrat de l'API interne du catalogue : [`docs/contracts/catalog-control-api-v1.md`](docs/contracts/catalog-control-api-v1.md)
- Exploitation locale de l'API du catalogue : [`docs/runbooks/catalog-control-api-local.md`](docs/runbooks/catalog-control-api-local.md)
- Dossier de revue finale CAT-002 : [`docs/reviews/CAT-002-final-review.md`](docs/reviews/CAT-002-final-review.md)

Les lanceurs `.cmd` des contrôles Windows appliquent une dérogation uniquement au processus PowerShell qu'ils ouvrent. Ils ne modifient pas la politique d'exécution globale du poste.
