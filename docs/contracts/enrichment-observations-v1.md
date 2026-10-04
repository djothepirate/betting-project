# Contrat d'observations d'enrichissement v1

## Portée

`enrichment-observations-v1` fixe la représentation interne des familles de données rattachées
à une rencontre canonique. Le périmètre accepté d'ENR-002 est le calendrier PPL/PD/DED/ELC et
l'enrichissement PPL/PD. L'enrichissement est exécuté en mode autonome API ; le receiver J7
reste séparé et son import ne supprime aucun appel.

Les seules intégrations sportives possibles sont Highlightly et football-data.org, sélectionnées
par la clé exacte du registre. Cette décision ne remplit aucune entrée du registre classpath de
production et n'active aucun compte.

## Familles et unités de preuve

Les familles internes sont `MATCH_DETAIL`, `LINEUP`, `TEAM_STATS`, `EVENTS` et `PLAYER_STATS`.
Une réponse DETAIL peut porter plusieurs sous-familles décrites dans son schéma fournisseur ;
une requête HTTP reste une seule tentative et coûte une unité au budget. Le hash des octets reçus
reste celui du snapshot brut. Chaque représentation dérivée porte sa propre famille et version
de parseur, ainsi qu'une référence au même snapshot ; elle ne remplace jamais les octets.

Les états d'une donnée dérivée sont `NOT_PRESENT`, `NULL_VALUE`, `EMPTY`, `AVAILABLE`, `PARTIAL`
et `INCOMPATIBLE`. Ils ne sont pas interchangeables : un champ absent dans un JSON, un `null`, un
tableau vide, une réponse partielle et un schéma non reconnu restent distincts. Une absence de
requête est enregistrée par la décision de planification, pas maquillée en réponse vide.
Une fenêtre planifiée mais non exécutée prend l'issue `MISSED_WINDOW`.

Un échec de transport demeure une issue de tentative et ne devient pas une observation parseur.
Une réponse reçue est conservée avant interprétation ; JSON non conforme, endpoint vide et donnée
absente dans DETAIL restent des états explicitement visibles.

## Parsing hors réseau livré au lot 1

Les parseurs reçoivent les octets conservés et n'effectuent aucun appel réseau fournisseur. Les
fixtures de qualification sont synthétiques, réutilisent les fixtures représentatives ENR-001 et
sont indexées dans [`enr-002-parser-fixtures-v0.1.json`](../benchmark/enr-002-parser-fixtures-v0.1.json),
qui épingle le manifeste source et le SHA-256 de chaque fichier.

| Fournisseur | Famille | Version initiale du parseur | Représentation conservée |
|---|---|---|---|
| Highlightly | `MATCH_DETAIL` | `highlightly-match-detail-v1` | ID de rencontre/compétition, saison et round natifs, kickoff programmé, statut source, équipes, score observé et états des tableaux de statistiques/événements. |
| football-data.org | `MATCH_DETAIL` | `football-data-match-detail-v1` | ID numérique de saison et stage natifs, identités, kickoff programmé, statut source et score full-time ; aucune donnée d'enrichissement n'est inventée lorsqu'elle manque. |
| Highlightly | `LINEUP` | `highlightly-lineup-v2` | Références d'équipe/joueur, nom et rôle source disponibles, ordre des lignes et évaluation `ABSENT`/`INCOMPLETE`/`COMPLETE`/`COMPLETE_LATE`/`UNKNOWN`. La version v1, limitée aux IDs, reste disponible uniquement pour rejouer les anciennes observations. |
| Highlightly | `TEAM_STATS` | `highlightly-team-stats-v1` | Blocs regroupés par `team.id`, noms source des métriques, ordre fournisseur et valeurs distinctes de l'absence, de `null` et du zéro. |
| Highlightly | `EVENTS` | `highlightly-events-v1` | Ordre source, notation telle que `45+2`, type et références optionnelles ; aucun temps écoulé, période ou fait canonique n'est déduit. |
| Highlightly | `PLAYER_STATS` | `highlightly-player-stats-v1` | Groupement par IDs source équipe/joueur, identité, rôle disponible, minutes et métriques nommées ; l'ID seul n'est pas une jointure inter-endpoints. |

