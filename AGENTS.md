# Instructions du dépôt Betting Project

## Commandes de référence

- Build et tests standards Windows : `.\mvnw.cmd verify`
- Build et tests standards Linux/WSL2 : `./mvnw verify`
- Tests PostgreSQL réels : `.\mvnw.cmd -Pintegration verify` ou `./mvnw -Pintegration verify`
- Contrôle de secrets Windows : `.\scripts\check-no-secrets.cmd`
- Validation Windows complète : `.\scripts\verify-windows.cmd`
- PostgreSQL local : `docker compose --env-file .env up -d postgres`

## Contraintes d'architecture

- Le projet reste un monolithe modulaire ; ne pas créer de microservice ou de second dépôt dans ce répertoire.
- Les packages `domain` ne dépendent jamais de Spring, JDBC, HTTP ou d'un package `adapter`.
- Les adaptateurs implémentent des ports déclarés dans les packages `application`.
- Les modules métier de premier niveau ne doivent pas former de cycle.
- Les profils autorisés au jalon 0 sont `control-api`, `batch-worker` et `replay`. Le profil live est interdit.

## Données et migrations

- PostgreSQL est la source de vérité.
- Les migrations déjà partagées sous `src/main/resources/db/migration/` sont immuables ; ajouter une migration corrective.
- Les effets externes passent par une clé d'idempotence et l'outbox.
- Les tests de persistance utilisent PostgreSQL/Testcontainers, jamais H2.

## Sécurité

- Ne jamais lire, copier ou versionner le fichier local de clés API du benchmark.
- Aucun secret réel dans le dépôt, les fixtures, les logs, les rapports ou les arguments de commande.
- Ne jamais ajouter d'endpoint privé SofaScore ni de dépendance au laboratoire local.
- Les fixtures de replay doivent être expurgées et fonctionner sans réseau.

## Workflow

- Tout changement significatif se rattache à un Work Order dans `docs/work-orders/`.
- Ne pas modifier simultanément ce worktree depuis Eclipse et Codex.
- Ne pas fusionner dans la branche principale sans revue humaine.
- Le rapport de fin indique les fichiers modifiés, tests exécutés, limites et décisions ouvertes.
