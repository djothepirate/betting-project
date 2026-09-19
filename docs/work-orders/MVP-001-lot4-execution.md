# MVP-001 — Lot 4 : exécution

## Reprise et activation — 2026-09-19

- Demande explicite : réaliser le lot 4, jobs, outbox, claim, reprise et planification.
- Base exacte : `f958a88d2ceb5440017c782044ea51d869d99888`, worktree initial propre.
- Branche : `feature/V0.1.0-RC01-CODEX-MVP-001`, cible finale inchangée.
- La CI du lot 3 n'a exécuté aucune étape : restriction de facturation GitHub Actions,
  run `35399114529`. La nouvelle demande autorise la réalisation locale ; elle ne transforme
  pas cette preuve distante manquante en succès. La qualification distante reste à obtenir.
- V001–V010 immuables ; V011 additive livrée. Registre vide et fournisseurs désactivés.

## Périmètre et invariants

Le moteur utilise les jobs persistants existants, un dispatch d'outbox strictement dédié,
des claims PostgreSQL `SKIP LOCKED`, un bail de 120 secondes et un jeton renouvelé à chaque
tentative. Les écritures du détenteur sont protégées sous verrou par ce jeton et par le bail.
Les transactions de base sont courtes ; aucun verrou ne reste acquis pendant HTTP.
Les reprises conservent le même identifiant de collecte et les mêmes intentions budgétaires.
Une autorisation déjà commitée sans résultat devient incertaine : aucun nouvel envoi.
Une page conservée peut être interprétée sans rappeler le fournisseur ni réappliquer un effet
logique déjà acquis. Les tentatives et événements sont conservés.

La planification reçoit explicitement date UTC, échéance, routes exactes, fenêtres existantes
et années de filtre. Elle ne crée ni fenêtre ni capacité et ne déduit aucune saison ou phase.
Les jobs d'enrichissement restent un vocabulaire réservé, sans handler exécutable. Le poller
est limité à `batch-worker`, opt-in et désactivé par défaut. Les cas d'usage internes restent
testables sans boucle ni réseau ; aucun endpoint de contrôle n'est ajouté avant le lot 5.

## Livrables et preuves

| Périmètre | Réalisation | Preuve principale |
|---|---|---|
| Jobs et outbox | Création atomique, clé/contenu idempotents, entrée typée, dispatch fermé aux anciens messages | `CalendarJobsIT`, `JobModelTest` |
| Concurrence | Claim `SKIP LOCKED`, bail/jeton, fencing, version, reprise bornée et événements | `twoConcurrentClaimsReturnOnlyOneOwner`, `lateOwnerCannotWriteOrAcknowledgeAfterExpiryAndReclaim` |
| Rollback | Échecs injectés du journal, de l'outbox et de l'entrée sans job orphelin ni ack partiel | `enqueueRollbackDoesNotLeaveAnOrphanJob`, `journalFailureRollsBackClaimAndAckIncludingOutbox` |
| Budget | Même réservation en reprise/cadence ; pas de seconde permission pour un engagement | `reservedButNeverAuthorizedPageCanResumeWithOnePermission`, `cadenceDefersTheSameReservationThenPermitsOneSendWhenTheSlotExpires` |
| Reprise | Octets reçus réutilisés ; pages jamais envoyées seules collectables ; accusé perdu sans double effet | `rawResponseSurvivesApplicationFailureAndRetryDoesNotCallTwice`, `receivedFirstPageIsReusedBeforeFetchingOnlyTheUnsentSecondPage`, `ackFailureAfterApplicationDoesNotDoubleNormalizeOnRecovery` |
| Incertitude | Timeout, transport, interruption et détenteur expiré jamais réémis automatiquement | `uncertainTransportIsTerminalWithoutAutomaticReissue`, `expiredWorkerCannotPersistResponseOrCanonicalChanges` |
| Redémarrage réel | Contextes Spring et pools fermés/réouverts sur la même base, coût acquis conservé | `realContextAndPoolRestartPreservesConsumedIntentAndNoSecondPermission` |
| Replay natif | Effet et reçu atomiques, aucun appel fournisseur, ack perdu sans deuxième application | `workerReplayHasOneLogicalEffectAcrossLostAcknowledgementAndNeverCallsProvider` |
| Planification | Quatre routes exactes, primaire et contrôles, aucune fenêtre inventée ni réservation future | `dailyPlannerUsesExactBindingsAndIsIdempotentAcrossFourRoutes`, `futureJobsDoNotConsumeBudgetOrSend` |
| Configuration figée | Empreintes du registre et version du parseur dans chaque job ; changement refusé avant budget | `pinnedRegistryAndParserChangesFailBeforeAnyBudgetReservation` |
| Migration | Installation vide et upgrade V010 réellement peuplée, conservation des dix tables antérieures observées | `V011MigrationIT` (3 tests) |
| Profils | Boucle uniquement batch, opt-in, absente par défaut ; replay toujours sans persistance | Trois tests de profils, `CollectionWorkerLoopTest`, `ArchitectureTest` |

