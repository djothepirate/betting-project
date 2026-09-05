# CI-003 — Reprise et note de clôture (essai T1-B)

START_UTC : 2026-09-04 23:40:18 UTC
CONTEXT_READY : 2026-09-04 23:40:44 UTC (26 secondes)

## Fiche de reprise

- Base : worktree `C:\Users\geoff\.codex\worktrees\8922\betting-project`, HEAD détachée `5a8161e0070632f0b0dbbff760194e57ab4e5b1f`, aucun changement signalé par `git status --short` ni `git diff --stat`.
- Objectif : empêcher une ref non fiable d'alimenter le cache lu par le contrôle bloquant de `main`, tout en conservant le flux NVD sans clé et le ratchet CVSS 7.
- État établi : le WO décrit la correction `649288e` et ses qualifications historiques ; son statut « PR #10 ouverte » contredit le commit de fusion #10 présent au HEAD le 2 septembre 2026. Le travail restant identifié est documentaire ; l'état distant courant et la décision humaine ne sont pas établis par cette lecture locale.
- Périmètre : préparer une reprise et un closeout révisables ; aucune nouvelle implémentation, opération distante ou modification du dépôt n'est autorisée dans cet essai.
- Invariants : `security:dependencies` doit imposer `cache: []`, aucun cache mutable GitLab, flux NVD JSON 2.0, Dependency-Check 12.2.2, `failOnError=true`, CVSS 7 bloquant, aucune clé et aucune modification des protections GitLab. Les frontières du monolithe, PostgreSQL et les profils autorisés restent inchangés.
- Prochaine action : examiner le diff correctif, le dernier delta documentaire et les contrats ; préciser la correction de statut et rattacher chaque preuve à son SHA ou à sa portée historique.
- Incertitude : l'historique établit une fusion incluse localement, sans prouver les checks distants actuels ni l'autorisation humaine de #10. Aucune raison de recommencer la correction technique uniquement à cause d'un statut périmé.

## Note de clôture préparée

**Conclusion.** CI-003 est implémenté et sa fusion #10 est incluse dans le HEAD local `5a8161e`, daté du 2 septembre 2026 à 18:11:58 +02:00. Il ne faut ni rouvrir artificiellement la PR ni refaire le correctif. Le statut du WO est antérieur à cette fusion. Le dernier changement fonctionnel est `649288e34e57165827229d22bcc597e2e3115e07`. `5dc7f4190308a4dbacec1fc7e37933b64a0a2ad5` ne modifie ensuite que CI-002 et CI-003 ; son arbre est identique au HEAD fusionné.

**Correction documentaire à préparer.** Dans `docs/work-orders/CI-003.md`, remplacer le statut par « Fusionné par la PR GitHub #10 le 2 septembre 2026 au commit 5a8161e — inclusion constatée dans l'historique local » et ajouter cette preuve aux résultats. Séparer l'ancienne case combinant revue et autorisation : la fusion est établie ; sa trace de décision humaine n'a pas été consultée et ne doit pas être inventée. Conserver les qualifications datées et présenter l'ancienne définition « prêt pour revue » comme une étape préalable accomplie, sans prétendre qu'un futur commit documentaire a déjà passé sa CI. CI-002 peut recevoir la référence du merge #10 si une synchronisation est souhaitée ; son fond, le runbook et ADR-008 décrivent déjà correctement l'absence de cache. Aucune modification de roadmap ni déplacement du prochain jalon produit n'est nécessaire.

**Portée des preuves.** Le YAML impose une unique déclaration `cache: []` dans `security:dependencies`, sans `allow_failure` dans ce job. Le contrat correspondant est dans `ci/test-package-guards.sh`. Le lanceur conserve Dependency-Check 12.2.2, le flux JSON 2.0, `failOnError=true`, CVSS 7 et l'absence d'option de clé ; son dernier commit est `dcbbde3876432a9f233d4809975de7ae3b26e99d`. La base GitLab utilise `.m2/dependency-check-data/` via `MAVEN_USER_HOME`.

