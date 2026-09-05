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
- Un nouveau Work Order part du HEAD exact de sa branche d'intégration
  `feature/Vx.y.z`, `feature/Vx.y.z-RCnn` ou `feature/Vx.y.z-RCnn-SNAPSHOT`, avec `nn`
  compris entre `01` et `99`. Ce SHA de départ est consigné dans le Work Order.
- Sa branche est exactement
  `feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>` ; l'identifiant du Work Order reste en
  majuscules sous la forme `<TYPE>-<NNN>`, avec `TYPE` en lettres et `NNN` compris entre `001`
  et `999`, sans slug supplémentaire. Le document `docs/work-orders/<WORK-ORDER>.md` doit exister.
- Une Pull Request de Work Order cible la branche `feature/<TRAIN>` de même version. La Pull
  Request finale de cette branche feature cible `main`. Les fusions GitHub utilisent un merge
  commit ; squash et rebase sont interdits. La CI vérifie la version Maven du train et exige la
  version stable finale avant une Pull Request `feature/Vx.y.z` vers `main`.
- Après la fusion finale, `feature/<TRAIN>` avance en fast-forward normal sur le merge commit de
  `main`. `main` et cette branche feature sont ensuite synchronisées vers GitLab avant la Merge
  Request fast-forward `feature/<TRAIN>` vers la branche protégée GitLab `release/<TRAIN>`.
- Les branches `release/V*` existent uniquement sur GitLab et sont les seules branches protégées.
  Le motif de tags `v*` est protégé séparément sur GitLab ; une protection de tag n'est pas une
  protection de branche. Les tags restent `vX.Y.Z` ou `vX.Y.Z-rc.N`, avec un `v` et un suffixe
  `rc` minuscules.
- Un snapshot durable provient uniquement d'un push de la branche feature d'intégration exacte,
  jamais d'une branche de Work Order, d'une Pull Request ou de `main`.
- L'exception historique `codex/ci-004-version-branch-workflow` vers `main` ne vaut que pour le
  bootstrap CI-004 sur la base `3fb224e9724698324a56b47fe5d943ecd366f197`. Le SHA de tête doit
  descendre de cette base et leur merge-base doit être exactement cette base.
- Les anciennes branches `codex/*`, `human/*` et `hotfix/*` restent des références historiques en
  lecture seule ; ne pas les renommer, les réutiliser ou les supprimer implicitement.
- Ne pas fusionner dans `main`, une branche feature ou une release sans revue humaine.
- Le rapport de fin indique les fichiers modifiés, tests exécutés, limites et décisions ouvertes.
