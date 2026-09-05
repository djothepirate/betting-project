# SKL-001 — Évaluation des cinq skills candidats

- **Date :** 5 septembre 2026 (horodatages bruts UTC le 4 septembre au soir).
- **Statut :** PILOTE_ANALYTIQUE_TERMINE — candidats utilisables localement, bénéfice général indéterminé.
- **Work Order :** [SKL-001](../work-orders/SKL-001.md).
- **Cas et consignes :** [Protocole apparié](SKL-001-cases.md).
- **Base Git :** 5a8161e0070632f0b0dbbff760194e57ab4e5b1f.
- **Inventaire source :** document local du 5 septembre, SHA-256 13e0d6e6841562fc1d0766b6b9fadcc9c7e198802b78e1fbc934b75443a1d6de.

## Portée

Suivi du 5 septembre : le propriétaire a validé les cinq skills et autorisé leur livraison sur main. Voir [SKL-003](../work-orders/SKL-003.md). Le rapport ci-dessous décrit le pilote initial ; sa mention d'installation locale n'annule pas cette décision ultérieure.

Quatre tâches réelles de reprise/revue du projet sont effectuées par des agents indépendants : A sans candidat, B avec le ou les candidats explicites. Il s'agit d'un pilote analytique en lecture seule, pas d'une répétition complète des développements historiques ni d'un essai de production. Les sources applicatives n'ont pas changé entre les bras.

Les cinq skills restent courts et fondés sur les références du dépôt. Les noms sont bp-work-order, bp-verify, bp-postgres-change, bp-data-contract-replay et bp-review-closeout. Ils sont disponibles dans .agents/skills de ce worktree ; aucune installation globale n'est réalisée.

## Baseline historique séparée

| Cas | Comptage documentaire défendable | Preuve |
|---|---|---|
| DEVX-001 | 1 défaut distinct de faux vert de propagation native ; 1 mécanisme distinct d'intégration omise ; 1 correction de revue du scanner. | [DEVX-001](../work-orders/DEVX-001.md), constat et résultats ; 25d1c29 → 2fc6b52, correction de revue 06ba0e2. |
| CAT-002 | 1 correction de revue des littéraux runtime ; 12 scénarios PostgreSQL ne sont pas 12 défauts. | [CAT-002](../work-orders/CAT-002.md), correctif P2 ; 92c98f2 → 6fb69e2. |
| ENR-001 | 127 preuves vérifiées, 7 fixtures ; provenance historique parserVersion absente explicitée. Corrections de revue non mesurées. | [ENR-001](../work-orders/ENR-001.md), résultats de finalisation ; 743aff7. |
| CI-003 | 1 correction de revue du cache NVD ; 1 fait documentaire périmé : PR #10 dite ouverte malgré fusion incluse localement. | [CI-003](../work-orders/CI-003.md), statut et origine ; 649288e et merge 5a8161e. |
| Clôture CAT-002 | 1 incohérence indépendante : critère encore décrit ouvert alors que la revue et les 36 critères sont acquis. | Ligne historique 380 du WO à 70e34c2, correction aa1b12e, merge 85dc943. |

Agrégat sur ces cas sélectionnés : trois corrections de revue uniques et deux incohérences documentaires indépendantes. Le défaut de faux vert et le mécanisme d'intégration omise ne sont pas présentés comme deux exécutions historiques observées. Les quatre injections DEVX sont des scénarios de test, pas quatre incidents.

Temps historique de reprise et répétitions de tests inutiles : **NON_MESURES**. Ni durée de scan NVD, ni heure de commit, ni totalité d'un tour d'agent ne fournit le temps de reprise. Une relance après changement ou échec n'est pas inutile par définition.

Ces nombres historiques ne servent pas de dénominateur à un gain A/B. Ils justifient le choix des cas et des invariants.

## Mesures du pilote

La campagne v0.1 produit les résultats suivants. Les quatre paires restent présentées intégralement, y compris le cas plus lent et la proposition à corriger.

