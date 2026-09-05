---
name: bp-review-closeout
description: "Préparer une revue ou clôturer un Work Order et une PR Betting Project en reliant critères, diff, autorisations et preuves au bon commit, sans recopier un état documentaire périmé."
---

# Revoir et clôturer un lot
Résoudre les chemins depuis la racine Git. Lire AGENTS.md, le WO, le diff et les preuves de revue utiles. docs/adr/ADR-005-workflow-eclipse-codex-git-work-orders.md décrit le workflow ; les instructions et autorisations actuelles de l'utilisateur restent applicables.

## Vérifier l'état
Établir pour le candidat : base et diff, dernier changement fonctionnel, preuves de tests/revue qui le couvrent, décisions humaines et état Git connu. Distinguer implémenté, testé, validé, publié et fusionné. Un merge présent dans l'historique local établit son inclusion locale ; il ne remplace pas une consultation de l'état distant courant si celle-ci est nécessaire.

Ne pas traiter une ancienne restriction ou mention « en attente » comme un état actuel lorsque des preuves ou autorisations ultérieures la remplacent. Identifier une contradiction par fait, pas par nombre d'occurrences textuelles.

## Revue et validation
Relier chaque remarque à un défaut concret, une preuve et un correctif ; écarter les préférences de style sans impact. Vérifier que les tests couvrent le contenu corrigé. Après un delta documentaire seul, ne pas relancer spontanément toute la suite applicative ; respecter néanmoins les checks explicitement exigés pour le candidat livré.

## Clôture
Mettre en cohérence le WO et les synthèses réellement concernées en une passe. Conserver les preuves datées ; utiliser des formulations durables plutôt que prétendre qu'un futur commit a déjà passé sa CI.
Reprendre les autorisations déjà données sans les redemander. Préparer les actions déjà autorisées et nécessaires jusqu'au résultat révisable. Une revue ne crée pas par elle-même l'autorisation de fusion.

## Sortie
Donner conclusion, défauts restant à corriger, fichiers touchés, tests exécutés/non exécutés et décisions ouvertes. L'incertitude sur une preuve distante doit être explicitée ; elle ne transforme pas le reste du travail local en échec.

