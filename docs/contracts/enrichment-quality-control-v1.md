# Contrat de consultation qualité ENR-002 v1

## Périmètre

Ce contrat décrit une consultation locale en lecture seule du plan quotidien d'enrichissement
et de ses observations déjà persistées. Il ne déclenche aucune collecte, aucun replay, aucune
réservation budgétaire, aucun job et aucun appel fournisseur.

Le seul endpoint est :

```http
GET /internal/collection/enrichment/daily?date=2030-08-10
```

`date` est obligatoire et représente la date UTC du plan. Seul ce paramètre est accepté ; un
paramètre absent, dupliqué, inconnu ou invalide produit un `400` générique. Les noms `path`,
`uri`, `url`, `file` et `filePath` sont explicitement refusés. Une date sans plan répond `200`
avec `planPresent=false` et `fixtures=[]` : l'absence de plan n'est ni un incident fournisseur,
ni la preuve qu'aucune rencontre n'était éligible.

L'API n'est pas authentifiée et reste accessible uniquement sur `127.0.0.1`. Elle ne doit pas
être proxifiée ou exposée sur un réseau. Les contrôleurs, le service et l'adaptateur SQL sont
chargés uniquement sous `control-api`.

## Borne et ordre

Une réponse couvre au plus un plan et ses sept admissions persistées. Une rencontre inclut au
plus six étapes ; les observations sont réduites à la dernière reçue pour chacune des cinq
familles ; les constats sont agrégés par code et portée ; les tentatives sont réduites à la plus
récente par étape et famille. Les requêtes SQL ont également des limites de garde légèrement
supérieures aux maxima métier : une violation de ces invariants échoue plutôt que de produire
une liste non bornée.

Les exécutions multiples du plan ne créent pas de pages. Les lectures s'effectuent dans une
transaction PostgreSQL `REPEATABLE READ`, afin que le plan, les étapes, observations, constats et
tentatives de la réponse partagent le même snapshot de lecture.

## Projection

La réponse contient :

- le plan : date, UUID, fenêtre budgétaire, SHA-256 du registre, nombre d'admissions, instants
  d'évaluation et de création ;
- chaque rencontre admise : UUID canonique, ordre, priorité, kickoff prévu, noms canoniques,
  compétition, saison, phase et statut ;
- les étapes : code, portée de famille, condition, état, résultat, horaire, marqueurs et version
  de politique de déclenchement éventuellement observés ;
- une entrée pour chacune des familles `MATCH_DETAIL`, `LINEUP`, `TEAM_STATS`, `EVENTS` et
  `PLAYER_STATS` ;
- les constats de qualité sous forme de comptes groupés par code et portée, sans identifiant de
  joueur ou entité fournisseur ;
- au plus la dernière tentative pour chaque paire étape/famille, avec endpoint logique,
  fournisseur, état, statut HTTP, raison technique à code borné, versions, quota restant,
  horodatages et provenance de snapshot.

Chaque observation disponible donne les états existants (`NOT_PRESENT`, `NULL_VALUE`, `EMPTY`,
`AVAILABLE`, `PARTIAL`, `INCOMPATIBLE`), les références fournisseur et le contexte logique sans
les confondre, les hashes distincts du brut et de la représentation, le UUID du snapshot, les
versions et les instants d'envoi/réception/source lorsqu'ils existent. Pour LINEUP, la projection
peut inclure le statut structurel allowlisté de l'évaluation.

Une famille sans observation persistée apparaît avec `state=NOT_COLLECTED`. Cela signifie
uniquement qu'aucune observation n'a été enregistrée dans le périmètre du plan ; ce n'est pas
équivalent à une réponse fournisseur `ABSENT`, `NULL_VALUE`, `EMPTY`, ou à une étape manquée.
Ces états et les fenêtres `MISSED_WINDOW` restent distincts.

`ageSeconds` est la différence signée, en secondes, entre l'instant de génération de la réponse
et `receivedAt` de la dernière observation. C'est une mesure, pas une évaluation : ce contrat ne
définit aucun seuil « frais », « périmé », admissible ou complet et aucune décision métier n'est
déduite de l'âge.

## Confidentialité et erreurs

La projection n'inclut ni `raw_snapshot.payload`, ni `representation_json`, ni corps de requête,
URL authentifiée, empreinte de requête, clé d'idempotence, identifiant d'audit, secret, stacktrace
ou valeur personnelle d'entité fournisseur. Elle ne fournit que le SHA-256 du payload conservé
pour permettre sa corrélation hors de l'API.

Les erreurs de requête sont traduites en `ProblemDetail` avec un code stable et un détail générique.
Aucune valeur de query string ou message d'exception interne n'est recopié dans la réponse.

Cette projection ne constitue pas une qualification de l'exactitude sportive, de la disponibilité
d'un fournisseur, de la couverture, d'une composition officiellement confirmée ou d'un résultat
final. La baseline fournisseur reste inactive tant que sa porte d'autorisation distincte n'est
pas franchie.
