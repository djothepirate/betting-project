# MVP-001 — Dossier de revue finale

Date : 19 septembre 2026. État : **READY_FOR_HUMAN_REVIEW**, pas accepté ni fusionné.
Revue technique : Codex. Revue humaine : **PENDING**, réservée au porteur dans Eclipse et la PR.

## Candidat et provenance

- Branche : `feature/V0.1.0-RC01-CODEX-MVP-001`.
- Base et cible : `feature/V0.1.0-RC01@5e4b05b5a1a7cfa66850643db31d9612192e7281`.
- Dernier commit fonctionnel : `6843ff833c1c74de26f6bfc6c7b71a11e3d64b5e` (lot 5).
- Le commit documentaire du lot 6 qui porte ce dossier ne change aucun octet sous `src`, aucun
  POM, script ou workflow par rapport au commit fonctionnel. Le HEAD publié et ses runs sont à
  vérifier dans la description et les checks de PR, sans auto-référence SHA dans ce fichier.
- Aucune activation réelle, fusion ou livraison du train n'est autorisée par cette qualification.

Diff cumulé : **170 fichiers**, dont 98 Java de production, 35 Java de tests, 3 migrations
(V009–V011), 1 registre classpath vide, 5 fichiers de fixtures et 28 documents. Les payloads
COV-002 complets restent hors Git ; seul leur index expurgé est dans le diff.

Inventaire exact à relire sur le HEAD de PR, y compris tous les nouveaux fichiers :

```powershell
git status --short
git diff --name-status 5e4b05b5a1a7cfa66850643db31d9612192e7281...HEAD
git diff --stat 5e4b05b5a1a7cfa66850643db31d9612192e7281...HEAD
git diff 5e4b05b5a1a7cfa66850643db31d9612192e7281...HEAD -- src
git diff 6843ff833c1c74de26f6bfc6c7b71a11e3d64b5e...HEAD -- src pom.xml scripts ci .github
```

La dernière commande doit être vide pour ce candidat documentaire. Toute correction fonctionnelle
ultérieure exige de nouvelles preuves et une revue du nouveau SHA. Ce dossier n'approuve pas par
avance un descendant modifié. Ne pas éditer simultanément le worktree depuis Eclipse et Codex.

## Frontières relues

| Sous-système | Livraison et garanties examinées |
|---|---|
| Registre/autorité | Clés exactes, parseur fermé, couverture distincte du rôle, aucune promotion ; SHA dans le watermark ; production vide |
| Budget V009 | Trois bornes, réserve appliquée une seule fois, réservation atomique, commit avant HTTP, cadence distincte et coût incertain conservé |
| Calendriers V010 | Deux parseurs natifs, octets avant parsing, audit/outbox, hashes brut/dérivé distincts, contextes natif et logique séparés |
| Catalogue | Normaliseur CAT-002 unique ; descriptifs inconnus sans mapping textuel ; contrôle sans mutation du canon ni du watermark |
| Jobs V011 | Claims `SKIP LOCKED`, bail/jeton, transactions courtes, reprise des pages acquises ; outboxes J7/publication exclues |
| Replay | UUID de page native, preuve vérifiée, journal et reçu d'effet ; demandes CAT-002 toujours séparées et manuelles |
| Contrôle/sélection | Pages bornées, paramètres liés, projections sans payload/clé/compte ; prévision de sept PPL/PD sans réservation ni enrichissement |

La revue technique ciblée des transactions, du fencing, de la provenance, des gardes HTTP,
des projections et des profils n'a identifié aucun défaut bloquant restant. Elle ne constitue
ni une revue humaine ni une garantie de couverture fournisseur réelle. Les tests ne revendiquent
pas un HTTP « exactement une fois » : une émission incertaine n'est jamais réautorisée automatiquement.

## Matrice des vingt critères

Numérotation dans l'ordre du [Work Order](../work-orders/MVP-001.md). Les classes citées sont
versionnées sous `src/test/java/com/bettingproject` et accessibles par la recherche Eclipse.

