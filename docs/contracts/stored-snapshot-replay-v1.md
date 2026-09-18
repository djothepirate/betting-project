# Contrat de rejeu d'un snapshot stocké v1

## Objet et périmètre

Le contrat `stored-snapshot-replay-v1` définit le rejeu contrôlé d'un `raw_snapshot` déjà conservé dans PostgreSQL. Il ne décrit ni un appel fournisseur, ni le chargement d'un fichier arbitraire, ni une boucle de worker.

Le rejeu stocké CAT-002 décrit par ce contrat appartient exclusivement au profil `control-api` :

- le cas d'usage crée durablement une demande, tente son exécution après commit, puis permet la consultation interne et la reprise manuelle d'une demande non terminale ;
- le lot 7 expose la création, la consultation et la reprise manuelle sous `/internal/catalog/replay-requests`, conformément à [`catalog-control-api-v1`](catalog-control-api-v1.md) ;
- aucun composant de rejeu stocké n'est chargé sous `batch-worker` ; aucune boucle de polling ou d'exécution automatique en arrière-plan n'est introduite ;
- le profil `replay` conserve uniquement le rejeu de fichiers ou d'octets fournis explicitement. Il reste strictement hors réseau, sans DataSource et sans Flyway.

Le payload stocké reste la preuve de référence. Le rejeu réutilise le parseur, le normaliseur, les politiques d'autorité et le cycle de vie des anomalies existants ; il ne contient aucun chemin alternatif de normalisation.

MVP-001 lot 4 ajoute séparément un job de replay de **page native fournisseur** par UUID, décrit
dans [collection-jobs-v1](collection-jobs-v1.md). Il ne consomme pas les demandes CAT-002, ne
modifie pas leurs états, clés ou callbacks, et ne charge pas leurs composants sous `batch-worker`.
Cette distinction ne change pas le profil `replay`, qui reste sans base ni réseau.

## Sélecteurs autorisés

Une demande manuelle choisit exactement un des deux sélecteurs suivants :

| Sélecteur | Forme | Résolution |
|---|---|---|
| `SNAPSHOT_ID` | UUID canonique minuscule du `raw_snapshot` | L'UUID doit désigner exactement une ligne existante. |
| `PAYLOAD_SHA256` | chaîne hexadécimale minuscule de 64 caractères | L'empreinte doit désigner exactement une ligne dans l'ensemble des snapshots stockés. |

Pour une recherche par empreinte :

- aucune ligne produit un résultat explicite `NOT_FOUND` ;
- plusieurs lignes produisent un résultat explicite `AMBIGUOUS_SHA256` ;
- une seule ligne permet la création de la demande.

Une ambiguïté n'est jamais résolue par le fournisseur, l'endpoint, la date de réception ou l'ordre des lignes. Le demandeur doit alors sélectionner l'UUID exact.

Un chemin de fichier n'est jamais un sélecteur valide. Aucun champ `path`, URI, chemin relatif ou chemin absolu n'est accepté par le cas d'usage. L'adaptateur HTTP du lot 7 rejette explicitement `path`, `uri`, `file` et `filePath`, y compris imbriqués, avec `400 ARBITRARY_PATH_FORBIDDEN` au lieu de les transmettre à une API de fichiers.

Les demandes dérivées d'une décision de mapping sélectionnent toujours le snapshot corrélé par son UUID. Elles ne réexécutent aucune recherche par empreinte.

## Validation de la preuve stockée

La demande conserve au moment de sa création :

- l'UUID réel du snapshot résolu ;
- son empreinte SHA-256 attendue ;
- le type et la valeur du sélecteur utilisés ;
- l'origine `MANUAL` ou `MAPPING_DECISION` ;
- le reçu de commande idempotent ;
- pour l'origine `MAPPING_DECISION`, l'UUID de la décision humaine.

Avant tout parsing, chaque tentative relit la ligne PostgreSQL et vérifie :

1. que le snapshot existe encore ;
2. que son mode de compression est pris en charge par le rejeu ; la baseline accepte uniquement `identity` ;
3. que l'empreinte enregistrée sur la ligne correspond à l'empreinte attendue par la demande ;
4. que le SHA-256 recalculé à partir des octets relus correspond également à cette empreinte.

