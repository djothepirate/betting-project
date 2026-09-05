# INT-001 - Rapport de préparation locale du receiver J7

## Verdict borné

```text
WORK_ORDER=INT-001
ORIGINAL_BASE_COMMIT=85dc943601a7d33d37ca69a0c913bf32f1162811
MAIN_RECONCILIATION_COMMIT=71c79c80764b03d37e21ada4c3080983b0b41f9d
RECONCILED_MAIN_COMMIT=5a8161e0070632f0b0dbbff760194e57ab4e5b1f
IMPLEMENTATION_COMMIT=3920a58c122cbee0fb379781abcd53d3eaa0f70d
SYNTHETIC_CORPUS_HARNESS_COMMIT=7dd6e33d80192b04dba77785e27df410a9e1a29d
SYNTHETIC_CORPUS_SQL_ALIAS_FIX_COMMIT=ded392f1b7cc42c2ba5d144cb723eba49b2e71ab
RESTORE_PREFLIGHT_SQL_ALIAS_FIX_COMMIT=8003d0216d52221d19924611804b0f5dbc3bd420
ASSESSMENT_AT_UTC=2026-09-04T11:32:14Z
IMPLEMENTATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_QUALIFICATION=READY_FOR_OWNER_REVIEW
CRYPTOGRAPHIC_BACKUP_RESTORE=PASS_LOCAL_SYNTHETIC
RECEIVER_ENABLED_BY_DEFAULT=NO
PROVIDER_NETWORK_CALLS=0
LOCAL_LAB_NETWORK_CALLS=0
REAL_RECEIVER_NETWORK_CALLS=0
SYNTHETIC_LOOPBACK_RECEIVER_TEST_CALLS=EXECUTED_BOUNDED
REAL_DATA_USED=NO
OWNER_REVIEW_REQUIRED=YES
```

L'implémentation locale, les migrations, le contrat HTTP, le mTLS synthétique, la persistance,
l'idempotence, la concurrence, l'outbox et la purge ont réussi leurs qualifications automatisées.
Une base PostgreSQL 17 synthétique a en outre été sauvegardée au format custom directement dans un
flux `age --passphrase`, sans dump clair, puis restaurée et vérifiée dans une cible PostgreSQL 17
fraîche et isolée. INT-001 est prêt pour la revue propriétaire, mais demeure actif. Ce résultat
n'autorise ni clôture automatique, ni livraison réelle, ni réseau distant, ni VPS, ni production.

## Périmètre vérifié

- branche `codex/int-001-j7-receiver`, créée depuis la fusion CAT-002
  `85dc943601a7d33d37ca69a0c913bf32f1162811` ;
- receiver désactivé par défaut et absent des profils `batch-worker` et `replay` ;
- route brute unique `/api/imports/sofascore/j7-canonical-events` sous `control-api` ;
- HTTPS/mTLS synthétique sur `127.0.0.1:8444`, `client-auth=NEED`, certificat feuille validé par
  date, EKU `clientAuth` et allowlist SHA-256 ;
- stores TLS locaux absolus non UNC, y compris refus des variantes `file://host/...` et
  `file:////host/...` après conversion URI vers `Path` ;
- configuration statique TLS, DataSource, Hikari et Flyway refusée par la garde web précoce avant
  résolution/initialisation, puis `JdbcConnectionDetails` et `HikariDataSource` effectifs inspectés
  par la garde bean ;
- enveloppe J7 stricte, limite de 5 MiB, hashes recalculés, statut `HUMAN_VALIDATED` et ACK
  déterministe de sept champs inférieur à 16 Kio ;
- import inbox/audit/outbox transactionnel, unicités PostgreSQL, verrous advisory ordonnés,
  rollback d'outbox, répétition exacte et divergence ;
