# Runbook — CI GitHub canonique et fabrique GitLab

Ce runbook décrit l'amorçage, la qualification et l'exploitation de la fabrique CI sans confondre
un artefact de build avec un livrable de production.

## État initial requis

- les deux comptes utilisent la 2FA ;
- chaque dépôt GitHub officiel est privé et présent dans
  `ci/betting-project-repositories.yml` ;
- chaque projet GitLab homonyme est privé, vide avant bootstrap et placé sous
  `djothepirate-betting-project` ;
- aucun runner n'est installé sur le VPS de production.

## Ordre d'installation

1. Fusionner et qualifier les workflows sans secret.
2. Créer le projet GitLab manquant `betting-sofascore-local-lab` sans README initial.
3. Laisser `main` et `feature/*` non protégées sur GitLab. Protéger uniquement le motif de branche
   `release/V*`, puis interdire le push direct et le force-push vers les branches de release.
   Protéger séparément le motif de tags `v*` contre toute création non autorisée et tout déplacement ;
   une protection de tag n'ajoute pas de branche protégée.
4. Réserver la fusion de `release/V*` aux Maintainers et configurer le projet en fast-forward sans
   squash, rebase ni suppression de la branche source.
5. Vérifier qu'un runner Linux autorise Docker-in-Docker pour Testcontainers.
6. Faire un bootstrap de `main` depuis une station d'administration de confiance.
7. Comparer `main`, chaque branche feature d'intégration synchronisée et les tags entre GitHub et
   GitLab ; exclure les branches `release/V*`, propres à GitLab, de cette comparaison.
8. Exécuter GitLab CI et vérifier le SHA exact, les rapports JUnit et l'artefact.
9. Mesurer la baseline qualité sur le SHA de référence et activer le ratchet.
10. Préparer le workflow de miroir dans une Pull Request séparée et désactivée.
11. Activer seulement après test d'une référence verte, rouge, déplacée et supprimée.

## Règles du miroir Free-compatible

- une clé d'écriture GitLab distincte par projet ;
- aucune clé partagée entre dépôts ;
- aucun checkout ni exécution du code source dans le job privilégié de miroir ;
- publication du seul SHA encore pointé par `main` ou par une branche d'intégration exacte
  `feature/<TRAIN>` sur GitHub après une CI verte ;
- aucune synchronisation des branches de Work Order
  `feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>` ;
- synchronisation des seuls tags SemVer immuables et protégés par le motif de tags GitLab `v*`,
  jamais des branches GitLab `release/V*` ;
- jamais de force-push sur `main` ni sur `feature/<TRAIN>` ;
- tags SemVer immuables ;
- rotation tous les 90 jours et immédiate en cas de suspicion.

Sur un dépôt privé GitHub Free, un secret de dépôt n'est pas isolé d'un auteur autorisé à ajouter
un workflow. L'activation stricte exige donc soit GitHub Pro avec un environnement limité à
`main`, soit un dépôt contrôleur séparé et réservé à l'administration. Aucun secret de miroir ne
doit être ajouté avant cette décision d'exploitation.

Le cache Maven GitLab reste désactivé : une politique ou une clé définie dans le YAML d'une branche
n'est pas une frontière de confiance. Sa réactivation exige une séparation serveur qualifiée des
caches de refs protégées et non protégées ; `main`, les features d'intégration et les tags ne
doivent jamais lire un cache qu'une ref non protégée peut alimenter.

## Grammaire des trains et des versions

`<TRAIN>` suit exactement l'une des formes suivantes :

```text
Vx.y.z
Vx.y.z-RCnn
Vx.y.z-RCnn-SNAPSHOT
```

Le cœur respecte SemVer sans zéro initial et `nn` vaut exactement `01` à `99`. Les branches
refusent donc `RC00`, `RC1`, `RC001`, `RC100` et l'ancien suffixe `-rc.N`. Le mapping vers Maven et
les tags conserve la décision 2A :

