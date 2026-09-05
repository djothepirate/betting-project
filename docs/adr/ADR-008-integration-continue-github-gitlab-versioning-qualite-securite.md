# ADR-008 — Intégration continue, versionnement, qualité et sécurité

- **Statut :** Accepté
- **Date :** 2026-09-01
- **Décideur :** Porteur du Betting Project
- **Portée :** Tous les dépôts officiellement rattachés au Betting Project
- **Choix validés :** 1A, 2A et 3A
- **Work Order d'application :** CI-001
- **Amendement historique :** 2026-09-02 — promotion GitLab de `main` vers
  `release/<SemVer>`, remplacée par l'amendement CI-004 ci-dessous
- **Amendement :** 2026-09-02 — flux JSON 2.0 public NVD sans cache inter-pipelines, corrigé par CI-003
- **Amendement :** 2026-09-05 — trains feature/release versionnés et gouvernance des Work Orders, appliqués par CI-004

## Contexte

Le code est actuellement réparti entre deux dépôts GitHub privés :

- `djothepirate/betting-project` ;
- `djothepirate/betting-sofascore-local-lab`.

Le groupe GitLab privé `djothepirate-betting-project` doit fournir la fabrique CI et conserver les
artefacts. GitLab Free ne fournit ni le pipeline natif pour dépôt externe ni le pull mirror natif.
Une synchronisation explicite est donc nécessaire, sans transformer GitLab en seconde source de
vérité.

Le dépôt principal vise une future production sur VPS. Le Local Lab reste expérimental, local et
non critique ; son intégration continue ne constitue jamais une approbation de production.

## Décision

### 1. Dépôts et responsabilités

GitHub est l'unique source canonique du code, des branches de Work Order, de `main`, des branches
d'intégration `feature/<TRAIN>`, des Pull Requests et des tags. Chaque dépôt officiel possède un
projet GitLab privé homonyme sous `djothepirate-betting-project`. GitLab reçoit les SHA qualifiés de
`main` et de la branche feature d'intégration exacte, exécute la CI, conserve les artefacts et porte
les références de promotion. Aucun développement général n'y est autorisé ; les seules Merge
Requests GitLab admises promeuvent `feature/<TRAIN>` vers `release/<TRAIN>`.

Le registre `ci/betting-project-repositories.yml` matérialise le périmètre officiel. Un nouveau
dépôt entre dans le Betting Project uniquement après une Pull Request de ce registre approuvée par
le porteur. Son admission n'est terminée qu'après création de son projet GitLab, synchronisation du
SHA exact, pipeline vert, classification de livraison et configuration des protections.

La synchronisation est unidirectionnelle, GitHub vers GitLab. Elle publie uniquement `main`, une
branche d'intégration exacte `feature/<TRAIN>` ou un tag SemVer immuable dont la CI GitHub est
verte ; une branche de Work Order n'est jamais synchronisée. Elle est préparée dans un lot distinct,
désactivé par défaut, puis activée seulement après création des projets, clés par dépôt, protections
et test de concordance des refs. Les branches `release/V*` sont propres à GitLab et ne sont jamais
recopiées vers GitHub.

### 2. Branches

Un train de branche suit exactement l'une des trois formes suivantes :

```text
Vx.y.z
Vx.y.z-RCnn
Vx.y.z-RCnn-SNAPSHOT
```

Le cœur `x.y.z` respecte SemVer sans zéro initial. `nn` contient exactement deux chiffres de `01`
à `99`. `RC00`, `RC1`, `RC001`, `RC100` et l'ancien suffixe de branche `-rc.N` sont refusés. La
notation de branche est volontairement distincte de Maven et des tags : `RC01` correspond à
`rc.1`, et `RC99` à `rc.99`.

Au début d'un train, la branche GitLab `release/<TRAIN>` et la branche GitHub
`feature/<TRAIN>` partent du même SHA qualifié de `main`. La release est créée sur GitLab sans push
direct et protégée immédiatement. La branche feature est canonique sur GitHub puis synchronisée à
l'identique vers GitLab. Tout nouveau Work Order part du HEAD exact de cette branche feature ; son
document enregistre ce SHA avant les travaux. Sa branche suit :

```text
feature/<TRAIN>-(CODEX|HUMAN)-<WORK-ORDER>
```