- rétention bornée, purge explicite sans surface runtime, tombstone et audit avant suppression ;
- migrations additives V006, V007 et V008, sans modification de V001 à V005 ; prochain slot V009 ;
- scripts de sauvegarde/restauration fail-closed, sans dump clair, qualifiés hors ligne sur un corpus
  synthétique borné dont les cinq tables de preuve sont restées stables avant et après le dump.

## Qualification cryptographique PostgreSQL 17

### Corpus source et archive

```text
PROOF_ID=int001-pg17-20260904-q1
PROOF_DIRECTORY_BASENAME=proof-int001-pg17-20260904-q1
BACKUP_FORMAT=PG_CUSTOM_FULL_DATABASE
ENCRYPTION=AGE_PASSPHRASE_NATIVE_PROMPT
MANIFEST_CREATED_AT_UTC=2026-09-04T11:16:53.5779281Z
ARCHIVE_SIZE_BYTES=108759
ARCHIVE_SHA256=bbe54aca77db77d0fe00efe03da9873dd38e86cb6d617155ce909c126905dfe8
MANIFEST_SIZE_BYTES=1547
MANIFEST_SHA256=41ee3fbc9dc28d59d725562e5cb70d7b2354b0a66c7cba5f3828edf1b6bcf256
MANIFEST_FORMAT_VERSION=1
MANIFEST_PURPOSE=INT-001_J7_FULL_DATABASE_BACKUP
MANIFEST_BACKUP_SCOPE=full-database
MANIFEST_POSTGRES_MAJOR_VERSION=17
MANIFEST_REQUIRED_MIGRATION=V008
MANIFEST_J7_EVIDENCE_STABLE_ACROSS_DUMP=YES
MANIFEST_ENCRYPTION_FORMAT=age-passphrase
MANIFEST_ENCRYPTION_TOOL_VERSION=v1.3.1
MANIFEST_SECRET_MATERIAL_PERSISTED=NO
MANIFEST_RESTORE_ISOLATED_LOOPBACK_TARGET_REQUIRED=YES
MANIFEST_RESTORE_PRIMARY_MUTATION_ALLOWED=NO
PLAINTEXT_DUMP_CREATED=NO
BACKUP_SUCCESS_EVIDENCE_SHA256=25df51c98f5e309c181b3b8ee17f0d6b0245c1d33bc8eef001f5ce3a761762b1
BACKUP_SUCCESS_EVIDENCE_CREATED_AT_UTC=2026-09-04T11:16:53.6187959Z
```

La source synthétique a appliqué V001 à V008 et produit exactement trois reçus, deux payloads,
cinq audits, trois messages d'outbox et un tombstone. Le harness emploie exclusivement les cas
d'usage applicatifs d'import et de purge ; son unique écriture SQL directe marque l'outbox exacte
comme `DELIVERED` afin de rendre la purge autorisée. Il ne découvre aucune base automatiquement et
refuse tout sélecteur PostgreSQL injecté.

| Table | Lignes | SHA-256 du contenu canonique |
|---|---:|---|
| `j7_import_receipt` | 3 | `d3505d0ca8f16f4174370b44a788f5eff1d90cc457b993665ea29454cea0bcff` |
| `j7_import_payload` | 2 | `fa68235909c765ef06b209f54a664718fccd9be6b59f0c541631b462c603c872` |
| `j7_import_audit` | 5 | `0f5f55bd95833f7bb676b05e91bd5e3e6b81781d77acc0f38863834701a36ba6` |
| `outbox_message` | 3 | `1d6d1dda55c578d683b9f8275eadd3f9fa0dd00a175ae36c2dd90aee2dd11df1` |
| `j7_import_payload_tombstone` | 1 | `16df3c3cdc8095a2c4592a29c7f2808dc50703526d02b8b6cf69e6a6ec4ce127` |

