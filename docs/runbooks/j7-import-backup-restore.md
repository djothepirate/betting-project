# Sauvegarde chiffrée et restauration isolée des imports J7

## Objet et autorisation

Ce runbook décrit la preuve de sauvegarde/restauration locale exigée par INT-001. La preuve utilise
uniquement une base et des exports synthétiques. Elle s'appuie exclusivement sur
`scripts/Backup-J7Imports.ps1`, `scripts/Restore-J7Imports.ps1` et leur module commun ; elle ne crée
aucun service, conteneur ou base, ne planifie aucune tâche et n'autorise ni donnée fournisseur réelle,
ni copie cloud, ni restauration dans la base primaire.

```text
RUNBOOK_SCOPE=SYNTHETIC_LOCAL_QUALIFICATION_ONLY
BACKUP_ENCRYPTION_REQUIRED=YES
PLAINTEXT_ARCHIVE_RETENTION=0
RESTORE_TARGET=FRESH_ISOLATED_POSTGRESQL_17
ACTIVE_DATABASE_RESTORE_AUTHORIZED=NO
REAL_J7_DATA_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
CRYPTOGRAPHIC_PROOF_STATUS=NOT_EXECUTED
```

La présente réconciliation documentaire n'exécute aucun dump, chiffrement, déchiffrement,
restauration, Docker ou suppression. `NOT_EXECUTED` reste le seul résultat valide jusqu'à une
qualification opérateur séparée et explicitement autorisée.

Une archive peut contenir les octets exacts encore retenus dans l'inbox. Elle a donc le même niveau
de confidentialité que la base source. Aucune archive, passphrase, fichier de credentials, sortie SQL
ou preuve contenant un payload ne doit entrer dans Git, les logs ou un terminal transcripté.

## Périmètre physique et preuves

Le dump est un dump PostgreSQL complet au format custom : schéma, fonctions, triggers, contraintes,
données et historique Flyway sont inclus. Il doit restaurer la chaîne additive complète, notamment
`V006__j7_import_inbox.sql`, `V007__j7_import_purge_integrity.sql` et
`V008__j7_import_upgrade_evidence_time_integrity.sql`. Il ne s'agit jamais d'un export `--data-only`
ou d'une sélection de tables. Le prochain slot Flyway disponible après ce schéma est `V009`.

En plus de ce dump complet, le manifeste prouve séparément le nombre de lignes et un SHA-256 de
contenu pour exactement cinq tables physiques, dans cet ordre :

1. `j7_import_receipt` ;
2. `j7_import_payload` ;
3. `j7_import_audit` ;
4. `outbox_message` ;
5. `j7_import_payload_tombstone`.

Les quatre tables `j7_import_*` sont créées par V006 ; `outbox_message` est préexistante et constitue
la cinquième table de preuve, pas une cinquième table créée par V006. Le hash de contenu agrège des
hashes de lignes JSONB triés. Le contrôle avant/après dump porte uniquement sur ces cinq tables : le
champ `j7EvidenceStableAcrossDump=true` ne prétend pas prouver l'immobilité de toute la base. Le dump
lui-même reste une image transactionnellement cohérente fournie par `pg_dump`.

La sauvegarde ne remplace pas la rétention J7, dont le défaut et la baseline valent 30 jours, et ne
réhydrate pas dans la primaire les octets déjà purgés.

## Outils, terminal et secrets

Les scripts exigent PowerShell 7 avec arrêt d'arbre de processus, les clients PostgreSQL 17
`pg_dump`, `pg_restore` et `psql`, un serveur PostgreSQL 17 et une version sémantique identifiable de
`age`. `age` est un outil opérateur externe, pas une dépendance Maven ou runtime. Ce runbook ne définit
aucun fallback vers une archive claire ni vers un autre outil.

Préconditions :

1. base source locale sur `127.0.0.1:5433`, avec uniquement des données synthétiques ;
2. répertoire destination absolu, déjà créé, hors dépôt, sur un stockage local, sans reparse point,
   protégé par le système d'exploitation ; le nom final `.age` et son manifeste compagnon ne doivent
   pas déjà exister ;
3. terminal natif interactif, local et non transcripté pour le prompt `age --passphrase` ;
4. mot de passe PostgreSQL fourni par le fichier standard libpq de l'opérateur ou par `PGPASSFILE`
   absolu, régulier, hors dépôt et déjà protégé ; aucun prompt PostgreSQL n'est permis ;