`CODEX` et `HUMAN` sont les seuls acteurs admis. `<WORK-ORDER>` reprend l'identifiant versionné en
majuscules sous la forme `<TYPE>-<NNN>`, avec `TYPE` en lettres et `NNN` compris entre `001` et
`999`, sans slug ajouté, par exemple `feature/V0.1.0-CODEX-CI-005`. Son document
`docs/work-orders/<WORK-ORDER>.md` existe sur la branche. Une Pull Request de Work Order cible
exclusivement `feature/<TRAIN>` de même train. La clôture d'un Work Order ou d'une suite de Work
Orders résulte des Pull Requests correspondantes, avec CI verte, discussions résolues, revue
humaine et merge commit ; squash, rebase et push direct vers la branche feature cible sont
interdits par la procédure.

Une fois le train prêt, une Pull Request GitHub finale fusionne `feature/<TRAIN>` dans `main` par
merge commit. Le garde vérifie avant fusion que la version Maven correspond exactement au train ;
un train stable porte déjà sa version finale sans `SNAPSHOT`. Après qualification de ce merge
commit `M`, la branche feature avance de son ancien
sommet vers `M` par fast-forward normal, jamais par force-push. Les références GitHub `main` et
`feature/<TRAIN>` désignent alors le même SHA `M` et sont synchronisées vers GitLab. La seule Merge
Request GitLab admise est ensuite `feature/<TRAIN>` vers `release/<TRAIN>` du même train.

Le motif GitLab `release/V*` est le seul motif de branche protégé : seuls les Maintainers peuvent y
fusionner une Merge Request, aucun acteur ne peut y pousser directement et le force-push reste
désactivé. `main` et `feature/*` restent non protégées afin de recevoir leur copie contrôlée. Une
branche release n'existe jamais sur GitHub. Les dépôts GitHub imposent les merge commits et
désactivent squash et rebase ; l'absence de protection des branches autres que release ne transforme
pas un push direct en voie de clôture autorisée.

Le motif de tags GitLab `v*` est protégé séparément : seuls les Maintainers ou l'identité de
synchronisation explicitement autorisée peuvent créer un tag de release, et aucun tag existant ne
peut être déplacé. Cette protection de tag ne crée aucune branche protégée supplémentaire et ne
remet donc pas en cause l'unicité du motif de branche protégé `release/V*`.

Le projet GitLab impose une fusion fast-forward, interdit squash et rebase et conserve la branche
source feature, afin que la release pointe exactement le SHA commun issu de GitHub. Toute
impossibilité de fast-forward ou tout changement inattendu du SHA source arrête la promotion. Le
pipeline refuse les Merge Requests issues d'un fork, une cible non protégée, des trains source et
cible différents, une version Maven différente du train converti et toute nouvelle modification
d'une branche de release déjà scellée par son tag. Sur un historique complet et après extraction
explicite des références distantes, il exige aussi que le SHA du pipeline, le sommet de la feature
source et `origin/main` soient le même commit `M`, puis que la release cible soit un ancêtre de `M`.

Les branches historiques `codex/*`, `human/*` et `hotfix/*` restent en lecture seule sans renommage,
réutilisation ou suppression implicite. Une seule exception de bootstrap est admise sous l'ancienne
convention : `codex/ci-004-version-branch-workflow` vers `main`, avec la base exacte
`3fb224e9724698324a56b47fe5d943ecd366f197`. Le garde reçoit également le SHA de tête, vérifie que
cette base en est un ancêtre et que leur merge-base est exactement la base déclarée ; le seul champ
GitHub indiquant le sommet courant de `main` n'est pas une preuve de l'origine de la branche.

Lorsqu'une branche possède une Merge Request ouverte, le pipeline `merge_request_event` est la
qualification de référence et le pipeline `push` redondant de cette branche est supprimé. Cette
déduplication vise uniquement les événements `push` : elle ne bloque ni les tags, ni un lancement
manuel, ni un pipeline déclenché par API ou par un autre pipeline.

### 3. Versionnement et immutabilité

Chaque dépôt suit SemVer indépendamment des autres :

- développement Maven : `X.Y.Z-SNAPSHOT` ;
- release candidate facultative : tag `vX.Y.Z-rc.N` ;
- release : tag immuable `vX.Y.Z` et version Maven exacte `X.Y.Z` ;
- snapshot distribué : `X.Y.Z-snapshot.p<id-pipeline>.g<sha-court>`.

Le mapping entre train de branche et version Maven est déterministe :

| Train de branche | Version Maven admise | Tag éventuel |
|---|---|---|
| `V1.2.3` | `1.2.3-SNAPSHOT` pendant le développement, puis `1.2.3` pour la promotion | `v1.2.3` |
| `V1.2.3-RC01` | `1.2.3-rc.1` | `v1.2.3-rc.1` |
| `V1.2.3-RC01-SNAPSHOT` | `1.2.3-rc.1-SNAPSHOT` | aucun |

