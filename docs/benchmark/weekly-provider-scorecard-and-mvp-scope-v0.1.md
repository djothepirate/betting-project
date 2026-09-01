# Bilan fournisseurs, quotas et préparation du périmètre MVP v0.1

- **Statut :** Baseline MVP acceptée pour la suite du projet
- **Date d'acceptation de la baseline :** 2026-09-01
- **Période sportive observée :** du 10 au 16 août 2026
- **Date de consolidation :** 17 août 2026
- **Fuseau métier :** Europe/Paris
- **Scénarios consolidés :** INV-01, CAL-01 et ENR-001
- **Fournisseurs :** Highlightly, football-data.org et API-FOOTBALL
- **Documents de référence :** `enrichment-results-v0.1.md`, `enrichment-collection-plan-v0.1.md`, `enrichment-sample-v0.1.json` et `../work-orders/ENR-001.md`

## 1. Résumé décisionnel

Le corpus fonde la baseline MVP acceptée sans désigner un fournisseur unique pour toutes les capacités.

La baseline v0.1 retenue est la suivante :

1. **retenir Highlightly comme fournisseur principal 2026** pour le calendrier étendu, le détail des rencontres, les scores, les statistiques d'équipe, les événements et les statistiques joueurs ;
2. **retenir football-data.org comme source de contrôle calendrier et identité** sur les compétitions explicitement couvertes par son plan gratuit, en commençant par la Liga Portugal, LaLiga, l'Eredivisie et le Championship ;
3. **ne pas intégrer API-FOOTBALL au chemin critique du MVP 2026** avec le plan gratuit testé, la saison 2026 étant inaccessible ;
4. **rendre les compositions prématch facultatives et non bloquantes** : Highlightly n'a fourni une composition complète avant le coup d'envoi que sur 5 des 10 rencontres P03 à P12 évaluables avec un protocole prématch exploitable ;
5. **ne jamais enrichir indistinctement toutes les rencontres du calendrier** : le quota Highlightly impose une liste quotidienne priorisée ;
6. **conserver les données brutes et appliquer des règles de qualité obligatoires** avant toute exploitation des données joueurs ;
7. **ouvrir le MVP sur un noyau restreint et mesuré**, puis activer les autres compétitions après une courte campagne de validation propre à leur phase et à leur type de données.

La stabilité technique est très bonne : Highlightly a retourné 161 réponses HTTP 2xx sur 161 appels dans le corpus INV-01, CAL-01 et ENR-001. La principale réserve n'est donc pas la connectivité du fournisseur, mais la disponibilité et la cohérence de certaines données.

## 2. Périmètre et règles de lecture

### 2.1 Corpus de preuves

| Lot | Portée | Appels fournisseur | Résultat principal |
|---|---|---:|---|
| INV-01 | 59 compétitions ciblées | 17 | Highlightly couvre 58 compétitions en 2026 ; football-data.org en mappe 5 ; API-FOOTBALL mappe les 59 sur 2024 mais pas sur 2026 |
| CAL-01 | 7 dates et 137 contrôles compétition/jour | 36 | 30 appels initiaux, puis 6 appels football-data.org ciblés |
| ENR-001 | 11 rencontres principales engagées sur 12, plus 1 contrôle transversal | 127 | 127 réponses HTTP 2xx, 127 preuves brutes et 127 replays `PASS` |
| **Total du corpus** | Inventaire, calendrier et enrichissement | **180** | 161 appels Highlightly, 17 football-data.org et 2 API-FOOTBALL |

Deux appels post-match P12 ont été exécutés le 17 août, mais sont rattachés à la rencontre PSG - Lens du 16 août. Ils sont inclus dans le corpus de décision et signalés séparément dans les tableaux de quota.

### 2.2 Corrections humaines appliquées à CAL-01

Les écarts initiaux du classeur ne sont pas tous considérés comme des erreurs fournisseur. Les corrections acceptées pendant CAL-01 ont notamment porté sur :

- le nombre de rencontres de Ligue Conférence du 12 août ;
- l'Austrian Bundesliga, LaLiga 2, l'Ekstraklasa, la Schweizer Cup et la Faroe Islands Premier League ;
- plusieurs conflits de date en Roumanie, à Malte, en Ukraine et au Luxembourg ;
- le mapping historique `Hirnyk` vers FC Kryvbas Kryvyi Rih.

La valeur de 119 contrôles exacts sur 136 chez Highlightly, soit 87,5 %, décrit donc le premier calcul automatique avant la résolution complète de ces erreurs de référence et conflits inter-sources. Elle ne doit pas être interprétée comme un taux d'exactitude fournisseur définitif.

### 2.3 Trois notions séparées

Le bilan distingue :

- **la stabilité technique**, c'est-à-dire la capacité à répondre sans erreur de transport, d'authentification ou de quota ;
- **la disponibilité fonctionnelle**, c'est-à-dire la présence de la donnée au moment où le produit en a besoin ;
- **la qualité métier**, c'est-à-dire la cohérence, la profondeur et la réconciliabilité des valeurs renvoyées.

Une réponse HTTP 200 contenant une composition vide est techniquement stable, mais fonctionnellement insuffisante pour LIN-01.

