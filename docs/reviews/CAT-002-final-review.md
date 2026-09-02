# CAT-002 - Dossier d'acceptation et attestation humaine

## État du dossier

- **Work Order :** `CAT-002`
- **Branche :** `codex/cat-002`
- **HEAD et base d'exécution :** `d8205b0c1efe164c869c7408378fcbf1ee89267d`
- **Référence locale `origin/main` au gel :** `88194d9136611fe44a809d6e1acc91945b6639b9`
- **Écart amont constaté :** 18 commits CI-001 postérieurs à la base ; aucun rebase ou merge n'est autorisé avant la revue et les autorisations Git
- **État :** `AWAITING_HUMAN_REVIEW`
- **Critères avant revue :** 35 sur 36
- **Manifeste :** [`CAT-002-review-scope.sha256`](CAT-002-review-scope.sha256)
- **SHA-256 du manifeste :** `b6153a2ac4fd90a3c993579b58864866c334558820024b3fc5abf536b53d124e`
- **Inventaire figé :** 194 fichiers couverts, dont 36 fichiers suivis modifiés et 158 fichiers non suivis ; 3 fichiers de contrôle exclus ; 197 chemins Git au total dans le candidat gelé.

Le manifeste couvre les octets du worktree Windows de tous les fichiers ajoutés ou modifiés par CAT-002, y compris les fichiers non suivis, à l'exception du manifeste lui-même et des deux fichiers réservés au geste final du porteur : ce dossier d'attestation et `docs/work-orders/CAT-002.md`.

`git diff` seul ne couvre pas les fichiers non suivis. La revue est valide uniquement si chaque chemin du manifeste a été lu dans Eclipse et si le recalcul final produit exactement le même manifeste.

## Résumé du changement à revoir

| Zone | Contenu principal | Risque à examiner |
|---|---|---|
| Migrations V003-V005 | Chronologie et journal d'application, décisions et cycle d'anomalies, demandes et tentatives de rejeu | Backfills honnêtes, contraintes, index et absence de mutation de V001/V002 |
| Normalisation | `observedAt`, autorité, transitions, identité ordonnée ou explicitement non ordonnée | Aucune régression du canon, aucun contrôle promu ou appliqué |
| Concurrence PostgreSQL | Verrou advisory, insertions atomiques, verrous de ligne et compare-and-set | Un seul canon, mapping et résultat logique |
| Décisions humaines | Version attendue, auteur local, justification expurgée, reçu et historique append-only | Aucun écrasement, fuite de secret ou identité fournie par HTTP |
| Anomalies | Projection courante et événements `OPENED`/`OBSERVED`/`RESOLVED`/`REOPENED` | Résolution uniquement après évaluation complète |
| Rejeu stocké | Deux transactions, claim, savepoint, tentative, reprise et corrélations | Preuve intacte, terminalité et absence de double effet |
| API interne | 16 routes `/internal/catalog`, pagination keyset, JSON strict et RFC 9457 | Aucun chemin arbitraire, payload brut, reçu ou donnée secrète exposés |
| Profils et architecture | Ports applicatifs, adaptateurs JDBC/web et écoute sur `127.0.0.1` | Aucun bean interdit sous `batch-worker` ou `replay` |
| Jobs/outbox | SQL déplacé derrière `JobOutboxRepository` | Atomicité, idempotence et rollback inchangés |
| Documentation | Contrats v2/v3, rejeu stocké, API, runbooks et mémoire du dépôt | Comportement décrit conforme au code et aux limites |

## Matrice des 36 critères

