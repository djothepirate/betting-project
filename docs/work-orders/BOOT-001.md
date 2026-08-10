# BOOT-001 - Initialiser le socle applicatif

- **Statut :** Accepté - clôturé
- **Date d'ouverture :** 2026-08-10
- **Date d'acceptation et de clôture :** 2026-08-11
- **Responsable de décision :** Porteur du Betting Project
- **Exécutant :** Codex, dans un dépôt isolé
- **ADR applicables :** ADR-001 à ADR-007

## Objectif

Créer un dépôt Java/Spring Boot reproductible qui matérialise les décisions d'architecture acceptées et fournit un premier chemin exécutable pour les profils `control-api`, `batch-worker` et `replay`.

## Périmètre autorisé

- Initialiser Git, Maven Wrapper 3.9.16 et Spring Boot 4.1.0 avec Java 25.
- Créer les frontières des huit modules métier initiaux.
- Ajouter les profils d'exécution du jalon 0.
- Ajouter PostgreSQL local avec Docker Compose et Flyway.
- Créer le schéma initial pour catalogue, mappings, snapshots, jobs, audits et outbox.
- Fournir un service transactionnel minimal de création de job et de message d'outbox.
- Fournir un mode replay hors ligne avec fixture expurgée.
- Ajouter des tests d'architecture, de profils, de replay, d'expurgation et d'intégration PostgreSQL.
- Versionner les ADR acceptés, le présent Work Order et les procédures locales.

## Exclusions

- Aucun appel aux trois API football ni consommation de quota.
- Aucun choix final de fournisseur par compétition.
- Aucun endpoint privé SofaScore et aucun lien avec le laboratoire local.
- Aucun déploiement VPS, staging ou production.
- Aucun mécanisme de publication Drive, Gmail ou Telegram.
- Aucun profil live, microservice, Redis, broker externe ou Kubernetes.
- Aucune copie du fichier local contenant les clés API.

## Critères d'acceptation

- [x] `mvnw.cmd -version` utilise Maven 3.9.16 et Java 25.0.4 LTS.
- [x] Le build Spring Boot 4.1.0 résout Spring Framework 7.0.8.
- [x] `mvnw.cmd verify` compile et exécute les tests standards.
- [x] Les tests ArchUnit interdisent les cycles de modules et les dépendances du domaine vers les adaptateurs ou Spring.
- [x] Les contextes `control-api`, `batch-worker` et `replay` démarrent séparément depuis le même artefact.
- [x] Le mode `replay` démarre sans base, réseau ou secret.
- [x] Docker Compose décrit PostgreSQL sans secret versionné.
- [x] Flyway crée le schéma sur une base PostgreSQL vide.
- [x] La création répétée d'un même job ne crée qu'un job et un message d'outbox.
- [x] Une même fixture rejouée deux fois produit le même hash et le même résultat.
- [x] La recherche automatisée ne trouve aucun secret réel ou endpoint privé SofaScore.
- [x] Les procédures Windows, WSL2, secrets et PostgreSQL sont documentées.
- [x] Le build complet est reproductible sous Windows 11 et Ubuntu 26.04 sous WSL2.

## Commandes de test

```powershell
.\mvnw.cmd -version
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration verify
.\scripts\check-no-secrets.cmd
```

```bash
./mvnw -version
./mvnw verify
./scripts/check-no-secrets.sh
```

## Définition de terminé

Le Work Order est terminé lorsque tous les critères d'acceptation sont vérifiés sous Windows 11 et Ubuntu/WSL2, que le dépôt ne contient ni secret ni modification hors périmètre et que le responsable de décision a prononcé son acceptation explicite.

## Résultats d'exécution

### Vérifications réussies sous Windows 11

- `mvnw.cmd -version` : Apache Maven 3.9.16 et Java 25.0.4 LTS, Oracle, architecture amd64.
- `mvnw.cmd verify` : build réussi, 11 tests standards exécutés, aucun échec ni test ignoré.
- Arbre de dépendances : Spring Boot 4.1.0 et Spring Framework 7.0.8 confirmés.
- `mvnw.cmd -Pintegration verify` : build réussi avec Testcontainers 2.0.5, Docker Desktop 29.6.2 et PostgreSQL 17.10 ; 11 tests standards et 4 tests d'intégration réussis.
- Flyway : migration `V001__bootstrap_schema.sql` validée puis appliquée sur un schéma PostgreSQL vide.
- Docker Compose : `compose.yml` accepté par `docker compose config --quiet` avec des valeurs locales factices non persistées.
- Artefact exécutable : le JAR démarre avec le profil `replay`, sans base ni secret, puis se termine normalement.
- Recherche de secrets : aucun secret à forte confiance ni endpoint privé SofaScore détecté.

Le premier passage PostgreSQL a détecté que le pilote ne déduisait pas le type SQL de `java.time.Instant`. La persistance convertit désormais explicitement les horodatages en UTC avec `OffsetDateTime`; la relance complète est réussie et confirme également le rollback transactionnel attendu.

### Rejeu par le porteur du projet — 11 août 2026

Le porteur du projet a exécuté `.\scripts\verify-windows.cmd` depuis son propre terminal Windows. Le rejeu transmis confirme :

- Apache Maven 3.9.16 avec Java 25.0.4 LTS ;
- le build standard réussi avec 11 tests sur 11 ;
- les trois profils `batch-worker`, `control-api` et `replay` démarrés par les tests ;
- le contrôle de secrets réussi ;
- Docker Desktop 29.6.2 détecté par Testcontainers 2.0.5 ;
- PostgreSQL 17.10 démarré dans un conteneur éphémère ;
- la migration Flyway `V001` appliquée sur un schéma vide ;
- les 4 tests d'intégration PostgreSQL réussis sans échec ni test ignoré ;
- les deux builds terminés avec `BUILD SUCCESS`.

Ce rejeu indépendant confirme la reproductibilité de BOOT-001 sous Windows.

### Validation WSL2 réussie — 11 août 2026

Après installation et configuration de Java 25 dans Ubuntu 26.04 sous WSL2, la procédure `./scripts/verify-wsl.sh` a été exécutée intégralement et s'est terminée avec un code de sortie nul. Elle confirme :

- Apache Maven 3.9.16 avec OpenJDK 25.0.3, distribution Ubuntu, architecture amd64 ;
- le build standard réussi avec 11 tests sur 11 ;
- l'exécution correcte du contrôle POSIX de secrets après correction de sa compatibilité avec `/bin/sh` ;
- Docker Engine 29.6.2 accessible depuis WSL2 par le socket Unix et détecté par Testcontainers 2.0.5 ;
- PostgreSQL 17.10 démarré dans un conteneur éphémère ;
- la migration Flyway `V001` appliquée sur un schéma vide ;
- les 4 tests d'intégration PostgreSQL réussis, sans échec ni test ignoré ;
- les builds standard et d'intégration terminés avec `BUILD SUCCESS`.

L'écart de correctif entre Java 25.0.4 LTS sous Windows et OpenJDK 25.0.3 sous Ubuntu est accepté : les deux environnements utilisent Java 25 et satisfont la règle Maven Enforcer `[25,26)`. La réserve WSL2 est entièrement levée.

## Décision de clôture

Le responsable de décision a accepté explicitement BOOT-001 le 11 août 2026 et a autorisé la création du commit initial sur la branche `codex/boot-001`. Tous les critères d'acceptation sont satisfaits ; aucune réserve bloquante ne subsiste.