## 3. Bilan des quotas

### 3.1 Consommation consolidée par fournisseur

| Fournisseur | Limite observée ou documentée | INV-01 | CAL-01 | ENR-001 | Total corpus | Incident de quota |
|---|---:|---:|---:|---:|---:|---|
| Highlightly | 100 appels/jour selon le plan ; budget projet 80 et réserve 20 ; reset non aligné sur minuit Paris | 14 | 23 | 124 | **161** | Aucun HTTP 429 ; réserve jamais entamée |
| football-data.org | 10 appels/minute ; budget projet 8/minute | 1 | 13 | 3 | **17** | Aucun HTTP 429 ; appels séquentiels |
| API-FOOTBALL | 100 appels/jour et 10 appels/minute observés | 2 | 0 | 0 | **2** | Aucun incident de quota ; saison 2026 bloquée par le plan |
| **Total** | — | **17** | **36** | **127** | **180** | Aucun dépassement |

Pour Highlightly, les 14 appels d'inventaire et les 23 appels CAL-01 ont été exécutés dans la même fenêtre de quota : la valeur restante est passée de 99 à 63. Le calendrier des sept jours a donc coûté 23 appels, soit 3,3 appels par date en moyenne et jusqu'à 7 appels pour une journée fortement paginée.

Pour football-data.org, les 13 appels CAL-01 comprennent :

- 7 appels au point d'entrée global par date ;
- 5 appels ciblés réussis pour `PPL`, `CL`, `DED`, `ELC` et `PD` ;
- 1 appel diagnostique `CL&season=2026` ayant renvoyé HTTP 404.

### 3.2 Consommation ENR-001 par jour civil Europe/Paris

| Date | Highlightly | football-data.org | Commentaire |
|---|---:|---:|---|
| 11 août | 1 | 0 | Baseline P01 |
| 12 août | 4 | 0 | Rattrapages P01 |
| 13 août | 63 | 0 | P01 post-match, puis diagnostics répétés P03 à P05 |
| 14 août | 26 | 1 | P06 à P08, contrôle P08 multi-fournisseurs |
| 15 août | 15 | 2 | P09 à P11 et contrôles P10/P11 |
| 16 août | 13 | 0 | Fin du lot P09 à P11 après minuit, puis P12 prématch |
| 17 août | 2 | 0 | P12 post-match, rattaché au match du 16 août |
| **Total** | **124** | **3** | **127 appels** |

Le pic calculé par jour civil Europe/Paris est de 63 appels Highlightly le 13 août. Le garde-fou local a enregistré un point haut plus prudent de 65 / 80 après le dernier suivi P05, la fenêtre locale du garde et la fenêtre de remise à zéro fournisseur n'étant pas identiques. Le bilan retient donc le point haut opérationnel de 65 / 80, soit :

- 65 % de la limite fournisseur de 100 ;
- 81,25 % du budget opérationnel de 80 ;
- 15 appels de marge avant la réserve de sécurité.

Ce pic n'est pas représentatif du protocole MVP recommandé. Il comprend des diagnostics live et post-match répétés sur P03 à P05, dont des appels de composition après le coup d'envoi qui sont désormais interdits dans LIN-01.

### 3.3 Appels par rencontre ENR-001

| Échantillon | Compétition | Highlightly | football-data.org | Total |
|---|---|---:|---:|---:|
| P01 | Supercoupe de l'UEFA | 6 | 0 | 6 |
| P02 | Saudi Pro League | 0 | 0 | 0, rencontre non engagée |
| P03 | Ligue Europa | 23 | 0 | 23 |
| P04 | Ligue Europa | 23 | 0 | 23 |
| P05 | Ligue Conférence | 16 | 0 | 16 |
| P06 | Saudi Pro League | 7 | 0 | 7 |
| P07 | Ligue 2 | 8 | 0 | 8 |
| P08 | Liga Portugal | 10 | 1 | 11 |
| P09 | Saudi Pro League | 7 | 0 | 7 |
| P10 | LaLiga | 8 | 1 | 9 |
| P11 | Liga Portugal | 8 | 1 | 9 |
| P12 | Trophée des Champions | 7 | 0 | 7 |
| C01 | Ukrainian Premier League, contrôle d'identité | 1 | 0 | 1 |
| **Total** | — | **124** | **3** | **127** |

P03, P04 et P05 ont volontairement consommé davantage de quota pour diagnostiquer une absence de données. Pour un fonctionnement courant, le plafond accepté reste de 10 appels par rencontre prioritaire lorsque T-5 est activé, avec arrêt immédiat des appels LIN-01 dès qu'une composition est complète.

### 3.4 Conséquence capacitaire pour le MVP

Dans le cas conservateur le plus chargé :

- 80 appels Highlightly sont utilisables sans entamer la réserve ;
- une journée de calendrier fortement paginée peut coûter 7 appels ;
- il reste alors 73 appels ;
- à 10 appels maximum par cycle complet, **7 rencontres prioritaires par jour** peuvent être enrichies sans dépasser le budget.