```text
SOURCE_CONTAINER=betting-int001-pg17-20260904-q1-source
SOURCE_VOLUME=betting-int001-pg17-20260904-q1-source-data
SOURCE_DATABASE=int001_j7_source_20260904_q1
SOURCE_BIND=127.0.0.1:5433:5432
SOURCE_POSTGRESQL_SERVER_VERSION=17.10
SOURCE_STOPPED_BEFORE_RESTORE=YES
SOURCE_STOPPED_AFTER_UTC=2026-09-04T11:16:53.6193228Z
SOURCE_STOPPED_BEFORE_UTC=2026-09-04T11:19:16.6264864Z
PRIMARY_MUTATION_PERFORMED=NO_CORROBORATED_BY_SOURCE_STOPPED
STANDARD_POSTGRES_PROVENANCE_OPERATOR_ATTESTED=YES
MAPPED_DRIVE_USED=NO
```

### Première cible Q1 : arrêt avant saisie et avant mutation

La première restauration s'est arrêtée pendant le contrôle de fraîcheur de la cible, avant le
déchiffrement et avant que `age` n'affiche son invite masquée. L'opérateur n'a donc saisi aucune
phrase secrète. La cause était l'usage d'alias SQL réservés dans le contrôle de catalogues. La cible
contenait zéro relation publique et aucun octet de l'archive n'y a été restauré. Le correctif
`8003d0216d52221d19924611804b0f5dbc3bd420` ne change ni l'archive, ni le manifeste, ni le protocole :
il remplace seulement ces alias et ajoute une garde Pester.

```text
Q1_TARGET_CONTAINER=betting-int001-pg17-20260904-q1-restore
Q1_TARGET_VOLUME=betting-int001-pg17-20260904-q1-restore-data
Q1_TARGET_DATABASE=int001_j7_restore_20260904_q1
Q1_TARGET_BIND=127.0.0.1:5434:5432
Q1_RESTORE=STOPPED_PRE_DECRYPT_PRE_MUTATION_RESERVED_ALIAS
Q1_AGE_PROMPT_REACHED=NO
Q1_OWNER_INPUT_CONSUMED=NO
Q1_PUBLIC_RELATIONS_AFTER_FAILURE=0
Q1_PARTIAL_OR_PLAINTEXT_RESIDUALS=0
Q1_FAIL_CLOSED=PASS
Q1_FAILURE_EVIDENCE_SHA256=ac4fc8486b041b3e1341868613ca607abd540f139ea8b35c71713d603e3ccfea
Q1_FAILURE_EVIDENCE_CREATED_AT_UTC=2026-09-04T11:19:18.5927824Z
```

### Seconde cible Q2 : restauration fraîche réussie

La reprise a employé une cible, un volume, une base et un secret de base neufs, sans montage ni
réutilisation du volume Q1. L'opérateur a saisi la phrase secrète conservée dans l'invite native
`age`; elle n'a été ni capturée, ni journalisée, ni persistée par les scripts. Le flux déchiffré a
alimenté directement `pg_restore`, puis les cinq familles de preuves ont été recalculées et comparées
au manifeste.

```text
Q2_TARGET_CONTAINER=betting-int001-pg17-20260904-q2-restore
Q2_TARGET_VOLUME=betting-int001-pg17-20260904-q2-restore-data
Q2_TARGET_DATABASE=int001_j7_restore_20260904_q2
Q2_TARGET_BIND=127.0.0.1:5434:5432
Q2_TARGET=FRESH_ISOLATED
Q2_RESTORE=RESTORED_AND_VERIFIED
Q2_RESTORE_EVIDENCE_CREATED_AT_UTC=2026-09-04T11:25:43.8495482Z
Q2_RESTORE_EVIDENCE_SHA256=6c088a4d33304fc0ad4b57bd12cd94a8726abe7330c8b1d6963947ca10dd7ba2
Q2_POSTGRESQL_SERVER_VERSION=17.10
Q2_FLYWAY_SCHEMA_VERSION=008
Q2_COUNTS=receipt:3,payload:2,audit:5,outbox:3,tombstone:1
FIVE_TABLE_EVIDENCE_MATCH=PASS
```