| Tâche | Contexte A sans skill | Contexte B v0.1 | Écart B − A | Corrections A / B | Faux verts A / B | Contradictions A / B | Relances injustifiées proposées A / B |
|---|---:|---:|---:|---:|---:|---:|---:|
| T1 — Reprise/clôture CI-003 | 39 s | 26 s | −13 s | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| T2 — Fiabilité Windows historique | 37 s | 27 s | −10 s | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| T3 — Migration V005 | 38 s | 30 s | −8 s | 0 / 1 | 0 / 0 | 0 / 0 | 0 / 1 |
| T4 — Contrats/replay | 14 s | 26 s | +12 s | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |

Les huit fiches sont jugées suffisamment exactes par la revue indépendante. Les sources, horodatages de début/fin et compteurs sont conservés dans [results.json](evaluations/SKL-001/results.json), avec les dix réponses intégrales et les deux revues dans [le dossier de preuves](evaluations/SKL-001). Les catégories de défauts se recoupent : l'unique relance injustifiée proposée est aussi l'unique correction de réponse, pas un deuxième défaut.

### Corrections issues de la revue

**D1, mineur, T3-B :** le plan propose -Pintegration verify puis verify, sans changement ni motif distinct. Le profil integration ajoute Failsafe au cycle standard sans supprimer les tests standards. Le second parcours n'ajoute donc pas de couverture après réussite du premier. Le skill PostgreSQL v0.2 explicite cette propriété et demande un motif pour un parcours supplémentaire.

**S1, observation statique :** le skill data v0.1 confinait trop largement tous les identifiants source aux adaptateurs. Les types ProviderMappingKey et FixtureObservation portent légitimement des références fournisseur et de la provenance. Le v0.2 distingue DTO externes, références source dans les types existants et identifiants canoniques. Aucune réponse T4 n'avait déplacé ces références : S1 n'est pas compté comme défaut expérimental.

Les [deux textes v0.1](evaluations/SKL-001/candidates-v0.1) et la [revue v0.1](evaluations/SKL-001/review-v0.1.md) restent conservés. Seuls deux paragraphes et leurs blancs finaux ont changé ; les trois autres candidats sont inchangés.

### Retests ciblés v0.2

| Retest indépendant | Contexte | Fin déclarée depuis début | Corrections | Faux verts | Contradictions | Relances injustifiées proposées |
|---|---:|---:|---:|---:|---:|---:|
| T3-B-v0.2 | 28 s | 121 s | 0 | 0 | 0 | 0 |
| T4-B-v0.2 | 37 s | 204 s | 0 | 0 | 0 | 0 |

La [revue v0.2](evaluations/SKL-001/review-v0.2.md) confirme D1 résolu dans le plan réellement produit et S1 résolu dans les instructions. Les réponses nouvelles ne reçoivent ni les erreurs précédentes ni la solution attendue. Aucun témoin A nouveau n'est exécuté : ce sont des vérifications correctives, pas une seconde comparaison A/B complète.

Les mesures de temps sont écoulées, à la seconde, incluant lectures et outils ; le temps actif et l'attente du service ne sont pas instrumentés séparément. La fiche CONTEXT_READY est relue pour éviter de valoriser une fiche rapide mais erronée.

Les fins d'analyse/rédaction ne sont pas homogènes. T1-A enregistre 109 s comme borne supérieure de fin d'analyse mais sa sauvegarde prend 189 s, avec attente d'un dossier de sortie initialement absent. T1-B distingue 121 s d'analyse et 190 s jusqu'à sauvegarde. Les autres fins mélangent davantage analyse et rédaction. Les temps de reprise ne sont ni randomisés ni répétés ; le reviewer connaît les bras. La sélection des cas favorise des connaissances historiques présentes dans les skills. Les durées sont descriptives, sans pourcentage global d'efficacité.

Les répétitions de tests exécutées sont **NON_MESUREES**, car les essais n'exécutent pas les suites applicatives. Les séquences de commandes proposées sont examinées séparément. Zéro commande lancée ne prouve pas zéro répétition sur un vrai développement.

## Validations locales du lot

