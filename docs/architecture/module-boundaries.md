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
- `ClasspathCalendarAuthorityConfiguration`, limité à `control-api` et `batch-worker`, assemble le port d'autorité. Le chargement CAT-002 historique est remplacé au lot 1 de MVP-001 par le registre décrit ci-dessous, sans fallback ; le profil `replay` ne charge pas ce port ;
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

Depuis le lot 1 de MVP-001, `collection.domain.capability` définit les clés fournisseur et routes
logiques exactes, la couverture, l'autorité distincte et les preuves de capacité. Les ports et
le routage pur sont dans `collection.application.capability`. Le parseur strict et l'assemblage
classpath restent dans `collection.adapter.configuration`, sans modifier Jackson globalement.
`catalog.application.RegistryCalendarAuthorityPolicy` consulte uniquement le port de registre :
la dépendance va de `catalog` vers `collection`, jamais dans l'autre sens. Le SHA-256 du document
devient la version de politique du journal et du watermark existants. Registre, routeur et politique
sont limités à `control-api` et `batch-worker`, absents sous `replay`. La baseline réelle reste vide.
Le routage ne réalise aucun HTTP, réservation budgétaire, mapping automatique ou travail planifié.
Voir le [contrat du registre](../contracts/provider-capability-registry-v1.md).

Depuis le lot 2 de MVP-001, `collection.domain.budget` porte les fenêtres, intentions unitaires,
observations de quota, incidents, événements et calculs conservateurs, sans dépendance vers le
catalogue. Les trois bornes budgétaires utilisent les intentions persistées et leur couverture
explicite ; aucune réponse fournisseur ne réécrit un second compteur local. La cadence est une
contrainte indépendante, évaluée au passage avant envoi sur toutes les fenêtres du même périmètre.

- `ProviderBudgetRepository` est un port de `collection.application.budget` ;
  `JdbcProviderBudgetRepository` porte seul le SQL de V009, sous `control-api` et `batch-worker`.
- `ProviderBudgetService` et `ProviderBudgetAdministration` sont des façades `Propagation.NEVER` :
  elles refusent une transaction appelante ouverte et délèguent à des composants transactionnels
  distincts. Une nouvelle autorisation d'envoi n'est observable qu'après leur commit effectif.
  Aucun client HTTP n'est appelé dans ces transactions.
- Les verrous suivent l'ordre périmètre, fenêtre, intention ; les écritures sont non destructives
  et protégées par version. Le verrou global de normalisation calendrier n'est pas réutilisé.
  Réservation, résultat, incident, observation et événement associé sont atomiques.
- Les opérations courantes et la persistance sont disponibles sous `control-api` et `batch-worker`.
  L'initialisation et les réconciliations administratives, leur identité locale et leur expurgation
  sont limitées à `control-api`. Les ports d'identité et d'expurgation restent propres à `collection`,
  sans dépendance vers les services du catalogue.
- Le passage avant envoi conserve son coût après interruption ; une répétition ne délivre pas de
  seconde autorisation. `UNCERTAIN` ne suspend pas les autres intentions, tandis que HTTP 401/403/429
  suspend la fenêtre concernée. La réconciliation n'efface aucun débit ni incident historique.
- V009 ajoute les périmètres, fenêtres, intentions, observations, liens de couverture, incidents
  et événements. Les preuves et le journal sont append-only par les ports ; les clés étrangères
  composées imposent leur provenance dans la même fenêtre. V001–V008 restent immuables et aucune
  ligne de fournisseur réel n'est initialisée.
- Tous les composants budgétaires persistants sont absents de `replay`. Aucun endpoint, worker,
  poller, ordonnanceur, appel fournisseur ou dispatch d'outbox n'est ajouté par le lot 2.
  Le collecteur ENR et `provider_call_audit` restent distincts et inchangés.

Le contrat normatif [provider-budget-v1](../contracts/provider-budget-v1.md) distingue les preuves
de compteur, l'empreinte canonique du résultat budgétaire et les futurs hashes d'octets fournisseur.
Les connecteurs du lot 3 conservent les octets avant parsing ; l'orchestration du lot 4
devra utiliser l'autorisation commitée sans réexpédier une intention déjà engagée.

Depuis le lot 3 de MVP-001, les frontières calendrier natives sont explicites :

