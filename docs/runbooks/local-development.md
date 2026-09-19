# Développement local

## Prérequis

- Windows 11 avec Java 25.0.4 LTS ;
- Maven Wrapper du dépôt, qui télécharge et vérifie Maven 3.9.16 ;
- un moteur Docker accessible à Testcontainers pour l'intégration PostgreSQL ;
- Docker Compose uniquement pour conserver une base PostgreSQL de développement ;
- CI Linux avec Docker pour la qualification Linux complète ; WSL2 est un complément local.

## Configuration PostgreSQL

1. Copier `.env.example` vers `.env`.
2. Remplacer `REPLACE_WITH_LOCAL_ONLY_PASSWORD` par un mot de passe local distinct.
3. Ne placer aucune clé API fournisseur dans `.env` tant qu'un Work Order de connecteur ne l'autorise pas explicitement.
4. Démarrer PostgreSQL :

```powershell
docker compose --env-file .env up -d postgres
docker compose --env-file .env ps
```

Le fichier `.env` est lu par Compose, pas implicitement par Spring. Fournir aussi les variables
PostgreSQL au processus applicatif via son environnement protégé (notamment `BETTING_DB_PORT=5433`
pour le mapping Compose et `BETTING_DB_PASSWORD`), jamais dans les arguments ou le dépôt.

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

MVP-001 ne rend pas ce démarrage actif auprès d'un fournisseur : registre classpath vide,
clients désactivés et `betting.collection.worker.enabled` absent/false. La planification datée
reste un cas d'usage interne explicite. Le `control-api` écoute sur `127.0.0.1`, sans authentification
hors qualification J7 séparée ; ne pas le proxifier ni l'exposer. Les lectures de
`/internal/collection` n'exigent pas d'identité opérateur et ne créent aucun engagement.

Procédures détaillées : [calendrier fournisseur](provider-calendar-local.md),
[jobs et reprise](collection-jobs-local.md), [contrôle et sélection](collection-control-local.md).
Une future activation exige une décision dédiée, des preuves exactes, les mappings et une fenêtre
budgétaire initialisée. Une clé présente dans l'environnement ne suffit pas.

## Migrations

Le train de départ de MVP-001 contient V001 à V008. Le candidat MVP-001 ajoute V009 à V011,
sans modifier les migrations antérieures :

- `V001` : socle de persistance, jobs, outbox et snapshots ;
- `V002` : catalogue canonique, références fournisseurs, mappings, observations et anomalies ;
- `V003` : attributs calendrier v3, tampon d'autorité et journal d'application ;
- `V004` : versions et décisions de mapping, reçus d'idempotence et cycle de vie des anomalies ;
- `V005` : demandes durables de rejeu, tentatives et corrélations ;
- `V006` : receiver J7 optionnel, inbox et audit ;
- `V007` et `V008` : intégrité et temporalité des preuves de purge J7 ;
- `V009` : budget fournisseur, intentions, compteurs et incidents ;
- `V010` : collectes, pages brutes auditées et dérivations calendrier ;
- `V011` : jobs gérés de collecte, baux/fencing, entrées et journaux d'exécution.

V001 à V011 sont immuables. Le lot 6 n'ajoute aucune V012. Les tests `V009MigrationIT`,
`V010MigrationIT` et `V011MigrationIT` vérifient l'installation neuve et les mises à niveau depuis
des schémas antérieurs peuplés ; ils ne touchent pas la base de développement.

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

La référence courante est le [rapport du lot 6](../work-orders/MVP-001-lot6-execution.md), avec
les résultats réellement exécutés et le [dossier de revue](../reviews/MVP-001-final-review.md).
Les nombres antérieurs CAT-001 (17/11), CAT-002 (219/89) et INT-001 (303/98) restent historiques.
Les tests de collecte utilisent des fournisseurs synthétiques ou loopback ; aucun quota réel
n'est consommé. Une intégration réussie exécute aussi les tests standards : ne pas lancer une
seconde suite standard uniquement pour recopier son total.

La CI Windows et Linux du lot 5, `6843ff8`, est verte (run `35411773181`), avec PostgreSQL réel
sous Linux. La tête de la PR finale doit avoir sa propre CI verte avant fusion. Une validation
WSL locale qui omet Testcontainers faute de moteur Docker reste seulement partielle et ne ferme
pas cette porte. Les scripts étant inchangés au lot 6, leurs non-régressions et
`verify-windows.cmd` ne sont pas relancés localement après la même suite complète.

Les lanceurs `.cmd` fonctionnent même lorsque l'exécution directe des scripts PowerShell est désactivée. La dérogation reste limitée au processus de contrôle et ne change pas la politique globale de Windows. Le contrôle de secrets inspecte les fichiers suivis ou non suivis du working tree, les sorties locales ignorées `*.log` et `reports/**`, les blobs de l'index et, lorsqu'une base est fournie ou que `origin/main` est disponible, les blobs `HEAD` ainsi que chaque version modifiée dans `base..HEAD` ; il n'affiche jamais une valeur détectée. La sélection positive des sorties ignorées n'élargit pas le scan aux autres fichiers ignorés, notamment ceux qui servent à injecter des clés. Une base explicite introuvable fait échouer le contrôle au lieu de réduire silencieusement sa couverture.

## Arrêt

```powershell
docker compose --env-file .env down
```

Ne pas ajouter `-v` à la commande d'arrêt sauf si la suppression explicite des données locales est souhaitée.
