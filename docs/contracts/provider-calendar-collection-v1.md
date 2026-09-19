# Contrat de collecte calendrier fournisseur v1

- Version : `provider-calendar-collection-v1`.
- Work Order : [MVP-001, lot 3](../work-orders/MVP-001.md).
- Décisions et schémas documentaires consultés : 18 septembre 2026.
- Fournisseurs implémentés : `highlightly` et `football-data.org`, sans activation réelle.

Ce contrat décrit les cas d'usage internes synchrones, les transports, les interprétations et
leurs preuves. Il n'installe ni endpoint entrant, ni worker, ni ordonnanceur. Le registre de
production reste vide et les transports sont désactivés par défaut. Une implémentation testée
sur corpus synthétique ne qualifie pas une compétition réelle ou une campagne historique.

## 1. Références exactes et commande

Une commande comporte un UUID de collecte, une fenêtre budgétaire existante, une clé de capacité
`(provider, providerCompetitionId, sourceSeason, sourcePhase, CALENDAR)`, une date UTC et une
année de début de saison utilisée comme filtre fournisseur. La capacité doit exister exactement,
être active et opérationnelle ; la fenêtre doit appartenir au même fournisseur.

L'UUID de collecte porte l'idempotence de la commande. Son empreinte couvre la fenêtre, la clé,
la date et l'année de filtre. Une répétition identique relit l'état durable ; une réutilisation
avec un autre contenu produit `IDEMPOTENCY_CONFLICT`, sans envoi. Le SHA-256 du registre chargé
est conservé avec la collecte. La répétition d'une collecte `RUNNING` ne déclenche pas de reprise
d'envoi ; celle-ci n'est pas un mécanisme de reprise automatique du lot 4.

Les références fournisseur restent des littéraux exacts sensibles à la casse. Un code logique
de routage ne crée aucun mapping canonique, et une clé de compétition n'est pas un UUID canonique.
Une saison ou phase voisine ne remplace jamais celle demandée.

Le porteur a également accepté la séparation explicite des contextes le 18 septembre : les
valeurs natives `sourceSeason`/`sourcePhase` restent dans l'observation, la clé d'autorité et les
recherches de mappings. Le contexte de la route du registre fournit séparément la saison et la
phase **canoniques**. Ce sont ces correspondances exactes, configurées et versionnées, que le
catalogue utilise pour l'identité et la comparaison canonique. Aucun parseur ne transforme une
phase native ou un identifiant de saison par ressemblance textuelle. Les références synthétiques
du registre démontrent cette traduction sans activer de correspondance réelle.

## 2. Endpoints et limites de transport

| Fournisseur | Endpoint borné | Paramètres interprétés |
|---|---|---|
| Highlightly | `https://soccer.highlightly.net/matches` | `leagueId`, `season`, `date`, `timezone=Etc/UTC`, `limit`, `offset` |
| football-data.org | `https://api.football-data.org/v4/competitions/{code}/matches` | `season`, `dateFrom`, `dateTo` |

Pour football-data.org, `dateFrom` est le jour demandé et `dateTo` le lendemain exclusif. Le
parseur vérifie aussi chaque kickoff après conversion en date UTC. Aucun endpoint global
football-data.org n'est utilisé comme fallback. Les paramètres sont encodés ; une référence
fournisseur ne peut modifier l'hôte, injecter une autre query ou devenir un chemin arbitraire.
Les URI de base de production sont fixées par l'adaptateur ; leur substitution dans les tests
reste interne au package de transport, pour serveurs loopback et transports factices.

Bornes de production : connexion **5 secondes**, réception complète **30 secondes**, corps
**5 Mio** maximum, **100 pages** maximum par collecte. Highlightly est demandé par pages de
100 ; football-data.org ne reçoit aucun offset ou mécanisme de pagination inventé.

L'adaptateur effectue un seul envoi applicatif et ne suit pas les redirections. La configuration
refuse d'activer un client si les garde-fous JVM n'étaient pas fixés dès le démarrage :
`-Djdk.httpclient.disableRetryConnect=true` et `-Djdk.httpclient.redirects.retrylimit=1`.
Les valeurs effectives doivent rester identiques et la journalisation HTTP JDK doit rester
désactivée. Le code ne change pas après coup ces propriétés globales potentiellement déjà mises
en cache. Ces contrôles ne sont pas une revendication d'HTTP exactement une fois : une rupture
après émission demeure incertaine et ne doit pas provoquer de réexpédition.
Les options sont documentées par le [module HTTP JDK 25](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/module-summary.html).

