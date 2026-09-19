# MVP-001 — Exécution et qualification du lot 2

- Préparation : 2026-09-06 ; réalisation technique autorisée le 2026-09-13.
- Work Order : [MVP-001](MVP-001.md), lot 2 `COMPLETED` après qualification du 2026-09-13.
- Activation : demande explicite du porteur « Démarrer le lot 2 ».
- Branche : `feature/V0.1.0-RC01-CODEX-MVP-001`.
- HEAD de reprise : `0f8a7d504ef2da5b64371855932ea0cdceec5f81`.
- Base initiale du Work Order inchangée : `5e4b05b5a1a7cfa66850643db31d9612192e7281`.
- État constaté avant activation : worktree et index propres.

## Objectif et limites

Livrer un budget fournisseur durable, les réservations concurrentes et les incidents de quota
ou d'autorisation. Le résultat doit être utilisable par les futurs adaptateurs et jobs, sans
introduire ceux-ci dans ce lot. Les sections de préparation ci-dessous sont conservées ;
les résultats effectivement exécutés sont consignés en fin de document. Ils n'activent aucun
fournisseur réel.

Le registre classpath reste vide. Aucun appel HTTP, endpoint, worker, poller, ordonnanceur,
campagne réelle ou configuration opérateur réelle n'est ajouté. La sélection quotidienne relève
du lot 5 ; les adaptateurs HTTP et la conservation des réponses relèvent du lot 3.

## État établi et décisions déjà acquises

V001 définit `provider_call_audit`, `persistent_job` et `outbox_message`, mais aucune fenêtre ou
réservation budgétaire. V001 à V008 sont partagées et immuables ; V009 est le prochain numéro
disponible constaté. Le lot prévoit une migration additive, à qualifier depuis V008 peuplée.

Les [fondations validées](../contracts/mvp-001-collection-foundations-v1.md) imposent :

- capacité Highlightly de référence 100, plafond opérationnel 80, réserve 20 non soustraite deux fois ;
- réservation durable avant envoi et inclusion des réservations concurrentes dans le disponible ;
- minimum prudent entre informations fournisseur, comptabilité locale et budget projet ;
- initialisation explicite, sans fenêtre créée ou renouvelée à minuit ;
- une hausse de compteur ne prouve pas une remise à zéro ;
- envoi possiblement effectué : coût conservé et aucune relance automatique ;
- 401/403/429 : suspension de la fenêtre fournisseur et incident durable ;
- absence de perte ou de suspension implicite des autres fournisseurs.

Les tests du lot 1 et sa CI restent des preuves historiques rattachées à `0f8a7d5`, pas une
qualification du lot 2 : 352 tests standards et 118 tests PostgreSQL/Testcontainers.

## Découpage de réalisation

### Campagne du jour : preuves consultées et conséquences

Le porteur confirme Highlightly et football-data.org comme seules API fournisseur appelables.
La [clarification COV-002](../benchmark/mvp-001-cov002-clarification-20260906.md) relève quinze
rencontres, 100 appels Highlightly et 18 familles post-match différées au point de mesure.
Les empreintes et JSON de 138 réponses ont été vérifiés hors réseau ; ce n'est pas encore leur
replay de production. La campagne demeure distincte de MVP-001 et de sa CI.

Le budget devra facturer une tentative HTTP et non chaque famille contenue dans DETAIL,
prendre en compte les 12 appels de préparation hors ledger d'enrichissement dans le scénario
mesuré, et distinguer la cadence par minute football-data.org d'une enveloppe quotidienne projet.
Ajouter des scénarios synthétiques de réservations prioritaires avant familles optionnelles,
de report sans tentative et de libération uniquement avant envoi. Aucune collecte Lab ne libère
rétroactivement le coût d'un appel possiblement envoyé. Le consommateur métier J7 reste séparé.

Conséquences pour le contrat budgétaire : distinguer plafond fournisseur, plafond projet,
réserve de sécurité, engagements planifiés et consommation externe partageant éventuellement
le même quota. Ne pas confondre une réservation de 20 appels futurs avec 20 appels de sécurité
inconsommables. Ne pas supposer un quota football-data.org identique. Conserver 80+20 comme
politique de production actuelle jusqu'à décision explicite de changement.

Les compléments de bilan doivent préciser, par compétition et rencontre, le coût calendrier/détail/compositions/
statistiques/événements, les nouvelles vérifications, les horaires source et réception, la complétude
et les exclusions faute de budget. Comparer des phases temporelles comparables : un relevé Lab
en cours de match n'est pas une référence finale. Une réponse HTTP 200 ne prouve pas la qualité
de chaque métrique. Distinguer les incidents de match des incidents techniques fournisseur.

