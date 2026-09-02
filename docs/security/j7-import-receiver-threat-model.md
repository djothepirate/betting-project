# Modèle de menace — receiver local J7 v1

## Statut et décision de sécurité

Ce modèle couvre uniquement INT-001 et le contrat
[`j7-import-receiver-v1`](../contracts/j7-import-receiver-v1.md). Le receiver est un adaptateur
entrant local, optionnel et non critique. Il est désactivé par défaut et ne constitue pas une porte
d'autorisation vers SofaScore, le SofaScore Local Lab, un VPS ou Internet.

```text
THREAT_MODEL_VERSION=1.0
RECEIVER_DEFAULT=DISABLED
LISTEN_ADDRESS=127.0.0.1
ENABLED_PORT=8444
TLS_REQUIRED_WHEN_ENABLED=YES
CLIENT_AUTH=NEED
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## Actifs à protéger

- les octets J7 importés, leurs hashes et leur provenance ;
- l'unicité de l'inbox et de l'événement `J7_IMPORT_ACCEPTED` ;
- les données existantes du catalogue et la chaîne additive de migrations V001 à V008 ;
- les clés privées, mots de passe de stores et autorités de confiance locales ;
- l'identité du certificat client autorisé ;
- PostgreSQL et ses sauvegardes ;
- les routes historiques du `control-api` et les endpoints Actuator ;
- la preuve qu'aucun appel fournisseur, callback ou retry n'est déclenché.

Le payload, les clés privées et les mots de passe sont confidentiels. Les identités, hashes,
horodatages, audits et ACK doivent en plus conserver leur intégrité. La disponibilité est secondaire :
une fermeture sûre est préférable à un import ambigu.

## Frontières de confiance

```text
[processus client explicitement autorisé]
        |
        | HTTPS mTLS sur loopback, certificat client NEED
        v
[connecteur Tomcat unique : 127.0.0.1:8444]
        |
        | garde d'activation + allowlist empreinte feuille
        v
[adaptateur web J7 : limite, en-têtes, JSON, schéma, hashes]
        |
        | commande validée sans type HTTP
        v
