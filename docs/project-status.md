# État du projet au 4 septembre 2026

## Synthèse

Betting Project possède un socle applicatif, un catalogue canonique, une chaîne de validation et un benchmark football fusionnés, avec le corpus complet de benchmark conservé hors Git. INT-001 qualifie séparément un receiver local J7 optionnel, prêt pour revue propriétaire et désactivé par défaut. Le projet ne dispose pas encore d'une chaîne de collecte planifiée et d'enrichissement exploitable de bout en bout.

| Couche | État | Conclusion |
|---|---|---|
| Cadrage produit et architecture | Consolidé | Les décisions durables du cadrage v0.5.1 sont retranscrites dans `project-brief.md` et les ADR. |
| Socle applicatif | Fusionné et clôturé | BOOT-001 a livré Java 25, Spring Boot 4.1, PostgreSQL, Flyway, les trois profils autorisés, des jobs/outbox minimaux et le replay hors réseau. |
| Catalogue canonique | Fusionné et clôturé | CAT-001 a livré compétitions, saisons, équipes, rencontres, mappings, anomalies, provenance et normalisation idempotente. |
| Benchmark fournisseurs | Exécuté | INV-01, CAL-01 et ENR-001 totalisent 180 appels et établissent une baseline par capacité. |
| ENR-001 | Fusionné et clôturé | Les 127 preuves sont vérifiées, les validations sont vertes et la Pull Request `#3` est fusionnée dans `main` au commit `6913cea`. |
| CAT-002 | `ACCEPTED - MERGED - CLOSED` | La Pull Request `#8` est fusionnée dans `main` au commit `85dc943`. Le correctif fonctionnel `6fb69e2` et l'alignement documentaire `a3b471f` sont présents ; les 36 critères sur 36 sont satisfaits, les quatre checks Windows/Linux des deux commits sont verts et la discussion P2 est résolue. |
| INT-001 | `READY_FOR_OWNER_REVIEW - LOCALLY_QUALIFIED` | Le receiver J7 local est implémenté sur une branche séparée avec flag désactivé par défaut, contrat strict, mTLS/JDBC loopback et migrations additives `V006` à `V008`. Les validations complètes, la purge, la sauvegarde chiffrée et la restauration isolée synthétique sont qualifiées. La clôture et toute livraison réelle restent soumises à des décisions propriétaires distinctes. |
| Chaîne opérationnelle | Non réalisée | Aucun ordonnanceur réel, routeur fournisseur, worker de jobs complet, workflow de résolution des anomalies ou connecteur de production n'est fusionné. |
| Produit de paris | Non commencé | Cotes, probabilités, valuebets, recommandations, documents, diffusion et suivi de performance sont différés. |

## Catalogue fusionné et évolution CAT-002

### BOOT-001

Le socle fournit :

- Java 25 avec règle Maven Enforcer `[25,26)` ;
- Maven 3.9.16, Spring Boot 4.1.0 et PostgreSQL 17 ;
- profils `control-api`, `batch-worker` et `replay` ;
- migration Flyway `V001` ;
- jobs persistants, outbox minimale, audit de snapshots et replay hors réseau ;
- contrôles d'architecture et procédures locales initiales.

### CAT-001

Le catalogue fournit :

- modèle canonique des compétitions, saisons, équipes et rencontres ;
- références fournisseurs, mappings confirmés/ambigus/rejetés et anomalies ;
- provenance jusqu'au snapshot brut ;
- normalisation et rejeu idempotents ;
- migration additive Flyway `V002` ;
- 17 tests standards et 11 tests PostgreSQL/Testcontainers validés sous Windows et Ubuntu/WSL2.

