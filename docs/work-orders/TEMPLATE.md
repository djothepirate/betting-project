# WO-XXX - Titre

- **Statut :** Proposé
- **Date d'ouverture :** AAAA-MM-JJ
- **Responsable de décision :**
- **Exécutant :**
- **ADR applicables :**
- **Train de version :** `Vx.y.z`, `Vx.y.z-RCnn` ou `Vx.y.z-RCnn-SNAPSHOT`
- **Branche d'intégration :** `feature/<TRAIN>`
- **SHA de départ :** HEAD exact de la branche d'intégration à l'ouverture
- **Branche de travail :** `feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>`, avec
  `<WORK-ORDER>=<TYPE>-<NNN>` et `NNN=001..999`
- **Cible de Pull Request :** `feature/<TRAIN>`

## Objectif

Décrire le résultat observable recherché.

## Périmètre autorisé

- Élément autorisé.

## Exclusions

- Élément explicitement exclu.

## Critères d'acceptation

- [ ] Critère testable.

## Commandes de test

```text
Commande reproductible
```

## Définition de terminé

- **Réalisation locale :** préciser le résultat observable et le diff attendu.
- **Tests :** relier chaque commande exécutée au HEAD qualifié.
- **Revue :** exiger une Pull Request vers la branche d'intégration de même train, des discussions
  résolues, des checks verts et une validation humaine avant le merge commit.
- **Livraison éventuelle :** la distinguer de la clôture du Work Order ; aucune synchronisation,
  promotion, création de tag ou livraison externe n'est implicite.

## Résultats d'exécution

À compléter avant la revue.