- `collection.application.calendar` porte les commandes, résultats, ports de transport,
  parsing, sérialisation, stockage et application canonique. Ses cas d'usage synchrones vérifient
  la capacité, utilisent le budget qualifié, conservent la preuve puis demandent son application.
  Ils ne créent aucun endpoint, worker, poller ou ordonnanceur ;
- `CalendarCollectionService` refuse une transaction appelante. La réservation, la préparation
  page/audit/outbox et l'autorisation budget sont durables avant l'envoi ; les transports HTTP
  refusent également de s'exécuter dans une transaction. Aucun verrou de base ne reste acquis
  pendant l'attente réseau ;
- `collection.adapter.http.calendar` porte seul les URI fixes, paramètres encodés, authentification,
  bornes réseau et garde JVM sans retry ni journalisation HTTP. Il est assemblé uniquement sous
  `control-api` et `batch-worker`, désactivé par défaut et sans dépendance au collecteur ENR ;
- `collection.adapter.replay.calendar` contient les parseurs natifs stricts et le codec v3, sans
  réseau ni JDBC. Ces composants restent utilisables en mémoire sous `replay`. Les champs natifs
  restent dans les adaptateurs ; inconnus descriptifs et ordre source ne sont jamais comblés ;
- `CalendarCollectionStore` est implémenté par `JdbcCalendarCollectionStore`, seul détenteur du
  SQL des collectes/pages/dérivations de V010, de leur audit et de leur outbox minimisée. Les
  corps natifs et dérivés réutilisent le port de snapshots, avec empreintes distinctes. Le résultat
  budgétaire, le brut, l'audit et la terminaison d'outbox sont enregistrés atomiquement ;
- `CalendarApplicationPort` est déclaré dans `collection` et implémenté côté `catalog` pour
  déléguer au normaliseur existant. La dépendance reste **catalog vers collection**, jamais
  collection vers catalog. Aucune seconde normalisation ne se développe dans un connecteur ;
- le contexte canonique de saison/phase provient de la route exacte et versionnée du registre,
  tandis que l'observation, l'autorité et les mappings gardent leurs valeurs natives. Cette
  traduction explicite est distincte du parsing et ne repose sur aucune règle textuelle ;
- l'application et son journal de dérivation partagent une transaction courte. Une dérivation
  échouée ne peut valider une mutation canonique partielle ; la preuve native déjà acquise demeure
  conservée dans sa transaction antérieure ;
- `CalendarNativeReplayService` est un cas d'usage interne du seul `control-api`, sélectionnant
  une page par UUID, sans chemin arbitraire, client HTTP ou réservation. Le replay recalcule le
  hash natif, reprend l'instant de réception de la page et ajoute une interprétation versionnée ;
- les services persistants sont absents de `replay`. Le registre réel reste vide ; la présence
  du code transport ou d'un secret d'environnement ne lui confère ni activation ni autorité.

V010 est additive après le budget V009 ; les migrations antérieures sont immuables. Les réponses
partielles restent non exhaustives ; les pages antérieures validées ne sont pas perdues. Le
restant fournisseur est audité et appliqué au budget avec une couverture d'engagements vide,
sans inventer de preuve d'inclusion ni prolonger la validité initialement admise du compteur.
L'orchestration générale, le claim de jobs et leur fencing restent au lot 4. Voir le
[contrat calendrier fournisseur v1](../contracts/provider-calendar-collection-v1.md).

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

INT-001 ajoute une frontière d'import local optionnel dans le module `collection`, sans transformer le
monolithe ni créer de dépendance vers le SofaScore Local Lab :

- `collection.application.imports` porte les commandes, résultats, cas d'usage et ports J7. Ces types
  ne dépendent ni de Spring HTTP, ni de Servlet, ni de JDBC ; ils expriment l'import idempotent, son
  audit, l'événement accepté et la purge transactionnelle bornée ;
- `collection.adapter.web.j7` porte seul la route
  `/api/imports/sofascore/j7-canonical-events`, la lecture bornée du flux, le protocole HTTP, le JSON
  strict, le schéma Draft 2020-12, les trois hashes, l'ACK et les `ProblemDetail` expurgés. Il exige
  le chemin brut exact, sans préfixe de contexte/servlet, et refuse slash final, segment
  supplémentaire, paramètres matrix et alias percent-encodés. Son codec d'ACK privé construit
  directement les sept champs camelCase et l'`Instant` canonique sous la borne contractuelle, sans
  dépendre de la configuration Jackson MVC globale ;
