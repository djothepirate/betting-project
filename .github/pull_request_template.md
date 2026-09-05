## Work Order et version

- Work Order :
- Branche source :
- Branche cible :
- Train `Vx.y.z`, `Vx.y.z-RCnn` ou `Vx.y.z-RCnn-SNAPSHOT` :
- SHA exact de la branche feature d'intégration utilisé à la création de la branche :

## Nature de la Pull Request

- [ ] Clôture d'un Work Order : `feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>` vers
      `feature/<TRAIN>` de même train.
- [ ] Finalisation d'une version : `feature/<TRAIN>` vers `main`.
- [ ] Bootstrap CI-004 borné : `codex/ci-004-version-branch-workflow` vers `main`.

## Preuves

- [ ] La branche de Work Order est partie du HEAD exact de sa branche feature cible ; le SHA est
      consigné dans le Work Order.
- [ ] `<WORK-ORDER>` suit `<TYPE>-<NNN>` (`NNN=001..999`) et son document
      `docs/work-orders/<WORK-ORDER>.md` est présent.
- [ ] Les critères du Work Order sont reliés au diff et aux résultats exécutés sur le HEAD courant.
- [ ] La version Maven correspond au train ; une finalisation stable vers `main` porte déjà la
      version finale sans `SNAPSHOT`.
- [ ] Pour le bootstrap CI-004 uniquement, le SHA de tête descend de la base historique indiquée et
      leur merge-base est exactement cette base.
- [ ] Les checks Windows et Linux requis sont verts et les discussions sont résolues.
- [ ] Le merge commit GitHub est sélectionné ; squash et rebase sont interdits.
- [ ] La fusion et toute opération distante ultérieure restent soumises à l'autorisation humaine.

## Livraison

Les bundles de PR et de `main` restent éphémères. Seul un push de la branche d'intégration exacte
`feature/<TRAIN>` peut conserver un snapshot ; il garde `production.approved=false` et
`vps.deployable=false` tant que le Work Order OPS-001 n'est pas qualifié.