Une altération des octets, une incohérence d'empreinte ou une compression non prise en charge empêche le parsing et produit un échec terminal audité. Aucun hash n'est recalculé puis substitué silencieusement à celui de la preuve.

Le payload n'est ni réécrit ni enrichi. Son fournisseur, son endpoint logique, son instant de réception et sa version de connecteur restent ceux de la ligne d'origine.

## Idempotence des commandes

Toute création manuelle exige une clé d'idempotence de 1 à 128 caractères ASCII visibles, sans espace ni caractère de contrôle. Elle utilise le type de commande `REPLAY_REQUEST` dans le namespace global `control_command_receipt` introduit par CAT-002 :

- la même clé, le même type de commande et le même contenu canonique retournent la demande initiale, sans nouveau reçu ni nouvelle demande ;
- la même clé avec un autre type ou un autre contenu retourne `IDEMPOTENCY_CONFLICT`, sans écriture ;
- une commande invalide, un sélecteur absent, une empreinte ambiguë ou un snapshot absent ne laisse aucun reçu réussi.

L'empreinte de commande couvre une représentation canonique et versionnée du sélecteur. Elle ne dépend pas de l'heure de l'appel ou de l'état courant de la demande.

Pour une décision humaine de mapping, la clé interne est déterministe :

```text
mapping-decision:{decisionId}:snapshot:{snapshotId}
```

Une seule demande peut être corrélée à une même paire décision/snapshot. Plusieurs anomalies portant le même snapshot ne dupliquent pas la demande. Une décision sans snapshot corrélé reste valide et ne crée aucune demande.

L'idempotence de commande n'interdit pas à un opérateur de créer ultérieurement, avec une nouvelle clé, une nouvelle demande visant le même snapshot. Les contraintes déjà présentes sur le snapshot brut, l'observation et le catalogue empêchent alors les effets canoniques en double, tandis que chaque nouvelle évaluation reste traçable.

## États d'une demande

| État | Terminal | Sens et transitions permises |
|---|---:|---|
| `PENDING` | non | Demande durable créée mais pas encore revendiquée. Peut devenir `RUNNING`. |
| `RUNNING` | non | Demande revendiquée par compare-and-set dans la transaction d'exécution. Peut devenir `COMPLETED`, `FAILED_RETRYABLE` ou `FAILED_TERMINAL`. |
| `COMPLETED` | oui | Le payload a été vérifié et l'évaluation complète a été commitée avec son historique. Aucune reprise ne crée une nouvelle tentative. |
| `FAILED_RETRYABLE` | non | Une tentative a échoué sans invalider durablement la preuve. Une reprise manuelle peut la revendiquer de nouveau. |
| `FAILED_TERMINAL` | oui | Une condition durable, notamment une preuve altérée ou une compression non prise en charge, interdit toute nouvelle exécution. |

La projection de demande porte une version optimiste et un compteur de tentatives. Un claim compare-and-set ne peut réussir qu'à partir d'un état non terminal attendu. Une concurrence de reprise produit au plus une nouvelle tentative ; l'appel concurrent qui ne gagne pas le claim ne modifie ni la demande ni l'historique.

`COMPLETED` et `FAILED_TERMINAL` sont immuables du point de vue du cas d'usage. Relancer leur UUID retourne le résultat terminal déjà connu.

## Transactions et exécution après commit

### Transaction 1 — création durable

Pour une demande manuelle, la première transaction :

1. valide la commande et son sélecteur ;
2. acquiert le verrou de clé d'idempotence ;
3. résout exactement le snapshot ;
4. ajoute le reçu idempotent ;
5. crée la demande en `PENDING`.

Pour une décision de mapping, les opérations suivantes partagent la transaction de la décision humaine :

1. compare-and-set du mapping courant ;
2. ajout de la décision append-only ;
3. corrélation aux anomalies ouvertes ;
4. création atomique d'une demande par snapshot distinct corrélé, avec sa clé interne déterministe.

Aucun payload n'est parsé dans cette transaction. Un échec de création du reçu ou d'une demande annule la décision et ses corrélations ; inversement, aucune demande orpheline n'est publiée avant le commit.

