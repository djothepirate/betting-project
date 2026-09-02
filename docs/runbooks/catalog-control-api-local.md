# Exploiter localement le `catalog-control-api-v1`

## Objet et avertissement

Ce runbook décrit l'utilisation locale des routes CAT-002 sous `/internal/catalog`. L'API n'est pas authentifiée et écoute uniquement sur `127.0.0.1`. Ne pas la proxifier, l'exposer sur le LAN ou Internet, ni modifier son adresse d'écoute avant OPS-001.

Elle n'effectue aucun appel fournisseur. Elle consulte PostgreSQL, enregistre les décisions humaines et pilote les demandes durables de rejeu déjà stockées. Le profil `replay` reste hors PostgreSQL et ne sert pas ces routes.

## Préparer l'environnement

1. Copier `.env.example` vers `.env` si nécessaire et renseigner la configuration PostgreSQL locale.
2. Définir `BETTING_OPERATOR_ID` uniquement dans l'environnement local pour autoriser les décisions humaines. La valeur est trimée, doit contenir de 1 à 100 caractères et ne doit contenir aucun caractère de contrôle.
3. Démarrer PostgreSQL puis le profil `control-api` :

```powershell
docker compose --env-file .env up -d postgres
$env:BETTING_OPERATOR_ID = "local-operator"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"
```

Le serveur doit être joint uniquement via `http://127.0.0.1:8080`. Les lectures fonctionnent sans `BETTING_OPERATOR_ID`. Une nouvelle décision humaine sans identité valide retourne `503 OPERATOR_UNAVAILABLE` sans écriture.

Ne placez jamais une clé fournisseur, un payload brut, une justification sensible ou une clé d'idempotence réelle dans l'historique du terminal, les captures ou les rapports. Les exemples ci-dessous sont synthétiques.

## Consulter le catalogue

Les collections retournent `items` et `nextCursor`, sans total. Le curseur est opaque : le recopier sans le décoder ni le modifier.

```powershell
Invoke-RestMethod "http://127.0.0.1:8080/internal/catalog/anomalies?limit=50"
Invoke-RestMethod "http://127.0.0.1:8080/internal/catalog/provider-mappings?provider=synthetic-provider&limit=25"
Invoke-RestMethod "http://127.0.0.1:8080/internal/catalog/mapping-decisions?mappingId=00000000-0000-0000-0000-000000000042"
Invoke-RestMethod "http://127.0.0.1:8080/internal/catalog/replay-requests?status=FAILED_RETRYABLE"
```

Pour charger la page suivante, réutiliser les mêmes filtres avec `cursor=<nextCursor>`. Il est permis de changer `limit`. Changer les autres filtres produit `400 INVALID_CURSOR`.

Une absence de `season` ou `phase` ne filtre pas ce champ. Pour cibler le contexte global vide d'un mapping, fournir explicitement `season=` ou `phase=`.

Les réponses de provenance contiennent le SHA-256 et les métadonnées du snapshot, jamais ses octets.

## Confirmer ou rejeter un mapping

Lire d'abord le mapping courant pour obtenir sa `version`. Une décision utilise cette valeur comme `expectedVersion`; la valeur `0` signifie que la clé naturelle doit être absente.

Confirmation synthétique :

```powershell
$headers = @{ "Idempotency-Key" = "example-map-confirm-001" }
$body = @{
    provider = "synthetic-provider"
    entityType = "TEAM"
    providerEntityId = "team-42"
    season = "2026"
    phase = "REGULAR"
    decisionType = "CONFIRM"
    canonicalEntityId = "00000000-0000-0000-0000-000000000042"
    expectedVersion = 1
    justification = "Correspondance vérifiée dans une preuve synthétique."
} | ConvertTo-Json -Compress

Invoke-RestMethod `
    -Method Post `
    -Uri "http://127.0.0.1:8080/internal/catalog/mapping-decisions" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body
```

Pour un rejet, utiliser `decisionType = "REJECT"` et omettre `canonicalEntityId`. La justification est obligatoire et est expurgée avant persistance. L'auteur est toujours résolu côté serveur ; ne pas ajouter d'auteur au corps ou dans un en-tête libre.