Le [contrat normatif](../contracts/collection-jobs-v1.md) explicite les transactions, l'ordre de
verrouillage, les codes, les limites d'exactement-une-fois et les reprises. Le
[runbook local](../runbooks/collection-jobs-local.md) décrit planification, inspection et reprise,
sans endpoint HTTP ni manipulation manuelle des baux en exploitation.

## Qualification exécutée — Windows, 2026-09-19

Les suites ciblées ont couvert le domaine, les profils, la boucle opt-in, le normaliseur raccordé,
la migration et les jobs PostgreSQL. Dernière exécution ciblée à **00:48:12 Europe/Paris** :
**22 tests standards et 28 tests PostgreSQL**, sans échec, erreur ou omission. Une première
attente incorrecte de `V011MigrationIT` a été corrigée : une preuve déjà liée à une dérivation
doit refuser son effacement, pas autoriser sa conversion en envoi sans réponse. Aucune contrainte
de production n'a été affaiblie pour faire passer ce scénario.

Validation complète de **00:51:41 à 00:53:43 Europe/Paris** :

```powershell
.\mvnw.cmd -Pintegration verify
.\scripts\check-no-secrets.cmd
git diff --check
```

- Maven : **BUILD SUCCESS**, code de sortie **0**.
- Surefire : **572 tests**, 0 échec, 0 erreur, 0 omission ; 73 rapports frais datés entre
  00:51:44 et 00:51:55.
- Failsafe/PostgreSQL 17 : **224 tests**, 0 échec, 0 erreur, 0 omission ; 24 rapports frais datés
  entre 00:52:06 et 00:53:42, résumé `completed=224`, `flakes=0`.
- Delta : 22 tests standards et 28 tests PostgreSQL ajoutés aux 550 + 196 historiques du lot 3.
- Scan de secrets : **PASS**, périmètre `All`, code 0 ; aucun secret à forte confiance ni endpoint
  privé SofaScore détecté.
- `git diff --check` et contrôle explicite des espaces finaux : PASS, y compris fichiers non suivis.
- 43 fichiers du candidat : code, tests, V011 et documents liés au lot ; aucun fichier staged
  avant préparation du commit, aucun changement au POM, scripts, CI, changelog ou configurations
  d'activation. Pas de H2, V012, nouveau profil ou fixture fournisseur réelle.

Le cycle integration comprend déjà les tests standards. Aucun second build standard ni lancement
de `verify-windows.cmd` n'est requis par le Work Order pour ce diff : scripts Windows et CI inchangés.
Aucune exécution Linux locale n'est présentée comme preuve Linux/Testcontainers. La tentative
initiale de compilation en sandbox n'avait pas accès à la résolution Maven ; l'exécution autorisée
avec le wrapper a ensuite compilé et qualifié le même périmètre normalement.

## Empreintes des migrations

