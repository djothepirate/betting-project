# CI-003 — Fiche de reprise et note de clôture (T1-A)

START: 2026-09-04 23:37:20 UTC
CONTEXT_READY: 2026-09-04 23:37:59 UTC

## Fiche de reprise

- **Base :** Betting Project, référence `5a8161e0070632f0b0dbbff760194e57ab4e5b1f` ; historique local lu depuis ce commit, puis identité de HEAD confirmée.
- **Objectif :** empêcher une ref non fiable de publier une base NVD consommée par le contrôle bloquant de `main` ; préparer la reprise puis la clôture documentaire de CI-003.
- **État :** `security:dependencies` impose déjà `cache: []`. L’historique porte la fusion de la PR #10 le 2 septembre 2026 à 18:11:58 +02:00, alors que CI-003 indique encore « Prêt pour revue — Pull Request GitHub #10 ouverte » et conserve son critère de fusion non coché. Le reste identifié est documentaire.
- **Invariants :** aucun cache GitLab mutable ; flux public NVD JSON 2.0, Dependency-Check 12.2.2, aucune option de clé, `failOnError=true`, CVSS 7 bloquant ; protections d’ADR-008 inchangées ; revue humaine pour toute nouvelle fusion. Architecture et sécurité d’AGENTS.md restent applicables.
- **Prochaine action :** examiner le diff fusionné et les contrats ciblés pour préciser les preuves, puis proposer la correction du statut et du critère sans présenter l’historique comme preuve autonome de l’autorisation humaine ou d’un pipeline GitLab exécuté sur le SHA fusionné.

Cette fiche a été préparée et horodatée avant l’approfondissement. Sa première écriture a échoué à 23:37:59 UTC : le dossier de sortie n’existait pas ; sa création a été demandée à l’agent coordinateur.

## Note de clôture préparée

**CI-003 est techniquement fusionné ; sa fiche est restée à l’étape de revue.** Le commit correctif `649288e34e57165827229d22bcc597e2e3115e07` supprime la clé NVD, son chemin et sa politique de cache, puis renforce le contrat interdisant une autre déclaration. `5dc7f4190308a4dbacec1fc7e37933b64a0a2ad5` ajoute la référence à la PR ; `5a8161e0070632f0b0dbbff760194e57ab4e5b1f` enregistre sa fusion.

Correction documentaire proposée dans `docs/work-orders/CI-003.md` :

- remplacer le statut par « Fusionné par la Pull Request GitHub #10 le 2 septembre 2026 — commit 5a8161e0070632f0b0dbbff760194e57ab4e5b1f » ;
- ajouter cette référence et la date aux résultats ;
- distinguer « fusion constatée dans l’historique » de « revue et autorisation humaines tracées ». Le critère actuel combine ces faits : ne pas le cocher intégralement sur la seule preuve du merge ; rattacher la trace d’autorisation disponible dans la PR ou les décisions du lot.

CI-002, le runbook et ADR-008 décrivent déjà correctement l’absence de cache et la frontière de confiance. Aucune nouvelle correction technique, nouvelle PR corrective fonctionnelle ou seconde fusion de #10 n’est identifiée. Une future réintroduction de cache, un changement d’alimentation ou une montée de Dependency-Check relève d’un autre lot.

**Portée des preuves.** Le YAML et le lanceur confirment statiquement les invariants. Les contrats utilisent un faux Maven pour vérifier les arguments et des contrôles textuels du YAML ; ils ne réalisent pas un scan NVD et ne prouvent pas l’état du serveur GitLab. CI-003 rapporte 219 tests standards et 89 tests PostgreSQL/Testcontainers réussis, cinq migrations validées et les contrats/secrets verts : résultats historiques consignés, non rejoués ici. Les 7 min 09 s de CI-002 concernent le chargement à froid sur `390fd1c5ba5e271a839cb4cfd9cd30a51033b343`. Le scan corrigé en 17,421 s utilise un index chaud. Ni ces résultats, ni le lint/simulation GitLab mentionné dans CI-002, ne démontrent un pipeline réel sans cache sur le SHA fusionné. L’historique local établit la fusion ; il ne fournit pas le contenu de la revue humaine.

**Contrôles exécutés.** Lecture ciblée des sources, de `git log -10 --format=fuller 5a8161e0070632f0b0dbbff760194e57ab4e5b1f`, de HEAD et des diffs ; rapprochement du statut, du YAML et du lanceur ; `git show --check --format=` sur les deux commits correctifs précités, limité aux fichiers concernés : succès, aucune anomalie signalée. Aucun Maven, test applicatif, contrat shell, scan de secrets ou accès réseau exécuté.

**Contrôles proposés pour la correction documentaire.** Relire le diff limité à CI-003, vérifier les SHA, date, lien PR et cohérence des cases, puis exécuter `git diff --check -- docs/work-orders/CI-003.md`. Contrôler l’absence de secret dans le texte ajouté. Une suite Maven/PostgreSQL ou un nouveau chargement NVD n’est pas justifié par ce seul changement rédactionnel. Si une preuve opérationnelle nouvelle est exigée, consulter séparément la PR et les résultats GitLab au SHA exact, sans les présumer verts.

**Sources exactes.** `AGENTS.md` ; `docs/work-orders/CI-003.md` ; `docs/work-orders/CI-002.md` ; `docs/runbooks/ci-cd-github-gitlab.md` ; `.gitlab-ci.yml` ; `ci/run-dependency-check.sh` au HEAD ; `ci/test-dependency-check.sh` ; extraits ciblés de `ci/test-package-guards.sh`, `docs/adr/ADR-005-workflow-eclipse-codex-git-work-orders.md` et `docs/adr/ADR-008-integration-continue-github-gitlab-versioning-qualite-securite.md` ; historique et commits identifiés ci-dessus.

**Limites et décisions ouvertes.** Aucun fichier du dépôt modifié ; seul ce rapport est produit. État distant, artefacts et autorisation humaine non vérifiés. Aucune clarification métier nécessaire pour préparer cette note ; seule la trace d’autorisation devra être retrouvée avant d’affirmer ce critère entièrement satisfait.

ANALYSIS_COMPLETED_RECORDED: 2026-09-04 23:39:09 UTC (premier horodatage après les dernières lectures et vérifications ; analyse terminée auparavant, rédaction poursuivie pendant l’attente du dossier).
SAVE_STARTED: 2026-09-04 23:40:28 UTC
SAVE_COMPLETED: 2026-09-04 23:40:29 UTC
