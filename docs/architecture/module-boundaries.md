# Frontières modulaires

Le dépôt produit une seule application Spring Boot, mais organise le code sous huit modules métier de premier niveau :

| Module | Responsabilité initiale |
|---|---|
| `catalog` | Compétitions, saisons, équipes et rencontres canoniques |
| `collection` | Appels fournisseurs, snapshots bruts et replay |
| `identity` | Mappings, rapprochements et ambiguïtés d'identité |
| `qualification` | Éligibilité, qualité et raisons d'exclusion |
| `enrichment` | Compositions, statistiques et données complémentaires |
| `analysis` | Signaux, évaluations et futurs modèles |
| `publication` | Documents et canaux externes via l'outbox |
| `operations` | Profils, jobs persistants, audits et exploitation |

Chaque module peut contenir :

- `domain` : objets et règles sans dépendance à Spring, JDBC, HTTP ou aux adaptateurs ;
- `application` : cas d'usage et ports ;
- `adapter` : intégrations techniques entrantes ou sortantes.

Les adaptateurs dépendent des ports applicatifs. Les tests ArchUnit interdisent les cycles entre modules de premier niveau, les dépendances du domaine vers Spring ou les adaptateurs, les dépendances de l'application vers les adaptateurs, toute dépendance de la couche application vers JDBC, `java.sql`, `javax.sql` ou le pilote PostgreSQL, ainsi que toute dépendance de cette couche vers Spring HTTP/Web/MVC ou les API Servlet.

Depuis le lot 1 de CAT-002, le module `operations` applique cette frontière aux jobs et à l'outbox :

- `JobOutboxService` reste le cas d'usage transactionnel et dépend uniquement du port `JobOutboxRepository`, d'un `Clock` et des types applicatifs `PendingJob` et `PendingOutboxMessage` ;
- `JdbcJobOutboxRepository` est l'adaptateur PostgreSQL des profils `control-api` et `batch-worker` et porte seul le SQL des tables `persistent_job` et `outbox_message` ;
- le profil `replay` ne charge ni ce service ni ce port de persistance ;
- l'extraction conserve les clés d'idempotence et l'atomicité sans migration de schéma.

Depuis le lot 2 de CAT-002, le module `catalog` sépare aussi ses écritures de provenance et d'application :

- `FixtureObservationStore` conserve ou résout l'observation source immuable et `FixtureApplicationJournal` expose uniquement l'ajout d'une évaluation ;
- `JdbcFixtureObservationStore` et `JdbcFixtureApplicationJournal` portent le SQL PostgreSQL sous les seuls profils `control-api` et `batch-worker` ;
- `CatalogRepository` conserve la persistance des entités canoniques, sans reprendre l'écriture des observations ;
- le profil `replay` ne charge ni ces ports de persistance ni leurs adaptateurs ; il reste hors réseau, sans DataSource et sans Flyway.

Depuis le lot 3 de CAT-002, les décisions de catalogue sont également isolées sous forme de politiques pures :

- `FixtureChronologyPolicy` compare l'instant d'observation source, tous les faits canoniques et la matrice de transitions, sans Spring ni JDBC ; l'absence de tampon sur une ligne historique n'entraîne l'invention d'aucun instant d'autorité ;
- `FixtureIdentityPolicy` reconnaît l'ordre exact et n'autorise une inversion que lorsque l'observation porte explicitement `participantsUnordered=true` ; `neutralVenue` n'intervient jamais dans cette permission ;
- le port applicatif `CalendarAuthorityPolicy` résout une clé exacte et typée vers `PRIMARY`, `CONTROL` ou `UNASSIGNED`, avec sa version de politique ;
- `ClasspathCalendarAuthorityConfiguration`, limité à `control-api` et `batch-worker`, charge une configuration classpath versionnée. Sa baseline est vide et fermée par défaut afin de ne pas inventer de référence fournisseur ; le profil `replay` ne charge pas ce port ;
- ces politiques restent indépendantes de Spring, JDBC et des adaptateurs ; leur raccordement transactionnel appartient au service d'application.

Depuis le lot 4 de CAT-002, `CalendarNormalizationService` applique effectivement ces politiques et les ports de persistance protègent le catalogue contre les écritures concurrentes :

