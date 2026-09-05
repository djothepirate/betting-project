# Exploiter et qualifier localement le receiver J7 v1

## Objet et limite d'emploi

Ce runbook décrit la configuration locale et la qualification synthétique d'INT-001. Il ne constitue
pas une autorisation d'envoyer un export réel, de joindre le SofaScore Local Lab, d'appeler SofaScore,
d'ouvrir un listener hors loopback, de déployer sur un VPS ou de passer en production.

```text
RUNBOOK_SCOPE=LOCAL_SYNTHETIC_QUALIFICATION_ONLY
RECEIVER_DEFAULT=DISABLED
REAL_J7_EXPORT_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LOCAL_LAB_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Le contrat est [`j7-import-receiver-v1`](../contracts/j7-import-receiver-v1.md) et le modèle de
menace est
[`j7-import-receiver-threat-model.md`](../security/j7-import-receiver-threat-model.md). Une
qualification ne doit jamais assouplir une garde pour devenir verte.

## Deux modes exclusifs du `control-api`

| Receiver J7 | Connecteur du profil | Effet |
|---|---|---|
| Désactivé, valeur par défaut | `http://127.0.0.1:8080` | Routes historiques locales ; route J7 non chargée |
| Activé explicitement | `https://127.0.0.1:8444`, client-auth `NEED` | Routes J7, catalogue, bootstrap et Actuator sur le même connecteur mTLS |

Il n'existe pas de port Actuator séparé ni de listener HTTP secondaire. Un outil qui utilisait les
routes historiques sur 8080 doit être reconfiguré pour le même mTLS pendant une qualification J7.
Ne pas ajouter de reverse proxy ou de redirection. `server.servlet.context-path`,
`spring.mvc.servlet.path` et toute propriété `management.server.*` créant un connecteur séparé doivent
rester absents ou vides.

## Configuration versionnée

Le préfixe Spring est `betting.integration.j7-receiver` :

| Propriété | Variable | Valeur qualifiée |
|---|---|---|
| `enabled` | `BETTING_J7_RECEIVER_ENABLED` | `false` par défaut ; `true` seulement pendant une qualification autorisée |
| `retention-days` | `BETTING_J7_RETENTION_DAYS` | défaut/baseline `30`, configuration explicite admise de `1` à `3650` |
| `max-request-bytes` | non surchargeable | `5242880` |
| `max-ack-bytes` | non surchargeable | `16384` |
| `client-certificate-sha256-allowlist` | `BETTING_J7_CLIENT_CERTIFICATE_SHA256_ALLOWLIST` | vide à l'arrêt ; liste non vide de SHA-256 minuscules à l'activation |

Le port est fourni par `BETTING_CONTROL_API_PORT`. La compression serveur reste explicitement
désactivée. Les stores TLS utilisent
`BETTING_J7_KEY_STORE`, `BETTING_J7_KEY_STORE_PASSWORD`, `BETTING_J7_KEY_STORE_TYPE`,
`BETTING_J7_TRUST_STORE`, `BETTING_J7_TRUST_STORE_PASSWORD` et
`BETTING_J7_TRUST_STORE_TYPE`. Leurs emplacements doivent être des chemins locaux absolus hors Git ou
des URI `file:` locales absolues sans autorité. Chemins relatifs, classpath, HTTP(S), partages UNC,
SSL bundle, matériel PEM alternatif et bundles SNI sont refusés. Un lecteur Windows mappé peut garder
une syntaxe absolue tout en visant un partage : son caractère réellement local reste une vérification
opérateur obligatoire et toute ambiguïté interdit l'activation.

`.env.example` est une nomenclature, pas un magasin de secrets. `docker compose --env-file .env`
emploie `.env` pour PostgreSQL, mais Maven/Spring ne charge pas implicitement ce fichier dans
l'environnement du processus. Injecter les valeurs J7 par la configuration locale protégée de
l'IDE, du shell ou du gestionnaire de secrets. Ne jamais copier un mot de passe, une clé privée, un
certificat ou une empreinte réelle dans Git, un ticket, une capture ou l'historique du terminal.

## Préconditions

1. utiliser Java 25, Maven Wrapper et PostgreSQL 17 ;
2. travailler dans le worktree INT-001 sans modification Eclipse concurrente ;
3. employer uniquement une enveloppe J7 synthétique et une PKI éphémère synthétique ;
4. conserver les fichiers de stores hors dépôt, dans un répertoire local protégé, non UNC et non
   fourni par un lecteur réseau mappé ;