Cette borne ne signifie pas que chaque rencontre consommera 10 appels. Les compositions publiées tôt réduisent le nombre d'appels conditionnels. Elle constitue néanmoins la capacité prudente à retenir pour définir le périmètre initial.

Le MVP doit donc distinguer :

- un **périmètre calendrier**, potentiellement plus large ;
- un **périmètre d'enrichissement complet**, limité à une sélection quotidienne priorisée ;
- un **périmètre de contrôle**, utilisant football-data.org ou des vérifications humaines sans multiplier les appels Highlightly.

### 3.5 Remise à zéro du quota

Le compteur Highlightly ne s'est pas remis à zéro à minuit Europe/Paris de manière constante. Il est notamment passé :

- de 96 appels restants à 17:31 à 99 à 19:04 le 15 août ;
- de 77 appels restants à 01:00 à 99 à 19:52 le 16 août.

Le planificateur ne doit donc pas supposer une remise à zéro à minuit Paris. Il doit combiner :

- le compteur renvoyé par le fournisseur ;
- un compteur local par fenêtre de quota ;
- le plafond projet de 80 ;
- l'arrêt avant la réserve de 20.

## 4. Bilan de stabilité

### 4.1 Stabilité technique

| Fournisseur | Appels du corpus | Résultat HTTP | Authentification / limite | Conclusion technique |
|---|---:|---|---|---|
| Highlightly | 161 | 161 HTTP 2xx | Aucun 401, 403 ou 429 | Très stable |
| football-data.org | 17 | 16 HTTP 200 et 1 HTTP 404 diagnostique | Aucun 401, 403 ou 429 | Stable ; routage par compétition obligatoire |
| API-FOOTBALL | 2 | 2 HTTP 200 | Aucun 401, 403 ou 429 | Transport stable, mais rejet fonctionnel de 2026 |

Les 127 appels ENR-001 ont tous produit une preuve brute et un replay hors réseau `PASS`. Aucun échec réseau fournisseur n'a entraîné la perte d'un snapshot enregistré.

Les incidents suivants sont locaux et ne doivent pas dégrader la note technique des fournisseurs :

- l'instabilité de connexion signalée le 12 août, non reproduite les jours suivants ;
- le premier décalage de planification de deux heures lié au fuseau ;
- trois incidents récupérés de finalisation football-data.org, de parsing PowerShell et de résolution Maven, sans appel fournisseur perdu.

### 4.2 Latences Highlightly sur ENR-001

| Famille | Appels | Moyenne | Médiane | P95 | Maximum |
|---|---:|---:|---:|---:|---:|
| DETAIL | 20 | 1 033 ms | 768 ms | 2 743 ms | 3 831 ms |
| LINEUP | 54 | 1 292 ms | 569 ms | 4 489 ms | 8 119 ms |
| STATISTICS | 17 | 939 ms | 689 ms | 2 469 ms | 2 469 ms |
| EVENTS | 16 | 818 ms | 454 ms | 3 047 ms | 3 047 ms |
| BOX_SCORE | 17 | 2 139 ms | 1 318 ms | 10 792 ms | 10 792 ms |

Le BOX_SCORE est la famille la plus lente et la plus variable. Ces temps restent compatibles avec une collecte batch, mais justifient des timeouts différenciés par famille et l'absence de rafales parallèles agressives.

### 4.3 Stabilité fonctionnelle des calendriers

#### Highlightly

- 529 compétitions 2026 dans l'inventaire ;
- 58 des 59 compétitions ciblées mappées ;
- seule la Ligue 3 française n'était pas couverte dans l'inventaire initial ;
- 437 rencontres trouvées pour 437 attendues sur les contrôles déclarés applicables de CAL-01 ;
- aucune absence de priorité 1 dans le calcul initial ;
- pagination complète, aucun identifiant dupliqué et aucun décalage de date locale dans le collecteur.

La couverture calendrier est forte. Les conflits de programmation constatés démontrent néanmoins qu'un calendrier reste révisable et doit conserver ses observations successives.

#### football-data.org

Le point d'entrée global `/v4/matches?dateFrom&dateTo` n'a pas fourni le calendrier attendu. Le routage par compétition a en revanche renvoyé :

| Compétition | Code | Rencontres du 10 au 16 août |
|---|---:|---:|
| Liga Portugal | PPL | 9 |
| Eredivisie | DED | 9 |
| Championship | ELC | 11 |
| LaLiga | PD | 5 |
| Ligue des Champions | CL | 0 |

Les dix rencontres de Ligue des Champions attendues le 11 août appartenaient au troisième tour qualificatif retour. Les preuves sont compatibles avec une non-couverture des tours qualificatifs par football-data.org. Cette phase doit être considérée comme non disponible tant qu'un test contraire ne l'établit pas.

#### API-FOOTBALL

Le plan gratuit testé retourne un inventaire riche sur la saison 2024, mais refuse la saison 2026 en indiquant une plage gratuite 2022-2024. Cette restriction est bloquante pour le calendrier courant du MVP, indépendamment de la stabilité HTTP.

### 4.4 Stabilité fonctionnelle des compositions

