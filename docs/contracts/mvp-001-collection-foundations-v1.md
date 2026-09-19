# MVP-001 - Fondations de collecte v1

Décisions acceptées par le porteur le 6 septembre 2026 pour les lots 0 et 1.
Le budget durable est spécialisé par les décisions de réalisation du lot 2 du 13 septembre 2026.
Ce contrat prépare les lots de collecte ; son existence n'active aucun fournisseur.

## Références et activation

Précision du porteur du 6 septembre 2026 : les deux API fournisseur appelables par Betting
Project sont **Highlightly et football-data.org**. API-FOOTBALL et les autres candidats restent
des références historiques de benchmark, pas des connecteurs à activer dans MVP-001.
Ce choix de fournisseurs n'active aucune entrée réelle du registre.

La journée de la tâche [Poursuivre collectes](thread://01a076a2-5c0b-7342-a0f4-b9feb99032b2?hostId=local)
a couvert quinze rencontres du top 5 européen sous une dérogation datée à 100 appels Highlightly,
réserve 0. Elle mesure disponibilité, fraîcheur et complétude face aux relevés du Lab : 100 appels
consommés et 18 familles dédiées post-match encore différées au point de mesure de 23:07.
Voir la [clarification COV-002](../benchmark/mvp-001-cov002-clarification-20260906.md), qui distingue
audit hors réseau, résultats rapportés, simulations budgétaires et orientations du porteur.

La cible produit précisée est un calendrier quotidien top 5 + UEFA, avec extensions PT/NL/TR
éventuelles ; le noyau opérationnel PPL/PD/DED/ELC n'est pas élargi sans révision explicite des
critères et preuves. Le Lab n'est **plus seulement un comparateur** : le porteur souhaite une
alimentation complémentaire optionnelle par exports J7. Il ne devient pas une troisième API appelée
directement. Le receiver INT-001 reste local et opt-in ; son ACK ne prouve pas l'application
métier. Le consommateur J7 et le passage à des données exploitables restent à cadrer séparément.

Les futures compositions en mode normal ciblent T-30 puis T-15 conditionnel, avec DETAIL T0
distinct. Le contrôle T+45 souhaité doit être budgété ; sa famille DETAIL est une proposition à
confirmer. L'expérience live minute du Lab ne fait pas partie de l'activation de MVP-001.

Le [registre des preuves](../benchmark/mvp-001-capability-evidence-v1.md) distingue références
réelles, fixtures synthétiques et lacunes. Aucune saison, phase ou identité manquante n'est déduite
du nom d'une compétition. Le registre classpath initial reste vide et fermé. Les tests démontrent
le fonctionnement sur des références synthétiques. Un code de routage PPL/PD/DED/ELC n'est jamais
un UUID canonique ; les mappings explicites du catalogue restent nécessaires.

Le registre distingue couverture et autorité. PRIMARY exige le rôle PRIMARY, CONTROL le rôle
CONTROL, CALENDAR_ONLY un rôle explicite et le type CALENDAR. PILOT, NON_APPLICABLE et
BLOCKED_BY_PLAN n'accordent aucune autorité. Une entrée inactive reste UNASSIGNED.

## Provenance et replay

La chaîne de collecte future est : intention durable et réservation → tentative auditée → octets
reçus conservés avant parsing → représentation calendrier dérivée → normaliseur existant.
Chaque étape conserve ses liens de provenance. Le hash des octets fournisseur et le hash de la
représentation dérivée sont distincts. La version du parseur est enregistrée ; une nouvelle
interprétation ne réécrit pas une preuve historique. Un replay part des octets stockés sans HTTP.

L'instant observedAt est fixé à la collecte depuis l'instant fournisseur fiable, ou explicitement
depuis la réception avec provenance. Le normaliseur ne fait aucun fallback. L'ordre source et les
valeurs absentes restent représentés, sans tri implicite, zéro inventé ou mapping textuel.

## Budget et interruptions

Le lot 2 comptabilise des tentatives HTTP, pas des familles logiques : plusieurs données dérivées
du même DETAIL ne représentent pas plusieurs appels. Les limites de cadence football-data.org
par minute observées durant COV-002 sont distinctes du budget projet et ne prouvent pas un quota
quotidien fournisseur. Les états report sans tentative, fenêtre manquée, donnée absente et envoi
incertain doivent rester séparés. Aucune économie J7 n'est revendiquée avant preuve qualifiée et
disponible dans Betting Project ; un appel possiblement envoyé ne peut pas être remboursé ainsi.

La capacité fournisseur de 100 et le plafond opérationnel de 80 sont deux paramètres distincts.
La mention des 100 appels pour la campagne n'abroge pas à elle seule la réserve de sécurité
acceptée de 20. Les réservations d'appels planifiés de la campagne sont des engagements à
consommer, pas cette réserve de sécurité. Toute révision de cette politique sera explicitement
consignée après décision du porteur, sans déduire un renouvellement de fenêtre d'une date civile.
Si campagne et application partagent le même quota, leur consommation se cumule : Betting
Project ne dispose pas d'une seconde enveloppe de 100. Le lot 2 impose l'utilisation exclusive
du compte par l'application pendant sa fenêtre. La consommation externe antérieure est déclarée
à l'initialisation, sans fabriquer d'anciennes intentions ; une utilisation externe imprévue exige
une réconciliation, jamais une hypothèse de zéro consommé. Le quota de football-data.org reste
distinct et ne reçoit pas par analogie les paramètres Highlightly.

- Highlightly : capacité de référence 100, plafond opérationnel 80, réserve 20. Ne pas retrancher
  encore 20 du plafond opérationnel 80.
- Réserver avant l'envoi ; inclure les réservations concurrentes dans la disponibilité prudente.
- Comparer budget projet, compteur local et compteur fournisseur ; une absence n'est pas un zéro
  ni une capacité illimitée. L'activation réelle exige une fenêtre et un état explicitement initialisés.
- Ni minuit Europe/Paris ni la seule hausse d'un compteur ne renouvellent une fenêtre.
- Un appel possiblement envoyé dont le résultat n'est pas enregistré conserve un état incertain
  et son coût budgétaire. Il bloque son propre nouvel envoi, pas les autres intentions finançables.
  La réconciliation explicite ne permet ni son remboursement ni sa relance automatique.
- HTTP 401/403/429 suspend le fournisseur concerné pour la fenêtre et ouvre un incident.
- L'arrêt d'un fournisseur n'annule pas les preuves déjà conservées pour un autre.

Le contrat normatif [provider-budget-v1](provider-budget-v1.md) précise les trois bornes
concurrentes : projet, quota partagé moins réserve, et restant observé moins réserve et
engagements non couverts. Les réservations jamais envoyées sont retenues par chaque borne,
sans soustraire deux fois la réserve ni débiter une deuxième fois la réception d'une réponse.
La couverture d'un engagement par un compteur exige une preuve explicite ; elle ne se déduit
ni de l'ordre de réception, ni de l'horodatage, ni d'un compteur local. Depuis 100, le budget
80/20 permet 80 réservations ; après 12 appels externes antérieurs et un restant de 88, il en
permet 68. Une observation requise absente, expirée ou contradictoire ferme les autorisations.

La cadence se vérifie au passage avant envoi, pas à la réservation d'un appel futur. Elle prend
en compte les intentions du même périmètre à travers les fenêtres : un engagement sans résultat
conserve son créneau, puis le délai de libération commence au résultat ou à la réconciliation.
Aucun reset quotidien, nouvelle fenêtre ou redémarrage ne libère implicitement ce créneau.

Le lot 2 fournit les cas d'usage internes et V009, sans client HTTP. Les façades refusent une
transaction appelante ouverte ; l'autorisation nouvelle n'est retournée qu'après commit durable.
Les administrations sont limitées à `control-api`, avec identité locale et justification expurgée.
Le journal du budget ne remplace pas les octets bruts, leurs empreintes ou l'audit fournisseur du
lot 3. Le hash canonique du résultat budgétaire inclut statut, empreinte fournie et quota éventuel ;
il reste distinct du hash des seuls octets fournisseur. L'ordonnancement et l'envoi des effets
externes appartiennent aux lots ultérieurs, sans activer un dispatch d'outbox dans ce lot.

## Bornes des adaptateurs calendrier

Connexion : 5 secondes ; requête : 30 secondes ; réponse : 5 Mio au maximum ; collecte : 100 pages
au maximum. Une pagination cyclique, tronquée ou interrompue est explicitement incomplète. Les
pages reçues restent conservées, sans prétendre avoir obtenu un calendrier exhaustif.

La traduction des statuts et marqueurs est livrée par le
[contrat calendrier fournisseur v1](provider-calendar-collection-v1.md), ses parseurs et ses fixtures
synthétiques. Un statut inconnu n'est jamais transformé en succès nominal. Les clients ENR restent
des outils de benchmark et ne deviennent pas implicitement les adaptateurs de production.

Décisions du 18 septembre 2026 : les métadonnées descriptives manquantes restent inconnues ; les
mappings et identifiants restent obligatoires. La saison et la phase canoniques viennent de la
route exacte du registre, tandis que les observations, mappings et clés d'autorité gardent leur
contexte natif. V010 relie les preuves brutes, audits et dérivations. L'envoi synchrone utilise le
budget du lot 2 et une outbox minimisée, sans worker ou nouvelle autorisation sur une répétition.
Un compteur reçu apporte une borne conservatrice, avec couverture vide et sans prolongation de
validité. Les transports et le registre réels restent fermés ; l'orchestration appartient au lot 4.

## Livraison par lot

Le lot 5 ajoute les [consultations internes](collection-control-api-v1.md) des capacités, budgets,
intentions, incidents, jobs et pages, sans exposer de payload ou clé interne. La sélection PPL/PD
est une prévision sans réservation : quatre calendriers primaires terminés du noyau pour un même
jour UTC, budget restant réel, coût par rencontre explicitement fourni, puis priorité, kickoff et
UUID. Au plus sept rencontres sont proposées. Les fournisseurs restent désactivés ; ENR-002 devra
revalider, réserver et contrôler le plafond durable avant tout enrichissement réel.

Le lot 4 du 19 septembre ajoute les jobs et reprises selon
[collection-jobs-v1](collection-jobs-v1.md) : planification datée explicite, claim PostgreSQL,
bail/jeton, historique et backoff bornés. L'outbox dédiée ne consomme ni publication ni J7.
L'exécution gérée reprend les preuves acquises mais jamais un envoi incertain ; registre et
parseur sont figés par job. La boucle `batch-worker` est opt-in et livrée désactivée. Il n'y a
ni renouvellement automatique des fenêtres, ni activation de fournisseur, ni enrichissement.

Chaque lot terminé est validé, commité et poussé sur feature/V0.1.0-RC01-CODEX-MVP-001, puis les
checks du SHA publié sont examinés. Un échec pertinent est corrigé avant progression. Ces pushes
ne créent pas de snapshot durable du train. La PR finale cible feature/V0.1.0-RC01 ; une revue
humaine précède son merge commit. Les lots 0 et 1 ne créent ni migration, ni endpoint, ni worker.