5. vérifier que `.env` est ignoré et ne contient aucune donnée J7 ;
6. ne démarrer aucun sender, Playwright, poller, scheduler ou outil fournisseur ;
7. ne configurer aucune URI, IP LAN, IP publique, cible Local Lab ou VPS ;
8. obtenir une autorisation propriétaire distincte avant tout démarrage manuel du receiver, même
   sur loopback ;
9. laisser tout préfixe de contexte/servlet et tout port de management séparé désactivés.

Les tests automatisés doivent générer leurs certificats et données dans un répertoire temporaire
qu'ils possèdent, puis les supprimer en succès comme en échec. Les stores manuels ne sont jamais
réutilisés comme fixtures de test.

## Préparer PostgreSQL sur loopback

Copier `.env.example` vers `.env`, remplacer uniquement le mot de passe PostgreSQL local, puis
valider la résolution Compose avant de démarrer le service :

```powershell
docker compose --env-file .env config
docker compose --env-file .env up -d postgres
docker compose --env-file .env ps
```

La configuration rendue doit contenir exactement le bind hôte
`127.0.0.1:${BETTING_DB_PORT:-5433}:5432`. Avec l'exemple, l'application joint
`jdbc:postgresql://127.0.0.1:5433/betting`. Arrêter si Compose rend `0.0.0.0`, `::`, une IP LAN ou le
port hôte 5432 par défaut historique.

## Vérifier le mode désactivé

Sans définir de valeur J7, lancer les tests de profil et vérifier la configuration effective :

```powershell
git status --short --branch
docker compose --env-file .env config
.\mvnw.cmd verify
```

Résultat attendu : `BETTING_J7_RECEIVER_ENABLED` vaut conceptuellement `false`, aucun store TLS ou
allowlist n'est requis, la route J7 n'est pas chargée et le profil `replay` reste sans DataSource,
Flyway ou serveur J7. Le fait que le `control-api` historique puisse écouter sur HTTP/8080 loopback
ne vaut aucune autorisation de l'exposer.

## Activer uniquement pour une qualification synthétique

Préparer, hors dépôt, un keystore serveur, un truststore d'autorité cliente et l'empreinte SHA-256
du certificat feuille client synthétique. Ce certificat doit être en cours de validité et porter
explicitement l'EKU `clientAuth` (`1.3.6.1.5.5.7.3.2`). Les mots de passe sont lus depuis un mécanisme local
protégé ; les exemples suivants montrent seulement les valeurs non secrètes :

```powershell
$env:BETTING_J7_RECEIVER_ENABLED = "true"
$env:BETTING_CONTROL_API_PORT = "8444"
$env:BETTING_J7_RETENTION_DAYS = "30"
$env:BETTING_J7_CLIENT_CERTIFICATE_SHA256_ALLOWLIST = "<64-hex-lowercase-synthetic-fingerprint>"
$env:BETTING_J7_KEY_STORE_TYPE = "PKCS12"
$env:BETTING_J7_TRUST_STORE_TYPE = "PKCS12"
```

Définir ensuite les deux chemins de stores et leurs mots de passe dans l'environnement protégé du
processus, sans les afficher. Ne pas placer de secret en argument de ligne de commande. Avec toutes
les préconditions satisfaites, le démarrage local explicite est :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"
```

La garde doit interrompre le démarrage si l'adresse n'est pas `127.0.0.1`, si le port n'est pas
`8444`, si SSL/client-auth/stores/allowlist manquent, si la compression n'est pas explicitement
désactivée, si une empreinte est invalide, si la rétention sort de la plage `1..3650`, si les bornes
diffèrent du contrat, si un préfixe de contexte/servlet est défini ou si le management tente
d'utiliser un connecteur distinct. Elle refuse aussi les stores non locaux ou non absolus, les
alternatives TLS bundle/PEM/SNI, une URL PostgreSQL autre que
`jdbc:postgresql://127.0.0.1:5433/<base>` et les substitutions DataSource/JNDI/Hikari/Flyway. La phase
précoce refuse ces propriétés avant création de la base ou de Flyway ; la garde bean répète les
contrôles puis compare les `JdbcConnectionDetails` effectifs et le `HikariDataSource` assemblé. Une
DataSource non Hikari ou un driver non PostgreSQL échoue fermé. Modifier la durée exige une valeur
opérateur explicite ; aucun composant ne l'ajuste automatiquement. Ne jamais corriger un échec en
passant à HTTP, `want`, trust-all, une allowlist vide ou une interface plus large.

