# Contrat `j7-import-receiver-v1`

## Statut et frontière

Ce document est le contrat normatif du receiver local optionnel livré par INT-001. Il accepte un
export J7 canonique déjà marqué `HUMAN_VALIDATED`, en conserve les octets exacts et publie
transactionnellement une notification d'outbox. Un ACK positif signifie uniquement « import durable
et idempotent ». Il ne signifie ni enrichissement, ni application au catalogue, ni validation d'un
pari.

Le receiver appartient au monolithe Betting Project et au profil `control-api`. Il est désactivé par
défaut par `betting.integration.j7-receiver.enabled=false`. Sa présence n'autorise aucun appel vers
SofaScore, le SofaScore Local Lab, un VPS ou un receiver distant. Le dépôt ne charge aucun code,
artefact, fichier ou service du Local Lab à l'exécution ou au build ; le schéma JSON versionné dans
ce dépôt est une copie contractuelle autonome.

```text
CONTRACT_VERSION=1.0
PROTOCOL_VERSION=1.0
RECEIVER_ENABLED_BY_DEFAULT=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LOCAL_LAB_RUNTIME_DEPENDENCY=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## Activation et connecteur HTTPS

Lorsque le receiver est désactivé, le `control-api` conserve son fonctionnement loopback historique
sur HTTP/8080 et la route J7 n'est pas chargée. L'activation explicite doit satisfaire toutes les
conditions suivantes avant que l'application accepte du trafic :

- `server.address=127.0.0.1` ;
- `server.port=8444` ;
- `server.ssl.enabled=true` ;
- `server.ssl.client-auth=need` ;
- `server.compression.enabled=false` ;
- keystore serveur et truststore client présents, lisibles et non vides, référencés par des chemins
  locaux absolus hors Git ; les chemins relatifs, ressources classpath ou HTTP(S), URI `file:` avec
  autorité et partages UNC sont interdits ;
- absence de SSL bundle, de matériel PEM alternatif et de bundle SNI pouvant substituer les stores
  inspectés ;
- certificat feuille en cours de validité portant explicitement l'EKU `clientAuth`
  (`1.3.6.1.5.5.7.3.2`) ;
- allowlist non vide d'empreintes SHA-256 de certificats clients, hexadécimales minuscules ;
- bornes du contrat égales à `5 242 880` octets en entrée et `16 384` octets pour l'ACK ;
- rétention comprise entre 1 et 3 650 jours, avec défaut et baseline qualifiée à 30 jours ;
- `server.servlet.context-path` et `spring.mvc.servlet.path` absents ou vides ;
- aucune configuration `management.server.*` ne crée un second connecteur ;
- la propriété `spring.datasource.url`, puis les `JdbcConnectionDetails` effectifs et le
  `HikariDataSource` assemblé visent tous `jdbc:postgresql://127.0.0.1:5433/<base>` sans paramètre ;
- aucune DataSource non Hikari, substitution JNDI/type, propriété de localisation Hikari, URL ou
  identifiant/driver Flyway dédié ne peut introduire un autre endpoint PostgreSQL.

L'activation est fail-closed : une valeur absente, invalide ou différente de l'adresse/du port/du
mode TLS attendus empêche le démarrage. La validation statique intervient d'abord dans un
`WebServerFactoryCustomizer` de priorité maximale, avant toute résolution de ressource TLS par Spring
Boot ; un schéma de store interdit ne peut donc pas être déréférencé avant le refus. Tomcat ne possède
qu'un connecteur. Par conséquent,
`/api/imports/**`, `/internal/catalog/**`, `/internal/bootstrap/**` et `/actuator/**` partagent
`127.0.0.1:8444`, HTTPS et l'authentification cliente `NEED`. Il n'existe pas de port de management
séparé ni de route HTTP de repli.