L'activation demanderait en outre une décision explicite, une capacité réelle prouvée, une
fenêtre budgétaire admissible, `betting.providers.<provider>.enabled=true` et un secret valide
fourni hors Git (`HIGHLIGHTLY_API_KEY` ou `FOOTBALL_DATA_API_KEY`). Le segment de configuration
football-data.org est `football-data`. Ces conditions sont des portes, pas des valeurs activées
par ce lot. L'existence d'une variable d'environnement seule n'active pas un transport.

Le transport refuse une transaction appelante ouverte. Les secrets restent dans les seuls
en-têtes d'authentification ; aucune URL authentifiée, clé ou exception HTTP brute n'est persistée.
Un écho du secret est classé `SECRET_ECHO` et ses octets ne sont pas conservés. Un dépassement
de taille ou une interruption conserve au plus le préfixe reçu autorisé, avec une classification
d'échec ; ce hash éventuel est celui des octets retenus, jamais une preuve de réponse complète.

## 3. Enveloppes natives et interprétation fermée

Les versions de parseur sont `highlightly-calendar-v1` et `football-data-calendar-v1`.
Chaque parseur utilise un Jackson privé strict : objet racine attendu, clés dupliquées et données
après le document refusées, types vérifiés sans coercition. Les champs natifs additionnels sont
tolérés mais n'accordent aucune sémantique non contractualisée.

### Highlightly

- Enveloppe : tableau `data` et objet `pagination` avec entiers non négatifs
  `totalCount`, `offset`, `limit`.
- Offset et limite doivent être ceux demandés ; la longueur doit correspondre au minimum
  entre limite et nombre annoncé restant. Une page courte ou vide avant le total est incomplète.
- Une page suivante n'est annoncée que si `offset + longueur < totalCount` ; le nouvel offset
  doit progresser et rester représentable. Le coordinateur vérifie la stabilité du total, les
  répétitions de corps et les doublons de rencontres entre pages.
- Chaque rencontre comporte `id`, `league.id/name/season`, `round`, `date`, `state.description`
  et les deux objets `homeTeam`/`awayTeam` avec leur propre `id/name`.
- La saison numérique `league.season` doit correspondre à la saison source exacte et à l'année
  de filtre. La phase est le littéral **entier** de `round`, par exemple `Regular Season - 1`.
  Elle n'est pas réduite à une phase générique. Une page mélangeant plusieurs rounds ne peut
  pas être appliquée sous une seule affectation de phase ; elle reste incompatible dans v1.

### football-data.org

- Enveloppe : objet `competition`, objet `resultSet` et tableau `matches`.
- `resultSet.count` doit être égal à la longueur reçue. Aucun `nextOffset` n'est produit ;
  une demande avec offset non nul est incompatible avec la pagination de cet adaptateur.
- Le code de compétition racine et celui des rencontres doivent correspondre à la référence
  demandée ; leurs identifiants natifs doivent aussi concorder.
- Chaque rencontre conserve `id`, `utcDate`, `status`, `stage`, `season.id/startDate` et les
  deux équipes avec leur propre `id/name`.
- **`season.id` est la saison source**, distincte de l'année passée au filtre `season`.
  Le parseur vérifie l'année de `season.startDate` contre ce filtre. Il ne remplace jamais un
  identifiant de saison par une année ou une chaîne déduite du kickoff.
- `stage` est la phase source exacte. Une autre phase dans la réponse invalide la page,
  sans filtrage silencieux qui donnerait l'apparence d'une collecte exhaustive.

### Traduction des états

| Source | Valeur native admise | État calendrier dérivé |
|---|---|---|
| Highlightly | `Not started` | `SCHEDULED` |
| Highlightly | `Finished`, `Finished after penalties`, `Finished after extra time` | `FINISHED` |
| Highlightly | `Postponed` | `POSTPONED` |
| Highlightly | `Cancelled` | `CANCELLED` |
| football-data.org | `SCHEDULED`, `TIMED` | `SCHEDULED` |
| football-data.org | `FINISHED`, `POSTPONED`, `CANCELLED` | Valeur inchangée |

Tout autre statut est `UNSUPPORTED_STATUS`, notamment les états live, suspendus, interrompus,
abandonnés, awarded, inconnus et à annoncer. Une valeur future ou de casse différente n'est
pas convertie en nominal. Le lot n'étend pas les quatre états du canon et n'active aucun live.