P01 n'entre pas dans le taux principal : ses fenêtres T-60 à T-30 ont été manquées à cause du premier problème de planification et de connexion locale. Une preuve à T-17 était vide et une preuve à la mi-temps était complète, sans permettre de conclure sur l'heure réelle de publication.

Sur les dix rencontres P03 à P12 disposant d'un protocole comparable :

| Résultat LIN-01 | Nombre | Échantillons |
|---|---:|---|
| Composition complète avant T0 | 5 / 10 | P06, P08, P09, P10 et P12 |
| Composition complète seulement après T0 | 2 / 10 | P05 à environ T+3 ; P07 à la mi-temps |
| Composition non obtenue dans le lot | 2 / 10 | P03 et P04 |
| Absente au dernier contrôle prématch, disponibilité ultérieure inconnue | 1 / 10 | P11 à environ T-12 |

Les cinq premières compositions complètes ont été observées approximativement à T-29, T-28, T-14, T-12 et T-11. La donnée est donc utile lorsqu'elle est publiée, mais sa fraîcheur varie fortement selon la compétition et la rencontre.

Conclusion LIN-01 : **les compositions ne peuvent pas être une dépendance critique du MVP prématch**. Elles doivent enrichir un dossier lorsqu'elles existent, avec une valeur de statut `ABSENT`, `INCOMPLETE`, `COMPLETE`, `COMPLETE_LATE` ou `UNKNOWN`.

### 4.5 Stabilité fonctionnelle post-match

| Type | Rencontres autorisées et exécutées | Résultat |
|---|---:|---|
| Statistiques d'équipe | 11 / 11 | Toutes exploitables ; 6 schémas profonds et 5 schémas réduits ou moins profonds |
| Événements | 10 / 10 | Tous structurellement complets ; P12 non exécuté, donc non compté comme échec |
| Statistiques joueurs | 9 / 11 disponibles | P03 et P04 restent vides ; les 9 autres sont exploitables avec anomalies |

Les statistiques d'équipe peuvent être révisées après le statut `Finished` : seize valeurs P03 ont changé entre deux snapshots finaux espacés d'environ 52 minutes. Une collecte finale immédiate ne doit donc pas toujours être considérée comme définitive.

Les événements reconstruisent correctement les scores, les buts, les remplacements et les expulsions dans les lots observés. Ils doivent primer pour la discipline lorsqu'ils contredisent le box-score individuel.

Les quatre catégories d'anomalies joueurs confirmées sont :

- `MISSING_PLAYER_FULL_NAME` ;
- `ZERO_MINUTE_EXPECTED_METRICS` ;
- `INVALID_SECOND_YELLOW_VALUE` ;
- `MISSING_PLAYER_YELLOW_CARD`.

Une catégorie d'identité inter-endpoints, `CROSS_ENDPOINT_PLAYER_ID_MISMATCH`, interdit également de joindre silencieusement les événements et le box-score sur le seul identifiant joueur Highlightly.

### 4.6 Bilan par rencontre retenue

| Échantillon | Rencontre | Fournisseur(s) | ID/TIM | LIN-01 | Post-match | Bilan de stabilité fonctionnelle |
|---|---|---|---|---|---|---|
| P01 | Paris Saint-Germain - Aston Villa | Highlightly | `PASS` | N/C : vide à T-17, complète à la mi-temps | STA/EVT complets ; PLY avec anomalies | Transport stable ; fraîcheur LIN non mesurable à cause des fenêtres manquées |
| P02 | Al Diriyah - Al-Ahli | Highlightly prévu | N/E | N/E | N/E | Rencontre principale non engagée ; aucune note attribuée |
| P03 | Anderlecht - PAOK | Highlightly | `PASS` | `PERSISTENTLY_ABSENT` | STA 39 champs et révisée ; EVT complet ; PLY vide | Défaut fonctionnel persistant sur LIN et PLY |
| P04 | Rangers - Jagiellonia | Highlightly | `PASS` | `PERSISTENTLY_ABSENT` | STA 13 champs ; EVT complet ; PLY vide | Défaut fonctionnel persistant sur LIN et PLY |
| P05 | Shelbourne - Ajax | Highlightly | `PASS` | `COMPLETE_LATE` à environ T+3 | STA/EVT complets ; PLY avec anomalies | Données disponibles, mais trop tard pour le prématch |
| P06 | Al-Hilal - Al-Faisaly | Highlightly | `PASS` | Complète à environ T-28 | STA/EVT complets ; PLY avec anomalies | Cycle complet exploitable ; identifiant d'un remplaçant manquant |
| P07 | Saint-Étienne - Clermont | Highlightly | `PASS` | Absente à T-28 ; complète à la mi-temps | STA réduite ; EVT complet ; PLY avec anomalies | Enrichissement post-match exploitable ; LIN tardif |
| P08 | Sporting CP - Vitória SC | Highlightly + football-data.org | `PASS` chez les deux | Complète à environ T-11 | STA/EVT complets ; PLY avec anomalies | Comparaison fournisseurs cohérente ; cycle complet exploitable |
| P09 | Al-Nassr - Al-Fateh | Highlightly | `PASS` | Complète à environ T-29 | STA/EVT complets ; PLY avec anomalies | Cycle complet exploitable ; identifiant d'un remplaçant manquant |
| P10 | Sevilla - Rayo Vallecano | Highlightly + football-data.org | `PASS` chez les deux | Complète à environ T-12 | STA réduite ; EVT complet ; PLY avec anomalies | Comparaison fournisseurs cohérente ; garde-fous joueurs requis |
| P11 | Rio Ave - Porto | Highlightly + football-data.org | `PASS` chez les deux | Absente au dernier contrôle, environ T-12 | STA/EVT complets ; PLY avec anomalies | Calendrier et post-match fiables ; fraîcheur LIN insuffisante |
| P12 | Paris Saint-Germain - Lens | Highlightly | `PASS`, ordre stable | Complète à environ T-14 | STA réduite ; EVT N/E ; PLY avec anomalies | Cycle presque complet ; ordre post-match à joindre par `team.id` |
| C01 | FC Kryvbas Kryvyi Rih - FC Livyi Bereh Kyiv | Highlightly | Alias historique confirmé | N/A | N/A | `CONFIRMED_HISTORICAL_REBRAND_ALIAS` ; libellé fournisseur encore obsolète |