La phase web précoce refuse d'abord l'URL déclarée hors frontière, toute délégation JNDI, classe de
DataSource, propriété Hikari de localisation et les propriétés dédiées `spring.flyway.url`, `user`,
`password` et `driver-class-name`, avant que la base ou Flyway puisse être créé. La garde bean répète
ces contrôles, compare l'URL exposée par les `JdbcConnectionDetails` et celle du `HikariDataSource`
réellement injecté, puis refuse toute DataSource alternative ou driver autre que PostgreSQL.

Une chaîne cliente absente ou non approuvée est refusée pendant la négociation TLS, avant le
contrôleur. Une chaîne approuvée dont le certificat feuille est expiré, ne porte pas l'EKU
`clientAuth` ou n'est pas dans l'allowlist est refusée
avant parsing et avant persistance. Aucun mode trust-all, aucune désactivation de la vérification TLS
et aucun certificat ou clé privée versionné ne sont permis.

## Opération HTTP

```text
METHOD=POST
PATH=/api/imports/sofascore/j7-canonical-events
REQUEST_MEDIA_TYPE=application/vnd.betting-project.j7-canonical-event+json;version=1.0
ACK_MEDIA_TYPE=application/vnd.betting-project.j7-delivery-ack+json;version=1.0
MIN_REQUEST_BYTES=1
MAX_REQUEST_BYTES=5242880
MAX_ACK_BYTES=16384
REQUEST_COMPRESSION=NONE
TRANSFER_ENCODING=FORBIDDEN
```

Le chemin fourni par le client et observé dans la requête doit être, octet pour octet, exactement
`/api/imports/sofascore/j7-canonical-events`. Aucun `server.servlet.context-path` ni
`spring.mvc.servlet.path` ne peut le préfixer. Le slash final, tout segment supplémentaire, tout
paramètre matrix (`;...`) et toute variante percent-encodée (`%...`) sont des alias interdits : le
conteneur ou la garde d'enveloppe les refuse, et ils n'atteignent jamais le contrôleur. Les paramètres
de requête sont également interdits. La route ne suit aucune redirection, ne déclenche aucun callback
et ne propose aucune autre méthode. Les en-têtes contractuels sont interprétés sans tenir compte de
la casse de leur nom, mais chacun doit porter exactement une valeur.

| En-tête | Valeur v1.0 | Règle |
|---|---|---|
| `Content-Type` | `application/vnd.betting-project.j7-canonical-event+json;version=1.0` | Correspondance exacte, sans paramètre supplémentaire |
| `Accept` | `application/vnd.betting-project.j7-delivery-ack+json;version=1.0` | Correspondance exacte |
| `Content-Length` | entier décimal ASCII de `1` à `5242880` | Obligatoire, sans signe ni espace, égal au nombre d'octets reçus |
| `Idempotency-Key` | `j7:<exportId>:sha256:<fileSha256>` | Forme canonique de la section suivante |
| `X-J7-Protocol-Version` | `1.0` | Valeur constante |
| `X-J7-Export-Id` | UUID RFC 4122 canonique minuscule, versions 1 à 5 | Identique au manifeste et à la clé |
| `X-J7-File-SHA256` | 64 hexadécimaux minuscules | SHA-256 des octets exacts du corps |
| `X-J7-Data-SHA256` | 64 hexadécimaux minuscules | Identique au manifeste et au hash recalculé de `data` |

`Content-Encoding` est absent ou vaut exactement `identity`. `Transfer-Encoding` est absent. Les
corps compressés et chunked sont refusés. Les limites sont appliquées avant lecture non bornée : un
client ne peut pas contourner `Content-Length` en envoyant davantage d'octets.

## Corps J7 et calculs d'empreinte

Le schéma JSON Draft 2020-12 local
[`j7-canonical-event-export-v1.schema.json`](../../src/main/resources/schemas/j7-canonical-event-export-v1.schema.json)
est normatif. La validation active les assertions de format, interdit les propriétés inconnues et
ne télécharge jamais de ressource distante. Le document contient exactement la racine
`{manifest,data}`.

Le manifeste contient exactement les champs obligatoires suivants :