CI-003 consigne 219 tests standards, 89 tests PostgreSQL/Testcontainers, cinq migrations et des contrats/secrets réussis avant commit. Ces attestations historiques accompagnent le correctif ; aucun rapport brut ni rejeu n'a été vérifié ici. Les 7 min 09 s de CI-002 concernent le scan d'observation `390fd1c5ba5e271a839cb4cfd9cd30a51033b343`. Les 17,421 s et zéro dépendance vulnérable concernent sa qualification sur index chaud ; ce ne sont ni une nouvelle mesure à froid de CI-003 ni une garantie de vulnérabilités absentes aujourd'hui. Les checks GitHub verts et le lint GitLab rapportés par CI-002 ne prouvent pas ceux du HEAD actuel.

**Contrôles exécutés.** Lectures ciblées, `git rev-parse`, branche/status/diff/log/show en lecture seule ; comparaison des deltas ; `git diff --check 76a25db8a2111e64d4bcb8dec89b8677a3170a2e 5a8161e0070632f0b0dbbff760194e57ab4e5b1f` réussi ; contrôle statique PowerShell de l'unique cache vide et du caractère bloquant réussi. Aucun fichier du dépôt modifié.

**Contrôles seulement proposés.** Après correction documentaire : revue des faits, SHA, liens et cases, contrôle du diff final et `git diff --check`, contrôle de secrets borné au delta si requis par la livraison. Respecter les checks explicitement requis sur le candidat réellement publié. Un changement documentaire seul ne justifie pas de relancer Maven, Testcontainers ou le scan NVD. Les scripts de contrat n'ont pas été exécutés dans cet essai : certains écrivent des fichiers temporaires ou des références Git.

**Limites et clarification.** Aucun réseau, Maven, scan de secrets, test applicatif, mutation Git ou lecture de clés/.env. Publication distante courante, checks du HEAD et attestation humaine #10 non vérifiés ; aucune nouvelle action distante n'est demandée. Je n'aurais demandé aucune clarification pour préparer cette note : l'objectif et le périmètre suffisent. Une preuve absente est signalée sans bloquer le closeout documentaire.

## Sources exactes consultées

Toutes les sources versionnées ci-dessous sont celles du HEAD `5a8161e0070632f0b0dbbff760194e57ab4e5b1f` :

- `AGENTS.md` ; `docs/work-orders/CI-003.md` ; `docs/work-orders/CI-002.md`.
- `docs/runbooks/ci-cd-github-gitlab.md` ; `.gitlab-ci.yml`.
- `ci/test-dependency-check.sh` ; `ci/run-dependency-check.sh` ; `ci/test-package-guards.sh` (extraits).
- `docs/project-status.md` ; `docs/roadmap.md` (contexte, sans modification proposée).
- `docs/adr/ADR-005-workflow-eclipse-codex-git-work-orders.md` ; `docs/adr/ADR-008-integration-continue-github-gitlab-versioning-qualite-securite.md` (extraits).
- Instructions candidates : `tmp/skl-001/candidates/bp-work-order/SKILL.md` et `tmp/skl-001/candidates/bp-review-closeout/SKILL.md`.
- Historique et diffs locaux : `5a8161e0070632f0b0dbbff760194e57ab4e5b1f`, `5dc7f4190308a4dbacec1fc7e37933b64a0a2ad5`, `649288e34e57165827229d22bcc597e2e3115e07`, base `76a25db8a2111e64d4bcb8dec89b8677a3170a2e` ; dernier changement du lanceur `dcbbde3876432a9f233d4809975de7ae3b26e99d`.

Seul fichier écrit : `C:\Users\geoff\Documents\Betting Project\SKL-001-evaluation\T1-B.md`.
FIN_ANALYSE_UTC : 2026-09-04 23:42:19 UTC — durée d'analyse : 2 min 01 s ; contexte prêt en 26 s.
END_UTC : 2026-09-04 23:43:28 UTC — durée totale : 3 min 10 s. Vérification finale : rapport présent, 619 mots hors fiche avant cette ligne.