## 5. Méthode de notation

### 5.1 Échelle

| Note | Interprétation |
|---:|---|
| 5,0 | Excellent : couverture très forte, stable et directement exploitable |
| 4,0 à 4,9 | Solide : utilisable dans le MVP avec contrôles ordinaires |
| 3,0 à 3,9 | Exploitable sous garde-fous : limites matérielles mais non bloquantes |
| 2,0 à 2,9 | Fragile : pilote ou source secondaire uniquement |
| 1,0 à 1,9 | Insuffisant pour le besoin principal |
| 0 à 0,9 | Bloqué ou absent pour le périmètre testé |
| N/A | Capacité structurellement non applicable au fournisseur ou au plan |
| N/E | Capacité non évaluée ; aucune conclusion de qualité |
| N/C | Résultat non comparable à cause d'une fenêtre de test manquée |

### 5.2 Niveau de confiance

| Niveau | Base minimale |
|---|---|
| Élevée | Au moins 5 rencontres pertinentes ou 20 contrôles sur plusieurs dates |
| Moyenne | 2 à 4 rencontres, ou plusieurs dates de calendrier ciblées |
| Faible | Une seule rencontre ou un seul contexte de phase |

La confiance mesure la quantité de preuves, pas la qualité. Une note faible avec confiance élevée signifie qu'une limite s'est reproduite ; une note élevée avec confiance faible reste à confirmer.

## 6. Notation par fournisseur et type de donnée

| Fournisseur | Type de donnée | Note / 5 | Confiance | Motif principal | Décision retenue |
|---|---|---:|---|---|---|
| Highlightly | Inventaire 2026 | 4,8 | Élevée | 58 compétitions ciblées sur 59 | Source principale de découverte |
| Highlightly | Calendrier | 4,5 | Élevée | 437/437 au niveau agrégé applicable, aucune absence P1, pagination complète | Source principale, snapshots révisables |
| Highlightly | Identité, horaire, statut, score | 4,3 | Élevée | Aucun écart TIM-01 ouvert ; alias explicites efficaces | Source principale avec registre d'alias |
| Highlightly | Composition prématch | 2,5 | Élevée | 5/10 complètes avant T0 ; disponibilité tardive ou absente sur les autres | Facultative et non bloquante |
| Highlightly | Statistiques d'équipe | 3,8 | Élevée | 11/11 exploitables, profondeur variable, révisions après `Finished` | Inclure avec version de schéma et snapshot différé |
| Highlightly | Événements | 4,0 | Élevée | 10/10 complets, quelques divergences disciplinaires ou d'identifiants | Inclure ; source disciplinaire prioritaire |
| Highlightly | Statistiques joueurs | 2,5 | Élevée | 9/11 disponibles, 2 vides, anomalies sur toutes les réponses disponibles | Inclure sous quarantaine de champs |
| Highlightly | Stabilité technique et replay | 5,0 | Élevée | 161/161 HTTP 2xx ; 124/124 replays ENR `PASS` | Apte au MVP |
| Highlightly | Quota | 4,0 | Élevée | Aucun dépassement ; quota suffisant pour un lot priorisé, pas pour tout enrichir | Budget 80 + réserve 20 |
| football-data.org | Étendue de l'inventaire | 2,0 | Élevée | 5 compétitions ciblées mappées sur 59 | Source spécialisée, pas généraliste |
| football-data.org | Calendrier des compétitions supportées | 4,7 | Élevée | 34 rencontres sur PPL, DED, ELC et PD via endpoints ciblés | Contrôle calendrier prioritaire |
| football-data.org | Qualifications de Ligue des Champions | 0,0 | Moyenne | 0 rencontre renvoyée pour 10 attendues ; appel `season=2026` en 404 | Ne pas utiliser pour les qualifications |
| football-data.org | Identité, horaire, statut, score | 4,5 | Moyenne | 3/3 comparaisons ENR cohérentes | Contrôle multi-fournisseurs |
| football-data.org | Compositions, statistiques, événements, joueurs | N/A | Élevée | Hors applicabilité du plan et du protocole accepté | Ne pas transformer N/A en échec qualité |
| football-data.org | Stabilité technique | 4,5 | Élevée | 16 HTTP 200 ; 1 HTTP 404 diagnostique ; aucun incident de quota | Apte comme contrôle ciblé |
| football-data.org | Quota | 4,5 | Élevée | 10/minute, appels séquentiels et aucune saturation | Compatible avec le rôle ciblé |
| API-FOOTBALL | Inventaire historique 2024 | 4,8 | Élevée | 59 compétitions ciblées mappées | Candidat au benchmark historique uniquement |
| API-FOOTBALL | Saison et calendrier 2026 | 0,0 | Élevée | Plan gratuit limité à 2022-2024 | Exclure du chemin critique 2026 |
| API-FOOTBALL | Enrichissement 2026 | N/E | Élevée sur le blocage | Aucun appel autorisé dans ENR-001 | Ne pas noter la qualité non testée |
| API-FOOTBALL | Quota | 4,0 | Élevée | 100/jour et 10/minute observés | Sans effet tant que 2026 est inaccessible |