```text
exportId, schemaId, schemaVersion, generatedAt, generatorVersion,
selectionMode, dataSha256, sourceSetSha256, validation, sources, warnings
```

Les constantes et contraintes propres à l'import sont :

```text
schemaId=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
schemaVersion=1.0.0
selectionMode=LATEST_AVAILABLE
validation.status=HUMAN_VALIDATED
sources.length=5
data.identity.provider=SOFASCORE
```

Le nœud `data` contient exactement `identity`, `eventState`, `eventDetails`, `statistics`,
`incidents` et `lineups`. Le détail de leurs types, bornes, nullabilités et combinaisons autorisées
est fixé exclusivement par le schéma local. Un état `COHERENCE_CHECKED` ou `REJECTED` n'est jamais
promu par le receiver.

Le JSON est UTF-8 strict, sans BOM, sans clé dupliquée, sans token après la racine, sans retour
chariot et sans espace de présentation. Les octets acceptés sont la sérialisation JSON compacte du
document suivie d'un unique LF (`0x0A`). Cette exigence protège le caractère byte-exact du fichier ;
le receiver ne reformate jamais le corps persistant.

Les empreintes sont recalculées avant toute écriture :

1. `fileSha256 = SHA-256(corps HTTP exact)`, LF terminal inclus ;
2. `dataSha256 = SHA-256(sérialisation JSON compacte du nœud data)`, sans LF ajouté ;
3. `sourceSetSha256 = SHA-256(sérialisation JSON compacte de manifest.sources)`, sans LF ajouté.

Les trois valeurs doivent correspondre à leurs emplacements contractuels. Les comparaisons de hash
sont effectuées en temps constant. Une divergence de schéma, identité, validation ou empreinte est
refusée sans inbox ni outbox.

## Identité et idempotence

La clé complète est ASCII et mesure exactement 111 octets :

```text
^j7:[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}:sha256:[0-9a-f]{64}$
```

`X-J7-Export-Id`, `manifest.exportId` et la composante UUID de la clé sont identiques.
`X-J7-File-SHA256`, le hash recalculé et la composante SHA-256 de la clé sont identiques. Une reprise
manuelle du même artefact doit réutiliser les mêmes octets et la même clé ; il n'existe ni retry
automatique ni clé alternative.

| État durable observé | Effet | Réponse |
|---|---|---|
| Aucune identité connue, requête valide | Créer une inbox, son audit et une seule outbox `J7_IMPORT_ACCEPTED` | `201`, ACK `IMPORTED` |
| Même clé, même export et mêmes hashes | Aucun second import et aucune seconde outbox ; retrouver l'import initial | `200`, ACK `DUPLICATE` |
| Clé, export ou identité déjà connus avec un contenu divergent | Aucun import et aucune outbox | `409`, jamais d'ACK positif |

Les contraintes PostgreSQL sont l'autorité finale sous concurrence. Deux requêtes identiques
concurrentes produisent un unique import et une unique outbox ; l'une peut recevoir `IMPORTED` et
l'autre `DUPLICATE`. Un audit minimal peut consigner chaque tentative authentifiée valide et son
résultat, sans jamais contenir le corps.

Un reçu existant n'est un duplicate post-purge que si son payload exact existe encore ou si un
tombstone de purge qualifié est durable. Si le reçu existe mais que payload et tombstone sont tous
deux absents, l'état est incohérent : le receiver échoue fermé, ne retourne aucun ACK `DUPLICATE` et
n'ajoute aucun audit de duplicate. Cette situation exige une investigation de l'intégrité durable ;
elle ne doit jamais être masquée par un retry ou une nouvelle clé.

## Transaction, inbox et outbox

Pour un premier import, l'inbox, l'audit d'acceptation et l'outbox sont écrits dans une seule
transaction PostgreSQL. Une défaillance de l'une des trois écritures annule les autres et interdit
tout ACK positif. Le contrôleur ne construit la réponse positive qu'après commit.

