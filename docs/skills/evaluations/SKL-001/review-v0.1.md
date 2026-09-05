# SKL-001 — Revue indépendante du pilote v0.1

Revue du 5 septembre 2026, en lecture seule : huit réponses T1–T4, références métier au commit `5a8161e0070632f0b0dbbff760194e57ab4e5b1f`, scripts et POM de T2 exclusivement à `25d1c29`. Les cinq candidats ont été lus avant leurs corrections v0.2. Aucun test, Maven, SQL, réseau, fichier `.env` ou clé API consulté/exécuté. Seule écriture de cette revue : le présent fichier.

Un défaut distinct est compté une fois. Les colonnes « faux verts », « contradictions » et « relances proposées » décrivent sa nature et ne s'ajoutent pas au total des corrections. Une limite explicitée, une préférence de présentation ou un test souhaitable supplémentaire ne constituent pas automatiquement un défaut. Les labels A/B n'ont pas servi de critère de qualité ; cette revue connaît néanmoins leur signification et n'est pas aveugle.

| Réponse | Contexte prêt, s | Fin déclarée depuis début, s | Corrections | Faux verts | Contradictions | Relances injustifiées proposées |
|---|---:|---:|---:|---:|---:|---:|
| T1-A | 39 | 109* | 0 | 0 | 0 | 0 |
| T1-B | 26 | 121* | 0 | 0 | 0 | 0 |
| T2-A | 37 | 142 | 0 | 0 | 0 | 0 |
| T2-B | 27 | 117 | 0 | 0 | 0 | 0 |
| T3-A | 38 | 161 | 0 | 0 | 0 | 0 |
| T3-B | 30 | 168 | 1 | 0 | 0 | 1 |
| T4-A | 14 | 192 | 0 | 0 | 0 | 0 |
| T4-B | 26 | 219 | 0 | 0 | 0 | 0 |

Les répétitions de tests **exécutées sont NON_MESUREES dans les huit essais**, dont la demande imposait une revue statique ; elles ne valent pas zéro. Les huit fiches CONTEXT_READY sont suffisamment exactes pour orienter la tâche. Une base annoncée mais encore non vérifiée est explicitement présentée ainsi, notamment T4-B.

Les écarts horaires ont été recalculés depuis les horodatages des réponses, sans attestation indépendante de chaque événement. Ce sont des temps écoulés déclarés, pas du temps actif. *T1-A : 109 s jusqu'au premier enregistrement après analyse, qui constitue une borne supérieure et non une fin exacte ; sauvegarde terminée à 189 s, après attente du dossier de sortie absent. T1-B distingue analyse à 121 s et sauvegarde finale à 190 s. Pour T2–T4, la fin ne sépare pas aussi nettement analyse et rédaction. Comparer les totaux comme des durées homogènes serait trompeur. Les médianes de reprise sont 37,5 s en A et 26,5 s en B, sur quatre cas seulement, avec ordre et infrastructure non contrôlés.

## Défaut nécessitant correction

**D1 — T3-B, mineur, plan de validation redondant.** Citation exacte, `T3-B.md:38` : « Exécuter ensuite `.\mvnw.cmd -Pintegration verify` et les contrôles standards `.\mvnw.cmd verify` ». Au HEAD, `pom.xml:149–167` ajoute Failsafe (`integration-test`, `verify`, `**/*IT.java`) au profil `integration`, sans désactiver le cycle standard. Après réussite du premier parcours sur le même contenu, le second `verify` répète donc les contrôles standards sans nouvelle couverture, changement, échec ou exigence distincte indiqués. Correction : garder `-Pintegration verify`, ou expliciter le motif exigeant un second parcours. Ce défaut concerne une proposition ; aucun test inutile n'a été exécuté ni déclaré réussi.

La progression standard puis intégration de T2 ajoute la preuve PostgreSQL manquante. La vérification finale du lanceur complet en T2-B conserve un motif distinct : qualifier la propagation et l'orchestration qui constituent précisément le sujet du diagnostic. En T3-A, le second passage Flyway contrôle l'absence de seconde application ; les tests après ajouts de scénarios ont aussi un changement justificatif.

