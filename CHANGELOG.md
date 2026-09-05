# Historique des évolutions

Ce fichier recense les lots fonctionnels du dépôt. Il ne remplace ni les critères d'acceptation détaillés des Work Orders, ni l'historique Git.

## 2026-09-05 — CI-004 (trains feature/release versionnés, réalisation locale)

- convention exécutable pour les trains `Vx.y.z`, `Vx.y.z-RCnn` et
  `Vx.y.z-RCnn-SNAPSHOT`, avec `nn` borné à `01..99` et mapping Maven/tag `rc.N` minuscule ;
- branches de Work Order `feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>` issues du HEAD documenté de
  leur feature d'intégration, avec `<WORK-ORDER>=<TYPE>-<NNN>` (`NNN=001..999`) et document
  versionné, puis Pull Requests GitHub exclusivement vers cette feature de même train ;
- finalisation GitHub `feature/<TRAIN>` vers `main` par merge commit, puis avance fast-forward de la
  feature et promotion GitLab `feature/<TRAIN>` vers la release protégée du même train ;
- snapshots durables limités aux pushes des features d'intégration exactes ; contrôles renforcés
  pour la version Maven des PR et Work Orders, le graphe Git des PR/MR, les SHA canoniques de
  release et l'exception de bootstrap CI-004, bornée aussi à `0.1.0-SNAPSHOT` ; pipelines de branche
  GitLab limités à `main`, aux features d'intégration et aux releases exactes, avec voies MR/tag
  strictement exclusives et refus des refs indéterminées ; validation fail-closed des tags non
  protégés ou hors grammaire SemVer, indépendamment de la sélection du job de packaging ; protection
  GitLab distincte des tags `v*` ;
- aucun train distant `feature/V*` ou `release/V*` créé avant fusion du bootstrap, aucune branche
  historique renommée ou supprimée, et statut `production.approved=false` /
  `vps.deployable=false` conservé jusqu'à OPS-001.

Voir [CI-004](docs/work-orders/CI-004.md), [ADR-005](docs/adr/ADR-005-workflow-eclipse-codex-git-work-orders.md),
[ADR-008](docs/adr/ADR-008-integration-continue-github-gitlab-versioning-qualite-securite.md) et le
[runbook CI/CD](docs/runbooks/ci-cd-github-gitlab.md).

## 2026-09-05 — SKL-001 à SKL-003 (skills validés et livraison autorisée)

- cinq skills BP découverts au niveau du dépôt, avec métadonnées, inventaire, protocole, réponses et revues du pilote ;
- dossier d'extension Lab et ses cinq sources évaluées conservés pour traçabilité ; le dépôt Lab maintient son installateur personnel ;
- validation propriétaire des deux projets et autorisation explicite de livraison sur main consignées dans [SKL-003](docs/work-orders/SKL-003.md) ;
- octets des preuves historiques préservés par des attributs Git ciblés ; instructions et empreintes des skills validés inchangées ;
- [index des skills et usages](docs/skills/README.md), liens portables et décisions actuelles séparées des rapports historiques.