| # | Critère | Preuves du candidat | État |
|---:|---|---|---|
| 1 | Registre exact et fermé | `ProviderCapabilityTest`, `ProviderCapabilityDocumentParserTest` ; registre vide | PROVEN |
| 2 | Quatre routes, aucun contrôle promu | `ProviderRoutingServiceTest`, `RegistryCalendarAuthorityIT` | PROVEN |
| 3 | Contrôle sans mutation du canon | `RegistryCalendarAuthorityIT`, `CalendarCollectionIT.footballDataControlCannotOverwritePrimaryDespiteDifferentNativeShape` | PROVEN |
| 4 | Réservations/réserve atomiques | `BudgetCalculatorTest`, `ProviderBudgetServiceIT` : dernière unité, concurrence, autorisation unique après commit | PROVEN |
| 5 | Compteurs et reset incertains | `ProviderBudgetServiceIT` : observations désordonnées, péremption, hausse incohérente, couverture et réconciliation | PROVEN |
| 6 | Sélection après calendrier, sept maximum | `DailySelectionPolicyTest`, `DailySelectionServiceTest`, `CollectionControlIT.completeOfflineChainSelectsSevenWithRealBudgetAndNoNewIntent` | PROVEN — prévision |
| 7 | Pagination et incompatibilités | `NativeCalendarPageParserTest`, `CalendarCollectionServiceTest`, `CalendarCollectionIT` | PROVEN |
| 8 | Audit, octets, hashes, replay | `CalendarCollectionStoreIT`, `CalendarCollectionIT.nativeBytesAreAuditedBeforeDerivationAndReplayNeverDebitsOrSends` | PROVEN |
| 9 | Temps/ordre source, absences | Parseurs natifs, `CalendarNormalizationIT`, non-régression sur réception propre malgré déduplication du brut | PROVEN |
| 10 | 401/403/429 et incidents consultables | `CollectionControlIT.supplierSuspensionIsVisibleAndDoesNotBlockTheOtherProvider` (trois statuts) | PROVEN |
| 11 | Échec fournisseur isolé | `CalendarCollectionIT.laterFailureOfTheOtherProviderDoesNotRollbackPreviousEvidence` | PROVEN |
| 12 | Claim, fencing, tentatives bornées | `CalendarJobsIT` : claims concurrents, détenteur expiré, backoff, épuisement, rollback | PROVEN |
| 13 | Redémarrage et incertitude | `CalendarJobsIT.realContextAndPoolRestartPreservesConsumedIntentAndNoSecondPermission`, `ProviderBudgetServiceIT.committedAttemptAndBudgetSurviveActualContextAndPoolClosure` | PROVEN |
| 14 | Pas de double effet de replay | `CalendarJobsIT.workerReplayHasOneLogicalEffectAcrossLostAcknowledgementAndNeverCallsProvider`, perte d'ACK après application | PROVEN |
| 15 | API, confidentialité et profils | `CollectionWebContractTest`, `CollectionControlIT`, trois profils, `ArchitectureRulesTest` | PROVEN |
| 16 | Migrations neuves et V008 peuplée | `V009MigrationIT` peuple équipe/mapping/audit/outbox sous V008 puis migre jusqu'à V011 ; `V010MigrationIT`, `V011MigrationIT` vérifient les étapes peuplées et contraintes | PROVEN |
| 17 | Tests et scans exécutés | Qualification Windows 615 + 239, XML sans omission ; contrôles d'intégrité et scan du lot 6 | PROVEN |
| 18 | Documentation reliée au candidat | Contrats registre/budget/calendrier/jobs/contrôle, runbooks, dossier de revue, rapport lot 6 et mémoire | PROVEN |
| 19 | Revue humaine, fils résolus, CI tête PR | À effectuer sur le HEAD publié ; ni une revue technique ni un run d'ancêtre ne suffisent | OPEN |
| 20 | Fusion autorisée par merge commit | Cible exclusive `feature/V0.1.0-RC01`, après revue humaine ; aucune fusion ici | OPEN |

Total : **18/20** démontrés. Les critères 19–20 ne sont pas fermés par la préparation de PR.

## Exécutions et intégrité