L'intégration des conclusions exige des références datées et expurgées, hashes et résultats de
replay disponibles pour revue. Les messages de progression ne sont pas importés comme fixtures
ou affectations exécutables. Le registre réel reste vide ; aucune dépendance directe au Lab ajoutée.

### 1. Contrat du budget et modèles purs

Définir dans `collection` les modèles et ports de budget, sans dépendance inverse depuis ce
module vers le catalogue. Séparer fenêtre fournisseur, réservation/intention d'appel, observation
de quota et incident. Les états, résultats applicatifs et refus doivent être typés, sans HTTP ni JDBC.

Préciser dans le contrat avant les écritures SQL :

- identité d'une fenêtre explicitement initialisée et preuve de ses bornes ;
- signification du compteur fournisseur : restant observé, instant et périmètre de mesure ;
- calcul prenant en compte les réservations encore non reflétées dans ce compteur ;
- comportement fermé lorsqu'une donnée nécessaire est absente, périmée ou contradictoire ;
- budget initial déjà consommé hors application, sans inventer un historique d'appels ;
- idempotence exacte d'une intention, distinguant doublon identique et collision de contenu ;
- transitions autorisées et conditions de libération d'une réservation jamais envoyée ;
- réconciliation explicite d'une tentative incertaine, avec provenance et sans nouvel envoi implicite.

En particulier, appliquer naïvement `min(restant fournisseur, restant projet)` ne suffit pas :
les réservations concurrentes doivent être retirées des disponibilités qu'elles n'ont pas encore
consommées dans l'observation fournisseur. Inversement, une réponse déjà comptabilisée ne doit
pas débiter deux fois le budget local. Ces cas doivent être démontrés par des exemples et tests.

### 2. Persistance additive

Préparer V009 pour les projections durables et leur historique, avec clés étrangères, contraintes
de bornes, unicité des identités et clés d'idempotence, et index correspondant aux lectures réelles.
Aucune fenêtre réelle, réservation ou histoire synthétique n'est insérée par la migration.

Le SQL reste dans des adaptateurs implémentant les ports applicatifs. Verrouiller la fenêtre
concernée pour la décision et la réservation atomiques, sans réutiliser le verrou global du catalogue.
Les écritures de réservation, consommation et incident doivent être atomiques avec leur historique.
Fixer un ordre de verrouillage pour éviter les interblocages entre réservation et résultat d'appel.

### 3. Cas d'usage transactionnels

Préparer l'initialisation explicite, la consultation du disponible, la réservation idempotente,
le passage avant envoi, l'enregistrement d'un résultat ou de son incertitude et la suspension.
Le passage durable précédant l'envoi doit être commité avant que le futur client HTTP soit appelé.
Un rollback de transaction n'est jamais interprété comme preuve que le réseau n'a pas été sollicité.

Les services opérationnels et adaptateurs seront limités à `control-api` et `batch-worker` ;
`replay` restera sans ces ports de persistance. L'initialisation administrative ne doit pas être
confondue avec l'activation des capacités du registre. Aucun appel externe n'est exécuté par le budget.

### 4. Preuves requises

| Invariant | Scénario à exécuter |
|---|---|
| Plafond 80 et réserve 20 | 80 unités autorisées depuis 100, puis refus, sans double retrait de réserve |
| Disponibilité prudente | Compteurs fournisseur/local/projet différents, réservations en cours, informations absentes |
| Idempotence | Même clé et contenu : même réservation ; autre contenu : conflit sans débit |
| Concurrence | Transactions PostgreSQL distinctes en compétition pour la dernière unité |
| Atomicité | Défaillance forcée de l'historique : aucun débit ni réservation partielle |
| Incertitude | Arrêt après le passage avant envoi : coût retenu et aucun réenvoi automatique |
| Redémarrage | Nouvelle instance applicative sur la même base : état et budget conservés |
| Quota non renouvelé implicitement | Minuit, compteur augmenté, observation ancienne ou contradictoire |
| Suspension isolée | 401, 403 et 429 suspendent le fournisseur concerné, pas le second |
| Upgrade | V008 réellement peuplée vers V009, données et UUID préexistants conservés |
| Profils et architecture | Absence sous replay, application sans JDBC, absence de cycle |

## Validation et livraison

