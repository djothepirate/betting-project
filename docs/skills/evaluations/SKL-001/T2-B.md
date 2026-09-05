# T2-B — Analyse indépendante de validation Windows

- START_UTC : 2026-09-04 23:41:58 UTC
- CONTEXT_READY_UTC : 2026-09-04 23:42:25 UTC

## Fiche CONTEXT_READY

- Base : révision historique `25d1c29` du dépôt `C:\Users\geoff\.codex\worktrees\8922\betting-project` ; instructions actuelles `AGENTS.md` et skill candidat `tmp/skl-001/candidates/bp-verify/SKILL.md` lus.
- Objectif : déterminer si la conclusion de la validation Windows permet d'accepter un changement persistant ; proposer un diagnostic et un plan de validation proportionné.
- Périmètre : inspection des blobs historiques `scripts/verify-windows.ps1`, `mvnw.cmd` et du `pom.xml` si nécessaire. Aucune consultation des correctifs, scripts actuels ou documents historiques de solution.
- Invariants : lecture seule du dépôt ; aucun réseau, test applicatif, Maven, mutation Git, accès aux clés, secrets ou `.env`. PostgreSQL/Testcontainers pour qualifier la persistance ; migrations partagées immuables ; revue humaine avant fusion.
- Prochaine action : lire les deux scripts à `25d1c29`, établir les chemins d'échec et vérifier quels contrôles de persistance la commande réalise réellement.
- Clarification : aucune nécessaire pour l'analyse ; le changement concret et son environnement seront nécessaires à sa qualification ultérieure.

## Réponse

La conclusion de la validation Windows à `25d1c29a2c428a5ddf72ebef88e3bf2a8ae4d907` ne suffit pas à accepter un changement de persistance. L'inspection établit des lacunes de contrôle ; elle ne démontre ni qu'un build précis a échoué, ni que l'application est défectueuse.

**Défauts et conséquences.** Dans `git show 25d1c29:scripts/verify-windows.ps1`, lignes 6–12, les appels natifs Maven ne sont suivis d'aucune lecture immédiate de `$LASTEXITCODE`, d'aucun arrêt explicite sur erreur ni d'une propagation explicite du résultat. `$ErrorActionPreference = 'Stop'` (ligne 1) ne garantit pas à lui seul qu'un code natif non nul déclenche une exception, notamment sous Windows PowerShell. Un échec de `-version` ou `verify` peut donc laisser les étapes suivantes s'exécuter. Le code ne construit aucun verdict consolidé ; un apparent succès de l'enveloppe serait une preuve insuffisante.

Le même script teste uniquement la présence de la commande Docker (lignes 10–15). Cela ne prouve ni l'accès au moteur, ni son utilisation effective par Testcontainers. Si la commande est absente, l'intégration est omise avec un avertissement et aucun échec explicite. Or `git show 25d1c29:pom.xml`, lignes 133–157, lie Failsafe et les classes `*IT.java` au profil `integration`. La vérification standard ne remplace donc pas ce parcours d'intégration configuré. Pour accepter de la persistance, une intégration omise reste une qualification incomplète.

Le wrapper apporte une piste de panne distincte : `git show 25d1c29:mvnw.cmd`, lignes 91–95, indexe `.Target[0]` avant de vérifier sa nullité. Si `Target` vaut `$null` dans l'environnement concerné, l'indexation peut échouer avant le lancement de Maven ; le test de nullité arrive trop tard. La valeur réelle de cette propriété n'a pas été relevée ici : c'est une fragilité étayée par le code, pas une panne reproduite. La chaîne batch/PowerShell qui recherche `MVN_CMD` et lance Maven (lignes 35–44) doit aussi être qualifiée par ses résultats observés.

**Plan proposé, sans exécution dans cet essai.**

1. Rattacher l'intervention à un Work Order et identifier le contenu exact à accepter. Capturer le SHA, les modifications éventuelles et les versions Windows, PowerShell, Java et Maven. Le POM impose Java 25 et Maven `[3.9.16,4)` ; observer le résultat réel du wrapper avant d'examiner les tests applicatifs.
2. Tester les lanceurs Windows avec des substituts locaux contrôlés : succès, échec de démarrage, échec du build standard, du contrôle de secrets et de l'intégration ; vérifier code final non nul et absence d'exécution des étapes dépendantes. Couvrir Docker absent, moteur inaccessible, puis disponible. Vérifier le wrapper avec un répertoire Maven ordinaire et un lien, et des chemins contenant des espaces. Aucun réseau ni secret n'est nécessaire à ces tests de propagation.
3. Sur un environnement qualifié et le contenu retenu, exécuter ` .\mvnw.cmd verify`, ` .\scripts\check-no-secrets.cmd`, puis ` .\mvnw.cmd -Pintegration verify` avec PostgreSQL/Testcontainers réel. Contrôler l'accès au moteur et les rapports Surefire/Failsafe : tests attendus présents, échecs et erreurs nuls, exclusions ou tests ignorés expliqués. Un code zéro seul ne suffit pas.
4. Adapter les cas PostgreSQL au changement : migration depuis l'état antérieur si concernée, contraintes et transactions, isolation/concurrence, idempotence et outbox si touchées. Préserver les migrations partagées. Après qualification des lanceurs, vérifier aussi ` .\scripts\verify-windows.cmd`. Étendre à Linux/WSL seulement si affecté, puis soumettre à revue humaine.

**Preuves et limites.** Exécuté : lectures de `AGENTS.md`, du skill candidat et des trois blobs précités, résolution du SHA. Aucun build, test, contrôle de secrets ou diagnostic Docker n'a été exécuté. Manquent les sorties historiques, l'environnement historique, les rapports de tests et le changement concret à qualifier. Aucune clarification ne bloque ce diagnostic. Le seul fichier créé/modifié est ce rapport ; le dépôt reste inchangé.

- END_UTC : 2026-09-04 23:43:55 UTC