- `collection.adapter.configuration` porte la configuration, la garde d'activation fail-closed et
  l'allowlist d'empreintes de certificats feuilles. Le mTLS `NEED` reste une propriété du connecteur
  serveur partagé, tandis que la validité courante, l'EKU explicite `clientAuth` et l'autorisation
  par empreinte sont évaluées avant l'adaptateur J7. La même garde maintient la compression serveur
  désactivée, exige des stores TLS locaux absolus non UNC et refuse les alternatives bundle/PEM/SNI.
  Sa phase statique est exécutée par un `WebServerFactoryCustomizer` de priorité maximale avant toute
  résolution des ressources TLS et avant la création de la base ou de Flyway. Elle contrôle l'URL
  JDBC déclarée et refuse DataSource alternative, substitution JNDI/type, propriété Hikari de
  localisation ou configuration Flyway dédiée. La garde bean répète ces contrôles puis inspecte les
  `JdbcConnectionDetails` et le `HikariDataSource` effectifs afin qu'aucun assemblage runtime ne
  contourne l'URL PostgreSQL loopback exacte ;
- `collection.adapter.persistence.JdbcJ7ImportStore` implémente le port d'import sous le seul profil
  `control-api`. Il porte le SQL des tables `j7_import_receipt`, `j7_import_payload`,
  `j7_import_audit` et `j7_import_payload_tombstone`, ainsi que l'ajout minimisé dans
  `outbox_message`, sans exposer JDBC à la couche application ;
- les migrations additives `V006__j7_import_inbox.sql` puis
  `V007__j7_import_purge_integrity.sql`, complétées par
  `V008__j7_import_upgrade_evidence_time_integrity.sql`, matérialisent l'inbox, l'audit, les
  contraintes d'unicité et d'immutabilité, renforcent la purge et refusent à l'upgrade toute preuve
  historique datée dans le futur. V001 à V005 restent immuables ; V009, alors prochain slot,
  est maintenant utilisé par le budget de MVP-001 décrit ci-dessus ;
- le schéma J7 et le schéma d'ACK sont des ressources versionnées autonomes du Betting Project. Le
  build et le runtime ne lisent aucun chemin, artefact ou service d'un autre dépôt ;
- une première réception écrit inbox, audit et outbox `J7_IMPORT_ACCEPTED` dans la même transaction.
  Une répétition exacte retrouve l'import ; une divergence n'altère ni l'inbox ni le catalogue ;
- INT-001 ne consomme pas l'outbox et n'applique aucune donnée au `catalog`. Un futur enrichissement
  devra dépendre d'un port applicatif explicite et d'un Work Order séparé ;
- la purge est un cas d'usage applicatif transactionnel borné, sans route HTTP, scheduler, poller ou
  SQL opérateur direct. INT-001 qualifie la rétention et le tombstone, mais ne livre aucune surface
  de purge activable au runtime ; une telle surface exige un Work Order distinct ;
- tous les composants du receiver sont absents de `batch-worker` et `replay`. Aucun client sortant,
  callback, retry, sender ou appel fournisseur n'entre dans cette frontière.

La qualification locale conserve un même rôle PostgreSQL propriétaire pour Flyway et le runtime.
Les triggers et la fonction de purge protègent donc les chemins applicatifs normaux, mais le
propriétaire de la base reste capable de modifier ces protections : il constitue une frontière de
confiance explicitement acceptée pour ce laboratoire. Avant toute production, le propriétaire de
migration doit être séparé d'un rôle runtime à privilèges minimaux, sans DDL, désactivation de
trigger ni purge SQL directe.

Le contrat normatif est décrit dans
[`j7-import-receiver-v1.md`](../contracts/j7-import-receiver-v1.md). L'activation reste `false` par
défaut. Lorsqu'elle est explicitement qualifiée, le connecteur unique écoute sur
`127.0.0.1:8444` en HTTPS avec client-auth `NEED`; les routes historiques et Actuator partagent alors
ce même connecteur et ne bénéficient d'aucun port de management ou HTTP de contournement.

Le package `bootstrap` contient uniquement l'assemblage de l'application. Le package `shared` contient les primitives transverses qui ne portent pas une règle métier propre à un module.