Les IDs numériques doivent être des entiers positifs, pas des chaînes, des flottants ou `null`.
Une rencontre répétée, deux participants identiques, un kickoff invalide ou hors du jour UTC,
une référence ou phase incompatible invalide toute la page. Aucun sous-ensemble de ses rencontres
n'est retourné au normaliseur lorsqu'une rencontre voisine est incompatible.

## 4. Métadonnées inconnues et représentation calendrier

La décision du porteur du 18 septembre permet les **métadonnées descriptives inconnues** :
pays de compétition, type canonique de compétition et pays des équipes. Les parseurs conservent
`null` lorsqu'ils ne disposent pas de cette information. Le pays éventuel de la compétition
vient de `country.code` Highlightly ou `area.code` football-data.org ; il n'est jamais recopié
sur une équipe, notamment pour les compétitions internationales. Les types fournisseur
`LEAGUE`/`CUP` ne suffisent pas à déduire la classification nationale/internationale du canon.

Les identifiants, noms requis, saison et phase restent obligatoires. L'absence descriptive ne
crée aucun référentiel : les mappings explicites vers les entités canoniques existantes restent
nécessaires. Les pays et types canoniques déjà enregistrés ne sont pas écrasés par ces inconnus.

La représentation dérivée est `cal01-fixture-v3` : ordre `homeTeam`/`awayTeam` conservé,
`neutralVenue=null`, `participantsUnordered=false`. Aucun champ additionnel portant un nom
évocateur n'autorise une inférence de neutralité ou de non-ordre. Le wire format v3 reste inchangé.

`observedAt` est fixé à la **réception de cet appel**, explicitement choisie comme provenance
du temps source. Il n'est ni le kickoff, ni `lastUpdated`, ni l'heure d'un futur rejeu, ni celle
d'une ancienne ligne brute réutilisée par déduplication. Le normaliseur ne reçoit aucun fallback.

## 5. Budget, audit, outbox et transactions

Le chemin synchrone suit des étapes durables séparées :

1. vérifier capacité, disponibilité du transport et fenêtre, puis créer ou résoudre la collecte ;
2. réserver une intention unitaire, avec clé `calendar:{collectionId}:{offset}` ;
3. créer atomiquement page, audit d'appel préalable et outbox minimisée `PROVIDER_CALENDAR` ;
4. obtenir du budget une autorisation **nouvelle** après son commit effectif ;
5. effectuer l'unique envoi hors transaction ;
6. conserver résultat budgétaire, incident éventuel, octets reçus, audit et état d'outbox dans une
   transaction courte, avant tout parsing ;
7. interpréter la page, puis appeler le port d'application canonique et journaliser sa dérivation.

L'outbox porte uniquement les UUID de page et d'intention. Sa clé est `calendar-call:{intentId}`.
Un résultat reçu termine cet effet ; une réception incertaine le marque en échec. Il n'existe
aucun consommateur générique, polling, retry ou boucle de reprise d'outbox dans ce lot. Une ligne
restée `PENDING` après panne ne constitue jamais une permission de rappeler le fournisseur.

Le [budget qualifié](provider-budget-v1.md) reste l'autorité des coûts et autorisations : une
réponse ne débite pas une seconde fois, une répétition engagée ne donne pas de nouvelle permission,
un envoi incertain conserve son coût et ne bloque que sa propre intention. Les statuts HTTP
401/403/429 reçus sont traités par son chemin d'incident et suspendent la fenêtre correspondante.
L'autre fournisseur ne perd ni ses preuves ni ses disponibilités.

Le restant Highlightly exposé en en-tête est audité puis présenté au budget comme une observation
dont l'ensemble des engagements couverts est **vide**. L'empreinte de preuve identifie l'audit
et le compteur ; elle n'est pas présentée comme le hash des seuls octets du corps. La fin de
validité ne dépasse pas celle de la preuve initialement admise : un header ne prolonge pas une
fenêtre ou la durée de validité du compteur. Aucun UUID couvert n'est déduit de l'ordre d'arrivée
ou d'un numéro d'envoi. Les engagements dont l'inclusion reste inconnue demeurent donc soustraits
prudemment, même si cela réduit davantage le disponible observé.

Une observation ancienne ou périmée reste traitée conservativement ; une hausse de restant ne
crée pas de reset. Un restant supérieur à la capacité de la fenêtre est une incohérence bloquante
avec incident, pas une disponibilité supplémentaire. Une réconciliation explicite reste nécessaire
lorsque la preuve devient insuffisante. football-data.org ne reçoit pas de quota quotidien par
analogie ; sa cadence reste distincte.

