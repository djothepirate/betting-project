---
name: bp-work-order
description: "Reprendre, cadrer ou découper un Work Order du Betting Project en reliant l'état Git, les décisions et les preuves utiles. À utiliser pour préparer un lot ou reprendre une tâche interrompue."
---

# Reprendre et cadrer un lot
Localiser la racine du dépôt depuis le worktree courant. Lire AGENTS.md puis seulement les sources utiles au sujet ; résoudre les chemins ci-dessous depuis cette racine.

## Reprise
- Relever HEAD, branche ou HEAD détachée, diff local et WO concerné. Une branche ou un document lu dans un autre worktree ne décrit pas automatiquement l'état courant.
- Lire le WO et ses preuves récentes. Utiliser docs/project-status.md et docs/roadmap.md pour le contexte, puis arbitrer toute contradiction avec les décisions datées, contrats, commits et résultats pertinents. Une mention historique reste historique.
- Écrire une fiche courte : objectif, périmètre, base, état établi, invariants concernés, prochaine action et incertitude. Dire ce qui manque sans reconstruire une preuve absente.
- Reprendre les instructions et autorisations de la session. Ne pas redemander une décision déjà acquise ni déduire une nouvelle autorisation d'un ancien rapport.

## Work Order
Utiliser docs/work-orders/TEMPLATE.md. Définir résultat observable, périmètre, exclusions utiles, critères testables, validations proportionnées et livrables. Conserver des critères séparés pour réalisation locale, tests, revue et éventuelle livraison. Un WO documentaire ne requiert pas à lui seul un build applicatif.

Les décisions d'architecture se trouvent dans docs/adr ; les frontières dans docs/architecture/module-boundaries.md ; les contrats dans docs/contracts. Ne charger que ceux touchés. Ne pas recopier les totaux historiques de tests comme seuil permanent.

## Sortie
Fournir un WO utilisable ou une fiche de reprise, les sources qui établissent l'état, et les décisions réellement ouvertes. Synchroniser uniquement les documents dont la modification est nécessaire à la tâche, sans réécrire l'historique ni changer le prochain jalon produit par effet de bord.