### Après commit

Après le commit de la transaction 1, le même cas d'usage du `control-api` tente immédiatement chaque demande créée tant qu'elle est encore `PENDING`. Le callback après commit ne revendique jamais un état `FAILED_RETRYABLE` : cet état exige la commande de reprise manuelle avec sa version attendue. Le callback ne constitue ni un worker, ni une garantie de livraison en arrière-plan.

Un arrêt du processus entre le commit et le callback laisse une demande `PENDING` durable. Un échec du callback ne retire jamais la décision humaine et laisse la demande dans un état non terminal reprenable.

### Transaction 2 — tentative isolée

Chaque demande est exécutée dans une nouvelle transaction indépendante :

1. charger la projection et retourner immédiatement un résultat terminal déjà connu ;
2. revendiquer optimistement la demande et incrémenter son numéro de tentative ;
3. relire puis vérifier compression et empreintes du snapshot ;
4. acquérir le verrou global de normalisation calendrier ;
5. parser et normaliser le snapshot par le même chemin que toute autre normalisation ;
6. ajouter une tentative append-only et ses corrélations exactes aux journaux produits ;
7. faire évoluer la demande vers son nouvel état ;
8. commit.

La partie parsing/normalisation est protégée par un savepoint interne. Si elle échoue, ses écritures partielles sont annulées avant que la tentative `FAILED_RETRYABLE` et la projection de demande puissent être validées. Une défaillance lors de l'ajout de la tentative, d'une corrélation ou de la mise à jour finale annule toute la transaction 2 ; la demande conserve alors son dernier état durable reprenable.

La transaction 2 ne peut jamais annuler la transaction 1 déjà commitée. Une erreur de rejeu ne retire donc ni la décision humaine, ni son historique, ni la demande durable.

## Historique append-only et corrélations

Chaque tentative effectivement revendiquée produit une ligne append-only avec :

- l'UUID de la demande et son numéro de tentative ;
- les empreintes attendue et recalculée ;
- l'issue `COMPLETED`, `FAILED_RETRYABLE` ou `FAILED_TERMINAL` ;
- les instants de début et de fin ;
- pour une réussite, la compatibilité du schéma et les compteurs de normalisation ;
- pour un échec, un code et un message expurgé, sans payload ni secret.

Une tentative réussie est corrélée exactement :

- à chaque nouveau `FixtureApplicationLog` produit par cette évaluation ;
- à chaque nouveau `NormalizationAnomalyEvent` produit par cette évaluation.

Les corrélations sont append-only et ne modifient pas les journaux d'origine. Le verrou global de normalisation entoure la délimitation des entrées avant et après l'évaluation afin qu'une autre normalisation ne puisse pas être attribuée à la tentative.

La chaîne d'audit devient ainsi :

```text
reçu de commande
  → demande de rejeu
    → snapshot brut
    → décision de mapping éventuelle
    → tentative(s)
      → applications de fixture
      → événements d'anomalie
```

Une décision de mapping reste corrélée aux anomalies qui ont motivé la demande. Elle ne les résout pas elle-même. Après un rejeu complet :

- une cause disparue produit l'événement `RESOLVED` sur la même projection d'anomalie ;
- une cause qui persiste produit `OBSERVED` ;
- une cause résolue qui réapparaît produit `REOPENED` ;
- un mapping rejeté n'est jamais recréé par rapprochement textuel et peut conduire à ouvrir ou observer `REJECTED_MAPPING`.

## Idempotence des effets de normalisation

La reprise ou la création d'une nouvelle demande sur le même snapshot ne duplique pas les preuves métier :

- `raw_snapshot` reste dédupliqué par sa clé existante ;
- `fixture_observation` reste dédupliquée par `(raw_snapshot_id, provider_fixture_id)` et son UUID réel est résolu ;
- les insertions canoniques et mappings utilisent les opérations atomiques existantes ;
- les mutations canoniques restent soumises à la chronologie, à l'autorité, au verrou global et au compare-and-set ;
- les journaux d'application, de tentative et d'événement conservent chaque évaluation réellement exécutée.