### 6.1 Indicateur de complétude comme fournisseur unique

Cet indicateur n'est pas une décision globale. Il répond seulement à la question : « le fournisseur peut-il, seul, porter toutes les capacités du MVP testé ? » Les capacités manquantes valent zéro dans ce calcul, même lorsqu'elles sont légitimement `N/A` dans le rôle spécialisé du fournisseur.

Pondération : calendrier 25 %, identité/détail 10 %, composition 20 %, statistiques équipe 10 %, événements 10 %, joueurs 10 %, stabilité 10 % et quota 5 %.

Pour football-data.org, la composante « calendrier de bout en bout » vaut 2,5 dans ce calcul : elle combine l'excellente qualité des compétitions supportées avec une étendue limitée à 5 compétitions mappées sur 59. La note spécialisée de 4,6 ne s'applique qu'au rôle ciblé calendrier/contrôle.

| Fournisseur | Score de complétude unique | Lecture correcte |
|---|---:|---|
| Highlightly | **3,8 / 5** | Seul candidat à un rôle principal, avec compositions non bloquantes et garde-fous joueurs |
| football-data.org | **1,8 / 5** | Faible seul, mais **4,6 / 5 dans son rôle calendrier/contrôle** |
| API-FOOTBALL | **0,5 / 5** | Non retenable seul pour le MVP 2026 gratuit |

Le score spécialisé de football-data.org illustre pourquoi le projet ne doit pas choisir un « gagnant global » : il est meilleur comme contrôle calendrier que ne le suggère son score de complétude de bout en bout.

## 7. Notation par compétition, fournisseur et type de donnée

Les notes d'enrichissement portent uniquement sur les rencontres effectivement observées. Les notes de calendrier peuvent reposer sur un nombre plus large de rencontres CAL-01.

| Compétition / phase | Fournisseur | CAL | ID/TIM | LIN prématch | STA | EVT | PLY | Confiance du constat | Rôle retenu |
|---|---|---:|---:|---:|---:|---:|---:|---|---|
| Supercoupe de l'UEFA | Highlightly | 4,5 | 4,5 | N/C | 4,5 | 4,5 | 3,0 | Faible | Événement ponctuel ; ne pas utiliser P01 pour mesurer la fraîcheur LIN |
| Saudi Pro League | Highlightly | 4,5 | 4,5 | **5,0** | 4,5 | 4,5 | 3,0 | Moyenne, 2 matchs | Cycle complet fiable, source unique |
| Ligue Europa, 3e tour qualificatif retour | Highlightly | 4,5 | 4,5 | **0,0** | 3,0 | 4,0 | **0,0** | Moyenne, 2 matchs | Calendrier, score, équipes et événements ; aucune dépendance LIN/PLY |
| Ligue Conférence, 3e tour qualificatif retour | Highlightly | 4,5 | 4,5 | 1,5 | 4,5 | 4,0 | 3,0 | Faible, 1 match | Données post-match utilisables ; composition publiée à T+3 |
| Ligue 2 | Highlightly | 4,5 | 4,5 | 1,0 | 3,5 | 4,0 | 3,0 | Faible, 1 match | Pilote ; composition tardive et schéma équipe réduit |
| Liga Portugal | Highlightly | 4,5 | 4,5 | 2,5 | 4,5 | 4,5 | 3,0 | Moyenne, 2 matchs | Source d'enrichissement, LIN facultatif |
| Liga Portugal | football-data.org | 4,8 | 4,5 | N/A | N/A | N/A | N/A | Élevée calendrier, moyenne sur 2 comparaisons ENR | Contrôle calendrier, horaire, statut et score |
| LaLiga | Highlightly | 4,5 | 4,5 | 5,0 | 3,5 | 4,0 | 2,5 | Faible, 1 match | Source d'enrichissement avec garde-fous |
| LaLiga | football-data.org | 4,8 | 4,5 | N/A | N/A | N/A | N/A | Élevée calendrier, faible sur 1 comparaison ENR | Contrôle calendrier, horaire, statut et score |
| Trophée des Champions | Highlightly | 4,5 | 4,5 | 5,0 | 3,5 | N/E | 3,0 | Faible, 1 match | Événement ponctuel ; ordre par identifiant, pas par position de tableau |
| Eredivisie | football-data.org | 4,8 | 4,0 | N/A | N/A | N/A | N/A | Élevée calendrier | Calendrier validé ; enrichissement encore à tester |
| Championship | football-data.org | 4,8 | 4,0 | N/A | N/A | N/A | N/A | Élevée calendrier | Calendrier validé ; enrichissement encore à tester |
| Ligue des Champions, qualifications | football-data.org | **0,0** | N/E | N/A | N/A | N/A | N/A | Moyenne | Exclure football-data.org pour cette phase |
| Autres compétitions CAL-01 couvertes | Highlightly | 4,5 global | N/E | N/E | N/E | N/E | N/E | Élevée pour CAL, nulle pour ENR | Calendrier uniquement jusqu'à test d'enrichissement |
| Toutes compétitions 2026 | API-FOOTBALL gratuit | **0,0** | N/E | N/E | N/E | N/E | N/E | Élevée sur le blocage | Hors MVP 2026 |

