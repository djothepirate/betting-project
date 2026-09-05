# SKL-002 — Revue des deux essais d'adaptation

- **Date :** 2026-09-05.
- **Revue :** agent principal, après remise des deux réponses indépendantes.
- **Contenu évalué :** les dix fichiers identifiés par [installation-manifest.json](installation-manifest.json), inchangés entre essais et installation.
- **Portée :** revue des réponses analytiques et adéquation des skills au Lab ; aucune nouvelle qualification de l'application.

| Cas | Skills exercés | Reprise du contexte | Corrections requises dans la réponse | Faux verts déclaratifs | Incohérences documentaires dans la réponse |
|---|---|---:|---:|---:|---:|
| [WO-051 : reprise, clôture et livraison](eval-closeout.md) | ss-work-order, ss-review-closeout | 77 s | 0 | 0 | 0 |
| [WO-049 : qualification launcher et extensions ledger/contrat](eval-verify.md) | ss-verify, ss-postgres-change, ss-data-contract-replay | 99 s | 0 | 0 | 0 |

Durées calculées depuis les horodatages déclarés début/contexte prêt : 11:50:46 → 11:52:03 UTC et 11:51:03 → 11:52:42 UTC. Ces valeurs incluent lectures/outils et ne disposent pas de témoin sans skill. Elles ne prouvent aucun gain comparatif.

Les deux réponses distinguent correctement rapports historiques, décisions ultérieures et contrôles réellement effectués. WO-051 ne reste pas artificiellement bloqué par le rapport gelé ; l'état distant est vérifié sans publication. WO-049 ne transforme pas `--offline` ou un profil absent en preuve d'absence de tests PostgreSQL. Les scénarios d'évolution des données préservent les distinctions entre provenance, décision humaine, grant, tentative et réception.

Les répétitions inutiles de tests **exécutés sont NON_MESUREES** : les essais interdisaient l'exécution Maven/Docker. Aucune relance spontanée injustifiée n'est proposée. La séquence test ciblé puis vérification plus large du WO correspond à diagnostic puis qualification ; elle n'est pas comptée automatiquement comme doublon inutile.

Les agents suggèrent des précisions facultatives : consulter le distant pour établir un état de livraison, rendre explicites les particularités PowerShell/OS, contrôler les versions de schéma citées par les runbooks et respecter les états de payload purgé. Les instructions existantes demandent déjà cette adaptation au worktree, aux scripts effectifs, aux skips et à la provenance. Les réponses n'ont produit aucun défaut nécessitant correction sur ces points. Ces propositions restent des pistes pour une évolution ultérieure, sans modifier après coup le contenu testé.

**Décision :** cinq adaptations utilisables dans le Lab ; installation personnelle acceptée techniquement. Le contrôle de découverte vérifie le chargement et l'activation, pas la fréquence de sélection implicite. Aucun bénéfice général sur les développements complets, les faux verts d'exécution ou les répétitions réelles de tests n'est démontré par ces deux essais.
