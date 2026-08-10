# Validation WSL2

La validation WSL2 vérifie les scripts POSIX, les fins de ligne, les permissions et la compatibilité du build Linux. Elle ne remplace pas les tests Windows ni les tests PostgreSQL.

Depuis le dépôt accessible à Ubuntu/WSL2 :

```bash
java -version
chmod +x mvnw scripts/*.sh
./mvnw -version
./mvnw verify
./scripts/check-no-secrets.sh
```

Résultats attendus :

- Maven 3.9.16 ;
- Java 25 ;
- tests standards réussis ;
- aucune écriture d'un chemin Windows dans les fichiers générés ;
- aucun secret détecté.

Les tests Testcontainers sont lancés avec `./mvnw -Pintegration verify` uniquement lorsqu'un moteur Docker est accessible depuis WSL2. La procédure complète peut être rejouée avec :

```bash
./scripts/verify-wsl.sh
```

## Validation de référence du 11 août 2026

La procédure complète a été validée sous Ubuntu 26.04 dans WSL2 avec :

- Apache Maven 3.9.16 ;
- OpenJDK 25.0.3 Ubuntu, compatible avec la règle Maven Enforcer `[25,26)` ;
- 11 tests standards réussis ;
- contrôle POSIX de secrets réussi ;
- Docker Engine 29.6.2 accessible par `unix:///var/run/docker.sock` ;
- Testcontainers 2.0.5 et PostgreSQL 17.10 ;
- migration Flyway `V001` appliquée sur un schéma vide ;
- 4 tests d'intégration PostgreSQL réussis.

Les builds standard et d'intégration se sont tous deux terminés avec `BUILD SUCCESS`. La réserve liée à l'absence initiale de Java dans Ubuntu est levée.