L'inbox conserve au minimum l'identifiant distant, la clé d'idempotence, l'export UUID, les versions
de protocole et de schéma, les SHA-256, les octets exacts, leur taille, l'empreinte du certificat
client autorisé et les instants de réception/expiration. Les journaux et métriques n'exposent ni le
corps, ni la clé complète, ni les sujets de certificat, ni les stores TLS.

L'événement `J7_IMPORT_ACCEPTED` contient seulement les identifiants, versions, hashes et
horodatages nécessaires pour retrouver l'inbox. Il ne contient aucun octet J7, détail fournisseur,
cookie, token ou secret. INT-001 ne livre aucun consommateur de cet événement et ne modifie jamais le
catalogue ; ce traitement appartient à un futur Work Order.

Si le commit a réussi mais que le client ne reçoit pas l'ACK, la répétition byte-identique retrouve
l'import et retourne `DUPLICATE`. Le receiver ne tente aucun rappel du client.

## ACK positif

Le corps positif contient exactement les sept propriétés suivantes et aucune autre :

```json
{
  "protocolVersion": "1.0",
  "remoteImportId": "22222222-2222-4222-8222-222222222222",
  "status": "IMPORTED",
  "exportId": "11111111-1111-4111-8111-111111111111",
  "fileSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "dataSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "receivedAt": "2026-09-02T00:00:00Z"
}
```

L'exemple est entièrement synthétique. `remoteImportId` est l'UUID canonique de l'inbox durable.
Une réponse `DUPLICATE` réemploie cet UUID et le `receivedAt` initial. `fileSha256`, `dataSha256` et
`exportId` sont strictement corrélés à la requête. Le media type de réponse est
`application/vnd.betting-project.j7-delivery-ack+json;version=1.0`, l'encodage est absent ou
`identity`, et le corps encodé ne dépasse jamais 16 384 octets.

Le schéma autonome
[`j7-delivery-ack-v1.schema.json`](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json)
fixe les formats. La seule paire succès/statut permise est `201/IMPORTED` ou `200/DUPLICATE`. Toute
autre réponse, y compris un `2xx` sans ACK strictement valide, n'est pas un ACK positif.

Le contrôleur remet les sept champs à un codec privé déterministe qui construit les noms camelCase
et sérialise `receivedAt` par la représentation canonique de `Instant`. Il retourne directement les
octets bornés produits par ce codec : une stratégie de nommage, un module de date ou toute autre
personnalisation du mapper Jackson MVC global ne peut donc modifier le contrat positif.

## Refus et erreurs

Les erreurs HTTP utilisent RFC 9457 `ProblemDetail` avec un `code` sûr et stable. `detail` reste
générique ; `instance` exclut la query string. Aucun payload, ACK brut, clé d'idempotence, hash
complet, chemin de store, certificat, SQL, stacktrace ou message d'exception n'est recopié.

| HTTP ou couche | Famille |
|---|---|
| Échec TLS | certificat absent, chaîne non approuvée ou négociation invalide ; aucun HTTP et aucun contrôleur |
| `400` | enveloppe, en-tête, media type, encodage, transfert, longueur, UTF-8, JSON, schéma, statut, identité ou hash invalide |
| `403` | certificat client approuvé par TLS mais expiré, sans EKU `clientAuth` ou absent de l'allowlist |
| `405` | méthode différente de `POST` sur la route exacte |
| `409` | identité durable réutilisée avec un contenu divergent |
| `413` | corps déclaré ou effectivement supérieur à 5 242 880 octets |
| `500` | échec inattendu, persistance ou outbox impossible ; aucun ACK positif et transaction rollbackée |

Une erreur protocolaire détectée avant le cas d'usage ne crée ni inbox, ni audit durable, ni outbox.
Une erreur transactionnelle annule l'inbox, l'audit d'acceptation et l'outbox ensemble. Une réponse
d'erreur n'est jamais sérialisée avec le media type d'ACK.

