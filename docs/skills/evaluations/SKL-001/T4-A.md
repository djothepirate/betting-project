# T4-A — Revue indépendante normalisation/replay

Début : 2026-09-04 23:44:43 UTC
CONTEXT_READY : 2026-09-04 23:44:57 UTC

## Fiche initiale

- Base : HEAD vérifié `5a8161e0070632f0b0dbbff760194e57ab4e5b1f`, dépôt `C:\Users\geoff\.codex\worktrees\8922\betting-project` ; AGENTS.md lu.
- Objectif : préparer la revue du contrat de normalisation/replay pour cinq cas limites et identifier comportement conforme, preuves existantes et lacunes.
- Périmètre : deux contrats fournis, CalendarAuthorityKey/CalendarAuthorityAssignment et code/tests de domaine, normalisation et replay nécessaires. Revue statique uniquement.
- Invariants : monolithe modulaire ; domaine sans infrastructure ; PostgreSQL source de vérité ; migrations partagées immuables ; effets externes par idempotence/outbox ; replay expurgé hors réseau ; profils control-api, batch-worker et replay, jamais live.
- Prochaine action : lire les contrats et les deux classes de clé/attribution, puis rechercher les preuves ciblées pour les cinq cas.
- Limites : aucun skill, docs/skills, tmp/skl-001, WO CAT-002, rapport tiers, secret, .env, réseau, Maven ou test applicatif ; aucune mutation du dépôt. Historique non établi à partir du code courant. Seule écriture autorisée : présente note.

## Réponse de revue

Les contrats et les assertions consultées concordent sur les quatre premiers cas. Le rejeu après correction du parseur est permis, mais sa provenance logicielle historique et son résultat métier ne peuvent pas être déduits du seul SHA-256.

| Cas | Comportement conforme | Preuve existante et limite |
|---|---|---|
| Valeurs fournisseur littérales `*`, `?`, `%` | Conserver exactement les valeurs runtime, sans expansion. Sans affectation exacte : `UNASSIGNED`, observation/journal/anomalie conservés, aucun canon ni mapping créé. Une affectation configurée contenant ces caractères échoue au chargement. | `CalendarAuthorityKey:21`, `CalendarAuthorityAssignment:16`, résolution par égalité dans `ConfiguredCalendarAuthorityPolicy:42`. `ConfiguredCalendarAuthorityPolicyTest:53`, `ClasspathCalendarAuthorityConfigurationTest:95` et `CalendarNormalizationIT:354` ; ce dernier couvre les trois caractères dans les quatre champs. |
| Deux snapshots, même SHA-256 | `AMBIGUOUS_SHA256`, aucun reçu réussi, demande ou tentative ; sélectionner ensuite l’UUID exact. Aucun départage par fournisseur, endpoint, date ou ordre SQL. | `StoredSnapshotReplayIT:195` vérifie deux fournisseurs/endpoints et l’absence d’écritures. `JdbcStoredRawSnapshotReader:35` recherche globalement jusqu’à deux lignes ; le service refuse plusieurs résultats. |
| Payload relu altéré | Vérifier compression `identity`, empreinte attendue, empreinte stockée et SHA des octets avant parsing. `FAILED_TERMINAL`, `PAYLOAD_HASH_MISMATCH`, tentative auditée, aucune normalisation ; jamais substituer l’empreinte. | `NormalizationReplayExecutionService` vérifie avant l’appel au normaliseur. `StoredSnapshotReplayIT:269` affirme erreur, SHA recalculé, absence de nouvelles applications/corrélations et conservation du nombre d’observations/canons. |
| `neutralVenue=true`, `participantsUnordered` absent | En v3, rejeter le payload entier : aucun défaut implicite ni normalisation partielle. Si « sans » signifie explicitement `false`, le payload est valide mais l’inversion reste interdite. | `CalendarSnapshotParserTest:138` couvre l’absence avec neutralité `null` ; `FixtureIdentityPolicyTest:62` et `CalendarNormalizationIT:1042` couvrent terrain neutre/ordre conservé. `CalendarNormalizationIT:1147` couvre le rejet global d’un type invalide. Le croisement exact `true` + champ absent reste à ajouter si une modification touche cette règle. |
| Ancien snapshot après correction du parseur | Réutiliser les octets et métadonnées originels avec le parseur courant. Nouvelle demande/nouvelle clé si la précédente est terminale ; reprise manuelle possible si non terminale. Conserver l’observation immuable et ajouter les traces d’évaluation. L’autorité utilise `observedAt` : antérieur au tampon devient `STALE`, même instant contradictoire devient conflit ; la date du rejeu n’accorde aucune priorité. | `CalendarNormalizationIT:99` prouve déduplication et deuxième journal ; `:451` prouve chronologie ; `StoredSnapshotReplayIT:160` prouve absence de reprise terminale. Aucun scénario de correction effective du parseur identifié dans ces tests. |

La limite du dernier cas doit rester explicite : `connectorVersion` décrit la collecte, `sourceSchemaVersion` le format et `policyVersion` l’autorité. Les structures de tentative et d’application consultées ne portent aucune version de parseur. L’observation déjà stockée n’est pas réécrite (`JdbcFixtureObservationStore`, conflit ignoré). Une interprétation nouvelle peut donc différer de l’observation ancienne : il faut tester ce croisement et décider comment tracer l’interprétation exécutée, sans attribuer rétroactivement une version supposée. V1 demeure rejouable mais non normalisable ; v2 conserve neutralité inconnue et ordre strict.

Profils : rejeu PostgreSQL uniquement `control-api` ; normalisation catalogue également `batch-worker`, qui ne revendique aucune demande ; `replay` traite fichiers/octets hors réseau, sans DataSource/Flyway (`ReplayProfileTest`). `live` interdit.

Contrôles exécutés : HEAD, lecture AGENTS/contrats/code, recherche ciblée et examen d’assertions ; aucun test exécuté. Validation proposée après correction : tests unitaires concernés, scénario parseur avant/après sur mêmes octets, puis IT ciblées PostgreSQL/Testcontainers ; aucun H2. La revue seule ne justifie pas une suite complète. Aucun fichier du dépôt modifié ; seule cette note est créée. Aucune clarification nécessaire pour préparer la revue ; la traçabilité d’une correction sémantique reste une décision ouverte.

Fin : 2026-09-04 23:47:55 UTC