5. aucun secret dans les arguments ou variables `AGE_PASSPHRASE`/`PGPASSWORD` ; les scripts retirent
   aussi les sélecteurs libpq ambiants `PGHOSTADDR`, `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`,
   `PGSERVICE` et `PGSERVICEFILE` afin que les paramètres loopback explicites restent l'autorité ;
6. aucun receiver, sender, Playwright, poller, scheduler ou appel fournisseur en cours.

Le script refuse un chemin UNC et un reparse point, mais une lettre de lecteur Windows mappée peut
conserver une syntaxe absolue locale tout en visant un partage réseau. L'absence de lecteur mappé et
le caractère physiquement local du stockage constituent donc une frontière opérateur : ils doivent
être vérifiés avant l'appel. Un doute impose `NOT_EXECUTED`; il ne doit pas être compensé par une
copie ultérieure ou une exception au runbook.

Le mode `Interactive` demande une passphrase choisie dans le prompt natif `age`. Le mode
`AgeGenerated` demande de laisser vide le premier prompt afin que `age` génère sa passphrase ; elle
doit alors être capturée et conservée uniquement par le mécanisme secret opérateur autorisé. La
passphrase n'est ni persistée dans le manifeste, ni retournée par le script. Si l'outil, le terminal
natif, le secret ou la frontière de destination manque, consigner `NOT_EXECUTED` et arrêter.

## Préparer le corpus synthétique

Avant le dump, la qualification PostgreSQL doit avoir créé au minimum :

- un import `IMPORTED` non expiré avec son payload et une outbox corrélée ;
- sa répétition `DUPLICATE` sans seconde inbox/outbox ;
- un import synthétique expiré dont le payload a été purgé par le cas d'usage applicatif, avec
  tombstone conservé ;
- un import expiré non purgeable parce que son outbox n'est pas encore traitée ;
- les audits attendus pour ces scénarios.

Relever uniquement les compteurs, UUID synthétiques, hashes synthétiques, tailles et états utiles.
Ne jamais copier les octets dans le rapport.

## Créer l'archive chiffrée

Depuis la racine du worktree, ouvrir un terminal PowerShell 7 natif non transcripté. La destination
absolue ci-dessous est un exemple : son répertoire doit avoir été créé hors dépôt par l'opérateur.
Ne pas afficher ni interpoler de secret dans cette commande.

```powershell
$archivePath = "D:\BettingProject-Backups\INT-001\int001-j7-synthetic-20260902.age"
.\scripts\Backup-J7Imports.ps1 `
    -Destination $archivePath `
    -DatabaseHost "127.0.0.1" `
    -Port 5433 `
    -Database "betting" `
    -Username "betting" `
    -PassphraseMode Interactive `
    -TimeoutSeconds 300
```

Adapter uniquement le chemin, le nom de base et le nom d'utilisateur à la configuration locale
autorisée. Le script valide PostgreSQL/client 17, V006+V007+V008, les cinq tables attendues et les hashes
des payloads, puis calcule les preuves J7 avant le dump. Les scripts neutralisent `PGTZ` et
`PGDATESTYLE`, puis toutes les sessions PostgreSQL natives reçoivent explicitement
`bytea_output=hex`, `TimeZone=UTC` et `DateStyle=ISO,YMD` : les preuves restent donc indépendantes des
valeurs libpq ambiantes et le hash du payload demeure identique entre source et cible.

Le flux natif est exactement :

```text
pg_dump --format=custom --compress=9 --no-owner --no-privileges --serializable-deferrable
    stdout binaire
        -> stdin de age --passphrase --output <archive-partielle.age>
