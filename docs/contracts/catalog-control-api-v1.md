# Contrat interne `catalog-control-api-v1`

## Statut et frontière de sécurité

Ce document définit le contrat HTTP interne livré par le lot 7 de CAT-002. Les routes sont disponibles uniquement sous le profil `control-api`, avec le préfixe non versionné `/internal/catalog`. La version normative du contrat est `catalog-control-api-v1`.

Cette API est un outil local d'exploitation pour l'unique opérateur du projet. Elle n'est ni authentifiée ni destinée à être exposée, publiée ou placée derrière un reverse proxy. Le profil `control-api` écoute sur `127.0.0.1`. L'authentification, l'autorisation et l'exposition réseau appartiennent à OPS-001.

Les règles suivantes sont invariantes :

- aucune route ne retourne les octets de `raw_snapshot.payload` ;
- aucun reçu, clé ou digest d'idempotence n'est exposé par les lectures ;
- aucune commande n'accepte un chemin, une URI ou un fichier ;
- aucun nom de colonne, ordre SQL ou expression de filtre n'est fourni par le client ;
- les lectures restent disponibles sans identité opérateur ;
- les décisions humaines utilisent exclusivement `BETTING_OPERATOR_ID` via `OperatorIdentityProvider` ;
- aucun appel fournisseur, worker, poller ou ordonnanceur n'est déclenché par ce contrat.

## Routes

| Méthode | Route | Fonction |
|---|---|---|
| `GET` | `/internal/catalog/anomalies` | Lister les anomalies avec pagination keyset et provenance sûre |
| `GET` | `/internal/catalog/anomalies/{id}` | Lire une anomalie |
| `GET` | `/internal/catalog/anomalies/{id}/events` | Lister son historique `OPENED`, `OBSERVED`, `RESOLVED`, `REOPENED` |
| `GET` | `/internal/catalog/provider-mappings` | Lister les mappings courants |
| `GET` | `/internal/catalog/provider-mappings/{id}` | Lire un mapping courant |
| `GET` | `/internal/catalog/mapping-decisions` | Lister les décisions humaines, avec filtre optionnel par mapping |
| `GET` | `/internal/catalog/mapping-decisions/{id}` | Lire une décision humaine |
| `GET` | `/internal/catalog/mapping-decisions/{id}/anomalies` | Lister les anomalies corrélées à une décision |
| `POST` | `/internal/catalog/mapping-decisions` | Confirmer ou rejeter un mapping |
| `GET` | `/internal/catalog/replay-requests` | Lister les demandes durables de rejeu |
| `GET` | `/internal/catalog/replay-requests/{id}` | Lire l'état d'une demande |
| `GET` | `/internal/catalog/replay-requests/{id}/attempts` | Lister ses tentatives |
| `GET` | `/internal/catalog/replay-requests/{requestId}/attempts/{attemptId}/applications` | Lister les applications canoniques corrélées |
| `GET` | `/internal/catalog/replay-requests/{requestId}/attempts/{attemptId}/anomaly-events` | Lister les événements d'anomalie corrélés |
| `POST` | `/internal/catalog/replay-requests` | Créer une demande manuelle par UUID ou SHA-256 |
| `POST` | `/internal/catalog/replay-requests/{id}/resume` | Reprendre manuellement une demande non terminale |

Aucune route de suppression, d'annulation, de résolution manuelle d'anomalie, de rejeu forcé d'un résultat terminal ou de recherche floue de candidat canonique n'existe dans cette version.

## Commandes

### Décider un mapping

`POST /internal/catalog/mapping-decisions` exige l'en-tête `Idempotency-Key` et un objet JSON plat. Les seuls champs admis sont :

```json
{
  "provider": "synthetic-provider",
  "entityType": "TEAM",
  "providerEntityId": "team-42",
  "season": "2026",
  "phase": "REGULAR",
  "decisionType": "CONFIRM",
  "canonicalEntityId": "00000000-0000-0000-0000-000000000042",
  "expectedVersion": 1,
  "justification": "Correspondance vérifiée dans les preuves versionnées."
}
```

- `season` et `phase` sont facultatifs ; absence ou `null` signifie contexte global vide ;
- `canonicalEntityId` est obligatoire pour `CONFIRM` et interdit pour `REJECT` ;
- `SNAPSHOT` est reconnu syntaxiquement mais ne peut pas être mappé ;
- l'auteur ne figure jamais dans le corps ou dans un en-tête libre ;
- la justification brute est validée puis expurgée avant toute persistance ou journalisation ;
- `expectedVersion=0` exige l'absence du mapping ; une version positive protège une mutation existante.

Une décision appliquée sans demande de rejeu retourne `200`. Une décision nouvellement appliquée avec au moins une demande durable créée retourne `202`, même si la tentative synchrone après commit est déjà terminée. Une répétition idempotente retourne `202` tant qu'une demande associée reste non terminale, sinon `200`.

### Créer une demande de rejeu

`POST /internal/catalog/replay-requests` exige `Idempotency-Key` et exactement un des deux sélecteurs suivants :

```json
{"snapshotId":"00000000-0000-0000-0000-000000000001"}
```

```json
{"payloadSha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
```

Un SHA-256 doit désigner exactement un snapshot à l'échelle globale. Une absence retourne `404` et une ambiguïté `409`. Une nouvelle demande retourne toujours `202`. La répétition idempotente relit l'état durable actuel et retourne `200` si la demande est terminale, sinon `202`.

### Reprendre une demande

`POST /internal/catalog/replay-requests/{id}/resume` n'exige pas de nouvelle clé d'idempotence et accepte uniquement :

```json
{"expectedVersion":3}
```

