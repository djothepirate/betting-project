# Budget fournisseur durable v1

- Contrat : `provider-budget-v1` ; Work Order : [MVP-001](../work-orders/MVP-001.md), lot 2.
- Décisions de réalisation acceptées le 13 septembre 2026.
- Périmètre : cas d'usage internes, sans contrat HTTP ni activation d'un fournisseur.
- Sources complémentaires : [fondations de collecte](mvp-001-collection-foundations-v1.md)
  et [registre de capacités](provider-capability-registry-v1.md).

## 1. Unité de coût, périmètre et activation

Une intention représente **une tentative HTTP de coût unitaire**, indépendamment du nombre de
familles contenues dans sa réponse. Un détail contenant score, statistiques et événements ne
consomme donc qu'une unité. Une demande refusée avant création d'intention n'est ni une tentative,
ni une donnée absente, ni une preuve de disponibilité fournisseur.

Le périmètre de quota est la paire exacte `(provider, accountRef)`. `accountRef` est une référence
opaque sans secret, jamais une clé API. Pendant une fenêtre utilisée par Betting Project, le
compte est utilisé exclusivement par l'application. Les consommations externes antérieures sont
déclarées à l'initialisation ; une consommation externe imprévue exige une réconciliation.
Sans cette exclusivité, l'application ne peut garantir un plafond partagé avec un acteur invisible.

Chaque fenêtre explicite conserve son UUID, ses bornes UTC, les paramètres budgétaires, les
agrégats de consommation antérieure et une preuve `(logicalId, sha256)`. Aucune tentative passée
n'est fabriquée à partir de ces agrégats. L'initialisation est idempotente par UUID et définition
complète, y compris sa preuve et sa première observation ; une autre définition produit un conflit.

Une seule fenêtre `ACTIVE` ou `SUSPENDED` peut exister par périmètre. Les fenêtres successives ne
se chevauchent pas, y compris avec une fenêtre historique fermée. Une initialisation explicite
peut fermer son prédécesseur expiré dans la même transaction. Minuit, une hausse de compteur ou un
redémarrage ne créent ni ne renouvellent une fenêtre. Les autorisations exigent
`startsAt <= maintenant < endsAt`.

Highlightly conserve la référence `capacity=100`, `projectLimit=80`, `reserve=20`. La dérogation
100/0 de COV-002 reste une preuve historique datée et n'est pas une nouvelle politique.
football-data.org utilise une enveloppe projet et une cadence distincte, sans inventer un quota
quotidien fournisseur. Une fenêtre sans quota périodique a `capacity=null`, une réserve et une
consommation partagée initiale nulles au sens numérique (`0`), et une cadence explicite obligatoire.
Les champs d'observation périodique doivent alors être absents ; ce n'est pas un quota illimité.

La migration et les ressources classpath n'initialisent **aucune fenêtre réelle**. Les paramètres
des tests sont synthétiques ; le registre de production conserve ses entrées vides.

## 2. Intention durable et idempotence

Chaque intention conserve UUID, fenêtre, clé d'idempotence, endpoint logique, SHA-256 de la
description de requête sans secret, état, version et horodatages. Les clés d'idempotence sont
globalement uniques dans le namespace des intentions budgétaires, constituées de 1 à 128
caractères ASCII visibles sans espace. Le namespace des reçus de commande du catalogue reste
distinct et n'est pas importé dans `collection`.

Un endpoint est un identifiant logique, par exemple `calendar/matches`, pas une URL ni un chemin
local. Il est non vide, borné à 200 caractères, sans contrôle ni espace périphérique ; `:`, `?`,
`#`, `&`, `=`, l'antislash et le slash initial sont refusés. Les descriptions complètes,
authentifications et URL de requête ne sont pas persistées dans le budget.

```text
RESERVED → COMMITTED_FOR_SEND → RESULT_RECORDED
    │               └────────→ UNCERTAIN → RECONCILED
    └→ RELEASED
```

| État | Coût retenu | Autorisation de nouvel envoi pour cette intention |
|---|---|---|
| `RESERVED` | Une unité réservée | Possible après nouvelle vérification et commit |
| `COMMITTED_FOR_SEND` | Une unité consommée | Non ; `ALREADY_COMMITTED` |
| `RESULT_RECORDED` | Une unité consommée | Non ; `ALREADY_COMMITTED` |
| `UNCERTAIN` | Une unité consommée | Non ; réconciliation humaine seulement |
| `RECONCILED` | Une unité consommée | Non ; aucun remboursement |
| `RELEASED` | Aucune unité retenue | Non ; état terminal |