| Train | Version Maven | Tag éventuel |
|---|---|---|
| `V1.2.3` | `1.2.3-SNAPSHOT`, puis `1.2.3` pour la promotion | `v1.2.3` |
| `V1.2.3-RC01` | `1.2.3-rc.1` | `v1.2.3-rc.1` |
| `V1.2.3-RC01-SNAPSHOT` | `1.2.3-rc.1-SNAPSHOT` | aucun |

Le zéro de remplissage n'existe que dans la branche. Un tag `v1.2.3-rc.100` est syntaxiquement
valide selon la décision 2A, mais son packaging échoue explicitement : aucun train autorisé ne peut
matérialiser sa branche canonique. Aucun tag ne porte `SNAPSHOT`.

## Ouverture d'un train et Work Orders

1. Choisir un SHA de `main` qualifié sur GitHub et GitLab ; le noter comme `TRAIN_BASE_SHA`.
2. Créer sur GitLab `release/<TRAIN>` à ce SHA via l'interface ou l'API d'administration, puis
   vérifier que la règle `release/V*` interdit push direct et force-push et réserve la fusion aux
   Maintainers. Cette branche n'est jamais créée ni synchronisée sur GitHub.
3. Créer sur GitHub `feature/<TRAIN>` à ce même SHA, attendre sa CI, puis la synchroniser vers
   GitLab. Vérifier les trois égalités avec `TRAIN_BASE_SHA` avant d'ouvrir les travaux.
4. Pour chaque Work Order, noter le HEAD exact de `feature/<TRAIN>` dans le document puis créer la
    branche `feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>` depuis ce SHA. L'acteur et l'identifiant sont
   en majuscules, `<WORK-ORDER>` suit `<TYPE>-<NNN>` avec `NNN=001..999`, le fichier
   `docs/work-orders/<WORK-ORDER>.md` existe et aucun slug libre n'est ajouté.
5. Ouvrir sur GitHub une Pull Request de cette branche vers `feature/<TRAIN>` du même train. La CI,
   les discussions résolues, la revue humaine et un merge commit sont obligatoires ; squash,
   rebase et push direct vers la cible ne constituent pas une clôture valide.
6. Une suite de Work Orders se clôt par autant de Pull Requests que nécessaire vers cette même
   branche feature. Les branches de Work Order restent sur GitHub et ne sont jamais synchronisées.

La seule exception à cette topologie est le bootstrap historique de CI-004 :
`codex/ci-004-version-branch-workflow` vers `main`, sur la base exacte
`3fb224e9724698324a56b47fe5d943ecd366f197`. Son garde reçoit le SHA de tête, prouve que cette base
en est un ancêtre et que leur merge-base est exactement la base déclarée. Le seul `base.sha` fourni
par GitHub ne suffit pas. Cette exception ne crée aucun précédent. Les anciennes branches
`codex/*`, `human/*` et `hotfix/*` restent en lecture seule ; elles ne sont ni renommées, ni
réutilisées, ni supprimées implicitement.

## Finalisation GitHub et promotion GitLab

1. Fixer sur `feature/<TRAIN>` la version Maven de promotion : stable sans `SNAPSHOT`, ou `rc.N`
   minuscule obtenu par conversion de `RCnn`. Qualifier ce sommet `F`. Le garde de Pull Request
   vérifie cette version avant que la feature puisse entrer dans `main`.
2. Ouvrir la Pull Request GitHub finale `feature/<TRAIN>` vers `main`. Exiger CI Windows/Linux
   verte, discussions résolues, revue humaine et merge commit ; squash et rebase sont interdits.
3. Noter le merge commit `M` produit sur `main` et le qualifier. Vérifier que `F` est un parent de
   `M`, puis avancer normalement `feature/<TRAIN>` de `F` vers `M` par fast-forward. Ne jamais
   force-push. Vérifier que les deux références GitHub valent exactement `M`.
