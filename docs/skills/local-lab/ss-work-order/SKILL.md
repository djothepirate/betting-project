---
name: ss-work-order
description: "Reprendre, cadrer ou découper un Work Order du SofaScore Local Lab en reliant le bon worktree, les décisions et les preuves. À utiliser pour un lot du laboratoire ou une reprise interrompue."
---

# Reprendre un lot du SofaScore Local Lab

Appliquer ce skill au dépôt SofaScore Local Lab. Identifier la racine Git du worktree visé et lire son AGENTS.md ; résoudre les références ci-dessous depuis cette racine, pas depuis le dossier personnel du skill. Conserver les statuts EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY.

## Reprise

- Relever HEAD, branche, diff, WO et artefact réellement visés. La racine du projet enregistré peut être plus ancienne que le worktree d'un WO ; une branche `main` locale ne prouve pas l'état distant actuel.
- Chercher le WO dans docs/work_orders/active et docs/work_orders/completed, puis ses preuves dans docs/validation. Lire README.md et CHANGELOG.md pour s'orienter, puis départager les états par commits, dates, contenu et décisions applicables. Un rapport figé peut précéder une validation ultérieure.
- Produire une fiche courte : objectif, périmètre, base, état établi, invariants, prochaine action et incertitude. Retenir les autorisations actuelles de la session ; ne pas les redemander ni en élargir la portée.

## Cadrage

Rechercher un modèle du dépôt ; à défaut, reprendre la structure d'un WO voisin pertinent. Ne pas inventer `docs/work-orders/TEMPLATE.md`. Distinguer réalisation, qualification, revue, clôture et livraison Git. Une clôture n'implique pas à elle seule publication ou fusion.

Consulter selon le sujet les ADR-SS-*.md à la racine, docs/architecture/ARCHITECTURE.md, docs/runbooks et docs/validation. Une qualification synthétique ne vaut pas campagne fournisseur ou livraison réelle. Pour une campagne, identifier les ressources, le manifeste, l'autorisation et les bornes réellement applicables, sans figer une ancienne porte fermée comme état actuel.

## Sortie

Livrer la fiche ou le WO avec critères observables, commandes proportionnées, preuves attendues et décisions réellement ouvertes. Synchroniser seulement les synthèses concernées. Ne pas modifier une campagne, une base ou un jalon pour compléter artificiellement le cadrage.