L'ordre de fermeture fait partie de la preuve. `J7ReceiverEarlyWebServerGuard` intervient comme
`WebServerFactoryCustomizer` à priorité maximale et contrôle la configuration statique avant la
résolution des key/trust stores par Spring Boot. Le test avec le schéma synthétique interdit
`j7sentinel:` doit échouer sur le chemin avant toute résolution et conserver
`resolutionCount=0`. Cette phase refuse également toute propriété statique de base ou de Flyway hors
frontière avant initialisation. `J7ReceiverActivationGuard`, exécuté ensuite comme bean, répète les
contrôles statiques et inspecte les `JdbcConnectionDetails` et le `HikariDataSource` réellement
assemblés.
Ne pas déplacer le contrôle des emplacements TLS après la construction du serveur pour simplifier un
test ou un démarrage local.

Dans un second terminal, vérifier seulement l'écoute locale :

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8444 |
    Select-Object LocalAddress, LocalPort, OwningProcess
```

La seule adresse admise est `127.0.0.1`. L'absence de certificat client doit échouer pendant TLS et
ne doit produire aucun accès contrôleur. L'accès nominal aux endpoints, Actuator compris, utilise la
PKI synthétique du harness qualifié ; ce runbook ne fournit volontairement aucune commande qui
embarquerait un mot de passe ou un export dans l'historique du shell.

## Qualification du contrat

La suite automatisée doit couvrir avec des identités exclusivement synthétiques :

- chemin brut exact sans préfixe ; refus du slash final, du segment supplémentaire, de la query, des
  paramètres matrix et des alias percent-encodés ;
- corps de 1 octet, de 5 242 880 octets et dépassement d'un octet ;
- `Content-Length` absent, dupliqué, invalide, incohérent et flux réellement trop long ;
- compression et `Transfer-Encoding` refusés ;
- UTF-8, clé dupliquée, token final, mise en forme non canonique et schéma strict ;
- `fileSha256`, `dataSha256`, `sourceSetSha256`, UUID, schema ID/version et statut ;
- première transaction `201/IMPORTED` avec une inbox, un audit et une outbox ;
- répétition exacte `200/DUPLICATE` sans seconde inbox/outbox ;
- reçu existant sans payload ni tombstone qualifié : échec fermé, sans faux `DUPLICATE`, ACK positif
  ou audit de duplicate ;
- divergence `409`, y compris sous clés/export/hashes croisés ;
- deux requêtes identiques concurrentes avec un seul effet durable ;
- panne d'outbox provoquant le rollback et aucun ACK positif ;
- ACK exact à sept champs et borne de 16 384 octets, produit par le codec privé déterministe même si
  le mapper Jackson MVC global impose une stratégie de nommage ou de date hostile ;
- mTLS nominal, client absent, chaîne non approuvée, certificat expiré, mauvaise EKU et certificat
  approuvé mais non allowlisté ;
- refus par la garde web précoce d'un schéma TLS interdit avec zéro appel au resolver sentinelle, et
  des propriétés DataSource/Flyway hors frontière avant initialisation, puis inspection JDBC effective
  par la garde bean ;
- absence des composants J7 sous `batch-worker` et `replay` ;
- application réussie des migrations additives `V006`, `V007` puis `V008`, y compris l'upgrade
  d'une V006 peuplée avec conservation et rétroalimentation des tombstones existants, et refus par
  V008 d'une preuve historique dont `purged_at` est encore futur ;
- zéro appel fournisseur, Local Lab, VPS ou réseau non loopback.

Les payloads d'échec, ACK bruts, clés d'idempotence et détails TLS ne sont jamais copiés dans les
rapports. Conserver uniquement compteurs, catégories sûres, UUID/hashes synthétiques et résultats.

## Diagnostic fermé

| Observation | Action sûre |
|---|---|
| Échec de démarrage de la garde | Corriger la valeur locale attendue ; ne pas neutraliser la garde |
| Aucun certificat client | Conserver l'échec TLS attendu ; ne pas activer `want` |
| Chaîne approuvée, empreinte refusée | Vérifier le certificat synthétique et l'allowlist hors logs ; ne pas élargir la confiance |
| `400` | Vérifier enveloppe, en-têtes, media type, encodage, longueur, UTF-8, JSON, schéma, statut, identités et hashes |
| `405` | Employer uniquement `POST` sur la route exacte |
| `409` | Ne pas changer la clé ; réconcilier l'identité et conserver les mêmes octets |
| `413` | Ne pas augmenter la limite ; réduire uniquement la fixture synthétique |
| `500` ou ACK perdu | Aucun ACK positif ; transaction rollbackée en cas d'échec serveur, résultat client à réconcilier avant toute répétition manuelle |

Une répétition manuelle autorisée réutilise strictement les mêmes octets et la même clé. Elle attend
`200/DUPLICATE` si le premier commit avait réussi. Une nouvelle clé masquerait une ambiguïté et est
interdite.

## Rétention et purge

La valeur par défaut et la baseline de qualification sont 30 jours depuis `receivedAt`. Une valeur
explicite de 1 à 3 650 jours est admise ; aucune modification automatique n'existe. La purge est
transactionnelle et limitée aux octets expirés dont l'outbox corrélée exacte est `DELIVERED`. Après
`V008`, elle écrit d'abord le tombstone et l'audit concordants, puis supprime seulement la ligne de
payload brut ; les cutoffs futurs sont refusés. Elle conserve le reçu, le tombstone, l'identité, les
hashes et les audits ; elle ne cascade vers aucune autre donnée.

INT-001 livre uniquement le cas d'usage applicatif transactionnel pour sa qualification ; il n'est
pas activable par une route, une commande runtime, un scheduler ou un SQL opérateur. Les tests
PostgreSQL invoquent le service avec une horloge contrôlée pour prouver la sélection à J+30, la
transaction et l'idempotence de purge. Ne pas contourner cette frontière par une mise à jour SQL
directe. Ouvrir une surface administrative bornée exige un Work Order séparé.

Le rôle `BETTING_DB_USER` local initialise la base, exécute Flyway et sert au runtime. Il est donc
propriétaire et reste une frontière de confiance : les triggers ne protègent pas contre ce rôle s'il
est compromis ou mal utilisé. Cette simplification n'est admissible que pour la qualification locale.
Avant toute production, un Work Order doit livrer et tester un owner de migration distinct et un rôle
runtime non propriétaire à privilèges minimaux, sans DDL, désactivation de triggers ni purge SQL
directe ; cette séparation n'est pas encore implémentée ni qualifiée.

## Arrêt et nettoyage

1. arrêter normalement le processus Spring ;
2. vérifier qu'aucun listener 8444 ne subsiste ;
3. effacer du processus les variables J7 et TLS ;
4. supprimer uniquement les fichiers synthétiques temporaires créés pour cette qualification ;
5. ne jamais supprimer le volume PostgreSQL sans une décision explicite et une sauvegarde qualifiée ;
6. arrêter PostgreSQL si nécessaire avec `docker compose --env-file .env down` ;
7. relancer le contrôle de secrets et inspecter les fichiers non suivis.

Ne pas utiliser `down -v`, supprimer un répertoire large ou effacer un store qui n'appartient pas à
la qualification.

## Vérifications avant revue

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration verify
.\scripts\verify-windows.cmd
.\scripts\check-no-secrets.cmd
docker compose --env-file .env config
git diff --check
```