## Vérifications déterminantes

- **T1 :** `git show` établit le merge #10 au HEAD, le 2 septembre à 18:11:58 +02:00 ; `docs/work-orders/CI-003.md` garde le statut « PR #10 ouverte ». Les deux réponses distinguent correctement inclusion locale, autorisation humaine non consultée et tests historiques. Les durées 7 min 09 s et 17,421 s correspondent respectivement à l'observation sur `390fd1c5…` et à l'index chaud de CI-002. Aucune garantie distante actuelle n'est fabriquée.
- **T2 :** `25d1c29:scripts/verify-windows.ps1:6–15` ne relève pas les codes natifs et conditionne l'intégration à la présence de Docker. `25d1c29:mvnw.cmd:91–95` indexe `Target[0]` avant le contrôle de nullité. Les réponses décrivent correctement des fragilités statiques, sans inventer de panne reproduite.
- **T3 :** V005 ajoute bien quatre tables et neuf index explicites ; `V005MigrationIT` préremplit un snapshot synthétique et un reçu, vérifie surtout comptes/noms et rejets `SQLException` non isolés. Les remarques sur ces limites sont fondées. Le cas ancien reçu de T3-A n'est pas une panne de production affirmée : V004 autorise `REPLAY_REQUEST`, la fixture en conserve un sans demande, et `NormalizationReplayRequestService:97–107` lève bien une exception si type/hash concordent mais que la demande manque. Les deux réponses bornent correctement `ControlApiRestartIT` à trois contextes/pools d'une JVM, malgré la formulation documentaire « processus distincts ». Absence de protection SQL UPDATE/DELETE distinguée de l'append-only applicatif ; aucun trigger nouveau imposé sans décision.
- **T4 :** clés runtime littérales, interdiction réservée aux affectations, ambiguïté globale SHA, contrôle des octets et champ v3 obligatoire concordent avec contrats/code/tests. Les deux réponses préservent l'incertitude sur une correction réelle du parseur et sa provenance. `JdbcFixtureObservationStore` garde l'observation antérieure par `ON CONFLICT … DO NOTHING` ; ni la tentative ni `policyVersion` ne prouvent une version de parseur. Aucun faux vert identifié.

## Revue statique des cinq candidats

Les déclenchements se recouvrent utilement : cadrage/clôture, validation/persistance et persistance/sémantique du replay. Aucun conflit d'action obligatoire identifié. Leur sélection implicite effective n'est pas mesurée par des invocations explicites. Les candidats renvoient aux sources du dépôt, respectent autorisations courantes, migrations immuables, profils, provenance et distinction lecture/exécution ; ils ne créent pas d'autorisation de fusion ou d'accès externe.

**S1 — précision nécessaire, séparée des scores expérimentaux.** Le candidat data v0.1 disait : « Garder les DTO et identifiants source dans les adaptateurs. » Citation conservée dans `docs/skills/evaluations/SKL-001/candidates-v0.1/bp-data-contract-replay.md`. Cette généralisation reprend `docs/project-brief.md:67`, mais les références fournisseur sont légitimes dans `identity/domain/ProviderMappingKey.java:5–10` et `catalog/domain/FixtureObservation.java:7–15`. Elle risque d'inciter à déplacer ou supprimer de la provenance valide. Préciser la séparation entre DTO externes, références fournisseur portées par les types existants et identifiants canoniques. Aucune réponse T4 n'a commis ce déplacement : aucune régression expérimentale à compter.

Les quatre autres candidats ne présentent pas de défaut statique bloquant identifié. Une précision du candidat PostgreSQL sur le cycle Maven répondrait à D1 ; le pilote n'établit pas que le skill a causé cette proposition. Conserver les sorties et empreintes v0.1 avant toute nouvelle évaluation. La qualité globale est comparable ; aucun bénéfice causal, taux de réduction des bugs ou efficacité sur une implémentation complète n'est démontré.