Les migrations additives `V006__j7_import_inbox.sql`, `V007__j7_import_purge_integrity.sql` et
`V008__j7_import_upgrade_evidence_time_integrity.sql` forment ensemble le schéma courant ; le prochain
slot disponible est `V009`. `V006` crée les quatre tables J7 et réutilise `outbox_message`, déjà
existante ; `V007` renforce, sans réécrire V006, les références et l'ordre de preuve de la purge.
`V008` refuse l'upgrade lorsqu'une preuve historique possède un `purged_at` postérieur à l'horloge
PostgreSQL au moment de la migration. Le chemin de migration qualifié part d'une V006 déjà peuplée :
V007 rétroalimente `payload_expires_at` des tombstones existants depuis leur reçu avant de rendre la
colonne obligatoire et d'ajouter la référence composite, puis V008 contrôle les preuves historiques.
Reçus, audits, outbox et tombstones préexistants valides doivent être conservés sans perte.

## Rétention et purge

La valeur par défaut et la baseline qualifiée d'INT-001 sont 30 jours à partir de `receivedAt`. La
durée est configurable explicitement par l'opérateur dans la plage fermée de 1 à 3 650 jours ; une
valeur hors plage refuse l'activation et aucun composant ne la modifie automatiquement. INT-001
qualifie un cas d'usage applicatif de purge, limité aux imports expirés et exécuté dans une
transaction. Il ne livre aucune route, commande opérateur, tâche planifiée ou autre surface runtime ;
en ouvrir une exige un Work Order séparé. La purge est interdite tant que l'outbox corrélée exacte
n'est pas au statut `DELIVERED`.

Après `V008`, chaque candidat est verrouillé et traité dans l'ordre tombstone, audit
`PAYLOAD_PURGED/RETENTION_EXPIRED`, puis suppression de la seule ligne `j7_import_payload`. La garde
de suppression vérifie que le tombstone et l'audit concordants existent déjà et que la purge ne
précède pas l'expiration ; les cutoffs ou instants de purge futurs sont refusés. Le tombstone conserve
l'identité, les hashes, la taille et les dates de réception/expiration/purge. La purge ne supprime ni
reçu logique, ni audit, ni outbox, ni catalogue, ni projection ; aucune cascade n'est autorisée. Une
répétition post-purge reste donc `DUPLICATE` et une divergence reste `409`.

Dans la configuration locale actuelle, le même rôle propriétaire PostgreSQL exécute Flyway et le
runtime. Les contraintes, triggers et fonctions protègent le chemin DML normal, mais le propriétaire
reste une frontière de confiance capable de modifier ces protections. Cette limite est acceptée
uniquement pour le laboratoire. Avant toute production, un Work Order doit séparer le propriétaire
de migration d'un rôle runtime non propriétaire et à privilèges minimaux, définir et tester les
`GRANT`/`REVOKE`, et interdire au runtime le DDL, la désactivation des triggers et la purge SQL directe.

La procédure de sauvegarde chiffrée et de restauration isolée est définie dans
[`j7-import-backup-restore.md`](../runbooks/j7-import-backup-restore.md). Elle envoie le dump complet
directement vers `age --passphrase` et restaure directement dans PostgreSQL 17, sans dump clair
intermédiaire. Une sauvegarde ne modifie pas la politique de rétention et ne constitue pas une
autorisation de conservation indéfinie. Cette preuve cryptographique réelle reste `NOT_EXECUTED`
tant qu'un rapport de qualification synthétique ne l'atteste pas.

## Compatibilité

Toute modification incompatible de méthode, route, media type, limite, schéma, hash, idempotence,
ACK ou sémantique de statut exige une nouvelle version de contrat et un Work Order. Ajouter un
receiver distant, une seconde interface, un reverse proxy, un sender, un retry, un scheduler, un
consommateur d'enrichissement ou une dépendance au SofaScore Local Lab n'est pas une évolution
compatible de v1.0.
