# ADR-005 - Workflow Eclipse-Codex, Git et Work Orders

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.1
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1

## Résumé de la décision

Eclipse reste l'environnement de revue et de validation humaine. Codex intervient sur des changements bornés dans une branche ou un worktree isolé, avec Work Order, tests et diff relu avant toute fusion.

## Contexte

Le porteur maîtrise Eclipse et doit pouvoir comprendre, tester et accepter chaque changement produit avec l'aide d'un agent.

Une conversation n'est pas une trace de décision suffisante. Les spécifications, décisions, tests et résultats doivent être versionnés.

Une modification simultanée du même worktree par Eclipse et Codex augmente le risque de conflit, d'écrasement ou de confusion sur la provenance d'un changement.

## Décision

Eclipse est l'IDE principal pour la navigation Java, le débogage, l'exécution des tests et la revue humaine. VS Code reste facultatif.

Chaque changement significatif part d'un Work Order borné décrivant l'objectif, le périmètre autorisé, les exclusions, les critères d'acceptation, les commandes de test et les livrables attendus.

Codex travaille dans une branche, un clone ou un worktree dédié. Le worktree utilisé par Eclipse pour la revue n'est pas modifié simultanément par l'agent.

AGENTS.md décrit les commandes fiables, conventions, répertoires protégés, règles de sécurité et critères de fin applicables au dépôt.

Avant fusion, le porteur lit le diff, rejoue les tests pertinents et valide le comportement dans Eclipse. Aucune fusion ni publication externe n'est implicite.

Les décisions durables sont enregistrées dans docs/adr, les travaux dans docs/work-orders et les procédures dans docs/runbooks. La conversation reste un support de travail, pas l'unique mémoire.

## Invariants

- Pas de modification simultanée d'un même worktree par le porteur et Codex.
- Pas de commit direct sur la branche principale pour un changement significatif.
- Pas de secret de production confié à l'agent ou copié dans le dépôt.
- Le diff final doit rester dans le périmètre du Work Order ou expliciter tout écart.
- Les tests requis et leurs résultats sont consignés avant la revue humaine.
- Une décision d'architecture modifiée entraîne la mise à jour de l'ADR correspondant.

## Alternatives étudiées

### Codex modifie le clone ouvert dans Eclipse

**Résultat :** Rejetée

Risque de concurrence, d'état difficile à relire et d'écrasement de modifications.

### Conversation comme documentation principale

**Résultat :** Rejetée

Contexte instable, difficile à versionner et à relier aux changements de code.

### Fusion automatique après réussite des tests

**Résultat :** Rejetée

La validation humaine reste obligatoire pour les décisions et changements structurants.

### Plugin Eclipse obligatoire

**Résultat :** Rejetée

Codex peut travailler sur le dépôt indépendamment de l'IDE ; aucun plugin communautaire n'est requis.

## Conséquences

### Conséquences positives

- Responsabilités claires entre préparation automatisée et acceptation humaine.
- Changements reproductibles et faciles à auditer.
- Réduction des conflits de fichiers et des modifications hors périmètre.
- Mémoire de projet durable dans Git.

### Coûts, limites et risques acceptés

- Le workflow impose une préparation et une revue pour chaque changement significatif.
- Les clones ou worktrees multiples demandent une discipline de synchronisation.
- Le porteur doit conserver le temps nécessaire à la revue et au rejeu des tests.

## Critères d'acceptation

- [ ] Un modèle de Work Order est présent et contient objectif, périmètre, exclusions, tests et définition de terminé.
- [ ] AGENTS.md documente les commandes de build, test et rendu ainsi que les chemins protégés.
- [ ] Le dépôt prévoit des emplacements versionnés pour ADR, Work Orders et runbooks.
- [ ] Un changement pilote peut être réalisé dans un worktree isolé, testé puis relu dans Eclipse.
- [ ] La fusion n'a lieu qu'après confirmation humaine et état Git vérifié.
- [ ] Le rapport de fin distingue fichiers modifiés, tests exécutés, limites et décisions ouvertes.

## Déclencheurs de réexamen

- Le projet devient multi-contributeur et requiert une gouvernance de revue différente.
- Une plateforme CI/CD ou de pull request devient la voie obligatoire de fusion.
- Le workflow de worktrees génère plus d'incidents qu'il n'en évite.
- Un plugin Eclipse officiel apporte une intégration sûre et vérifiable justifiant son adoption.

## Références

- PROJECT_BRIEF v0.1, sections 2.2, 2.3, 9.1 et 9.2.
- Document de cadrage v0.5.1, sections 3.2, 3.4, 3.5, 9.3 et 9.4.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale acceptée sans modification par le porteur. |