| ID | Critère synthétique | État avant revue | Preuve principale |
|---|---|---|---|
| C01 | Activation du Work Order autorisée | Satisfait | décision du porteur du 2026-09-01 |
| C02 | Lot 0 clôturé avant technique | Satisfait | décisions D01-D08 et baseline du lot 0 |
| C03 | Migration additive depuis V002 peuplée | Satisfait | `V003MigrationIT`, puis V004/V005 |
| C04 | Rejeu par UUID ou SHA-256 univoque | Satisfait | `StoredSnapshotReplayIT` |
| C05 | Aucun chemin arbitraire accepté | Satisfait | parseur strict et tests MockMvc |
| C06 | Rejeu répété sans second effet logique | Satisfait | idempotence et terminalité du rejeu |
| C07 | Tentatives et applications historisées | Satisfait | journaux V003/V005 et corrélations |
| C08 | Anomalies consultables avec provenance | Satisfait | port, adaptateur JDBC et routes anomalies |
| C09 | Mappings et décisions consultables | Satisfait | port, adaptateur JDBC et routes mappings |
| C10 | Confirmation suivie d'un rejeu | Satisfait | `MappingDecisionNormalizationIT` |
| C11 | Rejet durable suivi d'un rejeu | Satisfait | scénarios de rejet et `REJECTED_MAPPING` |
| C12 | Décisions concurrentes déterministes | Satisfait | `MappingDecisionConcurrencyIT` |
| C13 | Version attendue obligatoire | Satisfait | compare-and-set et conflits optimistes |
| C14 | Observation ancienne sans mutation | Satisfait | résultat `STALE` |
| C15 | Convergence ancien/récent | Satisfait | scénarios séquentiels et concurrents |
| C16 | Tampon d'autorité persistant | Satisfait | V003, domaine et CAS PostgreSQL |
| C17 | `FINISHED`/`CANCELLED` terminaux | Satisfait | `FixtureChronologyPolicy` |
| C18 | Reprogrammation depuis `POSTPONED` | Satisfait | matrice complète des statuts |
| C19 | Inversion seulement si explicitement non ordonnée | Satisfait | `FixtureIdentityPolicy` |
| C20 | Inversion interdite visible et ordre source conservé | Satisfait | `CalendarNormalizationIT` |
| C21 | Neutralité distincte de l'ordre | Satisfait | contrat v3, modèles et V003 |
| C22 | Compatibilité v1/v2/v3 | Satisfait | parseur, rejeu et migrations |
| C23 | `PRIMARY` admissible peut actualiser | Satisfait | normalisation et tampon d'autorité |
| C24 | `CONTROL` contradictoire n'écrase jamais | Satisfait | `CONTROL_DIVERGENCE` |
| C25 | Aucune promotion implicite | Satisfait | politique fermée `UNASSIGNED` |
| C26 | Autorité contextualisée et versionnée | Satisfait | `CalendarAuthorityKey` et configuration classpath |
| C27 | Normalisations concurrentes sans doublon | Satisfait | `CalendarNormalizationConcurrencyIT` |
| C28 | Redémarrage puis reprise sans perte | Satisfait | `ControlApiRestartIT` et scénarios de claim/reprise |
| C29 | SQL jobs/outbox derrière un port | Satisfait | `JobOutboxRepository` |
| C30 | Atomicité/idempotence jobs/outbox préservées | Satisfait | `PostgreSqlBootstrapIT` |
| C31 | Architecture sans dépendance interdite | Satisfait | règles ArchUnit |
| C32 | PostgreSQL/Testcontainers, jamais H2 | Satisfait | profil d'intégration Maven |
| C33 | Trois profils seulement | Satisfait | tests de profils |
| C34 | Aucun secret/payload privé/SofaScore | Satisfait | scan de secrets et contrôles finaux |
| C35 | Documentation finale intégrée | Satisfait | contrats, runbooks, frontières et mémoire du dépôt |
| C36 | Diff final revu humainement | **À faire par le porteur** | présente attestation liée au manifeste |

## Résultats techniques du candidat

| Contrôle | Résultat |
|---|---|
| Tests standards Windows | 216, zéro échec, erreur ou omission |
| PostgreSQL/Testcontainers Windows | 77, zéro échec, erreur ou omission |
| Validation Windows complète | `BUILD SUCCESS`, code de sortie `0` |
| Scan de secrets | `PASS` |
| `git diff --check` | propre |
| Espaces finaux des fichiers non suivis | aucun |
| Index Git réel | vide |
| V006 | absente |
| Validation Linux complète | `PENDING_PR_CI` ; non simulée par un WSL sans Docker |

## Empreintes des migrations immuables

| Migration | SHA-256 |
|---|---|
| V001 | `d3ab6619dc4b81f7ac6807c06b5f18c3013de442c525701d150fe5d23ee1f090` |
| V002 | `69d9e4ff0415a4fcf395c2ce85fe79c84bc57672daa1c2b72f4b4d46ae516271` |
| V003 | `059b95738deb3c22b90399c6096dab9cd3e2594f8279f9d6ea22ab28dfff9dfd` |
| V004 | `2051f1caf8420d3a36c28becfba47dd6b0827d25b1910d4cf0162916bdec8cd3` |
| V005 | `f38ac525099e3b269b85b19df3ece34d2e0e38d460834b953bbbb8d41507f90f` |

