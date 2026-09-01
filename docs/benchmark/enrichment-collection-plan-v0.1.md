# Plan de collecte du benchmark d'enrichissement v0.1

- **Statut :** Exécuté - baseline consolidée, acceptée et fusionnée avec ENR-001
- **Échantillon métier :** Accepté le 11 août 2026
- **Période sportive observée :** du 12 au 16 août 2026
- **Dernières preuves post-match :** 17 août 2026
- **Fuseau de référence :** Europe/Paris
- **Manifeste :** `docs/benchmark/enrichment-sample-v0.1.json`
- **Index expurgé des preuves :** `docs/benchmark/evidence/enr-001-evidence-index-v0.1.json`

## 1. Objectif

Exécuter un benchmark reproductible de la qualité d'enrichissement des fournisseurs football sur douze rencontres principales, quatre réserves et un contrôle d'identité transversal.

Le benchmark doit mesurer séparément :

- les identités avec `ID-01` ;
- les horaires et statuts avec `TIM-01` ;
- la stabilité, la latence et les quotas avec `OPS-01` ;
- la reproductibilité hors ligne avec `REP-01` ;
- les compositions avec `LIN-01` ;
- les statistiques d'équipe avec `STA-01` ;
- les événements avec `EVT-01` ;
- les statistiques joueurs avec `PLY-01`.

## 2. Principes de comparaison

### 2.1 Donnée fournisseur et donnée canonique

Chaque réponse fournisseur est conservée avant normalisation. Les noms, rôles domicile/extérieur, horaires, statuts et identifiants observés ne sont jamais réécrits dans la preuve brute.

Une donnée canonique est créée ou rapprochée uniquement par un mapping explicite. Aucun rapprochement flou automatique n'est autorisé.

### 2.2 Rencontres sur terrain neutre

PSG - Aston Villa et PSG - Lens sont marquées `neutralVenue=true` et `participantOrderSignificant=false`.

Pour ces deux rencontres :

- la paire de participants identifie la rencontre indépendamment de l'ordre d'affichage ;
- l'ordre transmis par chaque fournisseur reste conservé dans son observation ;
- cette règle ne s'applique à aucune autre rencontre sans décision explicite ;
- une inversion d'ordre ne doit ni créer une seconde rencontre canonique ni être comptée comme une erreur d'identité.

Les alias `Paris Saint Germain` et `PSG` sont acceptés pour Paris Saint-Germain.

### 2.3 Applicabilité des fournisseurs

Highlightly est applicable aux scénarios 2026 de détail, composition, statistiques, événements et statistiques joueurs.

football-data.org est applicable comme contrôle des identités, horaires, statuts et scores pour :

- Sporting CP - Vitória SC ;
- Sevilla - Rayo Vallecano ;
- Rio Ave - Porto.

Les compositions, événements et statistiques détaillées de football-data.org sont `NON_APPLICABLE` avec le plan gratuit testé. Cette situation ne constitue pas un échec.

API-FOOTBALL est `BLOCKED_BY_PLAN` pour le chemin critique 2026 avec le plan gratuit testé. Aucun sous-échantillon historique n'a été autorisé ou exécuté dans ENR-001. Tout retour dans le benchmark exige une nouvelle décision et une preuve datée, sans mélanger ces résultats au corpus 2026.

## 3. Fenêtres de collecte

| Fenêtre | Appel principal | Scénarios | Règle |
|---|---|---|---|
| Baseline conservée | Réponse CAL-01 existante | ID-01, TIM-01, REP-01 | Aucun nouvel appel ; première observation historique |
| J-1 ou première exécution autorisée | Détail de la rencontre | ID-01, TIM-01, OPS-01, REP-01 | Un appel par fournisseur applicable |
| T-60 | Composition Highlightly | LIN-01, OPS-01, REP-01 | Appel systématique pour une rencontre principale active |
| T-45 | Composition Highlightly | LIN-01, OPS-01, REP-01 | Appel uniquement si T-60 est absent, incomplet ou non interprétable |
| T-30 | Composition Highlightly | LIN-01, OPS-01, REP-01 | Appel uniquement si T-60 et T-45 n'ont produit aucune composition complète |
| T-15 | Composition Highlightly | LIN-01, OPS-01, REP-01 | Contrôle tardif uniquement si aucune preuve antérieure n'est complète |
| T-5 | Composition Highlightly | LIN-01, OPS-01, REP-01 | Dernier contrôle facultatif, réservé aux rencontres explicitement prioritaires et encore non complètes |
| T0 et après | Aucun appel de composition LIN-01 | LIN-01 | Arrêt strict ; toute collecte tardive autorisée séparément est classée hors validation prématch |
| T+90 à T+180 | Détail final | TIM-01, OPS-01, REP-01 | Vérifier statut, score et horaire effectif |
| T+90 à T+180 | Statistiques d'équipe | STA-01, OPS-01, REP-01 | Un appel Highlightly par rencontre terminée |
| T+90 à T+180 | Événements | EVT-01, OPS-01, REP-01 | Un appel Highlightly par rencontre terminée |
| T+90 à T+180 | Statistiques joueurs | PLY-01, OPS-01, REP-01 | Un appel Highlightly par rencontre terminée |
| Correctif | Endpoint concerné | Scénario concerné, OPS-01, REP-01 | Uniquement pour confirmer une correction ou un résultat incomplet |

