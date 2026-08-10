# Betting Project

Socle du Betting Project principal, initialisé par le Work Order BOOT-001.

## Versions du socle

- Java 25 : Oracle JDK 25.0.4 LTS sous Windows et OpenJDK 25.0.3 sous Ubuntu/WSL2 ; règle de build `[25,26)`
- Apache Maven 3.9.16, imposé par le wrapper
- Spring Boot 4.1.0
- Spring Framework 7.0.8, géré par Spring Boot
- PostgreSQL 17 pour le développement local et les tests d'intégration

## Démarrage rapide sous Windows

1. Copier `.env.example` vers `.env` et remplacer le mot de passe factice.
2. Démarrer PostgreSQL : `docker compose --env-file .env up -d postgres`.
3. Vérifier le build : `.\mvnw.cmd verify`.
4. Démarrer l'API de contrôle : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"`.
5. Démarrer le worker dans un autre terminal : `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=batch-worker"`.

Sans profil actif, l'application démarre en mode `replay`, hors ligne et sans base de données.

## Vérifications

- Tests unitaires, profils et architecture : `.\mvnw.cmd verify`
- Tests PostgreSQL réels avec Testcontainers : `.\mvnw.cmd -Pintegration verify`
- Recherche locale de secrets : `.\scripts\check-no-secrets.cmd`
- Validation Windows complète : `.\scripts\verify-windows.cmd`

Les tests d'intégration nécessitent un moteur Docker accessible. Les fixtures de replay ne contactent aucun fournisseur et ne consomment aucun quota.

## Documentation

- Décisions : `docs/adr/`
- Work Orders : `docs/work-orders/`
- Procédures : `docs/runbooks/`
- Frontières modulaires : `docs/architecture/module-boundaries.md`

Les lanceurs `.cmd` des contrôles Windows appliquent une dérogation uniquement au processus PowerShell qu'ils ouvrent. Ils ne modifient pas la politique d'exécution globale du poste.