La validation doit aussi inclure le runbook de
[`sauvegarde/restauration`](j7-import-backup-restore.md). Aucune commande verte n'autorise commit,
push, livraison réelle ou exposition réseau sans les décisions humaines prévues.

À la date de cette réconciliation documentaire, aucun dump, chiffrement `age` ou restore réel n'a été
exécuté : `INT001_ENCRYPTED_RESTORE=NOT_EXECUTED` reste obligatoire jusqu'à une preuve séparée.

Bloc de résultat minimal :

```text
INT001_RECEIVER_DEFAULT_DISABLED=<PASS|FAIL>
INT001_LOOPBACK_8444_ONLY=<PASS|FAIL|NOT_EXECUTED>
INT001_MTLS_NEED=<PASS|FAIL|NOT_EXECUTED>
INT001_EXACT_IMPORT=<PASS|FAIL|NOT_EXECUTED>
INT001_IDEMPOTENCY=<PASS|FAIL|NOT_EXECUTED>
INT001_PURGE_30_DAYS=<PASS|FAIL|NOT_EXECUTED>
INT001_ENCRYPTED_RESTORE=<PASS|FAIL|NOT_EXECUTED>
INT001_PROVIDER_CALLS=0
INT001_LOCAL_LAB_CALLS=0
INT001_REAL_RECEIVER_CALLS=0
INT001_REAL_DATA_USED=NO
```