Les parseurs refusent les clés dupliquées, les jetons après la racine, les racines/types
incompatibles, les identifiants incohérents et les payloads de plus de 5 Mio. Les types JSON ne
sont pas coercés. Les membres non modélisés ne reçoivent aucune sémantique et restent uniquement
dans la preuve brute. Un statut fournisseur non reconnu est incompatible, jamais réécrit vers un
statut nominal. Les métadonnées descriptives non obligatoires, dont le pays/type d'une compétition
ou le pays d'une équipe, peuvent rester absentes ou nulles.

La saison et la phase logiques viennent de la route exacte du registre ; les parseurs ne traduisent
ni l'année/`round` Highlightly, ni l'identifiant de saison/`stage` football-data.org. L'instant de
réception d'une composition complète à ou après le kickoff prévu produit `COMPLETE_LATE` et ne
devient jamais une preuve prématch. La collecte LINEUP est refusée dès que l'heure locale atteint le
kickoff, avant réservation de budget ou transport ; une réponse commencée avant mais reçue à T0 ou
après reste conservée comme tardive et n'achève pas le critère prématch. L'absence demeure non
bloquante. Les parseurs/statisticiens exposent les cinq quarantaines :
`MISSING_PLAYER_FULL_NAME`, `ZERO_MINUTE_EXPECTED_METRICS` et
`INVALID_SECOND_YELLOW_VALUE` sont dérivables d'une observation joueurs isolée ;
`MISSING_PLAYER_YELLOW_CARD` exige une occurrence EVENTS rapprochée par équipe, ID et nom exacts,
unique dans chaque famille ; `CROSS_ENDPOINT_PLAYER_ID_MISMATCH` exige une correspondance unique sur
l'équipe, le nom exact et le rôle entre deux familles/endpoints différents. Les minutes écartent une
correspondance seulement lorsque les deux endpoints les exposent ; leur absence ne les fait pas
inventer. Le parseur LINEUP v2 conserve nom et rôle ; les observations LINEUP v1 restent rejouables
mais n'apportent que des IDs et ne sont pas utilisées pour cette évaluation. Les constats sont
persistés dans V012 avec l'observation qui les déclenche ; aucune jointure ne repose sur un ID seul.

## Provenance et clés

Toute observation de famille référence :

- rencontre canonique et référence de rencontre fournisseur exacte ;
- intention de budget et snapshot brut de l'appel qui l'a produite ;
- fournisseur, famille et clé logique exacte compétition/saison/phase choisie dans le registre ;
- références saison/phase source séparées lorsqu'elles existent (`season`/`round` Highlightly,
  `season.id`/`stage` football-data.org) ; aucune traduction fournisseur n'est inventée ;
- hash SHA-256 du brut, version du parseur, début d'envoi, réception et instant source s'il est fourni.

L'instant de réception est fixé à la frontière de collecte. L'instant source reste nullable ;
l'heure du kickoff prévu n'en tient pas lieu. Si une source n'expose aucun instant d'observation
fiable, le dérivateur peut utiliser explicitement l'heure de réception avec sa provenance.
Le catalogue conserve sa propre règle : le normaliseur calendrier ne remplace jamais `observedAt`
manquant par `receivedAt`.

Les IDs d'équipe et de joueur demeurent des références fournisseur. Les statistiques d'équipe
sont jointes à la rencontre par l'ID d'équipe observé, jamais par l'ordre du tableau. Une identité
de joueur inter-endpoints exige des indices indépendants (endpoint, équipe, rôle, nom et temps de
jeu) ; son ID fournisseur seul n'est pas une clé de jointure. Les métriques inconnues restent
conservées sous leur libellé source/version, sans attribution sémantique hasardeuse.

## Persistance livrée au lot 2

`V012__enrichment_admission_observations.sql` ajoute le plan journalier, ses rencontres admises,
ses étapes conditionnelles, les observations dérivées et les constats de qualité. Le plan est
idempotent par date UTC, clé et empreinte de commande ; il revalide la sélection MVP-001 dans la
transaction et ne contient pas plus de sept rencontres. Une nouvelle clé ne peut pas créer un
second plan pour la même date. Le coût reste estimatif : l'admission ne réserve pas le budget et
n'autorise aucun envoi HTTP.

