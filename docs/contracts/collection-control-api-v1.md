# Contrat collection-control-api-v1

Statut : livré par MVP-001 lot 5, consultations locales et prévision de sélection uniquement.
Les contrats [budget](provider-budget-v1.md), [calendrier](provider-calendar-collection-v1.md)
et [jobs](collection-jobs-v1.md) restent les sources des états et règles d'écriture.

## Frontière HTTP

Les composants sont présents exclusivement sous `control-api`, sur l'adresse loopback existante
`127.0.0.1`. Sans authentification, ces routes ne doivent être ni exposées ni proxifiées. OPS-001
devra traiter l'accès authentifié et le réseau. Les lectures n'exigent pas `BETTING_OPERATOR_ID`.
Aucune route ne permet d'initialiser un budget, de lever une suspension, de réserver/envoyer un
appel, d'activer un fournisseur, de planifier un job ou de démarrer un worker. La seule route POST
calcule une prévision sans mutation et ne nécessite donc pas de clé d'idempotence.

Préfixe : `/internal/collection`.

| Méthode | Route | Filtres et comportement |
|---|---|---|
| GET | `/capabilities` | `competitionCode`, `season`, `phase`, `dataType` obligatoires ; correspondance exacte d'une route, sans recherche approchée |
| GET | `/budget-windows` | `provider`, `status` |
| GET | `/budget-windows/{id}` | Projection de la fenêtre et compteurs d'intentions |
| GET | `/budget-windows/{id}/availability` | Résultat du calculateur budgétaire qualifié, avec code de refus éventuel |
| GET | `/budget-windows/{id}/intents` | `status` |
| GET | `/budget-windows/{id}/events` | Journal budgétaire minimisé |
| GET | `/incidents` | `provider`, `windowId`, `code` |
| GET | `/jobs` | `type`, `status` ; uniquement les jobs de collecte gérés par V011 |
| GET | `/jobs/{id}` | État du job et de son outbox dédiée |
| GET | `/jobs/{id}/events` | Événements du job, sans jeton de lease |
| GET | `/calendars` | `provider`, `windowId`, `status`, `date` UTC ISO |
| GET | `/calendars/{id}` | État de collecte et contexte fournisseur exact |
| GET | `/calendars/{id}/pages` | Tentatives/pages du parent, provenance brute sans octets |
| POST | `/daily-selection/preview` | Sélection prévisionnelle PPL/PD après les quatre calendriers du noyau |

Les statuts sont ceux des contrats d'origine, sensibles à la casse, sans valeur par défaut
implicite. Un détail ou parent absent retourne `404 RESOURCE_NOT_FOUND` ; un historique vide
d'un parent existant retourne `200` avec une page vide. Aucun total n'est calculé.

## Bornes, pagination et confidentialité

Les listes acceptent `limit` (défaut 50, de 1 à 100) et `cursor`. Leur forme est
`{"items":[],"nextCursor":null}`. Les capacités ajoutent `registryVersion` et `documentSha256`.
Les requêtes SQL lisent au plus `limit+1` lignes. Tri descendant par date et UUID :

- fenêtres : `(updatedAt, id)` ;
- intentions, incidents, événements, jobs et calendriers : `(createdAt, id)` ;
- pages : `(requestedAt, id)`.

Le curseur Base64URL sans padding contient le marqueur `collection-v1`, le scope, le SHA-256
des filtres exacts normalisés par ordre de nom, puis l'ancre date/UUID. Il est lié au parent mais
pas au `limit`. Pour les capacités immuables, l'ancre est un index dans les candidats de la route,
le scope vaut `capabilities` et l'empreinte comprend aussi le SHA du registre. Ce curseur n'est
pas une signature ni un secret ; tous ses champs sont contrôlés avant usage comme paramètres.
Une modification de filtre, scope, parent ou registre le rend invalide (`400 INVALID_CURSOR`).
La pagination d'une projection mutable n'est pas une vue transactionnelle figée.

Paramètre inconnu, dupliqué, trop long, blanc, UUID abrégé, type/enum/date/limite invalides : `400`.
Les noms `path`, `uri`, `url`, `file`, `filePath` sont explicitement interdits, sans tenir compte
de la casse. Aucun nom de colonne ni fragment SQL ne provient du client. Les filtres fournisseur
restent littéraux, y compris les caractères spéciaux ; ils sont liés comme valeurs SQL.

Les projections ne contiennent jamais le payload, la référence de compte/quota, les identifiants
opérateur, justifications, clés ou digests d'idempotence, tokens de claim, messages SQL ou exceptions.
Les pages exposent UUID d'intention/audit/brut, statut, compteur éventuel, horaires, empreinte des
octets et version du connecteur. Les capacités exposent les deux clés, couverture, rôle, activation,
admissibilité opérationnelle et nombre de preuves, pas leur contenu externe.