- Même clé, même fenêtre, même endpoint et même empreinte : même intention, dans son état courant,
  sans nouveau débit ni nouvel événement de réservation.
- Même clé avec un contenu différent, notamment une autre fenêtre : `IDEMPOTENCY_CONFLICT`.
- Seul `RESERVED` peut être libéré. Une répétition de libération déjà réalisée retourne l'état
  terminal sans nouvel effet. La répétition d'une réservation libérée ne la réactive pas.
- La première transition `COMMITTED_FOR_SEND` rend une autorisation nouvelle après commit.
  Les répétitions ne rendent jamais une autre autorisation, même si aucun appel n'a effectivement
  quitté le processus. Le budget retient ce cas conservateur.
- Un résultat enregistre le statut HTTP et son empreinte sans nouveau débit. Une répétition exacte
  est idempotente ; une modification de résultat, de quota associé ou de son absence est un conflit.
- `markUncertain` conserve le débit et ajoute `UNCERTAIN_SEND`. Il ne suspend pas toute la fenêtre :
  les autres intentions peuvent utiliser leurs disponibilités restantes.

`resultFingerprint` n'est pas le hash des seuls octets fournisseur. C'est le SHA-256 d'une
représentation canonique `budget-outcome-v1` contenant le statut HTTP, l'empreinte d'entrée fournie
par le futur adaptateur et, si présent, le quota associé : UUID, restant, dates, preuve et UUID des
engagements couverts triés. L'absence de quota reste distincte d'un quota ajouté lors d'une répétition.
Le hash des octets bruts et celui de la représentation calendrier dérivée restent des preuves
distinctes, à conserver par le futur connecteur avant parsing.

## 3. Disponibilité et preuves de quota

Pour un quota périodique, le calcul utilise :

| Symbole | Signification |
|---|---|
| `C` | Capacité fournisseur de la fenêtre |
| `S` | Réserve de sécurité |
| `P` | Plafond projet |
| `Bshared` | Consommation antérieure partageant le quota |
| `Bproject` | Part initiale imputée au budget projet |
| `D` | Intentions engagées, y compris incertaines et terminées |
| `H` | Intentions réservées, jamais engagées |
| `R` | Restant de l'observation fournisseur admissible |
| `DnonCouvert` | Engagements dont l'inclusion dans `R` n'est pas démontrée |

```text
disponible = max(0, min(
    P - Bproject - D - H,
    C - S - Bshared - D - H,
    R - S - DnonCouvert - H
))
```

Ces trois termes sont des bornes concurrentes, jamais trois débits à cumuler. Sans quota périodique,
seule la borne projet s'applique, avec contrôle séparé de la cadence. À l'autorisation d'une
intention déjà réservée, sa propre unité est exclue de `H` pour vérifier qu'elle reste finançable ;
toutes les autres réservations restent retenues.

Exemples synthétiques :

- Avec 100 disponibles, plafond 80 et réserve 20, 80 réservations sont possibles, puis refus.
- Avec 12 appels externes antérieurs et un restant initial de 88, 68 unités restent disponibles.
- Une réponse déjà reflétée dans `R` ne débite pas une seconde fois `D` ; sa couverture explicite
  évite seulement de la soustraire à nouveau de la borne fournisseur.
- Une couverture inconnue reste soustraite prudemment. Une unité réservée pour un contrôle futur
  n'est pas la réserve de sécurité incompressible.

Chaque observation conserve UUID, fenêtre, restant, `observedAt`, `validUntil`, preuve, disposition
et ensemble immuable d'intentions couvertes. Les références couvertes doivent appartenir à la même
fenêtre, être déjà engagées et ne pas avoir été engagées après l'observation. Cette vérification de
cohérence n'est pas une déduction de couverture : le futur adaptateur doit apporter la preuve.
Ni l'heure de réception, ni l'ordre des résultats, ni un compteur local séquentiel ne prouvent que
le fournisseur a déjà comptabilisé un autre appel.