Qualification Windows le **2026-09-19 à 10:32:46 Europe/Paris**, durée 2 min 23 s :
`mvnw.cmd -Pintegration verify`, `BUILD SUCCESS`, code 0. Rapports régénérés pendant cette
exécution : **615 tests standards** (76 suites), **239 tests PostgreSQL/Testcontainers**
(25 suites), zéro échec, erreur ou omission. Failsafe : 239 terminés, sans timeout ni flaky test.
Java 25.0.4, Maven 3.9.16, Spring Boot 4.1.1/Spring 7.0.9 et PostgreSQL 17.10 via Docker Desktop
named pipe. Sont inclus : migrations, concurrence, redémarrages, profils, ArchUnit, J7 et API interne.

Pas de changement de script : Pester, le build standard isolé et `verify-windows.cmd` ne sont
pas relancés après le cycle intégration, qui inclut déjà les tests standards. Aucun résultat WSL
nouveau. La CI du lot 5 [35411773181](https://github.com/djothepirate/betting-project/actions/runs/35411773181)
est verte sur `6843ff8`, Windows et Linux avec PostgreSQL réel. Les checks du commit documentaire
et de la PR sont à examiner séparément sur leur SHA ; ce dossier prépublication ne présume pas
leur réussite. Le [rapport du lot 6](../work-orders/MVP-001-lot6-execution.md) consigne le scan,
le diff et les contrôles finaux.

Les onze migrations sont inchangées, pas de V012 :

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

## Limites et suite

- Baseline réelle inactive : les références synthétiques ne qualifient pas une compétition réelle.
  Saison/phase/mappings et compte/quota doivent être explicitement étayés avant activation.
- Highlightly 100/80/20 reste la règle ; COV-002 100/0 ne l'abroge pas. Le top 5/UEFA souhaité
  reste une extension à cadrer, pas une affectation active du noyau PPL/PD/DED/ELC.
- Planification datée interne, aucune récurrence ou fenêtre auto-renouvelée. Un contrôle qui
  arrive avant le primaire nécessite une réévaluation par replay explicite.
- Une collecte complète peut garder des anomalies canoniques ; une page mélangeant des phases
  natives est incompatible. Aucun statut live ou champ absent n'est transformé en nominal.
- L'incertitude conserve son coût sans retry HTTP ; la cadence peut rester occupée jusqu'à
  réconciliation. Une réservation d'un job échoué n'est pas libérée implicitement.
- Sélection prévisionnelle : ENR-002 doit imposer le plafond durable, revalider et réserver avant
  enrichissement. Aucun enrichissement, pilote sept jours, consommateur J7, pari ou live ici.
- API locale sans authentification ; aucune exposition/proxy/staging avant OPS-001. Le rôle
  PostgreSQL de qualification reste propriétaire ; la séparation des privilèges reste à OPS-001.
- Aucune PR vers `main`, release GitLab, tag ou modification des paramètres de forge.

ENR-002 vient après la clôture réelle de MVP-001, sans activation implicite.

## Checklist du porteur — à remplir lors de la revue

- [ ] Relire tout le diff depuis la base indiquée, nouveaux fichiers compris, dans Eclipse/PR.
- [ ] Examiner V009–V011, contraintes et conservation des données lors des upgrades.
- [ ] Vérifier budget/envoi, incertitude, cadence, provenance, autorité et reprise/fencing.
- [ ] Vérifier profils, confidentialité HTTP et limites de la sélection prévisionnelle.
- [ ] Rejouer `mvnw.cmd -Pintegration verify` dans le bon checkout sans édition Codex simultanée.
- [ ] Vérifier CI Windows et Linux/PostgreSQL sur le HEAD exact de PR et traiter les remarques.
- [ ] Confirmer la cible RC01, approuver le diff et autoriser séparément le merge commit.

`reviewer: PENDING` — `reviewedAt: PENDING` — `reviewedCommit: PENDING` — `decision: PENDING`.
L'attestation humaine doit désigner le SHA effectivement relu. Ces champs restent ouverts
tant que le porteur n'a pas communiqué cette décision.