Les erreurs contrôlées utilisent `ProblemDetail`, type `urn:betting-project:problem:<code>`,
statut, titre, détail générique et `code`. `instance` est le préfixe fixe `/internal/collection`,
jamais une reprise du chemin ou de la query soumis. Les erreurs inattendues donnent
`500 INTERNAL_ERROR` sans message d'exception. Ces règles ne modifient pas Jackson globalement.

## Sélection prévisionnelle sans réservation

Le lot 5 prépare ENR-002 ; il n'implémente aucun appel d'enrichissement. La prévision n'est pas
un engagement budgétaire ni une promesse de disponibilité future. Aucune sélection durable n'est
inventée. Le coût estimé est obligatoire, de 1 à 80 appels par rencontre ; il ne provient pas
d'une estimation cachée du nombre de familles d'enrichissement.

Le POST exige `application/json`, au plus 16 Kio, et exactement ces cinq champs :

```json
{
  "windowId": "00000000-0000-0000-0000-000000000001",
  "date": "2030-08-10",
  "calendarCollectionIds": [
    "00000000-0000-0000-0000-000000000002",
    "00000000-0000-0000-0000-000000000003",
    "00000000-0000-0000-0000-000000000004",
    "00000000-0000-0000-0000-000000000005"
  ],
  "estimatedCallsPerMatch": 10,
  "priorityFixtureIds": []
}
```

Les UUID ci-dessus sont fictifs. `calendarCollectionIds` contient quatre UUID distincts ; les
priorités contiennent de zéro à cent UUID distincts. Le parseur local refuse coercitions, champs
dupliqués ou inconnus, données après l'objet et localisations arbitraires à toute profondeur.
Aucun candidat, budget disponible, auteur ou rôle n'est accepté depuis le corps HTTP.

Préconditions et sélection :

1. Fenêtre Highlightly existante ; quatre collectes `COMPLETED`, datées du même jour UTC et
   liées à cette fenêtre. Leurs références exactes sont encore actives et `PRIMARY` dans le
   même document de registre ; une et une seule route pour chacun de PPL, PD, DED et ELC.
2. Lecture du budget restant, après les consommations calendrier et toutes les réservations.
   Le calculateur conserve ses trois bornes prudentes et son verrou court de périmètre/fenêtre.
   Il ne retranche pas une deuxième fois la réserve de 20. Aucun verrou ne couvre un appel HTTP.
3. Lecture bornée des rencontres dont l'observation d'autorité courante appartient à une
   dérivation `APPLIED` d'une page de ces collectes. Le contexte source et le contexte logique
   doivent correspondre exactement. Ni mapping textuel ni nouveau canon ne sont créés.
4. Plus de 1 000 candidats, identité canonique dupliquée entre routes ou précondition manquante :
   refus explicite, jamais sélection sur une liste tronquée.
5. Seules PPL/PD, `SCHEDULED`, le jour UTC demandé et avant kickoff sont admissibles. L'ordre est
   priorité explicite, kickoff croissant, puis représentation canonique UUID croissante.
6. Nombre retenu : au plus sept et au plus `floor(disponible / estimatedCallsPerMatch)`.
   Les exclusions indiquent `OUTSIDE_ENRICHMENT_CORE`, `OUTSIDE_UTC_DAY`, `NOT_SCHEDULED`,
   `KICKOFF_REACHED`, `DAILY_CAP` ou `BUDGET_LIMIT`.

La réponse `200` contient le code, l'instant d'évaluation, la fenêtre, le SHA du registre, le
résultat budgétaire, le coût fourni, `maximumMatches=7`, `reservationCreated=false`, les candidats
sélectionnés et exclus, et le coût total estimé. Un budget épuisé, suspendu, périmé ou incohérent
produit une prévision vide avec son code budgétaire ; ce n'est pas un quota illimité.
Avec quatre appels calendrier, 100/80/20 sans consommation initiale et sans nouvelle couverture
du compteur, il reste 76 : coût déclaré 10 → sept rencontres ; coût 11 → six.

Les refus préalables sont `404 WINDOW_NOT_FOUND/COLLECTION_NOT_FOUND`,
`409 CALENDAR_NOT_READY/CANDIDATE_LIMIT` ou `400 INVALID_PRIORITY/INVALID_SELECTION`.
Une priorité inconnue du périmètre prouvé est refusée. La sélection peut changer lorsque les
faits ou le budget changent ; ENR-002 devra revalider, réserver et imposer le plafond durable
au moment des vrais enrichissements, sans cumuler naïvement plusieurs prévisions.

## Preuves et limites

`DailySelectionPolicyTest`, `DailySelectionServiceTest`, `CollectionWebContractTest` et
`CollectionControlIT` couvrent déterminisme, budget, garde des calendriers, bornes, pagination,
profils et chaîne hors réseau. Les tests de profils prouvent l'absence de ces composants sous
`batch-worker` et `replay`. Aucune migration supplémentaire, aucun enrichissement, worker ou
fournisseur réel n'est activé par cette API. Voir le [runbook local](../runbooks/collection-control-local.md).