- `CalendarNormalizationLock` est un port applicatif ; son adaptateur PostgreSQL acquiert un verrou advisory transactionnel global avant l'évaluation des rencontres. Cette sérialisation conservatrice ne charge aucun composant JDBC sous le profil `replay` ;
- les compétitions, équipes et saisons sont créées ou résolues atomiquement ; les rencontres sont recherchées sous verrou, insérées ou résolues sur conflit, puis mises à jour par compare-and-set sur l'identifiant de la dernière observation d'autorité ; les mappings fournisseur sont insérés sans écraser une décision existante ;
- une configuration de production sans affectation exacte aboutit à `UNASSIGNED` : l'observation, le journal et l'anomalie sont conservés, mais aucune saison, rencontre canonique ou mapping de rencontre n'est créé. Aucune source n'est promue implicitement vers `PRIMARY` ;
- une observation `CONTROL` ne modifie jamais le canon ni son tampon d'autorité : elle produit `UNCHANGED` si ses faits concordent, `CONTROL_DIVERGENCE` s'ils divergent, ou `CONTROL_WITHOUT_PRIMARY` lorsqu'aucune rencontre primaire n'existe ;
- une observation `PRIMARY` est évaluée sur son seul `observedAt` et la matrice de transitions. Une observation antérieure est journalisée `STALE` sans mutation canonique, des faits contradictoires au même instant ou une transition interdite ouvrent une anomalie, et une observation postérieure concordante avance le tampon d'autorité sans inventer de changement métier ;
- l'identité reste exactement ordonnée par défaut. Une inversion n'est admissible que si l'observation déclare `participantsUnordered=true` ; l'ordre source reste dans l'observation et l'ordre de la rencontre canonique existante est préservé ;
- chaque évaluation journalise le rôle, la version de politique et l'autorité précédente dans la même transaction que l'observation et l'éventuelle mutation canonique.

Le lot 4 réutilise intégralement le schéma `V003` et n'ajoute aucune migration propre.

Depuis le lot 5 de CAT-002, le module `catalog` porte également le cas d'usage de décision humaine et le module `identity` sépare l'état courant des mappings et anomalies de leur historique :

- `MappingDecisionService`, chargé uniquement sous `control-api`, reçoit une commande typée avec version attendue et clé d'idempotence. L'identité opérateur provient du port `OperatorIdentityProvider` et n'est jamais fournie par la commande ;
- une création de mapping réserve `expectedVersion=0`, tandis qu'une modification utilise un compare-and-set sur une version positive. Une décision concurrente ne produit ni mutation, ni reçu réussi, ni ligne d'historique ;
- `ControlCommandReceiptStore` conserve le reçu idempotent et son empreinte de commande, `ProviderMappingDecisionJournal` ajoute l'historique humain sans opération de modification et `MappingDecisionAnomalyStore` corrèle la décision aux anomalies ouvertes de même contexte sans les résoudre prématurément ;
- la répétition de la même clé et du même contenu retrouve la décision initiale. La réutilisation de la clé avec un autre contenu est un conflit et ne crée aucun nouvel effet ;
- `NormalizationAnomalyLifecycleService`, disponible sous `control-api` et `batch-worker`, maintient une projection versionnée et un journal append-only `OPENED`, `OBSERVED`, `RESOLVED` ou `REOPENED`. Une anomalie `IGNORED` reste ignorée lorsqu'elle est de nouveau observée ;
- seule l'évaluation complète d'un snapshot v2 ou v3 peut résoudre une anomalie qui n'est plus reproduite. Un payload invalide, un schéma inconnu ou v1 ne déclenche aucune résolution implicite ; une application `STALE` n'ouvre pas d'anomalie propre ;
- les anomalies produites pendant l'évaluation d'une rencontre peuvent référencer le `FixtureApplicationLog` exact. Une défaillance du journal d'événements annule dans la même transaction la projection d'anomalie, l'observation, l'application et toute mutation canonique ;
- une occurrence contextualisée peut enrichir sous verrou une unique anomalie historique dont saison et phase étaient inconnues. Plusieurs candidates interdisent tout rattachement arbitraire et conduisent à conserver ou créer le contexte exact séparément.

La migration additive `V004__mapping_decisions_and_anomaly_lifecycle.sql` matérialise les versions de mapping, les reçus de commande, le journal des décisions, leurs corrélations aux anomalies, la projection de cycle de vie et son journal d'événements. Les migrations `V001`, `V002` et `V003` restent immuables.

Depuis le lot 6 de CAT-002, le rejeu d'un snapshot déjà stocké respecte les mêmes frontières applicatives :