CAT-001 est fusionné et clôturé. CAT-002 traite désormais localement la concurrence, l'ordre des observations, l'autorité primaire/contrôle, les décisions humaines de mapping, le cycle de vie des anomalies et les demandes durables de rejeu. Le lot 7 expose ces cas d'usage sous `/internal/catalog` avec pagination keyset, commandes strictes, provenance expurgée et composants limités au profil `control-api`. Le lot 8 prouve en plus la reprise d'une demande `PENDING` après fermeture complète puis redémarrage du contexte Spring, sans migration V006, worker ni exposition publique. La correction P2 de la PR `#8` préserve les caractères fournisseur littéraux dans la clé d'autorité et maintient le chargement classpath fermé aux jokers de configuration. Le commit fonctionnel `6fb69e2` et l'alignement documentaire `a3b471f` ont quatre checks verts chacun ; la seconde attestation est approuvée, les 36 critères sont satisfaits et le fil P2 est résolu. La PR `#8` est fusionnée au commit `85dc943` : CAT-002 est accepté et clôturé.

## Receiver local J7 — INT-001 prêt pour revue propriétaire

INT-001 part de la base CAT-002 fusionnée `85dc943` et ajoute un adaptateur entrant optionnel au seul
profil `control-api`. Le contrat est `POST /api/imports/sofascore/j7-canonical-events`, avec media
types v1.0, corps byte-exact de 1 à 5 242 880 octets, schéma local strict, UUID/hashes corrélés et ACK
borné. La première réception doit produire `201/IMPORTED`, une répétition exacte
`200/DUPLICATE` et une divergence `409`. Le corps positif est produit par un codec privé
déterministe : ses sept champs camelCase et son `Instant` restent indépendants du Jackson MVC global.

Ce chemin externe est exact et ne reçoit aucun préfixe `server.servlet.context-path` ou
`spring.mvc.servlet.path`. Le slash final, les segments supplémentaires, les paramètres matrix et
les variantes percent-encodées ne sont pas des alias : ils sont refusés avant le contrôleur.

Le receiver reste `false` par défaut. Une activation locale explicite exige le connecteur unique
`127.0.0.1:8444`, HTTPS, client-auth `NEED`, stores hors Git, certificat feuille en cours de validité
avec EKU `clientAuth` et allowlist d'empreintes. Catalogue, bootstrap et Actuator partagent alors le
même connecteur mTLS, sans port de management séparé et sans compression serveur. PostgreSQL
Compose est lié à `127.0.0.1:5433`; l'activation exige des stores locaux absolus non UNC et refuse les
alternatives TLS bundle/PEM/SNI dans une garde web prioritaire avant résolution des ressources. Cette
garde contrôle aussi l'URL JDBC déclarée et refuse DataSource alternative, JNDI/type, propriétés
Hikari de localisation et configuration Flyway dédiée avant création de la base ou de Flyway. La
garde bean répète les contrôles statiques puis inspecte les `JdbcConnectionDetails` et le
`HikariDataSource` effectifs.

`V006` ajoute l'inbox exacte, l'audit, les tombstones et l'usage transactionnel de l'outbox existante.
`V007`, additive, relie intégralement le tombstone au reçu et impose que le tombstone et l'audit de
purge existent avant la suppression des octets, avec des horodatages non futurs. `V008`, additive,
refuse lors de l'upgrade toute preuve historique V006/V007 dont l'instant de purge est encore futur.
Le prochain slot Flyway est `V009`. La rétention est configurable explicitement de 1 à 3 650 jours,
avec défaut et baseline à 30 jours.
La purge reste un cas d'usage transactionnel borné sans route, scheduler, SQL opérateur ou surface
runtime ; ouvrir une telle surface exige un autre Work Order.

Le rôle PostgreSQL local est encore propriétaire et sert à la fois à Flyway et au runtime : il reste
une frontière de confiance de la qualification. Une cible de production devra séparer le rôle owner
de migration du rôle runtime à privilèges minimaux, incapable de DDL, de désactiver les triggers ou
d'invoquer une purge SQL directe. Les scripts de backup/restore réalisent un dump complet directement
chiffré et une cible PostgreSQL 17 standard, fraîche, isolée et vide, vérifiée dans les catalogues
observables. La preuve du 4 septembre a sauvegardé un corpus V008 synthétique sans dump clair. Une
tentative Q1 s'est arrêtée sur un alias SQL réservé, avant déchiffrement, toute saisie propriétaire
et toute mutation de la cible ; après correctif versionné, une seconde cible neuve a été restaurée et
les hashes des cinq tables ont été
recalculés avec succès. L'absence de template personnalisé, de `pg_upgrade`, de clone et de lecteur
réseau mappé reste une frontière opérateur.