Une erreur de transport, un timeout ou un arrêt entre engagement et enregistrement du résultat
ne déclenche pas de nouvel appel. Une erreur de persistance conserve l'engagement durable antérieur
et interdit une qualification optimiste de la réponse. Le lot 4 devra organiser les reprises
manuelles et les claims nécessaires sans transformer cet état en réservation neuve.

## 6. Preuve brute, dérivation et replay

V010 ajoute `calendar_collection`, `calendar_collection_page` et le journal append-only
`calendar_collection_derivation`. Elle réutilise `raw_snapshot`, `provider_call_audit`, l'outbox
et le budget V009 ; V001 à V009 restent immuables. Les références composées rattachent page et
intention à la même fenêtre, et chaque dérivation à l'empreinte brute de sa page.

Le SHA-256 natif identifie les octets reçus avant interprétation. Le SHA-256 du snapshot dérivé
identifie la sérialisation v3 et **ne remplace pas** le premier. La dérivation conserve l'UUID
de page, la version exécutée du parseur, l'empreinte native, le résultat et l'UUID du snapshot
dérivé appliqué. La version de connecteur reste distincte de celle du parseur et du registre.

`CalendarNativeReplayService`, sous `control-api` uniquement, reçoit l'UUID d'une page stockée.
Il ne reçoit ni chemin arbitraire ni URL, ne réserve aucun budget et n'appelle aucun client HTTP.
Il relit les octets, recalcule leur SHA-256, contrôle la provenance puis réinterprète avec le
parseur versionné courant en conservant l'instant de réception initial. Une altération produit
`INTEGRITY_ERROR`. Une nouvelle interprétation ajoute une dérivation ; elle ne réécrit pas l'audit
ou les observations historiques. Le normaliseur existant assure l'idempotence des identités
canoniques et conserve ses journaux successifs.

Ce replay de page ne déclare pas rétrospectivement complète une collecte interrompue. Le profil
`replay` permet les interprétations en mémoire sans PostgreSQL, DataSource ou transport. Il ne
charge pas les cas d'usage persistants de collecte et de replay stocké.

## 7. Complétude et limites de qualification

`COMPLETED` signifie que toutes les pages annoncées ont été reçues et interprétées dans les
bornes et le contexte de la commande. Il ne garantit ni couverture commerciale exhaustive,
ni exactitude sportive, ni absence d'anomalie de mapping, ni application de toutes les rencontres
au canon. Les rôles `PRIMARY`, `CONTROL`, `UNASSIGNED` et les autres politiques CAT-002 continuent
de décider de chaque application.

Une collecte `INCOMPLETE` conserve les pages déjà acquises et, le cas échéant, les applications
de ses pages valides antérieures. Une page invalide n'est jamais partiellement normalisée. Budget
insuffisant, pagination cyclique ou contradictoire, plafond de pages, interruption, HTTP en erreur
ou contexte incompatible ne sont pas présentés comme un calendrier exhaustif. Ces collectes ne
suppriment pas des rencontres au motif qu'elles n'ont pas été vues.

Les fixtures de `src/test/resources/fixtures/mvp001/calendar` sont synthétiques. Elles s'appuient
sur les schémas documentés officiellement, pas sur une prétendue copie des octets COV-002. Les
tests couvrent les interprétations fermées, serveurs factices/loopback, contraintes PostgreSQL,
preuve brute, dérivation et replay. Leurs résultats exécutés et les limites restantes sont
consignés dans le Work Order ; ce contrat ne remplace pas une qualification réelle datée.

Sources officielles consultées sans appel aux API :
[Highlightly, Matches](https://highlightly.net/football-api/documentation/),
[football-data.org, Competition](https://docs.football-data.org/general/v4/competition.html),
[football-data.org, Match](https://docs.football-data.org/general/v4/match.html).

## 8. Raccordement explicite du lot 4

Le [contrat des jobs](collection-jobs-v1.md) ajoute une exécution gérée avec fencing, bail et
reprise de pages. Elle réutilise ce même coordinateur et les mêmes parsers/budgets, sans second
normaliseur. Le chemin manuel `collect(command)` reste idempotent sans reprise automatique.
Les réponses déjà conservées ne sont pas rappelées ; l'envoi déjà engagé sans preuve devient
`SEND_UNCERTAIN`, sans inventer de snapshot vide ni d'heure de réception. V011 ajoute ce statut
par migration corrective additive. La boucle du seul `batch-worker` demeure désactivée par défaut.
