# T4 — Essai indépendant B, candidat v0.2

Début : 2026-09-04 23:52:54 UTC
CONTEXT_READY : 2026-09-04 23:53:31 UTC

## Fiche initiale
- Base : HEAD vérifié 5a8161e0070632f0b0dbbff760194e57ab4e5b1f. Éléments non suivis signalés par Git : .agents/, docs/skills/, docs/work-orders/SKL-001.md ; contenus exclus non consultés.
- Objectif : revue ciblée du contrat de normalisation/replay sur cinq cas limites ; distinguer comportement conforme et preuve disponible/manquante.
- Périmètre : AGENTS.md, skill candidat bp-data-contract-replay, contrats calendar-replay-v3 et stored-snapshot-replay-v1, puis code/tests directement nécessaires.
- Invariants : autorité runtime exacte ; configuration distincte des littéraux fournisseur ; contrôle des octets avant parsing ; historique et provenance conservés sans version rétroactive inventée ; participants ordonnés sauf autorisation explicite ; replay sans réseau fournisseur.
- Prochaine action : lire les classes d'autorité, repérer puis vérifier les assertions ciblées et le chemin de replay historique.
- Limite : lecture statique uniquement ; aucun test applicatif, Maven, réseau, accès aux secrets ou mutation Git. Seule cette note peut être écrite. Aucune qualification d'exécution ni exactitude métier externe ne sera revendiquée.

Contexte consigné avant approfondissement.

## Revue ciblée

Quatre cas sont étayés ; la traçabilité après correction du parseur reste à compléter.

| Cas | Comportement conforme | Preuve présente et limite |
|---|---|---|
| Littéraux fournisseur `*`, `?`, `%` | Comparaison exacte, aucune interprétation comme motif. `CalendarAuthorityKey` les conserve ; `CalendarAuthorityAssignment` les refuse en configuration. Sans affectation exacte : `UNASSIGNED`, preuve/observation/journal/anomalie conservés, aucun référentiel, mapping ou fait canonique créé. | `ConfiguredCalendarAuthorityPolicyTest:53/69`, `ClasspathCalendarAuthorityConfigurationTest:95` et `CalendarNormalizationIT:354` : ce dernier couvre les 3 caractères × 4 champs et les absences d'effets. |
| Deux snapshots, même SHA-256 | `AMBIGUOUS_SHA256`, aucune sélection par fournisseur/date/ordre ; aucun reçu réussi, demande ou tentative. Choisir ensuite l'UUID exact. | `NormalizationReplayRequestService:118` résout globalement ; `StoredSnapshotReplayIT:195` vérifie deux fournisseurs/endpoints et zéro écriture de commande. |
| Payload relu altéré | Avant parsing : vérifier compression `identity`, hash enregistré contre hash attendu, puis hash des octets relus. `FAILED_TERMINAL / PAYLOAD_HASH_MISMATCH`, tentative auditée ; aucune substitution du hash ni normalisation. | `NormalizationReplayExecutionService:131` contrôle avant l'appel au normaliseur ; `StoredSnapshotReplayIT:269` vérifie l'empreinte recalculée, l'échec terminal et l'absence de nouvelle application/observation/rencontre. |
| `neutralVenue=true`, `participantsUnordered` absent | En v3, rejet du payload entier : champ obligatoire, booléen non nul, aucun défaut. Si le champ vaut explicitement false, aucune inversion autorisée. | Validation indépendante dans `CalendarSnapshotParser:154`. `CalendarSnapshotParserTest:138` teste l'absence avec neutralité null ; combinaison exacte true+absence à ajouter si nécessaire. `FixtureIdentityPolicyTest:62` et `CalendarNormalizationIT:1042` couvrent true+false et l'inversion interdite. |
| Ancien snapshot, parseur corrigé | Relire les mêmes octets par le normaliseur existant ; conserver fournisseur, endpoint, réception, connecteur, observedAt et historique. Demande terminale : nouvelle demande et nouvelle clé requises. Nouvelle interprétation distincte de la classification historique ; version ancienne inconnue reste inconnue. Aucun effet canonique garanti : autorité et chronologie restent applicables, notamment STALE/conflit à temps égal. | `CalendarNormalizationIT:99` prouve structurellement l'idempotence avec deux applications mais une observation, sans changer de parseur. `JdbcFixtureObservationStore:25` conserve l'observation ; `NormalizationReplayAttempt` ne porte pas de version du parseur. Preuve ancien→nouveau parseur et attribution de version par interprétation non établies dans les sources consultées. |

Les contrats de référence sont `docs/contracts/calendar-replay-v3.md` et `docs/contracts/stored-snapshot-replay-v1.md`. Compatibilité préservée : v1 rejouable mais non normalisable ; v2 neutralité inconnue et ordre imposé ; v3 attributs explicites. Un champ absent, null et false gardent des sens distincts.

Profils : `replay` pour fichiers/octets sans réseau, DataSource ni Flyway ; rejeu PostgreSQL exclusivement `control-api`. `batch-worker` normalise mais ne revendique aucune demande stockée. `ReplayProfileTest:76` contient les assertions d'absence des composants persistants ; live interdit.

Contrôles exécutés : horodatage, HEAD/status Git et inspection statique des contrats, code et assertions. Chemin inexistant corrigé via `rg --files`. Aucun Maven, test applicatif, réseau, consultation de secrets ou mutation du dépôt ; aucun résultat PASS revendiqué.

Contrôles proposés : test unitaire synthétique true+absence ; scénario avant/après parseur sur mêmes octets, avec empreinte constante, provenance connue/inconnue explicite et historique conservé. Pour une correction effective, exécuter les tests unitaires concernés puis les deux IT PostgreSQL/Testcontainers ciblés ; pas de batterie Windows complète pour cette seule revue.

Seul fichier écrit : cette note. Aucune clarification bloquante ; décision ouverte : comment identifier durablement la version de chaque nouvelle interprétation sans rétroattribuer une version historique.

Fin : 2026-09-04 23:56:18 UTC

