# Développement local

## Prérequis

- Windows 11 avec Java 25.0.4 LTS ;
- Maven Wrapper du dépôt, qui télécharge et vérifie Maven 3.9.16 ;
- un moteur Docker accessible à Testcontainers pour l'intégration PostgreSQL ;
- Docker Compose uniquement pour conserver une base PostgreSQL de développement ;
- WSL2 pour la validation Linux.

## Configuration PostgreSQL

1. Copier `.env.example` vers `.env`.
2. Remplacer `REPLACE_WITH_LOCAL_ONLY_PASSWORD` par un mot de passe local distinct.
3. Ne placer aucune clé API fournisseur dans `.env` tant qu'un Work Order de connecteur ne l'autorise pas explicitement.
4. Démarrer PostgreSQL :

```powershell
docker compose --env-file .env up -d postgres
docker compose --env-file .env ps
```

## Profils

Mode replay hors réseau, sans base pour les fixtures actuelles :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=replay"
```

API de contrôle avec PostgreSQL :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"
```

Worker avec PostgreSQL :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=batch-worker"
```

## Migrations

Le dépôt fusionné contient :

- `V001` : socle de persistance, jobs, outbox et snapshots ;
- `V002` : catalogue canonique, références fournisseurs, mappings, observations et anomalies.

Ces migrations partagées sont immuables. Toute correction ultérieure utilise une migration additive à partir de `V003`.

## Contrôles locaux

Validation standard, sans dépendance Docker :

```powershell
.\mvnw.cmd verify
```

Validation PostgreSQL explicite :

```powershell
.\mvnw.cmd -Pintegration verify
```

Contrôle de sécurité et validation Windows complète :

```powershell
.\scripts\check-no-secrets.cmd
.\scripts\verify-windows.cmd
```

La commande `verify` seule n'est pas une validation complète. `verify-windows.cmd` exécute systématiquement les tests PostgreSQL/Testcontainers et doit échouer si le moteur Docker n'est pas joignable. Testcontainers détermine lui-même l'accessibilité du moteur ; la présence de la CLI `docker` ne décide pas de l'exécution.

La référence CAT-001 comprend 17 tests standards et 11 tests PostgreSQL. Les contrôles n'appellent aucun fournisseur et ne consomment aucun quota.

Les lanceurs `.cmd` fonctionnent même lorsque l'exécution directe des scripts PowerShell est désactivée. La dérogation reste limitée au processus de contrôle et ne change pas la politique globale de Windows. Le contrôle de secrets inspecte le working tree, les blobs de l'index et, lorsqu'une base est fournie ou que `origin/main` est disponible, les blobs `HEAD` ainsi que chaque version modifiée dans `base..HEAD` ; il n'affiche jamais une valeur détectée. Une base explicite introuvable fait échouer le contrôle au lieu de réduire silencieusement sa couverture.

## Arrêt

```powershell
docker compose --env-file .env down
```

Ne pas ajouter `-v` à la commande d'arrêt sauf si la suppression explicite des données locales est souhaitée.
