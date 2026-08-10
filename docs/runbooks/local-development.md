# Développement local

## Prérequis

- Windows 11 avec Java 25.0.4 LTS ;
- Maven uniquement pour le premier amorçage du wrapper ; ensuite utiliser Maven Wrapper 3.9.16 ;
- Docker avec Compose pour PostgreSQL ;
- WSL2 pour la validation Linux.

## Configuration PostgreSQL

1. Copier `.env.example` vers `.env`.
2. Remplacer `REPLACE_WITH_LOCAL_ONLY_PASSWORD` par un mot de passe local distinct.
3. Ne placer aucune clé API football dans `.env` pendant BOOT-001.
4. Démarrer PostgreSQL :

```powershell
docker compose --env-file .env up -d postgres
docker compose --env-file .env ps
```

## Profils

Mode replay hors ligne, sans base :

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

## Contrôles locaux

```powershell
.\scripts\check-no-secrets.cmd
.\scripts\verify-windows.cmd
```

Les lanceurs `.cmd` fonctionnent même lorsque l'exécution directe des scripts PowerShell est désactivée. La dérogation reste limitée au processus de contrôle et ne change pas la politique globale de Windows.

## Arrêt

```powershell
docker compose --env-file .env down
```

Ne pas ajouter `-v` à la commande d'arrêt sauf si la suppression explicite des données locales est souhaitée.