```

Il n'existe aucun fichier dump clair, même transitoire. Le script écrit seulement une archive chiffrée
partielle dans le répertoire final, l'efface en cas d'échec, recalcule les cinq preuves J7 après le
dump et publie atomiquement l'archive puis le manifeste seulement si elles sont stables. Il refuse
l'écrasement, une course sur le nom final et tout chemin dans le dépôt.

Le manifeste compagnon est exactement `<archive>.manifest.json`. Son contrat fermé contient :

- `formatVersion=1`, `purpose=INT-001_J7_FULL_DATABASE_BACKUP` et
  `backupScope=full-database` ;
- `postgresMajorVersion=17`, `requiredMigration=V008` et une validation effective de
  V006+V007+V008 ;
- nom de fichier seul, heure UTC, taille et SHA-256 de l'archive chiffrée ;
- `encryption.format=age-passphrase`, version de `age` et
  `secretMaterialPersisted=false` ;
- `restorePolicy.isolatedLoopbackTargetRequired=true` et
  `primaryMutationAllowed=false` ;
- les cinq preuves de tables et `j7EvidenceStableAcrossDump=true`.

Le manifeste exclut chemin utilisateur, host, port, base, username, SQL, payload et passphrase. Le
script retourne seulement des statuts, versions, comptes et hashes d'artefacts non secrets. Conserver
l'archive et le manifeste ensemble, hors dépôt, avec une durée de conservation explicitement bornée.

## Préparer une cible PostgreSQL 17 isolée

Le script de restauration ne crée, ne supprime et ne réinitialise aucune base ou instance. Il ne
lance ni n'arrête Docker. L'opérateur doit préparer séparément une cible PostgreSQL 17 standard,
fraîche, déjà démarrée, liée uniquement à `127.0.0.1` et créée depuis les templates PostgreSQL 17
standards. Une cible issue d'un template personnalisé, d'un `pg_upgrade`, d'un clone ou d'une
restauration antérieure n'est pas admissible. Avant l'appel, vérifier :

1. port distinct de la primaire `5433`, par exemple `5434` ;
2. nom distinct de la primaire et conforme à `int001_j7_restore_[a-z0-9_]{1,40}`, par exemple
   `int001_j7_restore_20260902` ;
3. base réellement vide : aucun objet utilisateur, aucun large object, aucune extension autre que
   `plpgsql`, aucune migration Flyway préappliquée ;
4. aucun volume, fichier ou endpoint de la primaire réutilisé ;
5. credentials de cible protégés hors dépôt et fournis via la même frontière libpq que pour le dump ;
6. aucune application Spring, Flyway, route J7, tâche ou outil fournisseur connecté à la cible.

La vérification fermée de vacuité inspecte les namespaces non standards ; les relations, routines,
types et collations de `public` ; les large objects ; publications, subscriptions et event triggers ;
foreign data wrappers, serveurs étrangers et la vue publique `pg_user_mappings` ; ACL par défaut,
security labels et langages non standards ; les catalogues à OID utilisateur pour casts,
conversions, opérateurs, classes/familles d'opérateurs, méthodes d'accès, transforms et recherche
plein texte ; enfin toute extension autre que `plpgsql`. Ces contrôles corroborent une cible standard
fraîche mais n'attestent pas son historique de création : l'absence de template personnalisé,
`pg_upgrade`, clone ou restauration préalable reste une déclaration opérateur obligatoire.

Le dump complet contient le schéma et l'historique Flyway : ne pas migrer la cible avant la
restauration. Un conflit de port, de nom, une base non vide ou un doute sur l'isolation impose
`NOT_EXECUTED`. Le script exige en plus l'accusé littéral :

```text
RESTORE INT-001 J7 TO ISOLATED TARGET
```

Cet accusé confirme la cible choisie ; il ne remplace aucune vérification d'isolation.

## Déchiffrer et restaurer

Toujours depuis un terminal PowerShell 7 natif non transcripté, appeler le script avec l'identité
explicite de la primaire et de la cible. L'exemple suppose une archive créée et un serveur cible
préparé par l'opérateur ; il n'a pas été exécuté pendant cette réconciliation.

```powershell
.\scripts\Restore-J7Imports.ps1 `
    -ArchivePath $archivePath `
    -PrimaryHost "127.0.0.1" `
    -PrimaryPort 5433 `
    -PrimaryDatabase "betting" `
    -TargetHost "127.0.0.1" `
    -TargetPort 5434 `
    -TargetDatabase "int001_j7_restore_20260902" `
    -TargetUsername "betting_restore" `
    -IsolationAcknowledgement "RESTORE INT-001 J7 TO ISOLATED TARGET" `
    -TimeoutSeconds 300
```

Le script lit strictement le manifeste, refuse les propriétés manquantes, supplémentaires ou
dupliquées, vérifie le nom, la taille et le SHA-256 du chiffré, PostgreSQL/client 17, la version de
`age`, la cible loopback distincte et la vacuité de la base. La passphrase est saisie uniquement par
le prompt natif `age`.

Le flux de restauration est exactement :

```text
age --decrypt <archive.age>
    stdout binaire
        -> stdin de pg_restore --exit-on-error --single-transaction
             --no-owner --no-privileges <cible isolée>
