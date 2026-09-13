# MVP-001 — Clarification après la journée COV-002

- État : `CLARIFIED - IMPLEMENTATION_SCOPE_TO_RECONCILE`.
- Date : 6 septembre 2026 ; point de mesure figé à **23:07:11 Europe/Paris** (`21:07:11Z`).
- Tâche source : [Poursuivre collectes](thread://01a076a2-5c0b-7342-a0f4-b9feb99032b2?hostId=local).
- Branche MVP-001 : `feature/V0.1.0-RC01-CODEX-MVP-001`, HEAD `0f8a7d504ef2da5b64371855932ea0cdceec5f81`.
- Cette note préserve les travaux locaux de préparation du lot 2 et ne ferme aucun critère technique.

## 1. Conclusion de cadrage

La journée apporte une preuve exploitable de calendrier sur les cinq grands championnats et une
mesure du coût d'enrichissement de quinze rencontres. Elle ne prouve pas qu'un protocole exhaustif
sur ces quinze rencontres tient dans 100 appels Highlightly : **100 appels ont été consommés et
18 collectes dédiées post-match restent différées** au point de mesure.

Le statut de la tâche consultée reste actif, avec des reprises football-data.org et des compléments
autorisés le lendemain. Distinguer « journée observée » de « campagne totalement clôturée », et
résultat obtenu le jour même de complément obtenu à J+1. Cette note ne modifie pas sa planification.

## 2. Sources et vérifications réellement effectuées

Sources documentaires de l'autre worktree, sous la racine logique `COV002_REPOSITORY` :

| Source | Chemin logique | SHA-256 de la version consultée |
|---|---|---|
| Rapport | `docs/benchmark/coverage-refresh-2026-09-06-v0.1.md` | `565f0bbf0640e5a18446f55c3c956fa465595c5c1a885ecf851724afc1943ffd` |
| Échantillon | `docs/benchmark/cov02-sample-2026-09-06.json` | `59113fa7d864e1045c656be09c95c1d1a6a2028822bf4c1dfd0254e8c1097851` |

Le rapport évolue : son empreinte identifie la version consultée, pas son futur état. Les sources
complètes restent dans le worktree de la tâche et ne sont pas importées comme fixtures de production.

L'[index expurgé](evidence/mvp-001-cov002-20260906-2307-index.json) couvre 138 réponses sous la
racine logique `COV002_OUTPUT_BENCHMARK` (`output/benchmark` du workspace source) :
100 Highlightly, 37 football-data.org et un diagnostic historique API-FOOTBALL. Ce dernier ne
réintègre pas le périmètre fournisseur actif. Les octets complets restent hors Git.

Pour ces 138 fichiers, les SHA-256 ont été recalculés et comparés aux métadonnées, et les JSON
relus hors réseau avec succès. Les statuts HTTP enregistrés sont tous 200 ; le diagnostic
API-FOOTBALL comporte néanmoins une erreur fonctionnelle de plan dans la réponse.
**Cette vérification prouve empreinte et lisibilité JSON, pas replay du parseur de production,
exactitude sportive, normalisation canonique ou qualité exhaustive.**

Les comparaisons Lab reposent sur les observations et hashes affichés consignés dans le rapport.
Aucun octet Lab ni export J7 n'a été audité dans cette étape ; les flags `COMPLETE` du Lab ne
sont pas transformés en qualification Betting Project. Aucun fichier de clés n'a été lu.

## 3. Mesures de la journée

### Calendrier et consommation

Le rapport compare quinze rencontres : Ligue 1 (3), Premier League (2), Serie A (4), LaLiga (4)
et Bundesliga (2). Cinq requêtes calendrier par API ont suffi ce jour-là ; les quinze horaires
et ordres des participants concordent dans la comparaison documentée. Ce rapprochement ne crée
aucune règle textuelle automatique, ni aucune preuve de couverture UEFA ou d'une autre journée.

| Famille Highlightly | Appels vérifiés |
|---|---:|
| Inventaire et calendrier | 12 : 7 inventaire, 5 calendrier |
| LINEUP | 37 |
| DETAIL | 24 |
| STATISTICS | 10 |
| EVENTS | 9 |
| BOX_SCORE | 8 |
| Total | **100** |

Les 27 familles dédiées acquises sur 45 possibles laissent 18 collectes manquantes. Reproduire
exactement les appels déjà faits et compléter ces familles demanderait **au moins 118 appels**,
hors nouvelles reprises. Le test a utilisé une dérogation **100 / réserve 0 pour le 6 septembre**,
pas la politique de production 80/20. Le quota restant enregistré après le dernier appel est zéro.

### Compositions

Selon la classification détaillée du rapport, 10 des 11 rencontres suivies en prématch obtiennent
une composition complète avant T0 ; une reste absente. Les quatre rencontres commencées avant
le suivi prématch ne sont pas quatre échecs de composition.

| Fenêtre nominale | Appels | Premières compositions complètes | Réponses vides |
|---|---:|---:|---:|
| T-60 | 8 | 0 | 8 |
| T-45 | 10 | 0 | 10 |
| T-30 | 10 | 2 | 8 |
| T-15 | 9 | 8 | 1 |

Les 18 appels précoces n'ont apporté aucune composition exploitable dans cet échantillon.
Le protocole T-30 puis T-15 conditionnel est donc étayé, sans garantie générale de disponibilité.
Les fenêtres manquées et le décalage réel des appels sont conservés. Retirer rétrospectivement
18 appels n'est pas la preuve qu'une autre journée obtiendrait les mêmes résultats en 100 appels.

### Fraîcheur et données embarquées

Les détails Highlightly ont parfois livré statut final, statistiques d'équipe et événements dans
une même réponse, notamment Espanyol–Sevilla. Les écarts de score/statut football-data.org,
les métriques joueurs incohérentes et certaines différences de statistiques avec le Lab restent
visibles. Comparer heures de collecte/réception et période, pas seulement l'heure de lecture.

Ne pas assimiler une famille présente dans DETAIL à une collecte de l'endpoint dédié ; conserver
la provenance de sous-section. Des cardinalités d'événements différentes peuvent correspondre
à des marqueurs de période, pas à des incidents sportifs manquants. Aucun fournisseur ne devient
une vérité absolue pour toutes les données. football-data.org reste utile au contrôle calendrier,
identité et score ; l'enrichissement détaillé n'est pas établi dans le périmètre de son plan testé.

## 4. Cible fonctionnelle précisée dans la tâche source

Les tours utilisateur `01a07833-ca0b-7261-b8d8-0bbaf972167d`,
`01a0785f-0052-7002-b4ec-8ffcecae470d`, `01a07869-7cea-7701-9290-e08d7061a3bc`,
`01a0786d-a9c2-7f91-9309-56c23df661e9` et `01a07877-c28d-7021-9582-90690e9bf8e3`
décrivent les orientations ci-dessous. Leur lecture précise la cible ; elle ne vaut pas activation
de code ou de planification dans le présent worktree.

- **Mode autonome** : Highlightly et football-data.org ; préparation quotidienne du périmètre
  analysable, reprise contrôlée en cas d'échec. Une exécution quotidienne peut consommer plusieurs
  appels ; elle n'interdit pas des actualisations ultérieures budgétées.
- **Périmètre produit souhaité** : top 5 + Ligue des Champions, Ligue Europa et Ligue Conférence ;
  Portugal, Pays-Bas et Turquie éventuellement. Le noyau technique PPL/PD/DED/ELC et le plafond
  de sept enrichissements sont encore les baselines versionnées, pas une preuve du nouveau périmètre.
- **Mode étendu** : les preuves du Lab deviennent un apport optionnel via J7, et ne sont plus
  seulement un comparateur. Pas d'appel direct SofaScore, pas de pilotage implicite de son interface,
  pas de panne du mode autonome lorsque le Lab est absent.
- **Compositions normales** : Highlightly à T-30, puis T-15 seulement si non confirmé ; arrêt
  dès qu'une composition confirmée, complète et admissible avant T0 est disponible. La confirmation
  doit avoir une signification attestée par la source, sans inventer un marqueur absent.
  Aucune validation prématch à T0 ou après T0.
- **Mode étendu prématch** : échéances Lab T-60/T-45/T-30 ; une preuve confirmée effectivement
  acceptée et exploitable dans Betting Project peut éviter les appels LINEUP correspondants.
  Le Lab est actuellement manuel. Un import reçu après la décision ne supprime pas l'appel passé.
- **T0** : DETAIL Highlightly systématique, distinct de LINEUP. **T+45** : contrôle Highlightly
  souhaité en mode normal, proposé sous la forme d'un DETAIL dans la tâche source ; cette famille
  exacte reste à confirmer avant contrat d'exécution. Il doit être budgété et ne garantit ni mi-temps
  ni début effectif à T0.
- Le suivi J4/J5 à la minute est une **demande d'expérimentation live distincte**, non une
  autorisation d'ajouter un profil, worker ou appel privé dans MVP-001.

### Deux écarts d'architecture à traiter explicitement

1. Le [receiver INT-001](../contracts/j7-import-receiver-v1.md) émet un ACK d'import durable et
   une outbox `J7_IMPORT_ACCEPTED`. Il n'applique ni enrichissement ni rencontre au catalogue.
   Un consommateur de preuves J7 avec identité, provenance, qualification et temporalité est requis
   avant de promettre des économies d'appels. Cadrer ce raccordement dans un lot/WO explicite.
2. Le canon CAT-002 n'a que `SCHEDULED`, `POSTPONED`, `CANCELLED`, `FINISHED`.
   Il ne modélise pas `IN_PLAY` ou la mi-temps. Les détails T0/T+45 doivent rester des observations
   source séparées tant que leur contrat n'est pas livré : ne jamais rabattre un état live sur
   `SCHEDULED`, ni élargir implicitement le contrat calendrier v3.

## 5. Budget du protocole envisagé — simulation, pas preuve exécutée

Sous l'hypothèse d'un DETAIL à T+45, pour quinze rencontres : `C` appels de préparation, `L` appels LINEUP, 15 DETAIL à T0,
15 à T+45, 15 DETAIL finaux, `D` appels dédiés supplémentaires et `R` reprises donnent :

`coût Highlightly = C + L + 45 + D + R`.

| Hypothèse, sans reprise | C=5, calendrier seulement | C=12, préparation mesurée ce jour |
|---|---:|---:|
| 1 LINEUP par match, DETAIL seulement | 65 | 72 |
| 2 LINEUP par match, DETAIL seulement | 80 | 87 |
| 1 LINEUP + 3 familles dédiées par match | 110 | 117 |
| 2 LINEUP + 3 familles dédiées par match | 125 | 132 |

Le cas C=5 suppose un inventaire déjà valable et la même pagination ; ce n'est pas une borne
universelle. « DETAIL seulement » ne promet pas toutes les métriques attendues. À 80 appels,
le scénario maximal sans endpoint dédié ne laisse aucune marge opérationnelle pour une reprise.
Les simulations du rapport antérieures au T+45 ne doivent pas être reprises sans ajouter son coût.

La stratégie recommandée est **DETAIL en premier, compléments dédiés selon les champs réellement
manquants, importance de la rencontre et budget**, avec PLAYER_STATS non systématique.
C'est une proposition à qualifier sur fixtures au lot 3/ENR-002, pas un remplacement déjà validé
de chaque endpoint. Les quinze dossiers calendaires ne doivent pas signifier quinze enrichissements
exhaustifs ni quinze analyses de pari prêtes : les cotes, probabilités et documents restent différés.

## 6. Ce qui permet d'avancer dès le lot 2

- Unité budgétaire : tentative HTTP, pas type logique de données. Un DETAIL peut servir plusieurs
  capacités pour un seul débit, mais chaque appel dédié consomme une unité supplémentaire.
- Périmètre du budget : fournisseur/compte/fenêtre connus, consommation globale y compris hors
  collecteur et manuelle ; ne pas lire seulement le ledger d'enrichissement (12 appels initiaux ici).
- Séparer plafond fournisseur, plafond projet, réserve de sécurité, engagements futurs et coût
  incertain ; les engagements protégés peuvent différer une collecte avant épuisement effectif.
- Distinguer `DEFERRED_QUOTA` sans tentative, réponse absente/vide, fenêtre manquée, arrêt sur
  401/403/429 et envoi incertain. Seuls les essais possiblement envoyés retiennent un coût consommé.
- football-data.org expose ici des indications **par minute**, pas une preuve de quota quotidien.
  Prévoir un budget projet et une limite de cadence fournisseur distincts ; pas de fenêtre journalière
  artificielle déduite de ces en-têtes, ni plafond Highlightly copié sur football-data.org.
- Pas de renouvellement à minuit présumé ; pas de rattrapage en rafale d'échéances manquées.
  Reprise connue sans envoi et reprise d'un envoi incertain restent deux chemins distincts.

Le mécanisme générique du lot 2 peut être réalisé sans attendre la couverture UEFA, les compléments
J+1 ou le consommateur J7. Il doit rester fermé sans initialisation explicite ; 80/20 demeure la
politique de production tant que le porteur ne généralise pas une autre enveloppe.

Matrice d'entrée et résultat à transformer en tests des lots concernés :

| Entrée | Résultat attendu | Preuve / travail restant |
|---|---|---|
| DETAIL avec statistiques et événements | Un débit HTTP, plusieurs interprétations de la même preuve | Variante rapportée P27 ; fixtures et parseur lot 3 |
| Famille non appelée faute de budget | Report budgétaire, jamais absence fournisseur | 18 familles différées ; tests de réservation lot 2 |
| Réservation devenue inutile avant tout envoi | Libération transactionnelle sans débit consommé | Test lot 2 ; qualification J7 séparée |
| Donnée reçue après envoi possiblement effectué | Coût conservé, aucune économie rétroactive | Test lot 2 puis orchestration lot 4 |
| Onze noms présents mais confirmation absente | Partiel/non confirmé, pas composition validée | Observations Lab rapportées ; fixtures ENR-002/J7 |
| Réponse vide de statistiques ou statut inconnu | Vide/absence/incompatible explicitement classé, pas zéro | Fixtures et traduction bornée lot 3/ENR-002 |
| Changement de jour sans fenêtre établie | Aucun reset ni appel de rattrapage automatique | Test lot 2 |

## 7. Arbitrages avant les lots aval

| Sujet | Proposition / limite |
|---|---|
| Calendrier cible | Réviser explicitement les critères MVP-001 pour top 5 + UEFA ; activer uniquement les clés fournisseur/saison/phase prouvées. Ne pas convertir les tests synthétiques du lot 1 en preuve réelle. |
| Profondeur | Calendrier large, enrichissement sélectif et DETAIL source qualifié ; quotas des contrôles T0/T+45 et post-match réservés avant familles optionnelles. |
| Budget de production | Conserver 80/20 par défaut ; la campagne 100/0 était datée. Arbitrer autrement explicitement si nécessaire. |
| Mode étendu J7 | Raccordement métier distinct avant suppression de LINEUP ; ne pas confondre ACK avec donnée exploitable ni import tardif avec connaissance prématch. |
| Live minute | WO/architecture et qualification séparés ; hors lot 2 et hors activation actuelle. |

Les trois derniers sujets ne sont pas des raisons de bloquer le budget durable générique.
Aucun lot terminé, nouvel appel fournisseur, migration, endpoint, commit, push ou PR n'est déclaré
par cette clarification. La qualification complète du lot 2 et sa publication restent à réaliser.