- Les cinq candidats v0.1 passent le validateur officiel skill-creator quick_validate.py.
- Métadonnées YAML, nom, description courte, prompt d'invocation et sélection implicite par défaut vérifiés.
- Références de fichiers du dépôt vérifiées ; staging et installation initiale identiques par SHA-256.
- Les deux SKILL.md modifiés ont été revalidés officiellement en v0.2, puis remplacés après contrôle de l'empreinte précédente et vérification de l'identité finale.
- Contrôle de secrets du dépôt (All) réussi ; git diff --check et contrôle explicite des fichiers ajoutés non suivis réussis, sans espace final ni lien local manquant.
- Empreintes des dix fichiers de skills, arithmétique des dix séries d'horodatages et présence des dix réponses vérifiées. Le [manifeste des preuves](evaluations/SKL-001/evidence-manifest.json) conserve les empreintes des seize fichiers de résultats, revues et versions associés, sans se référencer lui-même.
- Aucun test Java, migration ou changement applicatif effectué pour ce lot documentaire et d'instructions.

La dépendance temporaire PyYAML manquait au runtime. Le premier validateur a échoué pour cette cause, puis le téléchargement dans tmp/skl-001 a requis l'accès réseau autorisé par l'outil. Les droits hérités du paquet imposent l'exécution du validateur avec l'accès adapté. Ce sont des incidents de l'outillage de cette campagne, pas des faux verts ni des tests du produit. Le validateur corrigé a effectivement été réexécuté avec succès.

## Décision locale

| Skill | Version livrée | Preuve comportementale | Décision |
|---|---|---|---|
| bp-work-order | 0.1 | T1-B + revue indépendante | Utilisable pour reprise/cadrage ; création complète d'un WO en témoin isolé non mesurée. |
| bp-review-closeout | 0.1 | T1-B + revue indépendante | Utilisable pour revue/clôture documentaire ; livraison distante non évaluée. |
| bp-verify | 0.1 | T2-B + revue indépendante | Utilisable pour diagnostic/sélection des preuves ; faux vert de script non réexécuté par ce pilote. |
| bp-postgres-change | 0.2 | T3-B puis retest correctif | Utilisable pour revue/plan PG ; aucune migration qualifiée en exécution ici. |
| bp-data-contract-replay | 0.2 | T4-B puis retest correctif | Utilisable pour revue des contrats ; génération d'un nouveau parser non évaluée. |

**Résultat : les candidats sont créés et leur pilote analytique est achevé. Le bénéfice général demeure indéterminé.** Trois reprises v0.1 sont plus rapides, une est plus lente ; la qualité des réponses A était déjà bonne. La campagne a permis de corriger une recommandation de commande et une ambiguïté d'instruction, sans démontrer une baisse de bugs, de durée d'implémentation ou de relances exécutées. La découverte implicite effective n'a pas été testée, puisque B utilise des invocations explicites.

Les empreintes finales sont dans [candidate-manifest.json](evaluations/SKL-001/candidate-manifest.json). L'état de livraison est local : aucun commit, push, PR ni fusion. Les preuves courantes CI-003 et les autres Work Orders ne sont pas réécrits par les essais.

Exemples d'invocation : « Utilise $bp-work-order pour reprendre le prochain lot », « Utilise $bp-verify pour choisir les contrôles de ce diff », « Utilise $bp-postgres-change pour relire cette migration », « Utilise $bp-data-contract-replay pour revoir ce parser », « Utilise $bp-review-closeout pour préparer cette clôture ».

## Prochain pilote d'implémentation

Lors de trois prochains besoins de développement explicitement autorisés, conserver pour chaque tâche :
1. demande, base, état local et version des skills ;
2. début et première fiche exacte, fin de réalisation et temps d'attente distinct si instrumenté ;
3. première revue, défauts uniques et corrections effectuées ;
4. journal des commandes : contenu testé, raison de lancement, résultat, suite exécutée et précédente preuve applicable ;
5. contradictions documentaires par fait et cause d'une relance éventuelle.

Inclure un changement de persistance, un contrat/replay et une clôture documentaire au rythme des besoins. Les métriques d'exécution seront alors réellement observables. Un contrôle imposé par le WO ou le pipeline ne se classe pas inutile simplement parce qu'il exécute une suite déjà rencontrée.

Ne pas forcer trois nouveaux développements pour compléter des chiffres. Présenter résultats bruts, régressions et cas non comparables ; aucune cible arbitraire de réduction de temps n'est retenue.