4. Synchroniser `main` et `feature/<TRAIN>` vers GitLab, attendre les pipelines applicables et
   vérifier que les deux références GitLab valent aussi `M`.
5. Ouvrir la Merge Request GitLab `feature/<TRAIN>` vers `release/<TRAIN>`. La source et la cible
   appartiennent au même projet, portent le train complet identique et la cible est protégée. Le
   job extrait explicitement les tags et les références distantes `main`, feature source et release
   cible sur un historique complet. Le garde convertit `RCnn` en `rc.N` pour vérifier la version
   Maven, exige `SHA_PIPELINE == origin/feature/<TRAIN> == origin/main == M`, puis vérifie que la
   release cible est un ancêtre de `M`.
6. Avant fusion, vérifier que la source vaut toujours `M`, que le pipeline est vert, que toutes les
   discussions sont résolues et que GitLab annonce un fast-forward sans squash, rebase ni
   suppression de la source. Toute divergence ou modification du SHA arrête la promotion.
7. Après fusion, vérifier que `main`, `feature/<TRAIN>` et `release/<TRAIN>` valent exactement `M`.
   Conserver la branche release comme preuve durable et ne jamais y pousser directement.
8. Pour un train non-SNAPSHOT, créer après nouvelle revue humaine le tag GitHub correspondant
   (`vX.Y.Z` ou `vX.Y.Z-rc.N`) sur `M`, attendre sa CI verte, puis synchroniser ce tag immuable vers
   GitLab. Le pipeline GitLab refuse le bundle si le tag, `main`, `feature/<TRAIN>` et
   `release/<TRAIN>` ne pointent pas tous vers le commit extrait.
9. Vérifier le bundle, ses empreintes et sa provenance. Si une branche release est déjà scellée par
   son tag, toute correction exige un nouveau train et une nouvelle version.

Le workflow donne la priorité au pipeline `merge_request_event` lorsqu'une Merge Request est
ouverte. Pour le même changement, le pipeline de branche issu de `push` est alors supprimé via
`CI_OPEN_MERGE_REQUESTS`, notamment lorsque la source est `feature/<TRAIN>`. Le filtre reste limité
à la source `push` afin de conserver les pipelines de tag, les lancements manuels et les
déclenchements par API ou pipeline parent. Le pipeline de Merge Request qualifie alors le SHA ; il
ne publie pas de snapshot durable, réservé à un pipeline `push` de la branche d'intégration exacte.

Une branche de release déjà associée à son tag est scellée. Toute nouvelle Merge Request vers cette
branche doit échouer ; une correction crée une nouvelle version. Les tags complets doivent être
présents dans le checkout du pipeline de Merge Request afin que ce refus reste bloquant.

## Snapshots et releases

- un snapshot durable est produit uniquement par un pipeline `push` de la branche d'intégration
  exacte `feature/<TRAIN>`, avec l'IID du pipeline et le SHA court ;
- les bundles de `main`, d'une branche de Work Order ou d'une Pull Request restent éphémères et ne
  sont jamais téléversés comme snapshots durables ;
- le bundle d'une Pull Request référence le commit de fusion synthétique effectivement testé, reste
  éphémère dans le workspace du runner et n'est jamais téléversé ni promu en production ;
- une préparation peut déjà porter la version Maven finale : sans tag, le bundle reste un snapshot
  non promouvable, suffixé par l'IID et le SHA et doté d'un `source.tag` vide ;
- le tag est créé sur le SHA promu seulement après les CI de `main`, de la feature d'intégration,
  de la Merge Request GitLab et de `release/<TRAIN>`, puis après revue humaine ;
- un tag `vX.Y.Z` ou `vX.Y.Z-rc.N` est accepté seulement s'il désigne le commit extrait, correspond
  exactement aux sommets de `origin/main`, `origin/feature/<TRAIN>` et, sur GitLab,
  `origin/release/<TRAIN>`, avec une version Maven identique et non SNAPSHOT ; le motif de tags
  GitLab `v*` doit être protégé indépendamment des branches ;