La version optimiste protège le claim. Une demande terminée ou déjà connue comme terminale retourne `200`. Une exécution encore en cours ou un échec reprenable retourne `202`. Une version obsolète ou un claim concurrent retourne `409`.

### Validation stricte des corps et paramètres

Le parseur local CAT-002 n'altère pas la configuration Jackson globale. Pour ces commandes :

- le document doit contenir exactement un objet JSON ;
- les types JSON ne sont pas coercés ;
- les champs dupliqués, inconnus ou les données placées après l'objet sont refusés ;
- `path`, `uri`, `file` et `filePath`, quelle que soit leur profondeur ou leur casse normalisée par le garde, produisent `400 ARBITRARY_PATH_FORBIDDEN` ;
- tout paramètre de requête absent de la liste blanche de la route, ou présent plusieurs fois, produit `400`.

## Pagination keyset

Toutes les collections utilisent la forme :

```json
{
  "items": [],
  "nextCursor": null
}
```

- `limit` vaut `50` par défaut et doit être compris entre `1` et `100` ;
- l'adaptateur lit au plus `limit + 1` lignes et ne retourne aucun total ;
- `nextCursor` est une valeur Base64URL sans padding, opaque pour le client ;
- le curseur `v1` lie le scope de la route, le tuple de tri et le SHA-256 des filtres normalisés ;
- le `limit` n'entre pas dans l'empreinte : il peut changer entre deux pages ;
- un curseur tronqué, malformé, d'un autre scope ou réutilisé avec d'autres filtres produit `400 INVALID_CURSOR` ;
- la pagination ne garantit pas une vue transactionnelle figée sous écritures concurrentes.

| Collection | Tri fixe | Filtres |
|---|---|---|
| Anomalies | `(createdAt DESC, id DESC)` | `status` (`OPEN` par défaut), `provider`, `code`, `entityType`, `snapshotId`, `providerEntityId` |
| Événements d'anomalie | `(createdAt DESC, id DESC)` | parent fixé par la route |
| Mappings | `(updatedAt DESC, id DESC)` | `status`, `provider`, `entityType`, `providerEntityId`, `season`, `phase`, `canonicalEntityId` |
| Décisions | `(createdAt DESC, id DESC)` | `mappingId` facultatif |
| Anomalies d'une décision | `(createdAt DESC, targetId DESC)` | parent fixé par la route |
| Demandes de rejeu | `(updatedAt DESC, id DESC)` | `status`, `origin`, `snapshotId`, `mappingDecisionId` |
| Tentatives | `(attemptNumber DESC, id DESC)` | parent fixé par la route |
| Corrélations de tentative | `(createdAt DESC, targetId DESC)` | demande et tentative fixées par la route |

Pour les mappings, l'absence de `season` ou `phase` signifie « ne pas filtrer ». Une valeur présente et vide, par exemple `season=`, cible explicitement le contexte global vide.

## Projections de lecture

Les réponses utilisent des read models dédiés, indépendants de JDBC et de HTTP dans la couche application.

- Une provenance de snapshot expose l'UUID, le fournisseur, l'endpoint logique, les horaires, le statut HTTP, la latence, le quota exposé, le SHA-256, la compression, la version du connecteur et la date de création. Le payload est exclu.
- Une anomalie expose sa clé logique, son état courant, ses détails déjà expurgés, sa version, son nombre d'occurrences, ses dates et la provenance sûre du snapshot.
- Un mapping expose sa clé naturelle, son statut, sa cible, sa confiance, sa version et ses dates.
- Une décision expose les états et versions avant/après, l'auteur et la justification déjà expurgée. Elle n'expose aucun reçu, clé ou digest d'idempotence.
- Une demande expose son origine, son sélecteur, son état, sa version, son compteur de tentatives, son erreur générique, ses dates et la provenance sûre.
- Une tentative expose son numéro, son résultat ou son erreur, ses compteurs, ses empreintes et ses dates.
- Une corrélation expose une application canonique ou un événement d'anomalie sans embarquer de collection non bornée.

Les créations renvoient l'en-tête `Location` de la ressource courante.

## Erreurs HTTP

Les erreurs utilisent RFC 9457 `ProblemDetail` avec :

```json
{
  "type": "urn:betting-project:problem:INVALID_CURSOR",
  "title": "Requête invalide",
  "status": 400,
  "detail": "La requête ne respecte pas le contrat attendu.",
  "instance": "/internal/catalog/anomalies",
  "code": "INVALID_CURSOR"
}
```

`instance` ne contient jamais la query string. `detail` est générique. Aucun chemin soumis, payload, justification brute, clé d'idempotence, SQL, stacktrace ou message d'exception n'est recopié.

| HTTP | Familles de résultat |
|---:|---|
| `200` | lecture, décision sans rejeu pendant, résultat terminal connu, reprise terminale |
| `202` | décision ayant créé une demande, nouvelle demande, demande ou reprise non terminale |
| `400` | validation, curseur, hash ou sélecteur invalide, champ arbitraire, précondition absente |
| `404` | anomalie, mapping, décision, snapshot, demande ou cible canonique absente |
| `409` | SHA-256 ambigu, version obsolète, claim concurrent, conflit d'idempotence |
| `422` | commande valide syntaxiquement mais interdite par une règle métier, dont `SNAPSHOT_NOT_MAPPABLE` |
| `503` | identité opérateur locale indisponible pour une mutation humaine |

## Compatibilité et évolution

Le contrat dépend des migrations V001 à V005 et ne nécessite aucune V006. Il ne modifie ni `cal01-fixture-v2`, ni `cal01-fixture-v3`, ni `stored-snapshot-replay-v1`. Toute évolution incompatible des routes, curseurs, champs ou statuts exige une nouvelle version documentaire et une stratégie de compatibilité explicite.