Une réponse `202` signifie qu'au moins une demande durable de rejeu existe ou reste non terminale. Consulter les identifiants retournés puis leurs routes `/replay-requests/{id}`. Une réponse `409` sur la version exige de relire le mapping avant toute nouvelle décision. Ne pas réutiliser une clé `Idempotency-Key` avec un contenu différent.

## Créer et suivre un rejeu manuel

Créer par UUID :

```powershell
$headers = @{ "Idempotency-Key" = "example-replay-001" }
$body = @{ snapshotId = "00000000-0000-0000-0000-000000000001" } | ConvertTo-Json -Compress
Invoke-RestMethod `
    -Method Post `
    -Uri "http://127.0.0.1:8080/internal/catalog/replay-requests" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body
```

Créer par SHA-256 :

```powershell
$headers = @{ "Idempotency-Key" = "example-replay-002" }
$body = @{ payloadSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" } | ConvertTo-Json -Compress
Invoke-RestMethod `
    -Method Post `
    -Uri "http://127.0.0.1:8080/internal/catalog/replay-requests" `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body
```

Un SHA-256 ambigu retourne `409`; aucune ligne n'est choisie arbitrairement. Un identifiant ou hash absent retourne `404` sans demande.

Consulter ensuite :

```powershell
$requestId = "00000000-0000-0000-0000-000000000099"
Invoke-RestMethod "http://127.0.0.1:8080/internal/catalog/replay-requests/$requestId"
Invoke-RestMethod "http://127.0.0.1:8080/internal/catalog/replay-requests/$requestId/attempts"
```

Les états terminaux sont `COMPLETED` et `FAILED_TERMINAL`. Une demande `FAILED_RETRYABLE` nécessite une reprise manuelle explicite. Il n'existe aucun poller ou worker qui la relancera automatiquement.

## Reprendre après un arrêt ou un échec reprenable

1. Lire `/replay-requests/{id}` et relever sa `version` courante.
2. Vérifier que la demande n'est pas terminale.
3. Envoyer la version attendue :

```powershell
$requestId = "00000000-0000-0000-0000-000000000099"
$body = @{ expectedVersion = 3 } | ConvertTo-Json -Compress
Invoke-RestMethod `
    -Method Post `
    -Uri "http://127.0.0.1:8080/internal/catalog/replay-requests/$requestId/resume" `
    -ContentType "application/json" `
    -Body $body
```

Une réponse `409` indique une version obsolète ou un claim concurrent : relire la demande. `202` indique que la demande reste non terminale ou reprenable. `200` indique un état terminal connu. Une demande terminale n'est jamais forcée à rejouer.

Pour auditer une tentative, lire ses applications et événements d'anomalie corrélés avec les routes enfant documentées dans `catalog-control-api-v1.md`.

## Diagnostic sûr

- `400 ARBITRARY_PATH_FORBIDDEN` : retirer tout champ `path`, `uri`, `file` ou `filePath`; seul un UUID ou un SHA-256 stocké est accepté.
- `400 INVALID_CURSOR` : reprendre à la première page ou réutiliser exactement les filtres associés au curseur.
- `409 VERSION_CONFLICT` : relire la projection courante puis décider à partir de sa nouvelle version.
- `409 IDEMPOTENCY_CONFLICT` : utiliser une nouvelle clé uniquement pour une nouvelle intention ; ne pas modifier le contenu d'une commande déjà identifiée.
- `503 OPERATOR_UNAVAILABLE` : configurer localement `BETTING_OPERATOR_ID`; les lectures restent disponibles.

Les réponses d'erreur restent génériques. Ne pas contourner cette expurgation en ajoutant des logs de payload, de justification brute, de clé d'idempotence ou de SQL.

## Arrêt et validations

Arrêter le processus Spring normalement. PostgreSQL conserve les demandes non terminales, qui pourront être relues et reprises au prochain démarrage du `control-api`.

Avant toute revue du lot :

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration verify
.\scripts\verify-windows.cmd
.\scripts\check-no-secrets.cmd
git diff --check
```

Cette procédure ne vaut ni autorisation de commit ou publication Git, ni autorisation d'exposer l'API.