L'idempotence signifie donc « aucun effet canonique en double », pas « suppression de l'historique des tentatives ».

## Reprise manuelle et redémarrage

Le `control-api` expose un cas d'usage interne de reprise par UUID de demande. Depuis le lot 7, cette commande est disponible sous `/internal/catalog/replay-requests/{id}/resume`, avec une version attendue et sans chemin de fichier.

Une reprise :

- refuse une demande absente avec un résultat typé ;
- retourne sans effet un résultat terminal déjà connu ;
- tente un claim sur `PENDING` ou `FAILED_RETRYABLE` ;
- ne force jamais une demande `RUNNING` détenue par une autre transaction ;
- ajoute un nouveau numéro de tentative uniquement après un claim réussi.

Le claim et la fin de tentative appartiennent à la même transaction. Un arrêt brutal pendant cette transaction annule le claim ; après redémarrage, la demande retrouve donc son dernier état durable non terminal et peut être reprise. Aucune remise à zéro arbitraire de lignes `RUNNING` et aucune horloge de lease ne sont nécessaires dans cette baseline locale.

L'acceptation du lot 8 apporte une preuve de processus distincts : `ControlApiRestartIT` démarre un premier contexte Spring `control-api`, conserve le snapshot puis commit une demande `PENDING` tout en simulant l'arrêt avant le callback ; il ferme entièrement ce contexte et son pool JDBC. Un deuxième contexte sur la même base relit la demande et exécute sa reprise jusqu'à `COMPLETED`. Après une nouvelle fermeture, un troisième contexte retrouve le résultat terminal et refuse toute seconde tentative. Les assertions vérifient une seule demande, tentative, observation et application logique ainsi que les corrélations attendues.

Cette preuve ne transforme pas le callback après commit en mécanisme de livraison. En l'absence de worker ou poller, une demande laissée non terminale doit toujours être consultée puis reprise manuellement par le `control-api`.

## Résultats applicatifs et traduction HTTP

Les cas d'usage retournent des résultats typés indépendants de HTTP. Ils distinguent notamment :

- création et répétition idempotente ;
- sélecteur invalide ;
- snapshot absent ;
- empreinte ambiguë ;
- conflit d'idempotence ;
- demande absente ;
- claim non obtenu ;
- résultat terminal déjà connu ;
- exécution terminée ou laissée reprenable.

Le lot 7 traduit ces résultats selon `catalog-control-api-v1` : validation et champ arbitraire en `400`, absence en `404`, ambiguïté ou conflit d'idempotence en `409`, résultat terminal en `200`, et demande créée ou non terminale en `202`. Une version obsolète lors d'une reprise retourne également `409`. Les réponses relisent l'état durable après le callback et n'exposent jamais le payload, les reçus ou les clés d'idempotence.

## Schéma PostgreSQL

La migration additive `V005__durable_snapshot_replay.sql` crée :

- `normalization_replay_request`, projection versionnée de la demande ;
- `normalization_replay_attempt`, journal append-only des tentatives ;
- `normalization_replay_attempt_application`, corrélation aux applications de fixture ;
- `normalization_replay_attempt_anomaly_event`, corrélation aux événements d'anomalie.

Elle ne modifie pas les migrations antérieures et ne rétrocrée aucune demande ou tentative pour les snapshots déjà présents. Les clés étrangères conservent la traçabilité vers le reçu, le snapshot et, le cas échéant, la décision de mapping.

## Limites préservées après le lot 8

Les lots 6 et 7 n'introduisent :

- aucun endpoint public ou accessible hors boucle locale ;
- aucun sélecteur par chemin ;
- aucun accès réseau ou appel fournisseur ;
- aucun worker, poller, planificateur ou claim automatique sous `batch-worker` ;
- aucun changement au profil `replay` ;
- aucune nouvelle décision d'autorité calendrier.

Le lot 7 ajoute uniquement les projections paginées, les DTO stricts et la traduction HTTP interne sous `control-api`. Le lot 8 ajoute uniquement la preuve de redémarrage et la documentation d'acceptation. Une future automatisation de worker appartient à MVP-001 et devra réutiliser les mêmes états et clés d'idempotence sans modifier ce contrat de preuve.