- un port de lecture relit les octets et les métadonnées du `raw_snapshot` par UUID ou résout une empreinte SHA-256 unique ; aucun port n'accepte un chemin de fichier ;
- les ports de demande, de claim compare-and-set, de journal de tentative et de corrélation exposent les intentions métier sans SQL, JDBC ou type PostgreSQL ; leurs adaptateurs PostgreSQL sont confinés au profil `control-api` ;
- un port d'exécution après commit sépare la création durable de la tentative immédiate. Un autre port encadre le savepoint qui permet d'annuler une normalisation partielle tout en conservant l'échec reprenable de la tentative ;
- le cas d'usage de création écrit le reçu idempotent et la demande dans une première transaction. Le cas d'usage d'exécution revendique puis termine la demande dans une seconde transaction indépendante et réutilise le chemin normal de `CalendarNormalizationService` ;
- `MappingDecisionService` délègue à un coordinateur applicatif étroit : la décision, son historique et une demande par snapshot corrélé sont créés atomiquement, puis les tentatives ne commencent qu'après le commit humain ;
- chaque tentative ajoute son historique et ses corrélations exactes aux nouveaux journaux d'application de fixture et événements d'anomalie. Ces liens n'autorisent aucune mise à jour des journaux d'origine ;
- la reprise est une commande interne et idempotente du `control-api`. Aucun poller, scheduler ou worker de rejeu stocké n'est chargé sous `batch-worker` ; aucun endpoint HTTP n'est livré par le lot 6 ;
- le profil `replay` n'importe aucun de ces ports ou adaptateurs et demeure strictement hors réseau, sans DataSource et sans Flyway. Le rejeu de fichiers ou d'octets reste son seul périmètre.

La migration additive `V005__durable_snapshot_replay.sql` matérialise la projection versionnée des demandes, le journal append-only des tentatives et les corrélations aux applications et événements d'anomalie. Elle n'invente aucune demande historique et laisse `V001` à `V004` immuables. Le contrat normatif est décrit dans [`stored-snapshot-replay-v1.md`](../contracts/stored-snapshot-replay-v1.md).

Depuis le lot 7 de CAT-002, les lectures et l'adaptateur HTTP interne respectent une frontière supplémentaire :

- `AnomalyQueryPort`, `MappingQueryPort` et `ReplayQueryPort` portent des filtres, ancres keyset et read models applicatifs sans JDBC, HTTP ou payload brut ;
- leurs services de consultation sont transactionnels en lecture seule et ne sont chargés que sous `control-api` ;
- `JdbcAnomalyQueryAdapter`, `JdbcMappingQueryAdapter` et `JdbcReplayQueryAdapter`, également limités à `control-api`, portent le SQL statique et les paramètres liés sans étendre les repositories d'écriture utilisés par `batch-worker` ;
- le codec de curseur opaque, la validation JSON stricte, la garde des paramètres et la traduction en `ProblemDetail` appartiennent exclusivement à `catalog.adapter.web` ; aucune de ces préoccupations n'entre dans `application` ;
- les contrôleurs `/internal/catalog` sont confinés à `control-api`. Ils ne sont chargés ni sous `batch-worker`, ni sous `replay`, et le serveur local écoute sur `127.0.0.1` ;
- les projections de provenance excluent toujours `raw_snapshot.payload`, les reçus et les clés ou digests d'idempotence ;
- les commandes web délèguent aux cas d'usage des lots 5 et 6 puis relisent l'état durable. Elles n'ajoutent aucun worker, poller, ordonnanceur ou appel fournisseur.

Le contrat normatif est décrit dans [`catalog-control-api-v1.md`](../contracts/catalog-control-api-v1.md). Le lot 7 n'ajoute aucune migration et conserve V001 à V005 immuables.

Le lot 8 ne modifie aucune frontière de production. Son acceptation intégrée ajoute une preuve de redémarrage qui démarre puis ferme trois contextes `control-api` successifs sur une même base PostgreSQL : la première transaction laisse une demande durable `PENDING`, la seconde instance la reprend et la termine, et la troisième confirme sa terminalité sans nouvelle observation ni nouvel effet logique. Cette preuve utilise les ports et adaptateurs existants, sans worker, poller, nouvelle migration ou profil. Le dossier de revue finale et son manifeste SHA-256 bornent ensuite le diff soumis au porteur avant toute opération Git.

Le package `bootstrap` contient uniquement l'assemblage de l'application. Le package `shared` contient les primitives transverses qui ne portent pas une règle métier propre à un module.