Après qualification, la source et la cible Q2 ont été arrêtées. Les ports 5433 et 5434 ne portent
plus aucun listener. Les conteneurs arrêtés, leurs volumes, l'archive chiffrée, le manifeste et les
preuves privées autorisées sont conservés pour la revue propriétaire et un nettoyage ultérieur
explicitement borné ; ils ne sont donc pas décrits comme un « zéro résidu total ».

```text
PLAINTEXT_BACKUP_RESIDUALS=0
NATIVE_PIPELINE_PROCESS_RESIDUALS=0
POSTGRES_LISTENER_RESIDUALS=0
AUTHORIZED_PRIVATE_AND_EVIDENCE_ARTIFACTS_RETAINED=YES_PENDING_CONTROLLED_CLEANUP
PRIVATE_DIRECTORY_ACL=OWNER_ONLY
EXISTING_LOCAL_LAB_PRIMARY_CONTAINER_TOUCHED=NO
```

## Versions et empreintes des outils

| Élément | Version ou SHA-256 |
|---|---|
| Image PostgreSQL locale figée | `postgres:17-alpine` / `sha256:742f40ea20b9ff2ff31db5458d127452988a2164df9e17441e191f3b72252193` |
| Serveurs source et cible | PostgreSQL `17.10` |
| Client `psql` | `17.11` / `aa12e27530ac07f129e69daca953ea5f02202a7bde45d503e771abc39016536d` |
| Client `pg_dump` | `17.11` / `e856d19e6b73f351069d2d3d9f442e8c0371bebfc53c7e55b455adfc0b8ee14b` |
| Client `pg_restore` | `17.11` / `c85a472c22c4eb657f76e70a87b55e2070474cd0042bcb87b50a787d085d950c` |
| `age` | `1.3.1` / `90f5cc37249c06e0b302e476a8a63bcefeecd9437c192b8af33e6ff2d69558dd` |
| `Backup-J7Imports.ps1` | `3d54237e697a429d1fb575f5dfbe50f388d11f3206e5147b203d626aecde2b5a` |
| `Restore-J7Imports.ps1` | `f57eedbce303ae0b4414b73ebab67c650265a57044ff1f5126797afe47f391d8` |
| `J7-BackupRestore.Core.psm1` | `b5416e1f5b4096f5b52e0faa5a0e1fecf55ed9e6d21bc9430c32ac633ca02930` |
| Tests Pester | `e81be13fc67d1fc5f1d1e6e744fb5021c51ffb2a6a6a75685499a8ead5d64efd` |
| Harness synthétique | `11111a3df27bd00a3a0388631314602854916b0a681af7231c533fadc0cf6628` |

La sauvegarde Q1 a utilisé le module core antérieur
`cc43d4aa585b39f206f4ff13971e249d542d0622960f05e638a9d09a4f44b760`; la restauration Q2 a
utilisé le module corrigé `b5416e1f5b4096f5b52e0faa5a0e1fecf55ed9e6d21bc9430c32ac633ca02930`.
Cette différence est bornée à la qualification de fraîcheur de la cible de restauration et ne change
ni le format, ni les octets, ni le manifeste de la sauvegarde.

## Résultats reproductibles

