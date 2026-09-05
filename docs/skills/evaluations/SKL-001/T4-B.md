# T4-B — Revue indépendante normalisation/replay

Début : 2026-09-04 23:47:28 UTC.

## Fiche initiale

- Base fournie : `5a8161e0070632f0b0dbbff760194e57ab4e5b1f`.
- Objectif : comportement conforme et preuves existantes/manquantes des cinq cas.
- Périmètre : contrats calendar-replay-v3 et stored-snapshot-replay-v1, clés/assignations d’autorité, normalisation et tests correspondants.
- Invariants : identité canonique et autorité PRIMARY/CONTROL ; distinguer littéraux source et configuration ; ordre des participants ; octets/provenance conservés, intégrité avant parsing ; aucune provenance historique inventée ; replay hors réseau.
- Prochaine action : lire contrats, classes d’autorité et preuves ciblées.
- Limite : revue statique, sans Maven/tests applicatifs, réseau, mutation Git ni secrets. Seul ce rapport est écrit. Base initialement non vérifiée.
- Skill lu : `tmp/skl-001/candidates/bp-data-contract-replay/SKILL.md`, après `AGENTS.md`.

CONTEXT_READY : 2026-09-04 23:47:54 UTC.

## Réponse

Règles explicites pour les quatre premiers cas ; preuve supplémentaire nécessaire après correction du parseur. HEAD vérifié conforme.

| Cas | Comportement conforme | Preuve présente / manquante |
|---|---|---|
| Littéraux fournisseur `*`, `?`, `%` | `CalendarAuthorityKey` conserve les valeurs exactes. Aucun motif implicite : absence d’affectation exacte → `UNASSIGNED`, observation/journal/anomalie conservés, aucun canon ni mapping créé. `CalendarAuthorityAssignment` interdit ces caractères dans les affectations configurées. | Code des deux classes et résolution par `Map.getOrDefault`. `ConfiguredCalendarAuthorityPolicyTest:53`, `ClasspathCalendarAuthorityConfigurationTest:95` ; `CalendarNormalizationIT:354` vérifie les trois caractères dans les quatre champs, leur conservation et l’absence d’effets canoniques. Tests présents, non exécutés. |
| Deux snapshots, même SHA-256 | Recherche globalement ambiguë → `AMBIGUOUS_SHA256` ; aucun reçu, demande ou tentative. Aucun départage par fournisseur/date/ordre. Sélectionner explicitement l’UUID voulu. | `NormalizationReplayRequestServiceTest:119` et `StoredSnapshotReplayIT:195` ; ce dernier utilise deux fournisseurs/endpoints et vérifie les comptes nuls. |
| Payload relu altéré | Avant parsing, contrôler compression `identity`, hash enregistré contre attendu, puis hash recalculé des octets. Échec terminal audité `PAYLOAD_HASH_MISMATCH`, empreintes conservées, aucun nouvel effet canonique ; ne jamais remplacer silencieusement le hash attendu. | `NormalizationReplayExecutionService` place le contrôle avant le normaliseur. `StoredSnapshotReplayIT:269` vérifie état, code, hash réel, aucune application/corrélation ajoutée. Une assertion directe « parseur jamais appelé » renforcerait la preuve ; elle n’est pas démontrée par ce test. |
| `neutralVenue=true`, sans `participantsUnordered` | En v3, champ absent → rejet du payload entier, sans défaut ni normalisation partielle. Si « sans » signifie `false`, payload valide mais ordre strict : neutralité seule n’autorise aucune inversion. `null` reste invalide pour `participantsUnordered`. | `CalendarSnapshotParser.requireBoolean` ; test d’absence `CalendarSnapshotParserTest:138` avec neutralité `null`. Cas exact `true` + champ absent à ajouter. Non-inversion : `FixtureIdentityPolicyTest:62` et `CalendarNormalizationIT:1042` (`PARTICIPANT_ORDER_CONFLICT`). |
| Ancien snapshot après correction du parseur | Réinterpréter les mêmes octets vérifiés par le chemin existant ; conserver provenance, classification/observation historiques et temps source. Une demande terminale reste terminale : nouvelle évaluation via nouvelle clé d’idempotence. Aucun doublon canonique, nouvel historique d’évaluation. Appliquer l’autorité et `observedAt` : ancienneté → `STALE` si un primaire plus récent existe, sans rajeunissement artificiel. | `StoredSnapshotReplayIT:126` vérifie une observation/canon et deux applications ; cela ne teste pas un changement de parseur. `NormalizationReplayAttempt` n’enregistre pas sa version ; `FixtureApplicationLog.policyVersion` désigne la politique d’autorité. Test avant/après correction et provenance du parseur manquants dans le périmètre inspecté. |

La version historique du parseur doit rester inconnue si aucune preuve contemporaine ne l’établit. Une classification ultérieure doit être identifiée comme telle ; la version de schéma ou de connecteur ne permet pas de la reconstruire. En compatibilité, v1 reste rejouable mais non normalisable ; v2 conserve neutralité inconnue et participants ordonnés.

Profils applicables : `control-api` seul pour le rejeu PostgreSQL ; `control-api` et `batch-worker` pour la normalisation. `replay` traite fichiers/octets hors réseau, sans DataSource/Flyway ; `batch-worker` ne revendique aucune demande stockée. Aucun profil live.

Contrôles exécutés : lectures ciblées, recherches `rg`, vérification `git rev-parse HEAD`. Aucun test applicatif, Maven ou réseau exécuté. Validation proposée : tests unitaires ciblés d’autorité, parsing, identité et sélection ; puis `CalendarNormalizationIT` et `StoredSnapshotReplayIT` sous `-Pintegration` avec PostgreSQL/Testcontainers. Ajouter seulement les preuves manquantes ci-dessus, avec fixtures synthétiques hors réseau. Un parsing réussi ne prouve pas l’exactitude métier externe.

Seul fichier écrit : ce rapport. Aucun changement applicatif ni Work Order créé. Aucune clarification nécessaire pour les règles ; pour attester une correction particulière, il manque le changement concret, ses versions prouvées et son payload expurgé.

Fin : 2026-09-04 23:51:07 UTC.
