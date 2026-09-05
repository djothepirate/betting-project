# Évaluation indépendante — ss-work-order + ss-review-closeout / WO-051

- Début : **2026-09-05T11:50:46Z**.
- Contexte prêt (WO, qualification, diff, décisions et état PR) : **2026-09-05T11:52:03Z**.
- Fin de l'analyse : **2026-09-05T11:53:26Z** ; durée 2 min 40 s.
- Essai analytique en lecture seule du Lab ; aucun Maven, mutation du Lab, publication, message GitHub ou accès aux secrets, `.env`, clés, exports ou artifacts privés. Seul ce résultat est écrit dans le workspace Betting Project. Aucune autre sortie d'évaluation consultée.

## Fiche de reprise et clôture

**Objectif et périmètre.** Corriger l'assertion non portable du refus mTLS distant observée après la fusion #27, avec tests synthétiques loopback. Deux fichiers de test et quatre documents changent depuis la base `49d4308794411d41ece97cbd22fdce7fef377616` ; aucun runtime, workflow, pom, ADR ou migration modifié. Les statuts EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY restent applicables.

**Bon worktree et candidat.** `git worktree list --porcelain` identifie `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/w51`, branche `codex/wo-051-mtls-peer-rejection-ci`, HEAD propre `e53bb01b96fe073e2c64444369cf39c6c6c87a64`. Dernier changement fonctionnel : `992423055091276914555104bb299ef3ce76d2c9` ; documentation qualifiée : `4daa7984d0c3f524d2b8af25b4c23a27524fee52` ; clôture : `e53bb01`. Le `main` d'un autre worktree ne sert pas de preuve distante.

**État établi : implémenté, qualifié, validé, clôturé, publié et fusionné.** Le WO est déjà dans `completed`, statut `COMPLETED_OWNER_VALIDATED`. La décision propriétaire du WO remplace explicitement l'attente historique du rapport et autorise push et PR, sans fusion. La consultation GitHub révèle toutefois une livraison déjà effectuée : [PR #28](https://github.com/djothepirate/betting-sofascore-local-lab/pull/28), HEAD identique au worktree, créée à 11:06:36Z puis fusionnée à **11:17:18Z**, merge `dfe34b3ab6881823a8e8601764cb99a63727810b`. Les deux parents de ce merge, vérifiés localement, sont la base et `e53bb01` ; l'historique est conservé.

**Preuves utiles.** Le diff confirme TLS_FAILURE strict pour la confiance serveur/SAN, TLS_FAILURE ou IO_FAILURE pour le refus de l'identité cliente, contrôle positif sur le même listener, compteurs applicatifs inchangés et transport terminé. `needClientAuth` et loopback sont conservés. Le test unitaire distingue IOException sans preuve TLS et SSLHandshakeException imbriquée ; le classificateur runtime reste inchangé.

