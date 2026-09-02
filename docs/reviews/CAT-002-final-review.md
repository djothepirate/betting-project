# CAT-002 - Dossier d'acceptation et attestation humaine

## État du dossier

- **Work Order :** `CAT-002`
- **Branche :** `codex/cat-002-catalog`
- **HEAD publié de départ :** `92c98f2de02ff1457c6718bb12a47cc78d0028af`
- **Base historique d'exécution :** `d8205b0c1efe164c869c7408378fcbf1ee89267d`
- **Base `main` de la Pull Request #8 :** `88194d9136611fe44a809d6e1acc91945b6639b9`
- **État :** `APPROVED - READY_FOR_GIT - LINUX_CI_PENDING`
- **Critères après nouvelle revue :** 36 sur 36
- **Manifeste correctif :** [`CAT-002-review-scope-p2.sha256`](CAT-002-review-scope-p2.sha256)
- **SHA-256 du manifeste correctif :** `b26a3d2b80cb46ea135e7cef6d3342ec3f04aee24b81936a6866310d0a0f3b46`
- **Inventaire correctif figé :** 195 fichiers couverts, dont 159 ajoutés et 36 modifiés par rapport à la base historique ; 3 fichiers de contrôle exclus ; 198 chemins CAT-002 au total avec le nouveau manifeste.
- **Manifeste initial historique :** [`CAT-002-review-scope.sha256`](CAT-002-review-scope.sha256), SHA-256 `b6153a2ac4fd90a3c993579b58864866c334558820024b3fc5abf536b53d124e`

Le manifeste correctif couvre les octets du worktree Windows de tous les fichiers ajoutés ou modifiés par CAT-002 par rapport à la base historique, y compris l'ancien manifeste et les fichiers du correctif P2. Il exclut uniquement le nouveau manifeste lui-même et les deux fichiers réservés au geste final du porteur : ce dossier d'attestation et `docs/work-orders/CAT-002.md`.

La première attestation ne couvre pas le correctif P2 et ne peut pas être réutilisée. La nouvelle revue est valide uniquement si chaque chemin du manifeste correctif a été lu dans Eclipse et si le recalcul final produit exactement le même manifeste.

## Motif de la nouvelle revue

La revue de la Pull Request `#8` a confirmé la remarque P2 `discussion_r3911480832`. Une validation placée dans `CalendarAuthorityKey` rejetait les caractères fournisseur runtime `*`, `?` et `%` avant le traitement fermé `UNASSIGNED`. Le correctif les conserve comme littéraux exacts dans la clé runtime et déplace l'interdiction vers `CalendarAuthorityAssignment`, frontière des affectations de configuration. Douze scénarios PostgreSQL couvrent les trois caractères dans le fournisseur, la référence de compétition, la saison et la phase ; ils exigent la conservation du snapshot, de l'observation, du journal et de l'anomalie, sans création canonique.

## Résumé du changement à revoir

| Zone | Contenu principal | Risque à examiner |
|---|---|---|
| Migrations V003-V005 | Chronologie et journal d'application, décisions et cycle d'anomalies, demandes et tentatives de rejeu | Backfills honnêtes, contraintes, index et absence de mutation de V001/V002 |
| Normalisation | `observedAt`, autorité, transitions, identité ordonnée ou explicitement non ordonnée | Aucune régression du canon, aucun contrôle promu ou appliqué |
| Correctif P2 d'autorité | Valeurs runtime littérales et affectations de configuration sans joker | `UNASSIGNED` conserve toute la preuve sans créer de référentiel ni de canon |
| Concurrence PostgreSQL | Verrou advisory, insertions atomiques, verrous de ligne et compare-and-set | Un seul canon, mapping et résultat logique |
| Décisions humaines | Version attendue, auteur local, justification expurgée, reçu et historique append-only | Aucun écrasement, fuite de secret ou identité fournie par HTTP |
| Anomalies | Projection courante et événements `OPENED`/`OBSERVED`/`RESOLVED`/`REOPENED` | Résolution uniquement après évaluation complète |
| Rejeu stocké | Deux transactions, claim, savepoint, tentative, reprise et corrélations | Preuve intacte, terminalité et absence de double effet |
| API interne | 16 routes `/internal/catalog`, pagination keyset, JSON strict et RFC 9457 | Aucun chemin arbitraire, payload brut, reçu ou donnée secrète exposés |
| Profils et architecture | Ports applicatifs, adaptateurs JDBC/web et écoute sur `127.0.0.1` | Aucun bean interdit sous `batch-worker` ou `replay` |
| Jobs/outbox | SQL déplacé derrière `JobOutboxRepository` | Atomicité, idempotence et rollback inchangés |
| Documentation | Contrats v2/v3, rejeu stocké, API, runbooks et mémoire du dépôt | Comportement décrit conforme au code et aux limites |

## Matrice des 36 critères