```

Aucun dump déchiffré n'est écrit sur disque. La transaction unique empêche la publication d'un
restore partiel en cas d'échec. Aucun argument ne cible la primaire et le script ne lui envoie aucune
requête d'écriture ; `PrimaryMutationPerformed=false` est le résultat attendu d'un succès. Un échec
de déchiffrement, de manifeste, de pipeline, de restauration ou de vérification arrête la preuve ; ne
pas ignorer l'erreur, réutiliser une cible partielle ou passer à un fichier clair.

## Vérifications après restauration

Le script effectue lui-même les vérifications fermées suivantes sur la cible :

1. V006, V007 et V008 sont toutes trois présentes et réussies dans `flyway_schema_history` ;
2. les quatre tables J7 et `outbox_message` existent ;
3. chaque payload présent conserve taille, FK et SHA-256 des octets ;
4. les nombres de lignes et hashes de contenu des cinq tables correspondent exactement au manifeste ;
5. aucune preuve ne contient de payload ou secret ;
6. aucune mutation de la primaire n'a été effectuée.

La concordance des cinq tables prouve l'exactitude J7 demandée, tandis que le scope
`full-database` restaure aussi schéma, Flyway et autres données synthétiques du snapshot. Ne pas
modifier la cible pour « réparer » un écart. Tout écart rend la preuve `FAIL`; une nouvelle tentative
exige une autre base vide explicitement isolée.

## Timeout, annulation et nettoyage borné

Chaque commande native possède un timeout borné. `Ctrl+C`, timeout ou erreur déclenche l'arrêt de
l'arbre de processus producteur/consommateur et attend la confirmation de sa terminaison. Si le
nettoyage ne peut pas être confirmé, le script échoue fermé. Les fichiers partiels qu'il possède sont
supprimés ; il ne supprime jamais une archive publiée avec son manifeste, une base, un conteneur ou
un répertoire opérateur.

Après une preuve, l'opérateur arrête la cible par son mécanisme préalablement autorisé, vérifie que son
port n'écoute plus, puis supprime uniquement les artefacts exactement identifiés qu'il possède et dont
la durée de conservation est échue. Ce runbook ne fournit aucune commande de suppression automatique.
Ne jamais employer de glob large, supprimer le volume Compose primaire, purger la base primaire ou
viser un chemin calculé non vérifié.

Une politique de backup récurrente exige un Work Order distinct définissant planification, secret,
rotation, rétention, tests et responsable de récupération. Les scripts INT-001 n'effectuent aucune
purge de la primaire.

## Résultat de qualification

Lors d'une future exécution autorisée, le rapport expurgé peut reprendre les sorties sûres des scripts
et le bloc suivant, sans chemins, payloads, credentials ou passphrase :

```text
SOURCE_DATA=SYNTHETIC_ONLY
POSTGRESQL_SOURCE_VERSION=17
BACKUP_FORMAT=PG_CUSTOM
BACKUP_SCOPE=FULL_DATABASE_SCHEMA_DATA_FLYWAY
ENCRYPTION=AGE_PASSPHRASE_NATIVE_PROMPT
PLAINTEXT_DUMP_CREATED=NO
PLAINTEXT_BACKUP_RESIDUALS=0
RESTORE_TARGET_PORT=127.0.0.1:5434
RESTORE_TARGET_FRESH_AND_ISOLATED=<PASS|FAIL>
FLYWAY_V006_V007_V008_RESTORED=<PASS|FAIL>
J7_FIVE_TABLE_EVIDENCE_MATCH=<PASS|FAIL>
UNPURGED_PAYLOAD_HASHES_MATCH=<PASS|FAIL>
PRIMARY_MUTATION_PERFORMED=NO
PROVIDER_CALLS=0
LOCAL_LAB_CALLS=0
REAL_RECEIVER_CALLS=0
RESIDUAL_RESTORE_LISTENERS=0
```

État attesté par ce changement documentaire :

```text
INT001_BACKUP=NOT_EXECUTED
INT001_AGE_ENCRYPTION=NOT_EXECUTED
INT001_ISOLATED_RESTORE=NOT_EXECUTED
INT001_CRYPTOGRAPHIC_PROOF=NOT_EXECUTED
INT001_WORK_ORDER=IN_PROGRESS
INT001_QUALIFICATION=NOT_QUALIFIED
```

La présence des scripts et de ce runbook ne permet pas de déclarer INT-001 qualifié.