Le séquencement étendu T-60, T-45, T-30, T-15 et T-5 a été accepté le 14 août 2026 pour les prochains lots. Il ne requalifie pas rétroactivement les résultats déjà collectés. Chaque fenêtre après T-60 est conditionnelle : dès qu'une composition est `COMPLETE`, toutes les échéances LIN-01 restantes sont ignorées sans appel fournisseur. T-5 n'est activé que pour une rencontre explicitement prioritaire. Aucun appel postérieur à T0 ne peut valider LIN-01.

## 4. Endpoints attendus

### Highlightly

| Donnée | Endpoint relatif |
|---|---|
| Détail | `/matches/{matchId}` |
| Composition | `/lineups/{matchId}` |
| Statistiques d'équipe | `/statistics/{matchId}` |
| Événements | `/events/{matchId}` |
| Statistiques joueurs | `/box-score/{matchId}` |

### football-data.org

| Donnée | Endpoint relatif |
|---|---|
| Détail | `/v4/matches/{matchId}` |

## 5. Budget de requêtes

### Highlightly

- quota documentaire : 100 requêtes par jour ;
- budget opérationnel maximal : 80 requêtes par jour ;
- réserve de sécurité : 20 requêtes par jour ;
- maximum théorique par rencontre principale : 10 appels lorsque le contrôle facultatif T-5 est activé ;
- les appels T-45, T-30, T-15 et T-5 restent conditionnels ;
- une réserve non activée ne reçoit que le minimum nécessaire au contrôle de son statut.

### football-data.org

- limite documentaire : 10 requêtes par minute ;
- budget opérationnel maximal : 8 requêtes par minute ;
- aucune rafale parallèle ;
- seuls les trois matchs multi-fournisseurs du manifeste déclenchent les appels de détail.

### API-FOOTBALL

- aucun appel 2026 dans ce lot ;
- statut `BLOCKED_BY_PLAN` ;
- aucun quota ne doit être consommé sans nouvelle autorisation et nouveau test daté.

## 6. Activation des réserves

Une réserve peut remplacer une rencontre principale si :

1. la rencontre principale est reportée, annulée ou déplacée hors période ;
2. son identifiant fournisseur disparaît ou devient incohérent ;
3. le détail attendu n'est pas disponible ;
4. une contrainte opérateur empêche les collectes temporelles ;
5. le quota restant impose une réduction ;
6. une rencontre de réserve offre une comparaison plus exploitable.

La substitution doit être enregistrée avant la première collecte `LIN-01` de la rencontre remplacée. Une rencontre déjà engagée reste dans le bilan, même si ses données deviennent incomplètes.

## 7. Contrôle FC Kryvbas Kryvyi Rih

Le contrôle `ENR-C01` reste limité à `ID-01`, `TIM-01`, `OPS-01` et `REP-01`.

- collecte à J-1 ;
- collecte le jour du match ;
- conservation de `Hirnyk` comme valeur brute Highlightly ;
- mapping opérationnel `CONFIRMED` de l'entité Highlightly `TEAM:5522923` vers `FC Kryvbas Kryvyi Rih` ;
- qualification d'audit `CONFIRMED_HISTORICAL_REBRAND_ALIAS`, fondée sur l'historique officiel du club et la fiche officielle UPL ;
- portée limitée au fournisseur Highlightly, à l'identifiant `5522923` et au contexte ukrainien ; aucune substitution générique fondée sur le seul libellé `Hirnyk` ;
- signalement `STALE_PROVIDER_NAME` tant que Highlightly conserve l'ancien libellé ;
- toute correction ultérieure devient une nouvelle observation.

Cette requalification, acceptée le 15 août 2026, clôt `AMBIGUOUS_TEAM_IDENTITY` pour ENR-C01. Le contrôle du jour du match reste utile pour mesurer la fraîcheur du libellé fournisseur, mais ne conditionne plus la résolution de l'identité canonique.

## 8. Preuves et sécurité

Chaque appel doit produire :

- l'identifiant de l'appel ;
- le fournisseur ;
- le scénario ;
- le match du manifeste ;
- l'endpoint expurgé ;
- l'heure de début et de fin ;
- la latence ;
- le code HTTP ;
- les informations de quota non sensibles ;
- le fichier brut ;
- son empreinte SHA-256 ;
- la version du connecteur ;
- le résultat de replay ;
- les anomalies de normalisation.

Il est interdit de conserver :

- une clé API ;
- un en-tête d'authentification ;
- un paramètre sensible ;
- le chemin local du fichier de clés ;
- une réponse brute modifiée présentée comme preuve originale.

Après un HTTP 401 ou 403, le fournisseur concerné est arrêté jusqu'à diagnostic. Après un HTTP 429, aucun retry immédiat n'est effectué ; le délai fournisseur est respecté et l'incident est enregistré dans `OPS-01`.

## 9. Critères de passage

La phase initiale est considérée comme prête à lancer lorsque :

- le Work Order ENR-001 est accepté ;
- le manifeste JSON est valide ;
- les douze rencontres principales et quatre réserves sont présentes ;
- les identifiants fournisseurs sont renseignés ;
- la règle des terrains neutres est acceptée ;
- les budgets sont compatibles avec les quotas ;
- les emplacements de preuves sont hors des secrets ;
- le collecteur peut fonctionner sans afficher les clés ;
- le replay peut être exécuté sans réseau.

La décision finale sur les fournisseurs n'est prise qu'après consolidation de toutes les fenêtres applicables.