| Situation | Conservation et effet |
|---|---|
| Observation antérieure à la courante | `STALE`, conservée, aucun remplacement |
| Même instant, mêmes restant et couverture | Admissible sans interpréter un reset |
| Même instant, faits divergents | `CONTRADICTORY`, incident et autorisation fermée |
| Restant augmenté dans la même fenêtre | `CONTRADICTORY`, pas d'augmentation du disponible |
| Observation requise absente | `QUOTA_OBSERVATION_MISSING`, aucune autorisation |
| Observation courante expirée | `STALE_OBSERVATION`, aucune autorisation |
| Compteur périodique non applicable | Aucun faux compteur créé ; borne projet et cadence |

Une contradiction ne disparaît pas par l'arrivée d'une nouvelle observation ordinaire. La
réconciliation administrative exige la version courante de la fenêtre et une nouvelle preuve
admissible, non périmée et non antérieure. Elle exige **un nouvel UUID d'observation** : une preuve
déjà enregistrée, même identique, retourne `IDEMPOTENCY_CONFLICT` dans cette commande. Une hausse
réconciliée de `R` ne rembourse aucun engagement et reste limitée par les deux bornes locales.

Les dates fournies par les commandes pour les fenêtres et preuves doivent être représentables
exactement à la microseconde PostgreSQL ; une précision plus fine est refusée, jamais arrondie.
L'horloge locale d'évaluation est explicitement ramenée à la microseconde. La cadence utilise une
durée exacte en millisecondes, bornée entre une seconde et 365 jours, sans conversion tronquante.

## 4. Cadence, suspensions et réconciliation

La cadence est configurée indépendamment du quota périodique par `(nombre, durée)`. Elle est
vérifiée avant engagement, jamais lors de la réservation d'appels futurs. Elle parcourt les
intentions du même périmètre, **y compris les fenêtres précédentes**, afin qu'une nouvelle fenêtre
budgétaire ne renouvelle pas artificiellement les créneaux.

- Un engagement sans résultat occupe un créneau sans expiration implicite.
- Après résultat, le créneau reste occupé jusqu'à `resultAt + cadencePeriod`.
- Une intention incertaine occupe son créneau jusqu'à réconciliation, puis pendant la même durée
  à partir du `resultAt` de réconciliation. Les autres créneaux restent utilisables.
- `RATE_LIMITED` contient `retryAt` seulement lorsqu'une échéance est calculable. Si tous les
  créneaux nécessaires dépendent d'intentions sans résultat, `retryAt=null` signifie inconnu,
  pas disponible immédiatement. Aucune attente active ni relance n'est effectuée.

HTTP 401, 403 ou 429 enregistre dans une même transaction résultat, coût déjà acquis, incident
`HTTP_401`, `HTTP_403` ou `HTTP_429`, suspension et événements. La suspension bloque aussi les
réservations déjà présentes, sans altérer les autres fournisseurs. Une défaillance d'incident ou
d'historique annule la transaction complète ; aucun état partiel n'est annoncé comme terminé.

Les réconciliations d'intention et de quota ne lèvent jamais une suspension HTTP. Une intention
incertaine ne peut que devenir `RECONCILED`, avec preuve, justification expurgée et version attendue.
Une nouvelle initialisation reste explicite et ne peut pas contourner une fenêtre qui se chevauche.
Le lot n'expose aucune commande de remboursement ni de réactivation automatique.

## 5. Transactions, profils et persistance

`ProviderBudgetService` expose les opérations courantes sous `control-api` et `batch-worker`.
`ProviderBudgetAdministration` expose initialisation et réconciliations seulement sous `control-api`.
Les deux façades utilisent `Propagation.NEVER` : une transaction appelante déjà ouverte est une
erreur de protocole transactionnel. Elles délèguent à des composants transactionnels distincts ;
le résultat de nouvelle autorisation d'envoi ne revient qu'après commit effectif. Un futur client
HTTP ne doit être appelé qu'après ce retour, jamais en conservant un verrou PostgreSQL.

L'ordre des verrous est périmètre, fenêtre, intention. Les mutations rechargent l'état sous verrou,
réévaluent l'heure après attente et utilisent un compare-and-set défensif. La création d'un
périmètre ou d'une intention emploie `INSERT ... ON CONFLICT DO NOTHING`, puis un `SELECT`
séparé de la ligne réellement conservée. Le verrou global du catalogue n'est pas utilisé.
Le journal, la projection, les incidents et les observations associés appartiennent à la même
transaction. Le calcul part des intentions durables ; aucun compteur local secondaire n'est
réécrit à partir des réponses fournisseur.