Une observation dérivée référence une intention du ledger MVP-001 et le snapshot brut, conserve
séparément les hashes des octets bruts et de la représentation dérivée, puis épingle la famille,
le parseur et le contexte logique/source. L'ajout est immuable et idempotent pour la même provenance
et les mêmes hashes ; un contenu divergent sous la même identité est refusé. Les constats de
qualité sont eux aussi idempotents. Le payload brut n'est jamais copié dans la table d'observation
ni dans un constat.

Les étapes post-match sont persistées non armées. Seule une observation explicitement finale peut
les armer ; l'échéance reprend l'instant de cette preuve et le recontrôle prioritaire est fixé à
fin observée +60 minutes. Le lot 2 ne crée ni worker, ni dispatch, ni connecteur, ni appel réel.

## Collecte, audit et replay livrés au lot 3

`V013__enrichment_collection_attempts.sql` ajoute un journal durable de tentative lié à l'admission,
à son étape, à l'intention du budget, à l'audit fournisseur et, lorsqu'une réponse sûre a été reçue,
au snapshot brut. Une seule requête HTTP coûte une unité, même si la représentation DETAIL contient
plusieurs familles ou champs. L'identité d'idempotence est déterminée par admission, étape, famille
et fournisseur ; le hash canonique de requête lie également le mapping fournisseur, le contexte
logique, l'endpoint et la version du parseur.

La réservation, l'insertion de la tentative et l'audit initial sont validés avant l'autorisation
d'envoi. Cette autorisation est commitée séparément avant tout transport ; aucun appel HTTP n'a lieu
dans une transaction ou sous un verrou de base. Une répétition d'une intention déjà engagée ne
fournit jamais de seconde autorisation. Si l'envoi peut avoir eu lieu mais que son résultat n'est
pas connu, l'intention demeure consommée et incertaine ; aucune relance automatique n'est faite.
Une requête identique concurrente ne modifie pas l'état d'une tentative engagée, afin de laisser
le transport initial enregistrer son résultat.

À réception, le ledger, la tentative et les octets bruts sûrs sont enregistrés avant que le parseur
ne soit invoqué. Une taille excessive ou un écho détecté du secret ne conserve pas le corps. Le hash
des octets bruts et le hash JSON de la représentation dérivée restent distincts ; une dérivation
est append-only, épingle le parseur/version et peut être rejouée autant de fois que nécessaire à
partir du snapshot PostgreSQL existant, sans obtenir une autorisation ni appeler le fournisseur.
Les migrations antérieures restent immuables ; V013 est additive après V012.

Les clients sont bornés à 5 secondes de connexion, 30 secondes par requête et 5 Mio de corps, sans
suivre les redirections. Les URL et paramètres d'authentification sont définis dans chaque adaptateur
et ne sont pas persistés. Les secrets ne sont jamais recopiés dans les erreurs. Le client
football-data.org ne prétend pas connaître son restant de quota depuis un en-tête générique ; seul
le compteur Highlightly reconnu peut alimenter la réconciliation budgétaire existante.

L'assemblage HTTP est limité à `control-api` et `batch-worker`. Il reste indisponible par défaut :
une propriété `betting.providers.<provider>.enrichment-enabled=true`, un credential valide et les
garde-fous JDK de démarrage et d'exécution doivent tous être présents. Le registre classpath de
production reste vide et aucune campagne réelle n'est autorisée par ce contrat. Les preuves lot 3
utilisent un client synthétique dans l'intégration PostgreSQL et des serveurs loopback uniquement
pour les tests des adaptateurs ; elles ne constituent pas un appel fournisseur réel.

Le lot 3 a livré la collecte synchrone sans worker. Depuis, le lot 4 a ajouté V014 et les handlers
de jobs conditionnels sous `batch-worker`, tout en laissant la boucle et les clients désactivés par
défaut ; il n'introduit ni polling fournisseur continu ni rattrapage des fenêtres manquées. Le lot 5
a ajouté une lecture de qualité bornée sous le seul profil `control-api`, et le lot 6 a étendu la
preuve d'identité des compositions sans ajouter de migration. Les seules APIs sportives autorisées
pour l'évolution du produit demeurent Highlightly et football-data.org, sans troisième fournisseur.