- Qualification locale consignée : 21 tests ciblés ; build final 1149 standards, 0 échec/erreur, 4 ignorés, puis 4 IT mTLS réussis. Exclusions explicites J6 natif/superviseur JVM-sockets et absence d'IT PostgreSQL local. Clôture documentaire : 1149 tests avec `skipITs=true`, également consignée. Ces tests n'ont pas été relancés pendant l'essai.
- Empreinte du rapport **recalculée conforme** : `83468174d27cb5339690fcd3e947e495a228aa603e82f69816be4cf2be83d41f`. `git diff --check 49d4308..HEAD` exécuté sans anomalie ; état de travail propre revérifié.
- [CI de la PR, run 33962493086](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33962493086), trouvée pour le HEAD `e53bb01` : SUCCESS. Jobs Linux 101296686866 et Windows 101296687061 SUCCESS ; étape Linux « tests standards et PostgreSQL/Testcontainers » SUCCESS. Le workflow précise une qualification du merge synthétique de PR : ne pas présenter cela comme une exécution locale ou comme le run push du merge final. Aucun total distant de tests n'est inféré des seules métadonnées.
- [Résumé des revues](https://github.com/djothepirate/betting-sofascore-local-lab/pull/28#issuecomment-5551349056) : Code Review et Security Review terminées sur `e53bb01`. Listes des reviews formelles et des discussions inline vides ; aucune approbation humaine GitHub formelle déduite du résumé automatisé.

**Prochaine action proportionnée.** La clôture et la livraison Git n'ont plus à être préparées comme des opérations restantes. Une reprise opérationnelle pourrait ajouter un suivi daté reliant #28, le merge et la CI, en préservant le rapport gelé, puis vérifier le run push post-fusion si une qualification ou un bundle de `main` est demandé. Aucune nécessité de relancer Maven pour constater ces faits, rouvrir le WO, recréer une PR ou demander une seconde validation de son correctif.

**Décisions et incertitudes.** La validation du correctif et l'autorisation de clôture/publication sont acquises. Les sources consultées n'exposent pas l'instruction ultérieure de fusion : c'est une lacune de traçabilité documentaire, pas la preuve d'une fusion non autorisée, ni une nouvelle autorisation à solliciter pour une action déjà effectuée. La CI push du merge final n'a pas été vérifiée. Aucun nouveau POST WO-046, campagne fournisseur, accès receiver réel, VPS ou production n'est couvert.

## Résultat de l'essai des skills

**Résultat favorable sur ce cas.** Les candidats guident vers le bon worktree, distinguent les niveaux d'achèvement, préservent l'attestation gelée et évitent les deux erreurs concrètes du cas : recopier `OWNER_REVIEW=PENDING` et proposer une publication déjà réalisée. La vérification distante révèle également une fusion que la prose locale ne reflète pas.

**Ambiguïté mineure.** « Vérifier l'état distant lorsqu'une action Git en dépend » peut sembler conditionner la lecture distante à une mutation imminente ; ce cas justifie cette lecture dès un bilan de livraison. Une formulation « pour établir publication, état de PR, CI ou fusion » serait plus explicite. « Effectuer les commits, publications ou fusions déjà autorisés » n'a causé aucune action intempestive : la consigne actuelle d'essai analytique prime. Aucun défaut bloquant du skill relevé.

**Incident d'environnement.** Git a initialement refusé le dépôt pour ownership différent dans le sandbox. Résolution par `git -c safe.directory=<racine exacte>` pour chaque lecture, sans modifier la configuration globale. Ce n'est pas un défaut des skills.

## Sources locales principales

Racine Lab vérifiée : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/w51`.

- `AGENTS.md` de la racine Lab et du worktree ; aucun AGENTS imbriqué trouvé dans le périmètre.
- `docs/work_orders/completed/WO-SS-20260905-051-j9-mtls-peer-rejection-ci.md`, lignes 3–7, 24–51, 53–82.
- `docs/validation/J9-WO051-MTLS-PEER-REJECTION-CI-QUALIFICATION-20260905.md`, lignes 3–11, 37–62, 64–120.
- `README.md`, lignes 5–13 ; `CHANGELOG.md`, lignes 7–23.
- `src/test/java/com/bettingproject/sofascorelocal/integration/J7DeliveryMutualTlsLoopbackIT.java`, refus client lignes 183–187, contrôles lignes 256–326, loopback lignes 341–343.
- `src/test/java/com/bettingproject/sofascorelocal/adapter/bettingproject/transport/BettingProjectJ7DeliveryHttpTransportTest.java`, nouveau test à partir de la ligne 177.
- `src/main/java/com/bettingproject/sofascorelocal/adapter/bettingproject/transport/BettingProjectJ7DeliveryHttpTransport.java`, classificateur lignes 411–422.
- `.github/workflows/ci.yml`, déclencheurs et étape Linux `-Pintegration-tests clean verify`.
- Candidats évalués : `C:/Users/geoff/.codex/worktrees/8922/betting-project/docs/skills/local-lab/ss-work-order/SKILL.md` et `ss-review-closeout/SKILL.md`.
