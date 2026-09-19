# Betting Project

Betting Project est un monolithe modulaire Java/Spring destiné à construire une chaîne football prématch fiable, auditable et rejouable. `main` contient le socle applicatif BOOT-001, le catalogue canonique CAT-001/CAT-002, les garde-fous DEVX-001 et le benchmark football ENR-001 acceptés. INT-001 a livré séparément un receiver local J7 optionnel, qualifié et validé par le propriétaire ; son périmètre local est clôturé le 5 septembre 2026. Il reste désactivé par défaut ; sa livraison Git a été fusionnée par la [PR #11](https://github.com/djothepirate/betting-project/pull/11) vers `main`. La collecte planifiée, l'enrichissement de production, les cotes, les valuebets et la publication restent des étapes ultérieures.

Le projet est personnel et piloté par un humain. Il ne place aucun pari automatiquement et le profil live n'est pas autorisé.

## Versions du socle

- Java 25 : Oracle JDK 25.0.4 LTS sous Windows et OpenJDK 25.0.3 sous Ubuntu/WSL2 ; règle de build `[25,26)`
- Apache Maven 3.9.16, imposé par le wrapper
- Spring Boot 4.1.1, selon le POM du train
- Spring Framework 7.0.9, géré par Spring Boot
- PostgreSQL 17 pour le développement local et les tests d'intégration

## État du dépôt

- BOOT-001 : fusionné et clôturé ;
- CAT-001 : fusionné et clôturé, avec migrations Flyway `V001` et `V002` ;
- DEVX-001 : accepté, fusionné dans `main` par la Pull Request `#2` et clôturé ;
- ENR-001 : accepté, fusionné dans `main` par la Pull Request `#3` au commit de fusion `6913cea` et clôturé ;
- CAT-002 : accepté, fusionné dans `main` par la Pull Request `#8` au commit de fusion `85dc943` et clôturé. Le correctif fonctionnel `6fb69e2` et l'alignement documentaire `a3b471f` conservent leurs quatre checks Windows/Linux verts, 36 critères sur 36 satisfaits et la discussion P2 résolue ;
- INT-001 : `CLOSED - MERGED`. Le propriétaire a validé la readiness locale le `2026-09-04T14:13:23.3192773Z`, soit `2026-09-04T16:13:23.3192773+02:00` en Europe/Paris, puis demandé la clôture le 5 septembre. Le candidat `de06153` est conservé sur `codex/int-001-j7-receiver` ; la clôture documentaire est portée par `codex/int-001-closeout`. Le receiver J7 reste désactivé par défaut, avec migrations additives `V006` à `V008` et prochain slot `V009`. Les suites standard et PostgreSQL/Testcontainers, le mTLS, le corpus synthétique ainsi qu'une sauvegarde `age` et une restauration PostgreSQL 17 fraîche et isolée sont qualifiés. Le [rapport de clôture](docs/reviews/INT-001-closeout-20260905.md) distingue ces preuves historiques des vérifications actuelles ; le propriétaire a autorisé la publication et la création de la [PR #11](https://github.com/djothepirate/betting-project/pull/11) vers `main` ; la fusion a ensuite été autorisée et réalisée au commit `7f1f3aa` ; les campagnes réelles conservent leurs décisions propres ;
- MVP-001 : accepté par le porteur le 19 septembre 2026, avec clôture et fusion vers RC01 autorisées. La [PR #15](https://github.com/djothepirate/betting-project/pull/15), de `feature/V0.1.0-RC01-CODEX-MVP-001` vers `feature/V0.1.0-RC01`, établit la livraison : avant son merge, `MERGE_AUTHORIZED_IF_GREEN` et **19/20** ; dès sa fusion réelle par merge commit, `ACCEPTED - MERGED - CLOSED`, lots 0 à 6 terminés et **20/20**. Le [rapport du lot 6](docs/work-orders/MVP-001-lot6-execution.md) et le [dossier de revue](docs/reviews/MVP-001-final-review.md) conservent la qualification Windows **615 standards + 239 PostgreSQL/Testcontainers**, les quatre checks verts de `871617b` et les revues automatiques terminées sans remarque ; le closeout documentaire exige ses propres checks avant fusion. Le code reste celui de `6843ff8`. V001–V011 sont inchangées, registre réel vide, clients et boucle worker désactivés. Le [contrôle interne](docs/contracts/collection-control-api-v1.md) fournit une sélection prévisionnelle PPL/PD plafonnée à sept, sans réservation ni enrichissement. Aucun appel fournisseur réel, passage vers `main` ou activation d'ENR-002 ;
- profils autorisés : `control-api`, `batch-worker` et `replay` ;
- PostgreSQL : source de vérité ;
- fournisseurs sportifs : aucun appel requis pour construire, tester ou rejouer le dépôt.

Le détail vérifiable se trouve dans [`docs/project-status.md`](docs/project-status.md) et l'ordre des prochains lots dans [`docs/roadmap.md`](docs/roadmap.md).

## Démarrage rapide sous Windows

1. Copier `.env.example` vers `.env` et remplacer le mot de passe factice.
2. Démarrer PostgreSQL sur `127.0.0.1:5433` : `docker compose --env-file .env up -d postgres`.
3. Injecter les variables PostgreSQL de `.env`, dont `BETTING_DB_PORT=5433` et le mot de passe, dans l'environnement protégé des processus Maven ; Spring ne charge pas implicitement le fichier Compose `.env`.
4. Vérifier le build : `.\mvnw.cmd verify`.
5. Définir localement `BETTING_OPERATOR_ID` si les décisions humaines doivent être autorisées, puis démarrer l'API de contrôle : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"`.
6. Démarrer le worker dans un autre terminal : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=batch-worker"`.

Sans profil actif, l'application démarre en mode `replay`, hors réseau et sans PostgreSQL. Les fixtures actuelles restent autonomes ; le rejeu d'un snapshot stocké appartient au `control-api` interne. Celui-ci écoute uniquement sur `127.0.0.1` et ne doit pas être exposé avant OPS-001 ; son contrat et sa procédure locale sont documentés dans [`catalog-control-api-v1.md`](docs/contracts/catalog-control-api-v1.md) et [`catalog-control-api-local.md`](docs/runbooks/catalog-control-api-local.md).

INT-001 n'altère pas ce démarrage par défaut : `BETTING_J7_RECEIVER_ENABLED=false` conserve le `control-api` historique sur HTTP/8080 loopback et ne charge pas la route J7. Une qualification explicitement autorisée exige `127.0.0.1:8444`, HTTPS, client-auth `NEED`, compression serveur désactivée, des stores locaux absolus non UNC hors Git et une allowlist de certificat feuille synthétique en cours de validité portant explicitement l'EKU `clientAuth` ; les variantes SSL bundle, PEM et SNI sont refusées par une garde web de priorité maximale avant toute résolution de ressource TLS. Cette garde précoce impose aussi l'URL JDBC statique exacte `jdbc:postgresql://127.0.0.1:5433/<base>` et refuse DataSource alternative, JNDI/type, surcharge Hikari de localisation ou configuration Flyway dédiée avant la création de la base ou de Flyway. La garde bean répète ces contrôles puis inspecte les `JdbcConnectionDetails` et le `HikariDataSource` effectivement assemblés. Toutes les routes du `control-api`, Actuator compris, partagent alors ce connecteur mTLS ; aucun port de management séparé n'est admis. La route externe est exactement `/api/imports/sofascore/j7-canonical-events`, sans préfixe de contexte ou de servlet : slash final, segment supplémentaire, paramètre matrix et alias percent-encodé sont refusés. Les sept champs camelCase de l'ACK sont sérialisés par un codec privé déterministe indépendant du Jackson MVC global. Le runbook [`j7-import-receiver-local.md`](docs/runbooks/j7-import-receiver-local.md) ne permet ni export réel, ni appel SofaScore/Local Lab, ni VPS, ni production.

La configuration locale actuelle emploie encore le rôle propriétaire PostgreSQL pour Flyway et le runtime : ce rôle reste une frontière de confiance de la qualification, pas un modèle de production. Toute cible de production devra séparer le propriétaire de migration du rôle runtime à privilèges minimaux. Les scripts J7 documentés réalisent un dump PostgreSQL 17 complet directement vers `age --passphrase`, sans dump clair, puis une restauration directe vers une base standard fraîche, isolée et vide. Cette chaîne a été exécutée sur le corpus synthétique INT-001 : une première cible a échoué fermée avant déchiffrement et avant mutation sur un alias SQL réservé, puis la cible Q2 neuve a été restaurée et ses cinq familles de preuves ont été vérifiées. Les catalogues observables sont contrôlés ; l'absence de template personnalisé, de `pg_upgrade`, de clone et de lecteur réseau mappé reste une frontière opérateur.

## Vérifications

- Validation standard sans Docker : `.\mvnw.cmd verify`
- Tests PostgreSQL réels avec Testcontainers : `.\mvnw.cmd -Pintegration verify`
- Recherche de secrets dans les fichiers suivis ou non suivis, les sorties locales ignorées `*.log` et `reports/**`, l'index Git, les blobs `HEAD` et chaque commit depuis la base fournie ou `origin/main` : `.\scripts\check-no-secrets.cmd`
- Validation Windows complète, intégration PostgreSQL comprise : `.\scripts\verify-windows.cmd`
- Vérification hors réseau du corpus ENR-001 restauré : `.\scripts\verify-enrichment-evidence.cmd -EvidenceRoot "C:\chemin\vers\enr01"`

La commande `verify` seule n'est pas une validation complète. La validation Windows complète exécute systématiquement le profil `integration` et échoue si Testcontainers ne peut pas joindre un moteur Docker ; la présence de la CLI `docker` n'est pas un prérequis suffisant ni nécessaire à cette détection.

Les fixtures de replay ne contactent aucun fournisseur et ne consomment aucun quota. Pour ENR-001, fusionné dans `main`, les validations du 1er septembre 2026 comptent 57 tests standards, 12 tests PostgreSQL/Testcontainers et 127 preuves externes vérifiées hors réseau. Le correctif fonctionnel `6fb69e2` du lot 8 de CAT-002 porte la baseline fusionnée à 219 tests standards et 89 tests PostgreSQL/Testcontainers : outre la preuve de redémarrage réelle du candidat initial, douze scénarios PostgreSQL couvrent `*`, `?` et `%` dans chacun des quatre champs de la clé d'autorité et prouvent leur traitement fermé en `UNASSIGNED`. Les quatre checks Windows et Linux de `6fb69e2`, puis ceux de l'alignement documentaire `a3b471f`, sont verts, avec PostgreSQL/Testcontainers réellement exécuté sous Linux ; la discussion P2 associée est résolue et la PR `#8` est fusionnée. L'absence actuelle d'intégration Docker Desktop dans WSL n'est jamais présentée comme un succès. Aucun endpoint public, worker CAT-002 ou appel fournisseur n'est ajouté par ce correctif. La baseline INT-001 du 4 septembre compte 303 tests standards et 98 tests PostgreSQL/Testcontainers/mTLS, tous verts, plus 32 tests Pester et la restauration synthétique chiffrée qualifiée. Les nombres historiques de CAT-001 restent 17 tests standards et 11 tests PostgreSQL/Testcontainers.

## Documentation

- Skills de développement validés et preuves du pilote : [`docs/skills/`](docs/skills/README.md)
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
- Contrat du receiver local J7 : [`docs/contracts/j7-import-receiver-v1.md`](docs/contracts/j7-import-receiver-v1.md)
- Modèle de menace du receiver J7 : [`docs/security/j7-import-receiver-threat-model.md`](docs/security/j7-import-receiver-threat-model.md)
- Qualification locale du receiver J7 : [`docs/runbooks/j7-import-receiver-local.md`](docs/runbooks/j7-import-receiver-local.md)
- Sauvegarde/restauration J7 : [`docs/runbooks/j7-import-backup-restore.md`](docs/runbooks/j7-import-backup-restore.md)
- Dossier de revue finale CAT-002 : [`docs/reviews/CAT-002-final-review.md`](docs/reviews/CAT-002-final-review.md)
- Dossier de revue du candidat MVP-001 : [`docs/reviews/MVP-001-final-review.md`](docs/reviews/MVP-001-final-review.md)

Les lanceurs `.cmd` des contrôles Windows appliquent une dérogation uniquement au processus PowerShell qu'ils ouvrent. Ils ne modifient pas la politique d'exécution globale du poste.