V001–V010 ont été comparées à la baseline de départ et sont inchangées. V011 est l'unique
migration du lot ; elle ne précharge aucune capacité, fenêtre, intention ou historique.

| Migration | SHA-256 |
|---|---|
| V001 | `d3ab6619dc4b81f7ac6807c06b5f18c3013de442c525701d150fe5d23ee1f090` |
| V002 | `69d9e4ff0415a4fcf395c2ce85fe79c84bc57672daa1c2b72f4b4d46ae516271` |
| V003 | `059b95738deb3c22b90399c6096dab9cd3e2594f8279f9d6ea22ab28dfff9dfd` |
| V004 | `2051f1caf8420d3a36c28becfba47dd6b0827d25b1910d4cf0162916bdec8cd3` |
| V005 | `f38ac525099e3b269b85b19df3ece34d2e0e38d460834b953bbbb8d41507f90f` |
| V006 | `6557d9510b20f6c163f0b96bc914de6d181b151eebd08cb254928162276bc6e3` |
| V007 | `9a87c602c500a47f5c3984b690185f9906394a651ea4e3d4c9999f8537120cff` |
| V008 | `6fbee3fec6a84aa390df913df4fbd3b73fc1a522272197e82bbf6bdf01f7716b` |
| V009 | `d86d981c7e251af90e1a58ade6cbf94fc665d7ca760873e873b2d58be3afe5e1` |
| V010 | `5d61a461f32fbd799c821b948365dc119737be67465c27a8dd1e1782b816808a` |
| V011 | `2be2aa01b2437285aeb6739e5f1235ac98067f879de1f460bc839d4990a7b53d` |

## Revue du candidat et limites

La revue technique couvre l'atomicité job/outbox/entrée, le claim et le fencing, le commit de
l'autorisation avant HTTP, la conservation des reçus de normalisation, la reprise après réception,
la persistance des refus de parsing, l'immutabilité du registre/parseur par job et les profils.
Les tests démontrent ces frontières avec transactions distinctes. Aucun défaut connu n'est laissé
sans traitement ; cette revue technique ne remplace pas la revue humaine de la future PR.

- Une réponse HTTP tardive d'un ancien détenteur peut ne pas être conservée : aucune fausse
  preuve n'est inventée, le coût acquis reste retenu et le travail est signalé incertain.
- Un job épuisé peut laisser une réservation non engagée ou un engagement sans réponse.
  Il n'existe ni remboursement acquis, ni nouvel envoi, ni réouverture automatique.
- L'ordre de soumission primaire/contrôle n'est pas une dépendance entre workers : le contrôle
  précoce reste observable et demande une réévaluation explicite après disponibilité du primaire.
- Le plan est une commande datée, pas une planification quotidienne récurrente implicite.
- Le registre réel, les clients et la boucle restent désactivés ; tous les transports testés
  sont synthétiques ou les loopbacks déjà qualifiés, sans clé API ni quota fournisseur réel.
- La sélection quotidienne, la consultation API, l'enrichissement et la clôture intégrée restent
  hors lot 4. Ni J7 ni les demandes manuelles CAT-002 ne sont consommés par le nouveau worker.

## Sortie locale et livraison

Lot 4 : **COMPLETED localement**. État global : `ACTIVE - IMPLEMENTATION`.
Trois critères supplémentaires sont démontrés (claim/fencing, reprise/incertitude, effet de replay),
soit **12/20** ; les huit autres restent ouverts. Le lot 5 est prochain mais `PENDING`, comme le lot 6.

Le commit distinct et le push sont autorisés par le porteur pour chaque lot terminé. Le SHA publié
et ses checks doivent être consultés après publication : ce rapport ne prétend pas qu'un futur
commit a déjà une CI verte. Le blocage de facturation constaté sur la CI du lot 3 reste une dette de
qualification distante tant qu'une exécution Windows/Linux réelle ne l'a pas remplacé. Aucune
PR de clôture, fusion, activation fournisseur ou modification de facturation n'est réalisée ici.