Le zéro de remplissage appartient uniquement au nom de branche. Les tags conservent la décision
2A avec `rc.N` minuscule et `N` sans zéro initial. Un tag `rc.100` reste syntaxiquement SemVer, mais
sa publication est refusée explicitement car aucun train de branche autorisé au-delà de `RC99` ne
peut établir les références canoniques communes. Un tag portant `SNAPSHOT` est toujours interdit.

Un snapshot durable est conservé uniquement depuis un pipeline `push` de la branche feature
d'intégration exacte. Les bundles produits depuis une branche de Work Order, le merge synthétique
d'une Pull Request ou `main` restent éphémères. Avant conservation, la CI vérifie que la version
Maven correspond au train selon la table ci-dessus. La seule présence d'un snapshot ne constitue
ni une approbation de production, ni une autorisation de déploiement.

Un tag de release doit viser le SHA commun de `main`, de `feature/<TRAIN>` sur GitHub et de
`release/<TRAIN>` sur GitLab, correspondre exactement à la version Maven convertie puis ne jamais
être déplacé. Un artefact est construit une fois, identifié par SHA-256, accompagné d'un SBOM
CycloneDX et d'une provenance. Une provenance de release exclut tout identifiant de run ou de
forge ; elle porte la version d'artefact déterminée par le tag. La production promeut ces mêmes
octets ; aucun rebuild n'est autorisé sur le VPS. GitHub reste l'autorité de la référence de tag et
compile puis teste cette ref. Après synchronisation du tag exact, GitLab est l'unique producteur et
l'autorité de publication du bundle tagué.

Après création du tag associé, la branche `release/<TRAIN>` est scellée : aucune nouvelle Merge
Request ne peut la faire avancer. Une correction passe nécessairement par une nouvelle version et
un nouveau train de branches.

La préparation finale fixe la version Maven non-SNAPSHOT avant la Pull Request
`feature/<TRAIN>` vers `main`. Sans tag, ses builds restent des snapshots non promouvables nommés
`X.Y.Z[-rc.N]-snapshot.p<id-pipeline>.g<sha-court>` et portent un `source.tag` vide. Après le merge
commit GitHub vert, le fast-forward de la branche feature et la synchronisation des deux références,
la Merge Request GitLab promeut ce SHA commun vers `release/<TRAIN>`. Le tag canonique n'est créé
sur GitHub qu'après cette promotion et une nouvelle revue humaine, puis il est synchronisé à
l'identique vers GitLab ; seule son exécution GitLab produit le bundle de release canonique.
Une base Maven snapshot qui ne respecte pas exactement `X.Y.Z[-rc.N]-SNAPSHOT` est refusée avant
toute création d'artefact.

### 4. Qualité progressive sans régression

Les portes immédiatement bloquantes sont le build reproductible, les tests, les tests
d'architecture, le contrôle de secrets et les invariants propres au dépôt.

JaCoCo, Javadoc, PMD/CPD et l'analyse statique commencent par une mesure sur un commit de référence.
La baseline mesurée est ensuite versionnée et devient un ratchet : la couverture ne peut baisser,
les violations et duplications ne peuvent augmenter. Une modification de baseline exige une
décision humaine motivée ; une simple baisse de seuil dans une Pull Request est interdite.

Cibles de convergence :

- couverture : au moins 80 % des lignes et 70 % des branches ;
- duplication : au plus 3 % et aucun nouveau bloc dupliqué d'au moins 100 tokens ;
- Javadoc valide pour toute nouvelle API publique ou protégée.

Ces cibles ne sont pas remplacées par des valeurs arbitraires lorsque l'état initial n'est pas
encore mesuré.

### 5. Sécurité et chaîne d'approvisionnement

La CI applique progressivement : contrôle de secrets bloquant, SAST, analyse des dépendances et
licences, détection de vulnérabilités, SBOM CycloneDX, SHA-256 et provenance. Toute nouvelle
vulnérabilité critique ou élevée devient bloquante après établissement de la baseline. Une
exception est bornée, justifiée, approuvée et assortie d'une échéance.

Les actions, outils et images sont fixés par version immuable ou digest. Les identifiants de miroir
sont distincts par dépôt, limités en écriture au seul projet GitLab cible et rotés. Aucun secret de
production n'est accessible aux pipelines de build. Aucun runner CI généraliste n'est installé sur
le VPS de production.

Les jobs GitLab ne consomment aucun cache Maven partagé entre pipelines. Une branche contrôle sa
propre configuration CI et ne doit donc jamais pouvoir écrire dans un cache ensuite lu par `main`,
une branche feature d'intégration ou un tag. Un cache ne pourra être réintroduit qu'après
qualification d'une séparation imposée côté GitLab/runner entre refs protégées et non protégées,
indépendamment du YAML du dépôt.

