# INT-001 - Rapport de préparation locale du receiver J7

## Verdict borné

```text
WORK_ORDER=INT-001
BASE_COMMIT=85dc943601a7d33d37ca69a0c913bf32f1162811
CAT002_MERGE_VERIFIED=YES
IMPLEMENTATION_COMMIT=3920a58c122cbee0fb379781abcd53d3eaa0f70d
ASSESSMENT_AT_UTC=2026-09-02T12:13:53Z
IMPLEMENTATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_QUALIFICATION=INCOMPLETE_PENDING_CRYPTOGRAPHIC_BACKUP_RESTORE
RECEIVER_ENABLED_BY_DEFAULT=NO
PROVIDER_NETWORK_CALLS=0
LOCAL_LAB_NETWORK_CALLS=0
REAL_RECEIVER_NETWORK_CALLS=0
REAL_DATA_USED=NO
OWNER_REVIEW_REQUIRED=YES
```

L'implémentation locale, les migrations, le contrat HTTP, le mTLS synthétique, la persistance,
l'idempotence, la concurrence, l'outbox et la purge ont réussi leurs qualifications automatisées.
Le Work Order reste néanmoins actif : aucune sauvegarde PostgreSQL 17 réellement chiffrée puis
restaurée vers une base standard fraîche et isolée n'a été exécutée. Ce résultat n'autorise donc ni
clôture, ni livraison réelle, ni réseau distant, ni VPS, ni production.

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
- scripts de sauvegarde/restauration fail-closed, sans dump clair, testés uniquement hors ligne.

## Résultats reproductibles

| Contrôle | Commande ou preuve | Résultat |
|---|---|---|
| Java/Maven standard | `.\mvnw.cmd clean verify` | `297/297`, zéro échec, `BUILD SUCCESS` |
| PostgreSQL/Testcontainers et mTLS | `.\mvnw.cmd -Pintegration verify` | `98/98`, zéro échec, `BUILD SUCCESS` |
| Architecture | `ArchitectureRulesTest` dans la suite standard | `5/5` |
| Persistance J7 | `JdbcJ7ImportStoreIT` | `6/6` |
| mTLS serveur réel synthétique | `J7ReceiverMutualTlsIT` | `1/1` |
| Upgrade V006 vers V008 | `V006MigrationIT` | `2/2` |
| Scripts PowerShell | `Invoke-Pester scripts/tests/J7-BackupRestore.Tests.ps1` | `31/31` |
| Secrets | `.\scripts\check-no-secrets.cmd` | `PASS` |
| Texte | contrôle UTF-8 strict des 73 fichiers du diff | aucun BOM UTF-8, NUL ou octet invalide |
| Patch | `git diff --check` | `PASS` ; avertissement informatif LF/CRLF sur `.env.example` |
| Schéma J7 entrant | SHA-256 | `45c3350f4f05acf2bdf4cd9806a965723e0a73fec38f2c9347b1f9f85590c938` |
| Compose | `docker compose --env-file .env.example config --quiet` | `NOT_RUN_DOCKER_CLI_UNAVAILABLE` |

La CLI Docker n'est pas résoluble dans le terminal courant. La suite d'intégration a toutefois joint
Docker Desktop par le named pipe Testcontainers et exécuté les conteneurs PostgreSQL 17. Cela ne
remplace pas la validation Compose demandée ; celle-ci reste explicitement non exécutée.

## Empreintes des migrations nouvelles

| Migration | SHA-256 |
|---|---|
| `V006__j7_import_inbox.sql` | `6557d9510b20f6c163f0b96bc914de6d181b151eebd08cb254928162276bc6e3` |
| `V007__j7_import_purge_integrity.sql` | `9a87c602c500a47f5c3984b690185f9906394a651ea4e3d4c9999f8537120cff` |
| `V008__j7_import_upgrade_evidence_time_integrity.sql` | `6fbee3fec6a84aa390df913df4fbd3b73fc1a522272197e82bbf6bdf01f7716b` |

## Revues indépendantes

La revue d'architecture et de transactions n'a relevé aucun défaut matériel restant après le
correctif des chemins UNC. Elle a confirmé l'atomicité inbox/audit/outbox, les verrous ordonnés,
l'unicité finale en base, le rollback complet, la purge V007/V008 et les frontières du monolithe.
La revue de sécurité finale n'a relevé aucun défaut P0, P1 ou P2 matériel restant. Elle a notamment
revalidé les gardes précoces TLS/JDBC, les variantes UNC, les bornes avant allocation, le parser, les
hashes, l'ACK strict, le mTLS, les transactions et l'absence de réseau sortant ou de fuite de payload.

Un résidu non matériel demeure fail-closed : si une purge committe exactement entre la lecture du
reçu/tombstone et la lecture du payload d'un duplicate, cette tentative peut recevoir un `500`
transitoire. Elle ne produit ni faux ACK positif ni audit `DUPLICATE`; une tentative ultérieure voit
le tombstone durable.

## Portes encore fermées

```text
CRYPTOGRAPHIC_BACKUP_RESTORE=NOT_EXECUTED
DOCKER_COMPOSE_CONFIG=NOT_EXECUTED_DOCKER_CLI_UNAVAILABLE
DATABASE_ROLE_SEPARATION=NOT_IMPLEMENTED_LOCAL_OWNER_IS_TRUST_BOUNDARY
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Avant clôture, il reste à exécuter le runbook de sauvegarde/restauration avec PostgreSQL/client 17,
`age --passphrase`, une source synthétique et une cible standard fraîche, isolée et vide, puis à
consigner les hashes et l'absence de dump clair. La revue propriétaire demeure distincte. Une future
production devra en outre séparer le rôle Flyway propriétaire du rôle runtime à privilèges minimaux ;
INT-001 documente cette exigence sans prétendre la livrer.