## Compositions et fenêtres

Le protocole accepté programme des évaluations conditionnelles à T−30 puis T−15, une collecte
DETAIL ponctuelle autour de T0 puis T+45, un post-match après observation d'un état final explicite
et, pour les rencontres prioritaires, un recontrôle par défaut à fin observée +60 minutes. Le lot 4
fixe des créneaux UTC explicites avant tout dispatch : LINEUP T−30 dans `[T−30,T−15)`, LINEUP T−15
dans `[T−15,T)`, DETAIL T0 dans `[T−5,T+5)`, DETAIL T+45 dans `[T+40,T+50)`, recontrôle dans
`[fin observée +60, fin observée +65)`. Les jobs hors de leur créneau sont marqués `MISSED_WINDOW`
sans réservation de budget ni rattrapage ; le post-match initial, lui, est rendu éligible après le
commit d'une observation DETAIL portant un marqueur final reconnu. Ces règles n'affectent pas les
dates sources ou le statut canonique.
T0/T+45 ne crée pas de profil live et n'autorise aucun polling à la minute. T+15/T+75 est hors
périmètre. L'absence d'heure réelle de début/fin reste inconnue. Les DETAIL T0/T+45 sont des
observations d'enrichissement distinctes ; leur fenêtre de collecte et leurs horloges ne modifient
pas le statut canonique, le kickoff ou les faits de la rencontre. Aucun état « en cours », signal de
mi-temps ou fait de joueur n'est inféré depuis un décalage relatif, `clock` ou l'instant de réception.
Ces données ne contournent pas les statuts calendaires pris en charge par le normaliseur.

Une composition est `ABSENT`, `INCOMPLETE`, `COMPLETE`, `COMPLETE_LATE` ou `UNKNOWN`. Pour la
classification structurelle `COMPLETE`, les deux listes de titulaires doivent être présentes et
contenir chacune onze IDs fournisseur distincts, non blancs. Ce constat ne signifie pas qu'une
confirmation officielle distincte a été fournie ; un éventuel indicateur fournisseur est conservé
séparément et reste inconnu s'il est absent. Une première disponibilité et les vérifications
admissibles suivantes sont horodatées. Une composition complète dont les octets sont reçus à ou
après le kickoff prévu est `COMPLETE_LATE`, jamais une preuve prématch. Une composition absente
reste non bloquante pour la rencontre. Une fois le critère structurel atteint, la planification
n'ajoute plus de vérification LINEUP pour cette rencontre.

Les heures des fenêtres seront calculées depuis le kickoff programmé et conservées séparément
de l'instant d'envoi/réception. Une fenêtre manquée n'est ni `ABSENT` ni un gain de budget qualifié.
Le lot 2 persiste un plan par date UTC, idempotent par clé et empreinte de commande, revalidé depuis
la prévision MVP-001 et limité à sept admissions. Cette admission et son coût estimé ne réservent
pas le budget et n'autorisent aucun envoi. Le lot 3 consomme cette admission au moyen d'une
intention réelle du ledger MVP-001 avant tout transport ; aucune étape de plan ne déclenche
automatiquement un appel.

Les étapes post-match restent non armées jusqu'à une observation explicite de l'état final. Une
fois armée, l'étape reprend l'instant observé comme échéance ; la vérification prioritaire est
planifiée à cet instant +60 minutes. Une heure programmée, un délai écoulé ou un statut déduit ne
peuvent armer ces étapes.

## Dispatch conditionnel livré au lot 4

`V014__enrichment_job_dispatch.sql` rend exécutables les types de jobs d'enrichissement et persiste
leurs entrées typées ainsi que les routes fournisseur exactes. Les jobs sont créés idempotemment à
partir du plan quotidien ; chaque étape LINEUP ou DETAIL n'est éligible que dans sa fenêtre UTC. Une
fenêtre manquée se termine `MISSED_WINDOW` sans réserver le budget et sans rattrapage. Une composition
déjà évaluée `COMPLETE` empêche l'ajout du checkpoint LINEUP suivant, mais `ABSENT` ou `INCOMPLETE`
ne bloque pas la rencontre.

