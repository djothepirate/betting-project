# Validation WSL2

La validation WSL2 vérifie les scripts POSIX, les fins de ligne, les permissions et la compatibilité du build Linux. Le build standard ne remplace ni les tests Windows ni les tests PostgreSQL.

Depuis le dépôt accessible à Ubuntu/WSL2 :

```bash
java -version
chmod +x mvnw scripts/*.sh
./mvnw -version
./mvnw verify
./scripts/check-no-secrets.sh
./mvnw -Pintegration verify
```

Résultats attendus :

- Maven 3.9.16 ;
- Java 25 ;
- tous les tests standards réussis sur le candidat effectivement relu ;
- tous les tests PostgreSQL/Testcontainers réellement exécutés, sans omission ;
- migrations Flyway `V001` à `V011` appliquées sans modification pour MVP-001 ;
- aucune écriture d'un chemin Windows dans les fichiers générés ;
- aucun secret détecté.

Les tests Testcontainers nécessitent un moteur Docker accessible depuis WSL2. L'échec de `./mvnw -Pintegration verify` signifie que la validation complète n'est pas acquise. Le script de commodité peut être lancé avec :

```bash
./scripts/verify-wsl.sh
```

Si ce script signale que Docker est indisponible et omet l'intégration, son résultat est seulement une validation standard. La CI Linux, elle, exécute explicitement le profil `integration` et doit échouer si PostgreSQL/Testcontainers est indisponible.

## Validation de référence du 11 août 2026

La procédure complète a été validée sous Ubuntu 26.04 dans WSL2 avec :

- Apache Maven 3.9.16 ;
- OpenJDK 25.0.3 Ubuntu, compatible avec la règle Maven Enforcer `[25,26)` ;
- 17 tests standards réussis ;
- contrôle POSIX de secrets réussi ;
- Docker Engine 29.6.2 accessible par `unix:///var/run/docker.sock` ;
- Testcontainers 2.0.5 et PostgreSQL 17.10 ;
- migrations Flyway `V001` et `V002` appliquées sur un schéma vide ;
- 11 tests d'intégration PostgreSQL réussis.

Les builds standard et d'intégration se sont tous deux terminés avec `BUILD SUCCESS`. La réserve liée à l'absence initiale de Java dans Ubuntu est levée.

## Porte Linux historique du candidat CAT-002

La référence historique ci-dessus ne vaut pas validation du candidat CAT-002. Au 2 septembre 2026, la distribution WSL locale dispose de Java et Maven, mais Docker Desktop n'y expose pas `/var/run/docker.sock`. `verify-wsl.sh` peut donc vérifier le build standard et le scan de secrets, puis annoncer explicitement l'omission de Testcontainers ; ce résultat doit être enregistré comme `PARTIAL - NO_TESTCONTAINERS`.

Le porteur a retenu la CI Linux de la Pull Request `#8` comme porte complète. Le premier passage était vert sur le commit initial, mais le correctif P2 impose une nouvelle exécution directe de :

```bash
./mvnw verify
./scripts/check-no-secrets.sh
./mvnw -Pintegration verify
```

La dernière commande devra réellement exécuter les 89 tests PostgreSQL/Testcontainers du candidat correctif. CAT-002 pourra redevenir `READY_FOR_GIT - LINUX_CI_PENDING` après la nouvelle revue humaine locale, mais ne pourra pas être déclaré globalement accepté ou fusionnable tant que les CI du nouveau commit ne sont pas vertes.

Cette section conserve la décision de septembre : CAT-002 a depuis été fusionné par la PR #8,
avec ses checks verts, comme l'indique l'[état du projet](../project-status.md). Ce n'est plus
une porte ouverte du candidat courant.

## MVP-001 — qualification Linux du candidat final

La CI GitHub constitue la preuve Linux complète retenue. Le run
[35411773181](https://github.com/djothepirate/betting-project/actions/runs/35411773181), sur
`6843ff833c1c74de26f6bfc6c7b71a11e3d64b5e`, réussit avec 615 tests standards et 239 tests
PostgreSQL/Testcontainers sans échec, erreur ou omission. C'est la preuve publiée du lot 5,
pas celle d'un futur commit du lot 6. Les résultats du candidat final sont reliés dans le
[rapport de revue](../reviews/MVP-001-final-review.md) et dans les checks de sa PR.

Le passage du dépôt de privé à public par le porteur a débloqué les exécutions ; les runs
antérieurs sans étape démarrée sont des échecs historiques, jamais des tests réussis.
Cette procédure ne modifie aucun réglage de visibilité ou de facturation. Le lot 6 n'exécute
aucun test WSL ni diagnostic de sa socket Docker : aucune nouvelle disponibilité locale de
Testcontainers sous WSL n'est revendiquée. Sans exécution, aucune qualification Linux locale.