La baseline finale réussit 303 tests standards, 98 tests PostgreSQL/Testcontainers/mTLS, 32 tests
Pester, la validation Windows complète, le contrôle de secrets et la configuration Compose. Le lot
est localement qualifié mais reste actif jusqu'à la décision propriétaire. INT-001 ne contient aucun
sender, client sortant, appel SofaScore/Local Lab, consommateur d'enrichissement ou donnée réelle et
n'autorise ni livraison réelle, ni réseau distant, ni VPS, ni production.

## Benchmark fournisseurs

Les campagnes INV-01, CAL-01 et ENR-001 ont consolidé 180 appels :

- Highlightly : 161 appels sur 161 avec réponse HTTP 2xx ;
- football-data.org : 16 réponses HTTP 200 sur 17 appels, utile comme contrôle calendrier/identité ;
- API-FOOTBALL : accès technique démontré, mais saison 2026 inutilisable avec le plan testé ; statut `BLOCKED_BY_PLAN` sur le chemin critique gratuit.

Ces résultats sont des mesures du 9 au 17 août 2026, pas des garanties permanentes. Un changement de plan, de saison ou de capacité requiert une nouvelle preuve datée et rejouable.

Le collecteur ENR-001 est un outil de benchmark contrôlé. Il ne doit pas être présenté ni réutilisé implicitement comme le futur adaptateur de production.

## Sauvegarde de récupération ENR-001

Avant l'ouverture des modifications DEVX-001, une sauvegarde datée `2026-09-01T11-27-31` a été créée hors dépôt sous `Documents/Betting Project/recovery/ENR-001/`.

La sauvegarde référence le commit de base `25d1c29` et conserve :

- 33 entrées de statut Git logique ;
- les patches indexé et non indexé ;
- 139 fichiers utiles, dont 3 fichiers non suivis ;
- 457 fichiers de preuves de benchmark ;
- un manifeste SHA-256 de 1 203 entrées avec taille et chemin logique.

Une restauration à blanc a validé l'identité des patches indexé et non indexé ainsi que les octets des fichiers conservés. Le rapport et le manifeste de validation restent avec la sauvegarde externe ; aucun payload complet ni chemin propre à l'ancien checkout n'est requis par le dépôt.

## Finalisation et fusion ENR-001

Le lot a été reconstruit sur `codex/enr-001` depuis la sauvegarde restaurable, sans rebase destructif du checkout historique et sans appel fournisseur. Il apporte :

- un index expurgé de 127 appels et un vérificateur lecture seule, hors réseau ;
- sept fixtures synthétiques couvrant les familles réellement appelées ;
- le collecteur historique de benchmark, explicitement séparé des futurs adaptateurs de production ;
- un garde de quota sans hypothèse de remise à zéro à minuit, un verrou exclusif et des écritures atomiques ;
- les deux preuves Hirnyk distinctes : ambiguïté textuelle historique et alias exact explicitement confirmé ;
- la baseline MVP et toutes les limites ou non-exécutions enregistrées sans inventer de résultat.

La validation du 1er septembre 2026 réussit avec 57 tests standards, 12 tests PostgreSQL/Testcontainers et 127 preuves sur 127 conformes. La validation Windows complète et le contrôle de secrets sont verts. Le lot a été commité sous `743aff7`, publié sur `codex/enr-001`, revu puis fusionné dans `main` par la Pull Request `#3` au commit de fusion `6913cea`. ENR-001 est accepté, fusionné et clôturé.

## Risque P0 traité et fusionné par DEVX-001