## Limites maintenues

- La configuration d'autorité de production reste vide et fermée par défaut ; aucune référence fournisseur n'est inventée.
- Le `control-api` est interne, sans authentification, lié à `127.0.0.1` et interdit d'exposition avant OPS-001.
- Aucun worker ou poller de rejeu n'est introduit ; la reprise reste manuelle et versionnée.
- Aucun connecteur fournisseur, appel sportif, profil live, cote, valuebet ou placement de pari n'appartient à CAT-002.
- La branche est fondée sur `d8205b0` alors que `origin/main` contient désormais CI-001. L'intégration au `main` courant sera vérifiée par le merge synthétique et les CI de la future PR.
- La validation Linux/Testcontainers n'est pas acquise localement ; elle reste une porte de PR et de fusion.

## Checklist de revue humaine dans Eclipse

- [x] J'ai vérifié que Codex ne modifiait plus le worktree pendant ma revue.
- [x] J'ai lu chaque chemin du manifeste, y compris les fichiers non suivis.
- [x] J'ai revu V003, V004 et V005 ainsi que leurs tests de migration depuis une base peuplée.
- [x] J'ai revu chronologie, autorité, identité, concurrence et mappings.
- [x] J'ai revu décisions humaines, cycle des anomalies, rejeu, savepoints et redémarrage.
- [x] J'ai revu l'API interne, le parsing strict, les projections et l'absence de données sensibles.
- [x] J'ai revu les profils, règles d'architecture, fixtures et documents.
- [x] J'ai rejoué `.\mvnw.cmd verify` depuis Eclipse ou son terminal intégré.
- [x] J'ai rejoué `.\mvnw.cmd -Pintegration verify` depuis Eclipse ou son terminal intégré.
- [x] Le manifeste porte exactement l'empreinte recopiée ci-dessous.

## Attestation réservée au porteur

Ne renseigner cette section qu'après une revue complète et sans modifier un autre fichier du périmètre manifesté.

- **Reviewer :** `djothepirate`
- **Reviewed at :** `2026-09-02T02:04:00+02:00`
- **Manifest SHA-256 :** `b6153a2ac4fd90a3c993579b58864866c334558820024b3fc5abf536b53d124e`
- **Decision :** `APPROVED`
- **Observations :** `Aucune réserve ; diff complet relu ; 216 tests standards et 77 tests PostgreSQL/Testcontainers rejoués avec succès.`

La décision admise pour clôturer la porte est exactement `APPROVED`. Toute réserve ou tout correctif demandé laisse le lot 8 ouvert et impose un nouveau manifeste après correction.

## Modifications finales du Work Order réservées au porteur

Après décision `APPROVED`, modifier uniquement `docs/work-orders/CAT-002.md` et appliquer toutes les évolutions suivantes :

1. passer la version de `1.3-lot8-review-candidate` à `1.4-lot8` ;
2. passer le statut documentaire à `Lot 8 clôturé localement après revue humaine` ;
3. passer l'état d'exécution à `READY_FOR_GIT - LINUX_CI_PENDING` ;
4. remplacer l'autorisation actuelle par une mention précisant que la revue est acquise mais que commit, push, Pull Request et fusion restent non autorisés ;
5. cocher uniquement le critère C36, relatif à la revue humaine ;
6. passer le lot 8 de `ACTIVE - AWAITING_HUMAN_REVIEW` à `COMPLETED LOCALLY` ;
7. remplacer les mentions « 35 sur 36 » par « 36 sur 36 » dans le résultat courant ;
8. ajouter une entrée d'historique `1.4-lot8` avec la date, le reviewer et l'empreinte du manifeste ;
9. conserver explicitement `LINUX_CI_PENDING` et ne jamais écrire « accepté, fusionné et clôturé ».

Après ce geste, Codex vérifiera en lecture seule le manifeste, les deux fichiers de contrôle, les migrations, les secrets, le diff et l'index. Aucune modification du dépôt ne sera effectuée avant une nouvelle autorisation explicite.