Le worker réutilise le claim/fencing et le backoff d'infrastructure existants sous `batch-worker`.
Pour chaque collecte, il exige le PRIMARY exact et un budget de même fournisseur, puis passe par le
ledger avant le transport. Une intention déjà engagée ou incertaine ne reçoit jamais une seconde
autorisation HTTP ; une défaillance d'infrastructure ne transforme pas cette règle en retry réseau.
Le post-match n'est armé qu'après une observation DETAIL explicitement finale reconnue par le
parseur. Le recontrôle des rencontres prioritaires est fixé dans ce lot à fin observée +60 minutes,
avec une fenêtre bornée ; les observations successives restent append-only. Ce délai fixe satisfait
la décision D02 et ne constitue pas encore une configuration opérateur.

V014 est additive après V013. La configuration du registre de production reste vide, la boucle du
worker et les clients restent désactivés par défaut ; cette livraison et ses tests synthétiques
n'activent aucun fournisseur ni pilote et n'effectuent aucun appel externe.

## Post-match, versions et qualité

Les appels post-match ne démarrent qu'après une réponse fournissant explicitement l'état final
selon le parseur fournisseur versionné. T+120, un score visible ou l'écoulement du temps ne
prouvent pas `FINISHED`. Les réponses DETAIL, statistiques, événements et joueurs gardent leurs
observations successives. Une réponse plus récente mais vide ne détruit pas l'observation
précédente : la régression est signalée pour examen et aucun retry HTTP automatique n'est déduit
du seul `200` vide.

Codes de qualité à produire sans altérer la preuve source :

- `MISSING_PLAYER_FULL_NAME` ;
- `ZERO_MINUTE_EXPECTED_METRICS` ;
- `INVALID_SECOND_YELLOW_VALUE` ;
- `MISSING_PLAYER_YELLOW_CARD` ;
- `CROSS_ENDPOINT_PLAYER_ID_MISMATCH`.

Les faits disciplinaires d'événements priment pour exposer une contradiction ; aucun box-score
source n'est corrigé silencieusement. Une métrique absente ne devient pas zéro. Les raisons
d'exclusion se rapportent à la donnée ou à l'entité concernée et n'invalident pas toute la
rencontre par défaut.

Lorsqu'une observation devient `EMPTY` après une valeur `AVAILABLE` ou `PARTIAL` de même fournisseur,
famille et rencontre, l'observation antérieure est conservée et le nouvel enregistrement porte
`EMPTY_RESPONSE_WITH_HISTORICAL_REGRESSION`. Deux observations MATCH_DETAIL explicitement finales,
issues de fournisseurs distincts et contenant chacune les deux scores, produisent
`FINAL_SCORE_CONFLICT` lorsque les scores diffèrent. Ce constat est informatif : il ne modifie ni
l'autorité calendrier, ni le canon, ni l'une des preuves source. Les cinq quarantaines et ces deux
constats de régression/divergence sont persistés et consultables sous forme agrégée sans payload.

La reprise des jobs d'enrichissement utilise le claim et le fencing partagés : un propriétaire
expiré ne peut ni écrire ni acquitter après une reprise. Le backoff d'infrastructure est borné.
Une intention de budget autorisée n'est jamais réautorisée ; une réponse conservée peut être rejouée
sans appel fournisseur supplémentaire. Ces garanties techniques ne valent pas activation réelle
du worker ni des clients.

## Replay et sécurité

Le replay reparcourt les octets du snapshot conservé avec le parseur versionné, sans réseau
fournisseur ni chemin fichier fourni par HTTP. Hash du payload et hash de la représentation sont
distincts. Aucune projection de consultation ne renvoie les octets bruts, secrets, URL
authentifiée ou justification opérateur.

La configuration d'un fournisseur réel ne pourra être envisagée qu'après implémentation du
contrat, validation avec fixtures synthétiques et autorisation distincte. Les rapports COV-002
référencent des campagnes datées ; ils ne sont ni une valeur initiale de budget, ni une
autorisation de collecte, ni un résultat de replay du présent contrat.