L'identité administrative vient de `BudgetOperatorIdentityProvider`, via la propriété locale
`betting.operator.id` alimentée par `BETTING_OPERATOR_ID`. Elle n'est jamais dans les commandes.
Une identité absente ou invalide n'empêche pas les lectures, mais interdit les nouvelles mutations
administratives. La justification obligatoire, de 1 à 1 000 caractères après nettoyage initial,
est expurgée par `BudgetJustificationSanitizer` avant écriture : règles de sécurité partagées,
secrets reconnus, clés privées et caractères de contrôle. Aucune valeur opérateur réelle n'est
versionnée ; les messages d'erreur ne recopient pas les données soumises.

V009 ajoute sept tables :

| Table | Rôle |
|---|---|
| `provider_budget_scope` | Identité du périmètre et point de sérialisation |
| `provider_budget_window` | Projection versionnée et observation courante |
| `provider_call_intent` | Réservation, engagement, résultat et version |
| `provider_quota_observation` | Preuve de compteur immuable |
| `provider_quota_observation_intent` | Couverture explicite dans la même fenêtre |
| `provider_budget_incident` | Incident durable, sans suppression par réconciliation |
| `provider_budget_event` | Journal append-only des opérations |

Les clés étrangères composées empêchent une provenance inter-fenêtres. Les contraintes couvrent
bornes, états, horodatages, versions, clés et empreintes. L'absence de chevauchement historique est
vérifiée sous verrou de périmètre ; l'unicité de fenêtre ouverte est également contrainte en base.
Le port ne propose aucune mutation des observations, couvertures, incidents ou événements. Comme
les journaux CAT-002, cette frontière applicative ne retire pas les pouvoirs DDL au propriétaire
PostgreSQL ; la séparation des rôles de production reste une responsabilité OPS-001.

V001–V008, `provider_call_audit` et les outils de benchmark ENR restent inchangés. Le budget ne
remplace pas l'audit des octets HTTP. `collection` ne dépend pas de `catalog` ; les ports sont sans
JDBC et les modèles sans Spring. Tous les nouveaux composants persistants sont absents de `replay`.
Le lot n'ajoute ni V010, ni endpoint, ni worker, ni ordonnanceur, ni envoi par outbox. Les connecteurs
et leurs preuves brutes relèvent du lot 3 ; l'orchestration et les effets externes du lot 4.

## 6. Scénarios de preuve et qualification

### Raccordement calendrier au lot 3

Le [connecteur calendrier](provider-calendar-collection-v1.md) conserve le résultat HTTP et son
audit dans la même transaction que le résultat budgétaire. Un statut 401/403/429 connu suspend la
fenêtre même si le corps est incomplet ou écarté pour écho de secret. Un compteur reçu est transmis
avec couverture d'engagements vide et sans renouveler l'horizon de validité initial. Un compteur
hors capacité est audité et crée une incohérence bloquante à réconcilier. Réenregistrer une réponse
identique ne crée aucun nouvel événement, incident ou observation budgétaire.

### Preuves historiques du lot 2

| Invariant | Sources de tests |
|---|---|
| États, absence de remboursement, clés/empreintes/temps | `BudgetModelTest` |
| Trois bornes, 80/20, 12 appels antérieurs, couverture et cadence | `BudgetCalculatorTest` |
| Réservations, collisions, dernier créneau, transactions distinctes | `ProviderBudgetServiceIT` |
| Résultats désordonnés, preuves périmées/contradictoires, réconciliation | `ProviderBudgetServiceIT` |
| Rollback de l'historique/incident, suspension isolée, commit avant retour | `ProviderBudgetServiceIT` |
| Fermeture réelle du contexte et pool, reprise sans nouvelle autorisation | `ProviderBudgetServiceIT` |
| Installation neuve, V008 peuplée, contraintes et provenance | `V009MigrationIT` |
| Confinement des trois profils et absence de cycles/JDBC applicatif | Tests de profils et ArchUnit |

Cette matrice référence le code des scénarios ; elle ne constitue pas un résultat d'exécution.
La qualification du lot consigne les commandes, totaux réels, omissions et résultats dans le
Work Order. La preuve requise est `mvnw.cmd -Pintegration verify` avec PostgreSQL/Testcontainers,
complétée par le contrôle de secrets, le diff, les espaces finaux et les empreintes immuables.
Un build standard seul, un Testcontainers indisponible ou une campagne externe ne qualifie pas
la persistance du budget. Aucun total historique n'est requalifié comme résultat de cette exécution.