À l'ouverture de DEVX-001, deux défauts permettaient un faux succès de la validation Windows :

- le wrapper échouait lorsque le répertoire Maven `.m2` ordinaire exposait une propriété `Target` nulle ;
- le script de validation ne propageait pas systématiquement les codes d'échec des commandes natives et pouvait omettre l'intégration lorsque la CLI Docker était absente.

Le correctif, les tests de faux vert, la validation Windows complète et les contrôles Linux hors intégration locale sont réussis. L'intégration WSL2 locale a échoué comme attendu faute de socket Docker, sans être omise ni transformée en succès. Les CI Windows et Linux de la branche et de la Pull Request sont vertes sur le commit fonctionnel `06ba0e2`, le défaut P2 relevé en revue est corrigé et son fil est résolu. Le porteur a explicitement autorisé la fusion de la Pull Request `#2` le 1er septembre 2026, sous réserve de la CI verte du commit documentaire final. La présence de cette version dans `main` signifie que DEVX-001 est accepté, fusionné et clôturé, ce qui ouvre la reprise séparée d'ENR-001.

## Décisions actives

- Prochain jalon : pipeline football fiable, pas MVP prématch englobant immédiatement cotes et publication.
- Calendrier : `PPL`, `PD`, `DED` et `ELC`.
- Enrichissement initial : Liga Portugal et LaLiga, jusqu'à sept rencontres priorisées par jour.
- Highlightly : source principale pour les capacités mesurées ; football-data.org : source de contrôle sur le noyau ; API-FOOTBALL : bloqué par le plan testé.
- Budget Highlightly : 80 appels, réserve incompressible de 20.
- Compositions : facultatives et non bloquantes ; aucune validation prématch à T0 ou après T0.
- Aucun pari automatique, aucun appel live, aucune dépendance à SofaScore.
- Le receiver J7 d'INT-001 est localement qualifié, reste désactivé par défaut et attend la revue propriétaire ; aucune livraison réelle n'est autorisée.
- La rétention J7 vaut 30 jours par défaut et ne change que par configuration opérateur explicite dans la plage `1..3650`.
- PostgreSQL reste la source de vérité ; les preuves complètes du benchmark restent hors Git avec manifeste expurgé versionné lors d'ENR-001.

## Porte de fusion et règle de succession

| État externe vérifiable | Conséquence de gouvernance |
|---|---|
| Pull Request `#8` fusionnée et commit `85dc943` présent dans `main` | CAT-002 vaut définitivement `ACCEPTED - MERGED - CLOSED`. |
| INT-001 est localement qualifié mais reste en branche en attente de revue propriétaire | Receiver désactivé, aucune livraison réelle, aucune exposition et aucune clôture automatique. |
| INT-001 est ultérieurement accepté | Le receiver reste local et opt-in ; une livraison réelle, une cible ou un déploiement exigent des décisions et Work Orders séparés. |
| CAT-002 est clôturé | MVP-001 reste le prochain Work Order du pipeline produit, sans activation automatique ; ENR-002 vient ensuite. |
| Le pipeline local fiable est accepté | OPS-001 peut alors être ouvert, sans activation automatique. |

Chaque passage vers `main`, protection GitHub ou déploiement nécessite l'autorisation humaine prévue par ADR-005. Le commit fonctionnel CAT-002 `6fb69e2` et l'alignement documentaire `a3b471f` sont fusionnés par la PR `#8` au commit `85dc943`; leurs checks sont verts, la seconde attestation est conforme et la discussion P2 est résolue. Les routes du lot 7 restent internes, liées à `127.0.0.1` et interdites d'exposition avant OPS-001 ; aucun worker CAT-002 n'a été créé. INT-001 ne modifie cette posture que dans une qualification opt-in : son activation fait passer tout le connecteur local sous HTTPS+mTLS, mais ne vaut aucune autorisation externe. Son commit, sa publication, sa fusion et sa clôture restent soumis à validation et décision humaines séparées.