La base de cette livraison inclut INT-001, fusionné par la [PR #11](https://github.com/djothepirate/betting-project/pull/11) au commit `7f1f3aa`. Les sections INT-001 suivantes conservent l'état à leur date de rédaction ; elles ne décrivent plus une PR actuellement ouverte.

## 2026-09-05 — INT-001 (publication et PR autorisées)

- publication de `codex/int-001-closeout` et ouverture de la
  [PR #11](https://github.com/djothepirate/betting-project/pull/11) vers `main`, explicitement
  autorisées par le propriétaire après la clôture locale ;
- statut courant `CLOSED_LOCAL - PUBLISHED - PR_OPEN` ; aucune fusion réalisée ou autorisée ;
- alignement documentaire du Work Order et des synthèses, sans changement fonctionnel ni nouvelle
  exécution des suites locales ; résultats distants à consulter sur le HEAD courant de la PR.

## 2026-09-05 — INT-001 (clôture locale)

- état `CLOSED_LOCAL - OWNER_VALIDATED - NOT_PUBLISHED` à la demande du propriétaire, après
  vérification des critères de terminé et de la validation acquise le 4 septembre ;
- candidat fonctionnel conservé à `de06153` sur `codex/int-001-j7-receiver`, clôture documentaire
  isolée sur `codex/int-001-closeout` ; aucun changement applicatif, script ou migration ;
- rapport de préparation et ses empreintes préservés, résultats 303/98/32 réutilisés comme preuves
  historiques ; aucune suite Maven, PostgreSQL/mTLS ou Pester relancée pour la documentation ;
- contrôle distant : `main` à `5a8161e`, branche d'implémentation absente de GitHub et aucune PR
  INT-001 trouvée ; publication et fusion restent distinctes de la clôture locale ;
- [rapport de clôture](docs/reviews/INT-001-closeout-20260905.md), Work Order et synthèses synchronisés.

La section du 4 septembre ci-dessous conserve l'état et les décisions à leur date initiale.

## 2026-09-04 — INT-001 (validé localement par le propriétaire)

### Périmètre localement qualifié

- receiver J7 entrant strict, disponible uniquement sous `control-api` et désactivé par défaut ;
- chemin brut exactement égal à `/api/imports/sofascore/j7-canonical-events`, sans préfixe de contexte/servlet, avec refus des slashs finaux, segments supplémentaires et alias matrix ou percent-encodés ;
- contrat byte-exact de 1 à 5 Mio, validation JSON Schema Draft 2020-12, hashes fichier/data/sources et ACK v1.0 borné, sérialisé par un codec privé déterministe indépendant du Jackson MVC global ;
- idempotence PostgreSQL `201/IMPORTED`, `200/DUPLICATE`, `409` divergent, inbox/audit/outbox transactionnels et migrations additives `V006` à `V008`, dont le refus à l'upgrade des anciennes preuves de purge datées dans le futur ; prochain slot `V009` ;
- rétention configurable de 1 à 3 650 jours avec défaut/baseline à 30 jours, purge applicative bornée avec tombstone, sans route, scheduler ni surface runtime ;
- activation fail-closed sur `127.0.0.1:8444`, HTTPS, client-auth `NEED`, compression serveur désactivée, stores locaux absolus non UNC, refus des alternatives bundle/PEM/SNI par une garde web prioritaire avant résolution TLS, certificat feuille en cours de validité avec EKU `clientAuth` et allowlist d'empreintes ; toutes les routes et le management partagent ce connecteur unique ;
- bind PostgreSQL Compose et URL JDBC du receiver limités à `127.0.0.1:5433` : la garde web refuse avant création de la base/Flyway les propriétés statiques DataSource/JNDI/type, Hikari de localisation et Flyway dédiées, puis la garde bean inspecte les `JdbcConnectionDetails` et le `HikariDataSource` effectifs ;
- sauvegarde PostgreSQL 17 complète directement envoyée vers `age --passphrase`, sans dump clair, et restauration directe vers une base standard fraîche, isolée et vide, avec manifeste `requiredMigration=V008`, contrôles de catalogues et preuves séparées des quatre tables J7 plus `outbox_message` ; l'origine standard de la base et l'absence de lecteur réseau mappé restent des frontières opérateur ;
- rôle propriétaire PostgreSQL local conservé comme frontière de confiance de la qualification ; séparation owner de migration/rôle runtime à privilèges minimaux exigée avant toute production.

### Qualification

- branche réconciliée avec le `main` fusionné de CAT-002 sans rebase ni squash ;
- 303 tests standards et 98 tests PostgreSQL/Testcontainers/mTLS réussis sous Java 25, validation Windows complète verte, contrôle de secrets et configuration Compose valides ;
- 32 tests Pester réussis pour les scripts de sauvegarde/restauration ;
- corpus synthétique V008 sauvegardé au format PostgreSQL custom directement sous `age --passphrase`, sans dump clair ;
- la tentative Q1 s'est arrêtée sur un alias SQL réservé, avant déchiffrement, toute saisie propriétaire et toute mutation de la cible, puis un correctif fail-closed a été versionné ;
- cible Q2 neuve et isolée restaurée sous PostgreSQL 17, avec égalité des comptes et empreintes des cinq familles de preuves ;
- source et cible arrêtées, aucun listener 5433/5434/8444 ni processus natif résiduel, zéro appel fournisseur, Local Lab ou receiver réel/distant.

### Sécurité et statut

Le receiver ne dépend d'aucun code ou service du SofaScore Local Lab et n'ajoute aucun client sortant, sender, poller, scheduler, retry ou consommateur d'enrichissement. Aucune donnée réelle, clé ou certificat n'est autorisé dans Git. Le propriétaire a validé la readiness locale d'INT-001 le `2026-09-04T14:13:23.3192773Z`, soit `2026-09-04T16:13:23.3192773+02:00` en Europe/Paris. Le lot est `OWNER_VALIDATED - LOCALLY_QUALIFIED - NOT_PUBLISHED` : push, Pull Request, fusion, livraison réelle, exposition LAN/VPS et production restent soumis à des décisions séparées.

## 2026-09-02 — CAT-002

### Fusion et clôture

- Pull Request `#8` fusionnée dans `main` au commit `85dc943` ;
- correctif fonctionnel `6fb69e2` et alignement documentaire `a3b471f` présents dans la base INT-001 ;
- 36 critères sur 36 satisfaits, seconde revue humaine acquise, discussion P2 résolue ;
- quatre checks Windows/Linux verts sur chacun des deux commits, PostgreSQL/Testcontainers compris ;
- état final `ACCEPTED - MERGED - CLOSED`.

## 2026-09-01 — ENR-001

### Finalisé dans le lot

- reconstruction du diff historique depuis une sauvegarde restaurable et revalidée, sans modifier le checkout source ;
- conservation du collecteur comme outil manuel de benchmark, distinct des futurs adaptateurs de production ;
- index expurgé de 127 appels et vérificateur du corpus strictement hors réseau et en lecture seule ;
- sept fixtures synthétiques représentatives des familles réellement appelées, avec manifeste de provenance et tests ;
- garde de quota prudente, verrou d'exécution exclusif et écritures atomiques des preuves et replays ;
- conservation séparée de l'ambiguïté historique `Hirnyk` et du mapping explicite de `highlightly:TEAM:5522923` ;
- baseline MVP acceptée par capacité, compétition, saison, phase et type de donnée.

### Validation

- 57 tests standards réussis ;
- 12 tests PostgreSQL/Testcontainers réussis ;
- 127 preuves sur 127 vérifiées, empreintes conformes et replays `PASS` ;
- manifeste valide de 12 rencontres principales, 4 réserves et 1 contrôle transversal ;
- validation Windows complète et contrôle de secrets réussis, sans appel fournisseur.

### Gouvernance

Le lot a été commité sous `743aff7`, publié sur `codex/enr-001`, revu puis fusionné dans `main` par la Pull Request `#3`. Les CI Windows et Linux sont vertes et le commit de fusion est `6913cea`. ENR-001 est accepté, fusionné et clôturé.

## 2026-09-01 — DEVX-001

### Ajouté

- correction du Maven Wrapper Windows avec Maven 3.9.16 et vérification de la distribution par SHA-256 ;
- normalisation LF du wrapper POSIX pour les checkouts Windows utilisés depuis WSL2 ;
- propagation fiable des codes de sortie dans la validation Windows ;
- exécution obligatoire des tests PostgreSQL/Testcontainers dans la validation complète ;
- contrôles de non-régression des scripts et contrôle de secrets étendu au working tree, à l'index Git, aux blobs `HEAD` et aux commits intermédiaires du diff ;
- CI Windows/Linux sans étape, configuration, clé ni appel fournisseur ;
- mémoire durable du projet : cadrage, état courant, feuille de route et procédures actualisées.

### Validation et gouvernance

Le lot a été revu, publié et fusionné dans `main` par la Pull Request `#2`, après correction du défaut P2 signalé en revue et validation des CI Windows et Linux. DEVX-001 est accepté, fusionné et clôturé.

## 2026-08-11 — CAT-001

### Ajouté

- catalogue canonique des compétitions, saisons, équipes et rencontres ;
- mappings fournisseurs, anomalies structurées et provenance des observations ;
- normalisation idempotente et replay de snapshots de calendrier ;
- migration additive Flyway `V002` ;
- tests PostgreSQL/Testcontainers du catalogue et des contraintes d'architecture.

### Validation

- 17 tests standards ;
- 11 tests PostgreSQL/Testcontainers ;
- validations Windows 11 et Ubuntu/WSL2 ;
- lot fusionné dans `main` puis clôturé après autorisation humaine.

## 2026-08-11 — BOOT-001

### Ajouté

- socle Java 25, Spring Boot 4.1 et Maven Wrapper 3.9.16 ;
- monolithe modulaire et profils `control-api`, `batch-worker` et `replay` ;
- PostgreSQL, Flyway `V001`, jobs persistants et outbox minimaux ;
- replay hors réseau, règles d'architecture, ADR, runbooks et contrôles de secrets initiaux.

### Validation

BOOT-001 a été accepté, fusionné dans `main` et clôturé avant CAT-001.

## Corpus de benchmark hors de Git

INV-01, CAL-01 et ENR-001 ont produit des mesures et payloads complets conservés hors Git. ENR-001 versionne seulement les rapports, l'index expurgé, les empreintes, les fixtures synthétiques, le collecteur contrôlé et le vérificateur ; il ne constitue pas le connecteur de production du futur pipeline.