- le bundle contient l'artefact, un SBOM sans numéro de série et généré deux fois à empreinte
  identique, la provenance et `SHA256SUMS` ;
- la provenance porte `artifact.version` : l'IID reste dans la version des snapshots, mais aucun
  identifiant propre à GitHub ou GitLab n'entre dans le payload immuable d'un tag ;
- GitLab est l'unique producteur d'une release taguée ; GitHub vérifie le tag sous Windows et Linux
  mais ne republie pas le bundle avec une seconde chaîne d'outils ;
- un tag et son artefact sont immuables ; une correction crée une nouvelle version ;
- le VPS reçoit un artefact existant et vérifié, jamais un rebuild.

Le bundle de CI-001 porte encore `vps.deployable=false`. Un Work Order d'exploitation doit ajouter
et qualifier l'image OCI, la configuration, la migration, le healthcheck, le rollback et la
restauration avant de changer ce statut.

## Dependency-Check et alimentation NVD

Le job `security:dependencies` alimente sa base depuis le flux JSON 2.0 public officiel :

```text
https://nvd.nist.gov/feeds/json/cve/2.0/nvdcve-2.0-{0}.json.gz
```

Cette source évite l'API NVD et sa limite anonyme. Le lanceur `ci/run-dependency-check.sh` ne
transmet donc jamais `nvdApiKey` ni `nvdApiKeyEnvironmentVariable`, que `NVD_API_KEY` soit absente,
vide ou accidentellement présente dans l'environnement. Aucune variable GitLab n'est requise et
aucune clé n'entre dans le dépôt ou les arguments Maven.

La base est construite dans `.m2/dependency-check-data/` pendant le job, puis détruite avec son
espace de travail. `security:dependencies` impose `cache: []` : il ne télécharge et ne publie aucun
cache GitLab. Une clé incluant `CI_COMMIT_REF_SLUG` ne suffit pas, car une branche contrôle son YAML
et peut choisir la clé qu'elle alimente ; la séparation GitLab entre refs protégées et non protégées
ne protège pas non plus `main` tant qu'elle reste volontairement non protégée. Le premier chargement
observé par CI-002 a duré environ sept minutes, ce qui reste compatible avec le timeout de 60 minutes.
Le flux demeure la source de vérité et `failOnError=true` interdit de considérer un échec de mise à
jour comme un scan réussi. Un cache ne pourra être réintroduit qu'avec une frontière imposée côté
serveur ou un feed interne en lecture seule dont les branches ne contrôlent pas la publication.

Si le projet abandonne un jour le flux au profit de l'API, ce changement exige un Work Order et une
Pull Request dédiés. La clé devra alors être créée directement dans l'interface GitLab comme
variable de projet masquée, cachée si l'option existe, protégée et sans développement des
références ; sa valeur ne devra jamais être copiée dans un ticket, un journal ou le dépôt. Une
variable protégée étant indisponible sur `main` non protégée selon ADR-008, ce changement devra aussi
résoudre explicitement cette frontière d'exécution avant activation.

Le ratchet de CI-002 bloque à CVSS 7 après établissement de la baseline. Une exception ne peut
viser qu'une dépendance et une vulnérabilité exactes, doit expliquer le risque accepté et porte une
échéance. Une règle élargie, sans échéance ou devenue inutilisée fait échouer la qualification.
Dependency-Check `13.0.0` reste interdit à cause de sa régression sur la clé absente ; le passage à
`13.0.1` ou ultérieur nécessite une Pull Request dédiée et une reconstruction complète de la base.

## Onboarding d'un futur dépôt

Une Pull Request ajoute d'abord le dépôt au registre avec sa classification, ses commandes de test
et son SemVer. La revue humaine matérialise son entrée officielle. Le projet GitLab, la CI sur le
même SHA, le miroir et les protections sont ensuite obligatoires ; un onboarding partiel reste en
échec et n'autorise aucune release.