| Contrôle | Commande ou preuve | Résultat |
|---|---|---|
| Java/Maven standard | `.\mvnw.cmd clean verify` | `303/303`, zéro échec/erreur/skip, `BUILD SUCCESS` |
| PostgreSQL/Testcontainers et mTLS | `.\mvnw.cmd -Pintegration verify` | `303/303` Surefire et `98/98` Failsafe, zéro échec/erreur/skip, `BUILD SUCCESS` |
| Validation Windows consolidée | `.\scripts\verify-windows.cmd` | `PASS`, code 0, Java `25.0.4`, Maven `3.9.16` |
| Architecture | `ArchitectureRulesTest` dans la suite standard | `PASS` |
| Persistance et mTLS J7 | suites d'intégration | `PASS`, migrations V001 à V008 sur PostgreSQL `17.10` |
| Scripts PowerShell | `Invoke-Pester scripts/tests/J7-BackupRestore.Tests.ps1` | `32/32` |
| Sauvegarde/restauration | exécution hôte PostgreSQL 17 + `age` | Q1 fail-closed puis Q2 fraîche `RESTORED_AND_VERIFIED` |
| Secrets | `.\scripts\check-no-secrets.cmd` | `PASS` |
| Patch | `git diff --check` | `PASS` |
| Schéma J7 entrant | SHA-256 | `45c3350f4f05acf2bdf4cd9806a965723e0a73fec38f2c9347b1f9f85590c938` |
| Compose | `docker compose --env-file .env.example config --quiet` | `PASS`, code 0 |
| Listeners après tests | contrôles locaux | `5433=0`, `5434=0`, `8444=0` |
| Données et appels | compteurs de campagne | 0 fournisseur, 0 Local Lab, 0 receiver réel/distant, aucune donnée réelle |

## Empreintes des migrations nouvelles

| Migration | SHA-256 |
|---|---|
| `V006__j7_import_inbox.sql` | `6557d9510b20f6c163f0b96bc914de6d181b151eebd08cb254928162276bc6e3` |
| `V007__j7_import_purge_integrity.sql` | `9a87c602c500a47f5c3984b690185f9906394a651ea4e3d4c9999f8537120cff` |
| `V008__j7_import_upgrade_evidence_time_integrity.sql` | `6fbee3fec6a84aa390df913df4fbd3b73fc1a522272197e82bbf6bdf01f7716b` |

## Revues indépendantes et limites

La revue d'architecture et de transactions n'a relevé aucun défaut matériel restant après le
correctif des chemins UNC. Elle a confirmé l'atomicité inbox/audit/outbox, les verrous ordonnés,
l'unicité finale en base, le rollback complet, la purge V007/V008 et les frontières du monolithe.
La revue de sécurité finale n'a relevé aucun défaut P0, P1 ou P2 matériel restant. Elle a notamment
revalidé les gardes précoces TLS/JDBC, les variantes UNC, les bornes avant allocation, le parser, les
hashes, l'ACK strict, le mTLS, les transactions et l'absence de réseau sortant ou de fuite de payload.
Une revue opératoire indépendante a également recalculé les empreintes de l'archive et du manifeste,
relu le manifeste avec le parser versionné courant, contrôlé les cinq entrées de preuve de tables
consignées dans le manifeste, les ACL privées, l'absence de dump clair ou partiel et l'absence de
processus ou listener résiduel. Elle n'a relevé aucun défaut matériel.

Un résidu non matériel demeure fail-closed : si une purge committe exactement entre la lecture du
reçu/tombstone et la lecture du payload d'un duplicate, cette tentative peut recevoir un `500`
transitoire. Elle ne produit ni faux ACK positif ni audit `DUPLICATE`; une tentative ultérieure voit
le tombstone durable.

## Portes encore fermées

```text
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
CRYPTOGRAPHIC_BACKUP_RESTORE=PASS_LOCAL_SYNTHETIC
DOCKER_COMPOSE_CONFIG=PASS
DATABASE_ROLE_SEPARATION=NOT_IMPLEMENTED_LOCAL_OWNER_IS_TRUST_BOUNDARY
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PULL_REQUEST_AUTHORIZED=NO
```

La revue propriétaire et la décision explicite de clôture demeurent requises. Une acceptation
d'INT-001 ne vaudrait toujours pas autorisation d'envoyer la demande SofaScore, de livrer un export
réel ou dérivé du fournisseur, d'ouvrir un receiver distant ou de déployer sur le VPS. Une future
production devra en outre séparer le rôle Flyway propriétaire du rôle runtime à privilèges minimaux ;
INT-001 documente cette exigence sans prétendre la livrer.
