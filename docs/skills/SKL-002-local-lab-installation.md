# SKL-002 — Installation des cinq skills pour le SofaScore Local Lab

- **Date :** 2026-09-05.
- **Work Order :** [SKL-002](../work-orders/SKL-002.md).
- **Décision :** le propriétaire accepte le lot 1 et demande l'extension au Local Lab.
- **Version des adaptations :** 0.1, identifiée par le [manifeste d'installation](evaluations/SKL-002/installation-manifest.json).

## Installation et usage

Décision ultérieure du 5 septembre : les cinq skills des deux projets sont validés et leur livraison sur main est autorisée. [SKL-003](../work-orders/SKL-003.md) conserve ce dossier dans Betting Project ; le [dépôt Lab](https://github.com/djothepirate/betting-sofascore-local-lab/tree/main/docs/skills) maintient les sources et l'installateur personnel. Les états de livraison locale ci-dessous restent le compte rendu de l'installation initiale.

Les cinq skills sont installés dans **C:/Users/geoff/.agents/skills**. Cette portée utilisateur les rend disponibles depuis les worktrees du Lab sans duplication par checkout. Les descriptions les destinent explicitement au SofaScore Local Lab ; les chemins des documents sont résolus depuis sa racine Git. Les versions `bp-*` validées restent inchangées dans le Betting Project.

Le dossier utilisateur `.agents/skills` et la découverte des skills sont décrits dans la [documentation officielle Codex](https://learn.chatgpt.com/docs/build-skills). La présence réelle a également été vérifiée avec le Codex installé, par `skills/list` et `forceReload=true` ; il ne s'agit pas d'une conclusion tirée des seuls chemins de fichiers.

| Betting Project validé | Skill Lab installé | Adaptation principale |
|---|---|---|
| bp-work-order | [ss-work-order](local-lab/ss-work-order/SKILL.md) | Bon worktree, WO active/completed, ADR-SS et décisions actuelles. |
| bp-verify | [ss-verify](local-lab/ss-verify/SKILL.md) | Profil integration-tests, scripts PowerShell, Maven effectif et faux verts. |
| bp-postgres-change | [ss-postgres-change](local-lab/ss-postgres-change/SKILL.md) | PostgreSQL, ledgers, provenance, upgrade prérempli et reprise. |
| bp-data-contract-replay | [ss-data-contract-replay](local-lab/ss-data-contract-replay/SKILL.md) | Snapshots, complétude, contrats J7 et validation humaine. |
| bp-review-closeout | [ss-review-closeout](local-lab/ss-review-closeout/SKILL.md) | Qualification, rapports figés, clôture et livraison Git distinctes. |

Chaque skill contient un SKILL.md et agents/openai.yaml avec nom d'affichage, description courte et prompt. La sélection implicite reste disponible ; aucune dépendance MCP supplémentaire n'est requise.

Exemples depuis une tâche du Lab :

```text
Utilise $ss-work-order pour reprendre le WO concerné et établir son état actuel.
Utilise $ss-verify pour diagnostiquer cet échec CI et réaliser le correctif.
Utilise $ss-postgres-change et $ss-data-contract-replay pour préparer cette évolution du ledger et de son contrat.
Utilise $ss-review-closeout pour vérifier les preuves et clôturer ce lot.
```

Si une tâche déjà ouverte affiche encore son ancien catalogue, rouvrir la tâche ou redémarrer Codex pour actualiser sa découverte. Les sources versionnables sont conservées dans [local-lab](local-lab/) ; les fichiers personnels sont des copies autonomes, sans lien symbolique vers ce worktree. Une modification ultérieure de la source doit donc être réinstallée et son empreinte revérifiée.

## Vérifications réalisées

- Validateur officiel `skill-creator/scripts/quick_validate.py` : **5/5 valides**.
- Métadonnées YAML, descriptions UI de 25 à 64 caractères, prompts nommant le bon skill et UTF-8 : **conformes**.
- Références du Lab : **45 vérifications de présence** sur la racine enregistrée et les worktrees WO-049/WO-051. Les références conditionnelles restent à résoudre dans le worktree de la tâche.
- Empreintes SHA-256 : **10/10 fichiers installés identiques aux sources**.
- Baseline Betting Project : **10/10 fichiers identiques au manifeste SKL-001**, conservé tel quel.
- Contrôle `scripts/check-no-secrets.cmd`, scope All : **PASS**. `git diff --check` et contrôle explicite des nouveaux documents non suivis, de leur UTF-8, espaces et liens locaux : **PASS**.
- Découverte Codex : **5 skills activés, portée user, aucune erreur** dans chacun des cinq contextes ci-dessous. Preuve : [discovery.json](evaluations/SKL-002/discovery.json).

| Contexte interrogé | Résultat |
|---|---|
| C:/Dev/BettingProject/codex/betting-sofascore-local-lab | 5 skills Lab activés |
| Même dépôt, .tmp/w49 | 5 skills Lab activés |
| Même dépôt, .tmp/w51 | 5 skills Lab activés |
| C:/Dev/BettingProject/human/betting-sofascore-local-lab | 5 skills Lab activés |
| Worktree Betting Project 8922 | 5 skills Lab visibles à portée utilisateur ; description limitée au Lab |

Le processus local de découverte a été arrêté après interrogation. Aucune tâche, requête de modèle ou session de travail supplémentaire n'a été créée. La découverte n'est pas une mesure du taux futur de sélection implicite.

## Essais indépendants sur cas réels

Deux agents sans l'historique de conception ont reçu les skills candidats, les sources du cas et une consigne d'analyse en lecture seule. Les essais couvrent les cinq skills ; aucun test Java, PostgreSQL, transport ou campagne n'est exécuté.

Les résultats détaillés et les limites sont conservés dans [la revue des essais](evaluations/SKL-002/review.md). Reprise du contexte : **77 secondes** pour WO-051 et **99 secondes** pour WO-049. Aucun défaut nécessitant correction, faux vert déclaratif ou incohérence documentaire n'a été relevé dans les deux réponses. Les répétitions réelles de tests sont NON_MESUREES, puisque Maven/Docker n'ont pas été exécutés. Les temps restent indicatifs, sans groupe témoin A/B ; aucun gain général n'en est déduit.

## Périmètre livré et limites

Livraison locale : cinq SKILL.md, cinq métadonnées UI, copies personnelles identiques, WO SKL-002, suivi de validation propriétaire dans SKL-001 et preuves d'installation/essais. Aucun code, migration, CI ou configuration du Lab modifié. Aucun commit, push, PR ou merge réalisé par ce lot d'extension.

L'installation concerne le compte Geoffrey sur ce poste. Les autres postes et comptes nécessiteraient leur propre installation. Les cinq fichiers sources `bp-*` ne sont pas remplacés et le Lab reste EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY.
