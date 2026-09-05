# SKL-001 — Revue indépendante des deux retests v0.2

Revue du 5 septembre 2026. Périmètre : `T3-B-v0.2.md`, `T4-B-v0.2.md` et comparaison des deux candidats corrigés avec leurs archives dans `docs/skills/evaluations/SKL-001/candidates-v0.1/`. Sources métier au HEAD vérifié `5a8161e0070632f0b0dbbff760194e57ab4e5b1f`. `review.md` v0.1 demeure inchangé ; ses résultats ne sont ni remplacés ni fusionnés avec les retests.

Même grille : compter une fois chaque défaut concret nécessitant correction ; distinguer faux verts déclaratifs, contradictions et relances injustifiées proposées. Une preuve manquante explicitement reconnue n'est pas un défaut de réponse. Aucun test, Maven, SQL, réseau, secret ou fichier `.env` consulté/exécuté par cette revue ; seule écriture : le présent rapport.

| Réponse | Contexte prêt, s | Fin déclarée depuis début, s | Corrections | Faux verts | Contradictions | Relances injustifiées proposées |
|---|---:|---:|---:|---:|---:|---:|
| T3-B-v0.2 | 28 | 121 | 0 | 0 | 0 | 0 |
| T4-B-v0.2 | 37 | 204 | 0 | 0 | 0 | 0 |

Les deux fiches CONTEXT_READY sont suffisamment exactes : objectif, base, état pertinent, périmètre, invariants, prochaine action et limites orientent correctement la revue. Horaires recalculés : T3, 23:52:37 → 23:53:05 → 23:54:38 UTC ; T4, 23:52:54 → 23:53:31 → 23:56:18 UTC. Il s'agit de temps écoulés déclarés, sans mesure du temps actif ni séparation complète entre analyse et rédaction. Les répétitions de tests exécutées restent **NON_MESUREES**, car les essais demandaient une lecture seule.

## Résolution des points v0.1

**D1 résolu dans T3-B-v0.2.** Le plan propose d'enrichir les cas, puis de « lancer ` .\mvnw.cmd -Pintegration verify` sur une base jetable, sans second `verify` systématique ». Le résultat attendu est présent dans la séquence proposée : un seul cycle Maven. Le passage ultérieur sur une copie V004 expurgée représentative répond à un autre objet de preuve, la conservation et les contraintes d'exploitation d'une base utilisée. Ce passage n'est donc pas une relance injustifiée. L'évaluation ne dépend pas de l'emploi d'une formule imposée.

La correction statique du candidat PostgreSQL est conforme à `pom.xml:149–167` : Failsafe s'ajoute au cycle standard. Elle conserve les exceptions fondées sur un changement, un diagnostic ou une exigence applicable. Elle ne supprime aucun contrôle nécessaire. Le retest maintient les distinctions utiles entre tests présents et exécutés, savepoint et rollback complet, append-only applicatif et droits SQL, contextes successifs et arrêt brutal. Aucun nouveau défaut identifié.

**S1 résolu statiquement dans le candidat data.** L'ancien impératif visant tous les « identifiants source » est remplacé par : « Garder les DTO externes dans les adaptateurs. Une référence fournisseur ne devient pas un identifiant canonique ; les références et la provenance restent représentables dans le domaine avec les types existants, notamment ProviderMappingKey et FixtureObservation. » Cela concorde avec `identity/domain/ProviderMappingKey.java:5–10` et `catalog/domain/FixtureObservation.java:7–15`. Les règles d'autorité, de temps source, d'ordre et de littéraux restent conservées.

T4-B-v0.2 traite correctement les cinq cas : littéraux runtime et affectations configurées, SHA globalement ambigu, altération avant parsing, champ v3 obligatoire, puis nouvelle interprétation d'octets historiques. Il distingue la preuve d'idempotence existante du scénario de correction du parseur manquant, sans inventer de version historique. Aucune règle ne recommande de retirer la provenance du domaine. Cela confirme la cohérence du retest ; aucune régression liée à S1 n'avait cependant été observée dans T4 v0.1.

La comparaison archives → staging relève seulement les deux paragraphes annoncés, plus une différence de blanc final. Aucun élargissement de déclenchement ou nouvelle autorisation introduit ; aucun invariant supprimé.

## Décision finale par candidat

| Candidat | Version évaluée retenue | Statut à l'issue de la revue | Portée |
|---|---|---|---|
| bp-work-order | v0.1 conservée | Utilisable | T1 et revue statique ; bénéfice indéterminé. |
| bp-verify | v0.1 conservée | Utilisable | T2 et revue statique ; bénéfice indéterminé. |
| bp-review-closeout | v0.1 conservée | Utilisable | T1 et revue statique ; bénéfice indéterminé. |
| bp-postgres-change | v0.2 corrigée | Utilisable | D1 absent du retest ; précision de validation conforme au POM. |
| bp-data-contract-replay | v0.2 corrigée | Utilisable | S1 corrigé ; T4 sans défaut distinct identifié. |

« Utilisable » qualifie ces instructions pour le périmètre observé ; ce statut ne vaut ni qualification de l'application, ni preuve d'efficacité sur une implémentation complète. Les retests B suivent des corrections ciblées sur des sujets déjà connus, sans nouveaux témoins A. Ils confirment la résolution locale des points visés, pas une réduction causale des défauts ou du temps. Aucun changement supplémentaire nécessaire identifié par cette revue ; le suivi en développement réel reste à mesurer lors d'un travail autorisé.