| ID | Critère synthétique | État après revue | Preuve principale |
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
| C25 | Aucune promotion implicite | Satisfait | politique fermée `UNASSIGNED` et douze scénarios runtime `*`/`?`/`%` |
| C26 | Autorité contextualisée et versionnée | Satisfait | clé runtime exacte et affectations classpath sans joker |
| C27 | Normalisations concurrentes sans doublon | Satisfait | `CalendarNormalizationConcurrencyIT` |
| C28 | Redémarrage puis reprise sans perte | Satisfait | `ControlApiRestartIT` et scénarios de claim/reprise |
| C29 | SQL jobs/outbox derrière un port | Satisfait | `JobOutboxRepository` |
| C30 | Atomicité/idempotence jobs/outbox préservées | Satisfait | `PostgreSqlBootstrapIT` |
| C31 | Architecture sans dépendance interdite | Satisfait | règles ArchUnit |
| C32 | PostgreSQL/Testcontainers, jamais H2 | Satisfait | profil d'intégration Maven |
| C33 | Trois profils seulement | Satisfait | tests de profils |
| C34 | Aucun secret/payload privé/SofaScore | Satisfait | scan de secrets et contrôles finaux |
| C35 | Documentation finale intégrée | Satisfait | contrats, runbooks, frontières et mémoire du dépôt |
| C36 | Diff correctif final revu humainement | Satisfait | attestation corrective `APPROVED` de `djothepirate`, liée au manifeste P2 `b26a3d2…f3b46` |

## Résultats techniques du candidat

| Contrôle | Résultat |
|---|---|
| Tests standards Windows | 219, zéro échec, erreur ou omission |
| PostgreSQL/Testcontainers Windows | 89, zéro échec, erreur ou omission |
| Validation Windows complète | `BUILD SUCCESS`, code de sortie `0` |
| Scan de secrets | `PASS` |
| `git diff --check` | propre |
| Espaces finaux des fichiers du périmètre | aucun |
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
- La branche reste historiquement fondée sur `d8205b0` et la Pull Request `#8` cible `main` à partir de `88194d9`. Les premières CI du commit initial étaient vertes, mais ne couvrent pas le correctif P2.
- La validation Linux/Testcontainers du correctif n'est pas acquise localement ; elle devra être rejouée par la CI de la PR après un éventuel push autorisé.

## Checklist de revue humaine dans Eclipse

- [x] J'ai vérifié que Codex ne modifiait plus le worktree pendant ma revue corrective.
- [x] J'ai lu chaque chemin du manifeste correctif, y compris les cinq fichiers directement touchés par la P2.
- [x] J'ai revu V003, V004 et V005 ainsi que leurs tests de migration depuis une base peuplée.
- [x] J'ai revu chronologie, autorité, identité, concurrence et mappings.
- [x] J'ai vérifié la distinction entre valeurs runtime littérales et jokers interdits dans la configuration.
- [x] J'ai revu décisions humaines, cycle des anomalies, rejeu, savepoints et redémarrage.
- [x] J'ai revu l'API interne, le parsing strict, les projections et l'absence de données sensibles.
- [x] J'ai revu les profils, règles d'architecture, fixtures et documents.
- [x] J'ai rejoué `.\mvnw.cmd verify` depuis Eclipse ou son terminal intégré.
- [x] J'ai rejoué `.\mvnw.cmd -Pintegration verify` depuis Eclipse ou son terminal intégré.
- [x] Le manifeste correctif porte exactement l'empreinte recopiée ci-dessous.

## Attestation corrective réservée au porteur

Ne renseigner cette section qu'après une revue complète et sans modifier un autre fichier du périmètre manifesté.

- **Reviewer :** `djothepirate`
- **Reviewed at :** `2026-09-02T09:40:08+02:00`
- **Manifest SHA-256 :** `b26a3d2b80cb46ea135e7cef6d3342ec3f04aee24b81936a6866310d0a0f3b46`
- **Decision :** `APPROVED`
- **Observations :** `Périmètre b26a3d2…f3b46 approuvé conformément à la checklist complète ; 219 tests standards et 89 tests PostgreSQL/Testcontainers validés.`

La décision admise pour refermer la porte est exactement `APPROVED`. Toute réserve ou tout correctif demandé laisse le lot 8 ouvert et impose un nouveau manifeste après correction.

### Attestation initiale conservée comme historique

- **Reviewer :** `djothepirate`
- **Reviewed at :** `2026-09-02T02:04:00+02:00`
- **Manifest SHA-256 :** `b6153a2ac4fd90a3c993579b58864866c334558820024b3fc5abf536b53d124e`
- **External fingerprint :** `74f274594a8d89d0b803dc430263b7c6475f50d6c72f145d408ddd71b922cdd2`
- **Decision :** `APPROVED - SUPERSEDED_BY_PR8_P2`
- **Observations :** `Diff initial relu ; 216 tests standards et 77 tests PostgreSQL/Testcontainers rejoués avec succès. Cette attestation ne couvre pas le correctif P2.`

## Enregistrement de la seconde porte de revue

La décision corrective `APPROVED` a été donnée explicitement par le porteur le 2026-09-02. Seuls ce dossier d'attestation et `docs/work-orders/CAT-002.md`, tous deux exclus du manifeste, sont modifiés après le gel. Les conséquences enregistrées sont :

1. le Work Order passe de `1.6-lot8-p2-review-candidate` à `1.7-lot8-p2` ;
2. le lot 8 passe à `COMPLETED LOCALLY` et le critère C36 est fermé ;
3. l'état devient `READY_FOR_GIT - LINUX_CI_PENDING`, sans déclarer CAT-002 accepté, fusionné ou clôturé globalement ;
4. la même demande autorise le commit correctif et son push sur `codex/cat-002-catalog`, afin de mettre à jour la Pull Request `#8` ;
5. les nouvelles CI Windows et Linux/Testcontainers, la résolution de la discussion P2 et la fusion restent des portes ultérieures.

Avant le commit, Codex revérifie en lecture seule le manifeste correctif, les deux fichiers de contrôle, les migrations, les secrets, le diff et l'index, puis calcule une nouvelle empreinte externe finale. L'autorisation actuelle couvre le commit et le push, mais aucune fusion ni résolution distante de discussion.
