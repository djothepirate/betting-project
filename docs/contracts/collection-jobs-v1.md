# Jobs de collecte et planification v1

- Contrat : `collection-jobs-v1`, MVP-001 lot 4, 19 septembre 2026.
- Persistance : V011 additive ; V001 à V010 immuables.
- Réalisation hors fournisseur réel ; registre classpath vide et transports désactivés.

## Planification explicite

`CalendarJobPlanningService` appartient au `control-api`, sans nouvel endpoint HTTP. `planDay`
reçoit l'UUID du plan, une date UTC, une échéance UTC explicite et une à quatre routes du noyau
PPL/PD/DED/ELC. Chaque route contient les bindings exacts capacité fournisseur, fenêtre existante
et année de filtre. Le service n'invente ni calendrier récurrent, ni phase, ni saison, ni fenêtre.
Les capacités sont celles du registre exact ; les codes logiques ne créent pas de mapping.

Le primaire est planifié avant ses contrôles, qui conservent leur ordre déterministe du routeur.
Cet ordre de soumission n'est pas une dépendance d'exécution entre workers concurrents. Un contrôle
qui arrive avant le primaire conserve ses observations et le résultat `CONTROL_WITHOUT_PRIMARY`
du catalogue ; sa réévaluation demande ensuite un replay explicite, sans promotion d'autorité.
Sans primaire, le résultat est `NO_PRIMARY`, sans promotion d'un contrôle. Sans binding exact,
le résultat est `MISSING_EXACT_WINDOW_BINDING`, sans affectation de fenêtre voisine. Ces résultats
partiels sont explicites : ils ne constituent pas une preuve de couverture de toute la journée.

La clé de job dérive du plan et de la clé fournisseur exacte. Son contenu immuable couvre date,
fenêtre, année, capacité, empreinte du registre et version du parseur. Échéance (précision microseconde)
et nombre maximal de tentatives sont comparés séparément. Une répétition exacte retourne le même
job ; un autre contenu sous la même clé produit un conflit sans écriture. Job, entrée typée,
outbox et événement `ENQUEUED` partagent une transaction. Aucune réservation HTTP n'est prise
simplement en planifiant un travail futur.

Les opérations unitaires `enqueueDiscovery` et `enqueueReplay` sont également internes. La
seconde reçoit exclusivement l'UUID d'une page native stockée avec réponse complète, jamais un
chemin, une URL ou des octets fournis par une requête. Le registre et le parseur sont figés pour
les deux types de jobs ; un changement détecté donne `REGISTRY_CHANGED` ou `PARSER_CHANGED`.

## Types et périmètre de dispatch

Le vocabulaire comprend `CALENDAR_DISCOVERY`, `PREMATCH_ENRICHMENT`, `POSTMATCH_ENRICHMENT`,
`POSTMATCH_RECHECK` et `REPLAY_NORMALIZATION`. Seuls le premier et le dernier sont exécutables
dans ce lot. Les trois types d'enrichissement sont refusés à la soumission, pas acquittés en succès.

Le moteur ne revendique que `persistent_job.managed_collection=true` et l'outbox associée
`COLLECTION_JOB`. Les anciens jobs, messages J7, publications et outboxes des appels HTTP
`PROVIDER_CALENDAR` ne sont jamais pris par un consommateur générique. L'outbox de job ne contient
que son UUID ; sa clé est `collection-job:{jobId}`. La page conserve sa propre intention,
son audit et son outbox d'appel déjà livrés au lot 3.

Les demandes CAT-002 `normalization_replay_request` demeurent sous leur contrat et leur commande
manuelle du `control-api`. Le job `REPLAY_NORMALIZATION` de ce lot concerne une page **native**
MVP-001 et ne consomme pas ces demandes, ne change pas leurs clés et ne charge pas leurs beans
dans `batch-worker`. Dans les deux cas, l'application canonique utilise le même normaliseur.

## Claims, transactions et fencing

| Opération | Frontière durable |
|---|---|
| Planification | Job + entrée exacte + outbox + événement, tous ou aucun |
| Claim | Ligne due `FOR UPDATE SKIP LOCKED`, état `RUNNING`, tentative, version, token UUID et événement |
| Étape de collecte | Verrou du job, vérification du token et du bail, puis budget/périmètre/fenêtre/intention si nécessaire |
| Appel HTTP | Après commit de l'autorisation, sans transaction ni verrou PostgreSQL |
| Réception | Fencing, ledger/résultat/incidents, brut, audit et outbox d'appel atomiques |
| Application | Fencing, normaliseur existant et journal de dérivation atomiques |
| Fin | État du job, outbox et événement cohérents dans une transaction |

L'horloge des baux et échéances d'exécution est `clock_timestamp()` PostgreSQL, pas l'horloge
d'un worker. Le bail est de **120 secondes** et est renouvelé à chaque frontière de base protégée,
après vérification de sa validité. Il n'existe pas de heartbeat autonome prolongeant un travail
sans propriétaire. Un détenteur expiré ou dont le token a changé reçoit `JobLeaseLostException` ;
il ne peut ni écrire un nouvel effet sous ce token, ni acquitter le job.