CI-002 retient le flux JSON 2.0 officiel du NVD comme source externe. CI-003 retire le cache GitLab
initialement ajouté pour sa base locale : `main` étant non protégée, le suffixe GitLab entre refs
protégées et non protégées ne la sépare pas des branches de développement. La clé par slug n'est pas
non plus une frontière de confiance puisqu'une branche contrôle son propre YAML et peut choisir la
clé qu'elle publie. Le job `security:dependencies` impose donc `cache: []`, reconstruit sa base dans
son espace de travail éphémère et ne lit aucune donnée mutable produite par un autre pipeline. Toute
réintroduction d'un cache exige une séparation imposée côté serveur ou un feed interne en lecture
seule dont la publication échappe aux branches du dépôt.

La baseline de CI-002 est résorbée par des versions corrigées, sans règle de suppression : Spring
Boot 4.1.1 fournit Spring Framework 7.0.9, pgJDBC 42.7.13 et Log4j 2.25.5 ; Tomcat est surchargé à
11.0.25 jusqu'à ce que le BOM Spring Boot l'intègre. Toute vulnérabilité de score CVSS supérieur ou
égal à 7, toute indisponibilité du flux ou toute erreur d'analyse fait ensuite échouer le pipeline.

### 6. Frontière de livraison

Le dépôt `betting-project` est classé `vps-deployable`, mais un bundle ne devient effectivement
déployable qu'après qualification distincte de l'image, de la configuration de production, du
rollback et de la restauration. Tant que ce lot n'est pas terminé, les artefacts portent
`vps.deployable=false`.

Le dépôt `betting-sofascore-local-lab` reste en permanence :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Ses snapshots et releases sont des distributions locales. Aucun job de déploiement, secret de
production ou artefact VPS n'y est autorisé.

## Conséquences

- La CI GitHub reste la première porte de la Pull Request ; GitLab travaille sur `main`, la branche
  feature d'intégration exacte et les tags verts synchronisés, puis matérialise la promotion par
  Merge Request `feature/<TRAIN>` vers `release/<TRAIN>`.
- Une branche de release ne peut diverger du SHA canonique : merge commit, squash et push direct
  sont incompatibles avec cette décision.
- Les versions des dépôts peuvent évoluer sans coordination artificielle.
- Le premier lot qualité produit des mesures ; la fusion reste interdite tant que le ratchet n'est
  pas verrouillé.
- GitLab Free impose un mécanisme de synchronisation et des étapes d'administration manuelles.
- Le packaging VPS complet est un Work Order séparé ; un JAR traçable n'est pas présenté comme un
  livrable de production.

## Critères de conformité

- [ ] Chaque dépôt officiel figure dans le registre et possède un miroir GitLab privé homonyme.
- [ ] Les branches et tags GitLab correspondent au SHA GitHub attendu.
- [ ] `main` et `feature/*` restent non protégées sur GitLab et seule la règle `release/V*` protège
  des branches, avec fusion réservée aux Maintainers, push direct et force-push interdits.
- [ ] Le motif de tags `v*` est protégé séparément sur GitLab sans être compté comme une protection
  de branche ; un tag existant ne peut jamais être déplacé.
- [ ] Toute branche de Work Order part du HEAD documenté de `feature/<TRAIN>` et sa Pull Request
  cible la branche feature d'intégration du même train.
- [ ] La Pull Request finale `feature/<TRAIN>` vers `main` produit un merge commit ; la branche
  feature porte la version Maven exacte puis est avancée normalement en fast-forward vers ce
  commit, sans force-push.
- [ ] Une promotion GitLab `feature/<TRAIN>` vers `release/<TRAIN>` est fast-forward et le tag
  correspondant vise exactement le SHA commun aux trois références canoniques ; le pipeline
  contrôle cette égalité et l'ascendance de la release cible sur un historique complet.
- [ ] La baseline qualité mesurée est verrouillée sans régression.
- [ ] Les snapshots et releases contiennent SBOM, provenance et SHA-256.
- [ ] Un artefact du Local Lab indique explicitement `vps.deployable=false`.
- [ ] Un déploiement du projet principal promeut un artefact existant sans rebuild sur le VPS.

## Réexamen

- Passage à GitLab Premium/Ultimate permettant un miroir ou une CI de dépôt externe native.
- Passage des dépôts privés à un plan GitHub offrant protections et environnements restreints.
- Création d'une organisation GitHub et d'une fabrique de workflows réutilisables.
- Ajout d'un nouveau type de dépôt ou d'un nouveau canal de production.