[service d'application + transaction PostgreSQL]
        |
        +--> [inbox exacte + audit]
        +--> [outbox minimisée, sans consommateur INT-001]
```

Le connecteur est partagé : lors de l'activation, les routes J7, `/internal/catalog`,
`/internal/bootstrap` et `/actuator` passent toutes sous HTTPS avec certificat client obligatoire.
Il n'existe ni second port de management, ni listener HTTP, ni reverse proxy autorisé. Lorsque le
receiver est désactivé, la route J7 n'est pas chargée et le `control-api` historique reste seulement
sur HTTP/8080 loopback.

Le chemin HTTP fourni par le client traverse une seconde frontière : il doit être exactement la route
contractuelle brute, sans contexte/servlet, query, slash final, segment, paramètre matrix ou
percent-encodage alternatif. Une normalisation du conteneur ne doit jamais transformer un alias en
route autorisée.

La configuration d'activation traverse deux phases ordonnées. Le
`J7ReceiverEarlyWebServerGuard`, `WebServerFactoryCustomizer` de priorité maximale, refuse les
valeurs statiques dangereuses avant que Spring Boot puisse résoudre les ressources TLS ou créer la
base et Flyway ; une sentinelle de test prouve zéro résolution pour un schéma interdit. Le bean
`J7ReceiverActivationGuard` répète ensuite ces contrôles et inspecte les `JdbcConnectionDetails` et
le `HikariDataSource` effectifs. Une ressource TLS ou une base distante ne doit donc jamais être
touchée dans l'intervalle entre binding et garde bean.

La base locale possède une frontière de confiance explicite : `BETTING_DB_USER` est actuellement le
rôle propriétaire, Flyway et runtime. Les protections V006/V007/V008 bornent le DML normal et
l'upgrade, mais ne
résistent pas à ce propriétaire, capable de modifier ou désactiver fonctions et triggers. Cette
confiance est acceptée seulement pour le laboratoire ; elle ne constitue pas une séparation de rôles.

## Acteurs et hypothèses

L'acteur légitime est l'unique opérateur local qui possède une configuration explicitement activée,
une PKI locale qualifiée et des données synthétiques pour les tests. Les acteurs hostiles considérés
sont un processus local non autorisé, un client avec certificat non approuvé, un client avec chaîne
approuvée mais identité non allowlistée, un payload malformé ou surdimensionné, une répétition
concurrente et un opérateur commettant une erreur de configuration.

Une compromission complète du poste ou du compte utilisateur peut lire la mémoire du processus et
sort du périmètre de défense de l'application. Elle ne justifie aucun assouplissement : stores et
sauvegardes restent hors Git, sous contrôle du système d'exploitation, avec privilèges minimaux.

## Menaces, contrôles et résultat attendu

| Menace | Contrôles obligatoires | Résultat attendu |
|---|---|---|
| Activation involontaire | Flag `false` par défaut ; aucune valeur TLS réelle dans Git ; garde de démarrage | Route absente et aucun listener 8444 |
| Écoute LAN ou publique | `server.address=127.0.0.1` immuable sous activation ; garde contre toute autre adresse ; Compose lié à loopback | Aucun socket sur `0.0.0.0`, `::`, IP LAN ou publique |
| Port, compression ou transport de repli | Port activé exactement `8444`, SSL obligatoire, compression serveur explicitement désactivée, connecteur unique, aucun redirect/reverse proxy | Échec de démarrage, jamais HTTP dégradé ni ACK compressé |
| Client sans certificat ou chaîne hostile | `server.ssl.client-auth=need`, truststore local dédié, aucune confiance globale ajoutée | Échec pendant le handshake, contrôleur non invoqué |
| Certificat signé mais non autorisé | Validité courante, EKU `clientAuth` explicite et SHA-256 du DER du certificat feuille comparé à l'allowlist non vide | `403` avant parsing/persistance |
| Résolution précoce d'un store hostile | `J7ReceiverEarlyWebServerGuard` de priorité maximale avant résolution TLS ; test du schéma sentinelle avec compteur nul | Échec sur la configuration, zéro déréférencement |
| Trust-all, substitution ou désactivation TLS | Stores locaux absolus non UNC ; aucun SSL bundle, PEM alternatif ou bundle SNI ; aucun flag de bypass ; tests négatifs de chaîne | Échec fermé avant écoute |
| Exposition d'Actuator ou des routes historiques | Un seul connecteur et aucun port management séparé | Toutes les routes exigent le même mTLS lorsqu'INT-001 est actif |
| Substitution de la base loopback | Propriété JDBC et substitutions DataSource/JNDI/Hikari/Flyway refusées par la garde précoce avant initialisation ; `JdbcConnectionDetails` et `HikariDataSource` effectifs inspectés par la garde bean | Échec de démarrage avant connexion hors frontière ou import |
| Alias de route après normalisation | Chemin brut exact ; aucun contexte/servlet ; query, slash final, segment, matrix et percent-encoding alternatifs refusés | `400` ou rejet conteneur avant contrôleur, jamais un import |
| Rejeu byte-identique | Clé canonique dérivée de l'UUID et du hash ; contraintes uniques PostgreSQL | `200/DUPLICATE`, aucun second effet |
| Réutilisation divergente d'une identité | Corrélation clé/export/hash ; comparaison du contenu durable | `409`, aucune inbox/outbox supplémentaire |
| Payload disparu sans preuve de purge | Un duplicate exige le payload exact ou un tombstone qualifié | Échec fermé, aucun faux `DUPLICATE`, ACK positif ou audit de duplicate |
| Course entre requêtes identiques | Contraintes uniques et décision dans une transaction | Une inbox et une outbox au total |
| ACK émis avant durabilité | Inbox, audit et outbox dans la même transaction ; ACK après commit | Aucun ACK positif si le commit échoue |
| ACK modifié par la configuration Jackson globale | Codec privé déterministe produisant directement les sept champs camelCase et l'`Instant` canonique sous la borne de 16 Kio | Même ACK contractuel quelle que soit la stratégie MVC globale |
| Échec partiel de l'outbox | Atomicité transactionnelle | Rollback de l'inbox et de l'audit d'acceptation |
| Request smuggling ou chunking | `Content-Length` unique obligatoire ; `Transfer-Encoding` interdit ; longueur réelle comparée | Refus avant cas d'usage |
| Décompression explosive | `Content-Encoding` absent ou `identity` ; aucune décompression | Refus `400` |
| Corps surdimensionné ou sans borne | limite `5 242 880` appliquée au header et au flux avant allocation non bornée | `413`, aucune écriture |
| Ambiguïté JSON | UTF-8 strict ; pas de BOM/CR ; clés dupliquées et tokens finaux interdits ; octets compacts + LF | `400`, aucune écriture |
| Schéma contourné | Draft 2020-12, formats assertifs, propriétés inconnues interdites, résolution distante coupée | `400`, aucune écriture ni accès réseau |
| Substitution de contenu | Recalcul `fileSha256`, `dataSha256`, `sourceSetSha256` et corrélation manifeste/en-têtes | Refus avant persistance |
| Promotion d'un export non validé | Seul `HUMAN_VALIDATED` est accepté ; aucune mutation du statut | `400`, état source inchangé |
| Injection SQL | Ports applicatifs typés, SQL statique et paramètres liés dans l'adaptateur PostgreSQL | Aucune donnée contrôlée utilisée comme SQL |
| Fuite par logs ou erreurs | `ProblemDetail` générique ; aucune donnée brute, clé, hash complet, certificat ou message d'exception | Métadonnées minimales uniquement |
| Fuite par outbox | Événement minimisé qui référence l'inbox sans embarquer les octets | Aucun payload J7 dans `outbox_message.payload` |
| Purge destructive ou prématurée | V006+V007+V008 additives ; rétention bornée `1..3650`, défaut 30 jours, outbox `DELIVERED`, timestamps non futurs, tombstone et audit écrits avant le delete ; V008 refuse les preuves historiques future-dated à l'upgrade | Seule la ligne de payload expiré est retirée |
| Contournement par le rôle DB local | Propriétaire explicitement dans la frontière de confiance ; aucune prétention d'isolation contre lui | Limite acceptée localement, séparation owner/runtime obligatoire avant production |
| Sauvegarde en clair | `pg_dump` custom complet directement vers `age --passphrase`, secret au terminal natif, restauration directe synthétique isolée | Aucun dump clair ni restauration dans la base active |
| Appel fournisseur ou callback | Aucun client HTTP sortant, URI, sender, scheduler, poller ou consommateur dans INT-001 | Zéro appel SofaScore/Local Lab/VPS |
| Dépendance cachée au Local Lab | Schémas copiés et versionnés localement ; aucune dépendance Maven, chemin ou appel inter-dépôt | Build et runtime autonomes |
| Donnée réelle dans tests/Git | Fixtures et PKI générées synthétiquement ; contrôles de secrets et revue du diff | Aucun identifiant ou payload fournisseur réel |

## Configuration TLS et identité cliente

Les secrets sont injectés uniquement dans l'environnement du processus ou par un mécanisme local de
secrets approuvé. `.env.example` documente les noms, jamais les valeurs réelles. Les variables sont :

- `BETTING_J7_KEY_STORE`, `BETTING_J7_KEY_STORE_PASSWORD`, `BETTING_J7_KEY_STORE_TYPE` ;
- `BETTING_J7_TRUST_STORE`, `BETTING_J7_TRUST_STORE_PASSWORD`, `BETTING_J7_TRUST_STORE_TYPE` ;
- `BETTING_J7_CLIENT_CERTIFICATE_SHA256_ALLOWLIST`.

Les deux emplacements de stores sont des chemins locaux absolus hors Git ou des URI `file:` locales
absolues sans autorité. Les chemins relatifs, classpath, HTTP(S) et UNC sont refusés, comme les
variantes SSL bundle, PEM et SNI. Une lettre de lecteur Windows mappée peut masquer un partage malgré
sa syntaxe absolue : l'opérateur doit en vérifier le caractère réellement local avant activation ;
la garde ne transforme pas cette limite du système d'exploitation en présomption favorable.

L'allowlist contient des SHA-256 hexadécimaux minuscules du certificat feuille encodé DER, séparés
par des virgules. Une entrée vide, malformée ou dupliquée est une erreur de configuration. Elle ne
remplace pas la validation de chaîne TLS : truststore et allowlist doivent réussir. Le sujet, le
serial et l'issuer ne servent pas seuls d'identité.

Pour une rotation locale, l'opérateur qualifie d'abord le nouveau certificat, ajoute temporairement
les deux empreintes, redémarre et vérifie la négociation synthétique, puis retire l'ancienne empreinte
et l'ancien certificat. La durée de recouvrement est bornée par l'intervention. Aucune clé privée,
CSR, archive PKCS#12 ou empreinte réelle n'est ajoutée aux documents, rapports ou commits.

INT-001 ne définit aucun profil de certificat de production, aucune révocation distante, aucun ACME
et aucune autorité partagée. Ces sujets exigent une décision distincte avant toute cible réelle.

## Rétention, purge et sauvegardes

Le payload exact est conservé 30 jours par défaut à partir de l'instant de réception durable. Une
durée de 1 à 3 650 jours peut être fixée uniquement par configuration opérateur explicite ; aucun
automatisme ne la change. INT-001 qualifie la purge via le cas d'usage applicatif, sans route,
commande runtime, scheduler ni requête SQL manuelle. Toute surface opérateur future exige un Work
Order séparé. Sont invariants : import expiré, outbox corrélée déjà traitée,
verrou/transaction, tombstone et audit écrits avant retrait des octets, aucune cascade vers le
catalogue ou les journaux. `V006` crée les quatre tables J7, `V007`, additive et corrective, renforce
les références et la garde de suppression, puis `V008` refuse à l'upgrade toute preuve historique
dont l'instant de purge est encore futur ; l'outbox préexistante est la cinquième table de preuve du
backup. Le prochain slot disponible est `V009`.

Les backups peuvent contenir des octets non encore purgés. Ils sont donc classés au même niveau de
confidentialité que l'inbox, chiffrés dans le flux avant toute conservation et soumis à une durée
explicite. Une preuve de restauration utilise une base PostgreSQL 17 isolée, vide et des données
synthétiques. Aucune preuve cryptographique réelle n'a été exécutée pendant cette réconciliation ;
son résultat reste `NOT_EXECUTED`. Voir
[`j7-import-backup-restore.md`](../runbooks/j7-import-backup-restore.md).

## Risques résiduels acceptés pour la qualification locale

- Un processus exécuté sous un compte local compromis peut tenter d'accéder aux stores ou à la base.
- Le receiver peut subir un déni de service local par de nombreuses requêtes sous la limite ; aucune
  disponibilité critique ne dépend de lui et l'arrêt reste le repli.
- Le truststore local ne fournit pas à lui seul une politique de révocation distante. L'allowlist
  réduit la surface, mais une politique PKI réelle reste hors périmètre.
- Une sauvegarde chiffrée dépend de la protection de la clé opérateur et de l'outil local choisi.
- Le rôle PostgreSQL local propriétaire peut contourner les triggers. Avant toute production, il
  faut concevoir et tester des `GRANT`/`REVOKE`, séparer l'owner de migration du runtime non
  propriétaire et interdire à ce dernier DDL, désactivation de triggers et purge SQL directe.
- Activer le receiver protège aussi les routes historiques par mTLS et peut interrompre des outils
  HTTP locaux anciens ; c'est un effet explicite du connecteur unique, pas un défaut à contourner.

Ces risques ne valent pas autorisation de production ou de livraison réelle.

## Preuves minimales avant clôture

La qualification doit démontrer, avec PKI et données éphémères synthétiques :

1. flag par défaut désactivé et route non chargée ;
2. refus au démarrage pour adresse, port, compression, SSL, client-auth, stores, allowlist ou bornes
   invalides, et pour toute substitution TLS ou JDBC dédiée ; la sentinelle de schéma TLS interdit
   reste à zéro résolution avant l'inspection JDBC effective ;
3. listener activé uniquement sur `127.0.0.1:8444` ;
4. succès avec chaîne et empreinte attendues ;
5. échec TLS sans certificat et avec chaîne non approuvée, avant contrôleur ;
6. refus avant parsing avec chaîne approuvée mais certificat expiré, EKU autre que `clientAuth` ou empreinte non allowlistée ;
7. chemin brut exact et refus des alias, limite du flux, absence de compression/chunking et JSON strict ;
8. `201/IMPORTED`, `200/DUPLICATE`, `409` divergent et concurrence ;
9. rollback si l'outbox échoue et ACK privé déterministe indépendant du Jackson MVC global ;
10. migrations additives V006+V007+V008, refus à l'upgrade d'une preuve historique future-dated et
    purge à la baseline de 30 jours avec tombstone/audit préalables et protection de l'outbox non
    `DELIVERED` ;
11. backup chiffré et restauration PostgreSQL 17 isolée ;
12. zéro appel fournisseur, Local Lab, VPS ou autre réseau réel ;
13. zéro secret, certificat, clé privée ou payload réel dans Git et les rapports.

Un échec de l'une de ces preuves maintient INT-001 non qualifié et le receiver désactivé.