### 7.1 Limites de généralisation

- La Saudi Pro League et la Liga Portugal sont les seules compétitions avec deux rencontres enrichies hors phase UEFA qualificative.
- LaLiga, la Ligue 2, la Ligue Conférence, la Supercoupe de l'UEFA et le Trophée des Champions ne disposent que d'une rencontre chacune.
- Le défaut Ligue Europa est reproduit sur deux rencontres du même tour et de la même date. Il est solide pour ce lot, pas pour toute la compétition.
- Les prochains barrages aller de Ligue Europa et de Ligue Conférence constituent le meilleur test pour déterminer si la phase influence la couverture.
- La Ligue 1, la Premier League, la Bundesliga, la Serie A et la Süper Lig n'ont pas reçu de test d'enrichissement représentatif pendant cette semaine. Elles ne doivent pas être déclarées prêtes sur la seule base de l'inventaire.

## 8. Règles de données acceptées pour le MVP

### 8.1 Données obligatoires

Le traitement d'une rencontre peut avancer si les données suivantes sont disponibles :

- identité canonique des participants ;
- identifiants fournisseur et mappings explicites ;
- compétition, saison et phase ;
- horaire, fuseau et statut ;
- score final lorsque la rencontre est terminée ;
- provenance, heure de collecte, hash et preuve brute.

### 8.2 Données facultatives

Les données suivantes enrichissent la rencontre sans bloquer son traitement :

- composition initiale ;
- remplaçants et formation ;
- statistiques avancées non présentes dans tous les schémas ;
- nom complet du joueur lorsque le nom court et l'identifiant sont disponibles.

### 8.3 Garde-fous obligatoires

1. `cardsSecondYellow` est conservé brut mais ne produit jamais une exclusion sans concordance avec les événements et le rouge.
2. Les métriques attendues d'un joueur à zéro minute sont conservées mais exclues des agrégats métier.
3. `fullName=null` déclenche un repli explicite vers `name`, sans réécriture du brut.
4. Les joueurs ne sont pas joints entre endpoints sur le seul identifiant Highlightly lorsqu'une divergence est observée.
5. Les statistiques post-match sont associées par `team.id`, jamais par leur position dans un tableau.
6. Les rôles HOME/AWAY proviennent de DETAIL ou LINEUP ; une finale neutre conserve l'ordre brut sans l'utiliser comme identité canonique.
7. Une seconde collecte différée des statistiques d'équipe est prévue pour les rencontres prioritaires afin de détecter les corrections après `Finished`.
8. Toute donnée absente reste absente ; elle n'est ni remplacée par zéro ni inventée à partir d'une autre source.

## 9. Options de périmètre MVP

### Option A - Noyau mesuré et progressif, retenue

**Calendrier actif :** Liga Portugal, LaLiga, Eredivisie et Championship.

**Enrichissement complet initial :** Liga Portugal et LaLiga, avec une sélection quotidienne priorisée et des compositions non bloquantes.

**Piste de contrôle hors noyau européen :** Saudi Pro League, qui fournit les meilleurs résultats LIN-01 du lot, mais dont l'inclusion fonctionnelle dépend de la priorité métier du projet.

**Extension conditionnelle :** Eredivisie après au moins deux rencontres ENR complètes ; Championship après au moins une journée ENR si cette compétition reste prioritaire.

Avantages :

- les deux compétitions enrichies du noyau possèdent une comparaison directe Highlightly / football-data.org ;
- le quota reste maîtrisable ;
- l'extension s'appuie sur des critères mesurables ;
- le périmètre reste cohérent avec les priorités européennes du projet.

Limites :

- le nombre initial de compétitions est réduit ;
- les autres championnats prioritaires attendent leur première campagne d'enrichissement ;
- la disponibilité LIN reste non garantie.

### Option B - Périmètre européen élargi dès le départ

**Calendrier actif :** toutes les grandes compétitions couvertes par Highlightly et football-data.org.