Exécuter les tests ciblés, puis `mvnw.cmd -Pintegration verify`. Consigner les totaux réellement
exécutés et toutes les erreurs ou omissions. Vérifier le scan de secrets, `git diff --check`,
les espaces finaux des fichiers non suivis, V001–V008 et le périmètre exact de V009.

Ne fermer que les critères entièrement démontrés. L'incident durable seul ne suffit pas à clore
la consultation HTTP du lot 5 ; la réservation seule ne prouve pas encore le cycle HTTP du lot 3.
Le lot 2 ne devient `COMPLETED` qu'après qualification. Son commit et son push sont déjà autorisés
par la règle de livraison par lot ; vérifier la CI de son SHA avant de poursuivre au lot 3.
La PR finale reste vers `feature/V0.1.0-RC01`, après les lots restants et revue humaine.

## Résultats du 13 septembre 2026

Le plan est validé et le lot 2 est `COMPLETED`. Le
[contrat du budget](../contracts/provider-budget-v1.md) décrit le comportement livré, ses
frontières transactionnelles et les refus typés. Le lot 3 est prochain mais reste `PENDING`.
Le Work Order atteint **5/20** : seuls les critères de réservation durable et de compteurs
incertains sont nouvellement fermés. Les critères transversaux HTTP, orchestration, reprise
globale, migrations finales et revue restent ouverts malgré les preuves partielles de ce lot.

### Preuves effectivement exécutées

| Commande ou suite | Résultat du candidat local |
|---|---|
| Budget ciblé : `mvnw.cmd -Pintegration -Dtest=*Budget*Test -Dit.test=ProviderBudgetServiceIT,V009MigrationIT verify` | 61 standards + 39 PostgreSQL ; zéro échec, erreur ou omission |
| `mvnw.cmd -Pintegration verify` | 413 standards + 157 PostgreSQL ; zéro échec, erreur ou omission ; code 0 à 23:00:10 Europe/Paris |
| `check-no-secrets.cmd` / scope `All` | PASS |
| `git diff --check` et espaces finaux de tous les non suivis | PASS |
| V001–V008 contre l'inventaire de départ | SHA-256 inchangés |
| V009 | Unique migration ajoutée ; aucune V010 ; aucune donnée préchargée |

`BudgetModelTest`, `BudgetCalculatorTest`, les tests d'identité et d'expurgation ajoutent
61 cas standards. `ProviderBudgetServiceIT` couvre 36 scénarios, dont les transactions
concurrentes, l'expiration pendant une attente réelle de verrou, les rollbacks forcés,
les observations reçues dans le désordre et le redémarrage complet des contextes et pools.
`V009MigrationIT` ajoute trois scénarios d'installation, d'upgrade V008 peuplée et de contraintes.
Les tests des trois profils et ArchUnit restent verts. Ces nombres sont issus des rapports
Surefire/Failsafe du candidat ; les 352/118 du lot 1 restent une référence historique distincte.

La première tentative sous sandbox a échoué sur l'accès Maven aux dépendances, avant toute
qualification. Un échec ciblé sur l'expurgation d'une valeur Bearer a été corrigé ; la suite
ciblée et la suite complète ont ensuite réussi sur ce correctif. La revue finale a corrigé le
refus typé d'une preuve de réconciliation réutilisée avec un contenu différent : conflit
d'idempotence, sans mutation. Les deux suites ont de nouveau réussi sur ce dernier changement.
Aucun résultat en échec ou
test omis n'est assimilé à une validation. Les scripts et workflows étant inchangés, leurs
tests locaux de non-régression ne sont pas réexécutés par ce lot.

V009 SHA-256 : `d86d981c7e251af90e1a58ade6cbf94fc665d7ca760873e873b2d58be3afe5e1`.
Le POM reste `0.1.0-rc.1-SNAPSHOT`. Aucun H2, nouveau profil, appel fournisseur, endpoint,
worker, ordonnanceur ou capacité réelle n'est ajouté. Le collecteur ENR reste inchangé.

Les sept documents modifiés et trois fichiers de préparation présents avant la réalisation
sont conservés, y compris la note et l'index COV-002 datés ; aucun payload fournisseur complet
n'est importé. L'index est resté vide jusqu'à la préparation du commit borné au lot.

Le commit distinct et le push sont autorisés. La CI Windows et Linux/PostgreSQL doit être
vérifiée sur le SHA publié avant le lot suivant ; une validation locale n'en préjuge pas.
Aucune PR de clôture ni fusion n'est créée à cette étape.