Le claim commit avant le handler. Une expiration est traitée sous verrou, avec événement
`LEASE_EXPIRED`. Dix expirations au maximum sont traitées par tick, puis un seul nouveau job.
Les transactions courtes maintiennent l'ordre job → périmètre budgétaire → fenêtre → intention ;
le verrou global du catalogue n'est utilisé qu'à la normalisation existante.

Le fencing n'est pas une transaction distribuée avec le fournisseur. Un processus peut perdre
son bail entre le dernier contrôle et le transport. Son intention étant déjà engagée, un nouveau
worker ne reçoit jamais une seconde permission. Une réponse qui revient à un détenteur désormais
expiré n'est pas appliquée : le coût reste conservé et la preuve manquante est signalée, sans
prétendre que l'appel n'a pas eu lieu ou fabriquer une réponse.

## Reprises et idempotence

La reprise gérée conserve l'UUID du job comme UUID de collecte, et les clés d'intentions
`calendar:{collectionId}:{offset}`. Une collecte manuelle du lot 3 conserve son comportement :
répéter `collect(command)` ne reprend pas implicitement un état `RUNNING`.

| État observé à la reprise gérée | Action |
|---|---|
| Aucune intention | Réservation et première autorisation permises si budget admissible |
| Réservation jamais engagée | Même intention, première autorisation éventuelle |
| Engagement acquis, pas de réponse | `UNCERTAIN`, incident, page `SEND_UNCERTAIN`, job en échec ; aucun envoi |
| Réponse conservée, application non commitée | Parsing depuis les octets stockés puis application atomique |
| Page déjà appliquée | Reconstruction du parcours/pagination, sans seconde application logique |
| Collecte déjà complète | Acquittement du job sans appel ou normalisation supplémentaire |
| Timeout, erreur HTTP, réponse incompatible ou pagination invalide | Résultat incomplet explicite, sans réémission automatique |

`SEND_UNCERTAIN` conserve `received_at`, statut HTTP et snapshot à `NULL`. L'instant de détection
est porté par les événements de job/budget ; il ne devient pas une fausse heure de réception.
L'outbox d'appel est mise en échec. Aucun coût engagé n'est remboursé. Une intention initialement
engagée sans résultat peut aussi rester telle quelle si toutes les tentatives sont épuisées
avant sa reprise : elle reste comptabilisée, jamais transformée automatiquement en réservation.

Une page reçue avant une panne ne sera pas téléchargée de nouveau. Les pages encore jamais
engagées peuvent être demandées après reprise des preuves antérieures et contrôle de pagination.
Une erreur de stockage ne convertit pas un appel possiblement envoyé en tentative sans coût.

Pour un job de replay natif, `collection_job_effect` enregistre le résultat de l'effet
`replay-page:{pageId}` dans la transaction de normalisation et de dérivation. Perdre l'acquittement
du job après ce commit ne refait pas l'effet. Une nouvelle clé explicite peut demander une autre
interprétation ; cette nouvelle demande est distincte, sans duplication d'identité canonique.

## Tentatives et backoff

Un job accepte **1 à 10 tentatives**, avec **3** par défaut pour `planDay`. Chaque claim compte
une tentative même si une panne survient avant le transport ; ce nombre n'est pas le compteur
d'appels HTTP. Le backoff vaut `min(300 s, 5 s × 2^(tentative−1))`. Il est persisté en échéance,
sans attente active ni sommeil dans le cas d'usage.

Les erreurs techniques reprenables et `RATE_LIMITED` passent à `RETRY` dans cette borne. La cadence
réutilise la même réservation, sans débit supplémentaire. Une erreur de fournisseur déjà reçue,
une preuve incompatible, un envoi incertain ou un changement de configuration restent terminaux.
À épuisement : `FAILED / ATTEMPTS_EXHAUSTED`. Les erreurs durables sont des codes génériques,
jamais le message d'une exception. L'historique `collection_job_event` reste append-only.

Un job échoué peut conserver une réservation jamais engagée ; aucune libération automatique
n'est déduite de son échec. Une décision opérateur peut utiliser le cas d'usage budgétaire existant
après inspection. Un débit engagé n'est jamais remboursable par cette opération.

## Profils et activation

- `control-api` : planification interne, lecture/écriture durable par les ports, pas de worker.
- `batch-worker` : moteur et deux handlers ; le tick peut être testé explicitement sans boucle.
- `replay` : aucune persistance, aucun job, aucun transport, aucune planification.
- La boucle `CollectionWorkerLoop` n'existe que sous `batch-worker` et avec
  `betting.collection.worker.enabled=true`. Elle est absente par défaut, cadence fixe d'une seconde
  après la fin du tick, sans exécution concurrente locale implicite.

Ce flag n'active aucun fournisseur ni compte API. Le registre reste vide ; fenêtres, preuves,
mappings et clients réels demandent toujours une activation séparée. Aucun endpoint, profil live,
consommateur J7, enrichissement ou sélection quotidienne de sept rencontres n'est ajouté ici.
Le contrôle API et la sélection restent au lot 5 ; la qualification finale et la PR au lot 6.