**Enrichissement :** Liga Portugal, LaLiga, Eredivisie, Championship, puis Ligue 1, Premier League, Bundesliga, Serie A et Süper Lig dès leur reprise.

Avantage : couverture métier plus rapide.

Risques :

- les capacités ENR n'ont pas encore été observées sur la plupart de ces compétitions ;
- la limite de 7 rencontres détaillées par jour dans le cas conservateur impose malgré tout une sélection ;
- les anomalies joueurs et variations de schéma pourraient différer par compétition.

Cette option est possible uniquement avec une activation par vagues et un droit de retrait automatique d'une capacité défaillante.

### Option C - Enrichissement exhaustif des 58 compétitions Highlightly

Cette option n'est pas recommandée avec le plan gratuit :

- la pagination calendrier consomme déjà une partie du quota ;
- 10 appels théoriques par rencontre rendent le volume incompatible avec 80 appels opérationnels ;
- la qualité ENR n'est pas mesurée sur la majorité des compétitions ;
- l'élargissement diluerait la capacité de contrôle humain des anomalies.

## 10. Décisions MVP acceptées

| Décision | Baseline v0.1 | Statut |
|---|---|---|
| MVP-D01 | Routage par capacité, compétition, saison et phase ; aucun fournisseur unique imposé | Acceptée |
| MVP-D02 | Highlightly principal pour le calendrier étendu et l'enrichissement 2026 | Acceptée avec garde-fous |
| MVP-D03 | football-data.org prioritaire comme contrôle calendrier sur PPL, PD, DED et ELC | Acceptée |
| MVP-D04 | API-FOOTBALL en `BLOCKED_BY_PLAN` pour le chemin critique 2026 ; tout retour exige une nouvelle preuve datée | Acceptée |
| MVP-D05 | LIN-01 facultatif ; T-60/T-45/T-30/T-15, avec T-5 seulement pour une rencontre explicitement prioritaire ; arrêt strict avant T0 | Acceptée |
| MVP-D06 | Budget Highlightly 80, réserve incompressible 20, capacité conservatrice maximale de 7 rencontres enrichies par jour après calendrier | Acceptée |
| MVP-D07 | Option A comme périmètre initial : Liga Portugal et LaLiga en enrichissement ; DED/ELC calendrier ; Saudi en contrôle | Acceptée |
| MVP-D08 | Garde-fous joueurs et jointures listés en section 8 obligatoires | Acceptée |
| MVP-D09 | Snapshot post-match initial puis contrôle différé configurable, par défaut vers T+60, pour les rencontres prioritaires | Acceptée |
| MVP-D10 | UEFA qualificatif : calendrier, score, statistiques d'équipe et événements autorisés ; aucune dépendance aux compositions ou données joueurs avant réplication | Acceptée |

## 11. Conditions enregistrées pour les Work Orders suivants

Les conditions suivantes sont confirmées et deviennent des contraintes d'acceptation de CAT-002, MVP-001 et ENR-002 :

- les fournisseurs sont choisis par capacité, pas globalement ;
- les compositions sont non bloquantes ;
- les règles d'anomalies joueurs sont acceptées ;
- le nombre de rencontres enrichies est borné par le quota ;
- le noyau de compétitions initial est accepté ;
- les compétitions non testées restent derrière un statut `CALENDAR_ONLY` ou `PILOT` ;
- SofaScore n'est ni une dépendance, ni une source appelée par le projet ; aucun endpoint privé n'est autorisé ;
- toute extension du périmètre passe par une preuve enregistrée et rejouable.

## 12. Séquencement retenu

ENR-001 ayant été revu et fusionné séparément dans `main` par la Pull Request `#3`, le prochain Work Order est **CAT-002 - Durcir le catalogue et rendre les anomalies opérables**. Il précède l'ingestion planifiée en volume et couvre notamment l'autorité PRIMARY/CONTROL, les observations anciennes, les rencontres neutres, la concurrence de normalisation et le replay sécurisé par identifiant ou empreinte.

Le Work Order suivant sera **MVP-001 - Implémenter le registre de capacités, le routage et la collecte calendrier**.

Le Work Order devra couvrir :

1. le registre `provider × competition × season × phase × dataType` ;
2. les états `PRIMARY`, `CONTROL`, `PILOT`, `CALENDAR_ONLY`, `NON_APPLICABLE` et `BLOCKED_BY_PLAN` ;
3. le routage Highlightly / football-data.org conforme aux décisions MVP-D01 à MVP-D10 ;
4. le budget quotidien et la réserve de quota ;
5. le séquencement conditionnel LIN-01 ;
6. les validateurs et quarantaines des anomalies joueurs ;
7. la jointure par identifiants d'équipe et provenance d'endpoint ;
8. le snapshot post-match différé ;
9. les tests de replay hors réseau ;
10. une première vague de validation sur les compétitions activées.

ENR-001 a été revu, publié au commit `743aff7`, fusionné dans `main` par la Pull Request `#3` au commit `6913cea`, puis clôturé le 1er septembre 2026. CAT-002 est désormais le prochain Work Order ; MVP-001 ne démarre qu'après acceptation de CAT-002.
