# ADR-008 — Intégration continue, versionnement, qualité et sécurité

- **Statut :** Accepté
- **Date :** 2026-09-01
- **Décideur :** Porteur du Betting Project
- **Portée :** Tous les dépôts officiellement rattachés au Betting Project
- **Choix validés :** 1A, 2A et 3A
- **Work Order d'application :** CI-001
- **Amendement :** 2026-09-02 — promotion GitLab de `main` vers `release/<SemVer>`
- **Amendement :** 2026-09-02 — flux JSON 2.0 public NVD et cache borné par CI-002

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

GitHub est l'unique source canonique du code, des branches de travail, de `main`, des Pull Requests
et des tags. Chaque dépôt officiel possède un projet GitLab privé homonyme sous
`djothepirate-betting-project`. GitLab reçoit le SHA de `main` qualifié par GitHub, exécute la CI,
conserve les artefacts et porte les références de promotion. Aucun développement général n'y est
autorisé ; les seules Merge Requests GitLab admises promeuvent `main` vers une branche
`release/<SemVer>`.

Le registre `ci/betting-project-repositories.yml` matérialise le périmètre officiel. Un nouveau
dépôt entre dans le Betting Project uniquement après une Pull Request de ce registre approuvée par
le porteur. Son admission n'est terminée qu'après création de son projet GitLab, synchronisation du
SHA exact, pipeline vert, classification de livraison et configuration des protections.

La synchronisation est unidirectionnelle, GitHub vers GitLab, et ne publie que `main` ou un tag
SemVer immuable dont la CI GitHub est verte. Elle est préparée dans un lot distinct, désactivé par
défaut, puis activée seulement après création des projets, clés par dépôt, protections et test de
concordance des refs. Les branches `release/*` sont propres à GitLab et ne sont jamais recopiées
vers GitHub.

### 2. Branches

`main` est la seule branche permanente de développement. Une branche de travail GitHub suit :

```text
<acteur>/<work-order-sans-WO>-<slug>
```

Exemples :

```text
codex/ci-001-gitlab-ci-bootstrap
human/ci-001-gitlab-ci-bootstrap
codex/ss-20260901-031-ci-bootstrap
```

Les acteurs initiaux autorisés sont `codex` et `human`. Les correctifs urgents utilisent
`hotfix/<incident-id>-<slug>` et doivent être reliés à un Work Order avant fusion. Il n'existe ni
branche `develop`, ni branche générique nommée `release`. Toute branche de travail est fusionnée
dans `main` par Pull Request GitHub après revue humaine, puis supprimée. Les branches ouvertes avant
cet ADR sont tolérées jusqu'à leur clôture, sans créer de précédent.

GitLab ajoute des branches durables de promotion nommées strictement `release/X.Y.Z` ou
`release/X.Y.Z-rc.N`, sans zéro initial et avec `N` strictement positif. Elles ne sont pas des
branches de développement. Le motif GitLab `release/*` est le seul motif de branche protégé : seuls
les Maintainers peuvent y fusionner une Merge Request, aucun acteur ne peut y pousser directement
et le force-push reste désactivé. La branche GitLab `main` reste non protégée afin de recevoir la
copie contrôlée du SHA canonique ; elle n'est jamais une cible de Merge Request GitLab.

Une promotion utilise exclusivement une Merge Request GitLab de `main` vers la branche
`release/<SemVer>` correspondante. Le projet impose une fusion fast-forward, interdit le squash et
conserve la branche source `main`, afin que la branche de release pointe exactement le SHA issu de
GitHub. Toute impossibilité de fast-forward ou tout changement inattendu du SHA source arrête la
promotion. Le pipeline refuse les Merge Requests issues d'un fork, une cible non protégée, une
version Maven différente du suffixe de la branche et toute nouvelle modification d'une branche de
release déjà scellée par son tag.

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

Un tag de release doit viser un commit de `main`, correspondre exactement à la version Maven et
au sommet de la branche GitLab `release/<SemVer>` associée, puis ne jamais être déplacé. Un artefact
est construit une fois, identifié par SHA-256, accompagné d'un SBOM CycloneDX et d'une provenance.
Une provenance de release exclut tout identifiant de run ou de forge ; elle porte la version
d'artefact déterminée par le tag. La production promeut ces mêmes octets ; aucun rebuild n'est
autorisé sur le VPS. GitHub reste l'autorité de la référence de tag et compile puis teste cette ref.
Après synchronisation du tag exact, GitLab est l'unique producteur et l'autorité de publication du
bundle tagué.

Après création du tag associé, la branche `release/<SemVer>` est scellée : aucune nouvelle Merge
Request ne peut la faire avancer. Une correction passe nécessairement par une nouvelle version et
une nouvelle branche de release.

La Pull Request de préparation fixe d'abord la version Maven exacte `X.Y.Z[-rc.N]`. Tant qu'aucun
tag ne désigne le commit, ses builds de Pull Request puis de `main` restent des snapshots
non promouvables nommés `X.Y.Z[-rc.N]-snapshot.p<id-pipeline>.g<sha-court>` et portent un
`source.tag` vide. Après fusion et CI GitHub verte, le SHA de `main` est synchronisé vers GitLab et
qualifié. La Merge Request GitLab promeut ensuite ce même SHA vers `release/X.Y.Z[-rc.N]`. Le tag
canonique `vX.Y.Z[-rc.N]` n'est créé sur GitHub qu'après cette promotion et une nouvelle revue
humaine, puis il est synchronisé à l'identique vers GitLab ; seule son exécution GitLab produit le
bundle de release canonique.
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
propre configuration CI et ne doit donc jamais pouvoir écrire dans un cache ensuite lu par `main`
ou par un tag. Un cache ne pourra être réintroduit qu'après qualification d'une séparation imposée
côté GitLab/runner entre refs protégées et non protégées, indépendamment du YAML du dépôt.

CI-002 qualifie une exception étroite pour la seule base publique NVD de Dependency-Check. Le flux
JSON 2.0 officiel du NVD est la source externe ; le cache local ne sert qu'à éviter de reconstruire
l'index complet à chaque pipeline. Ce cache n'inclut jamais le dépôt Maven `.m2/repository`, porte
la version de l'outil, le format du flux et la ref GitLab dans sa clé, et bénéficie en plus de la
séparation serveur entre refs protégées et non protégées. Un cache miss déclenche une reconstruction
complète ; il ne transforme donc pas le cache en source de vérité et toute erreur de mise à jour
reste bloquante après activation du ratchet.

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

- La CI GitHub reste la première porte de la Pull Request ; GitLab travaille sur `main` et les tags
  verts synchronisés, puis matérialise la promotion par Merge Request vers `release/<SemVer>`.
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
- [ ] `main` reste non protégée et seule la règle `release/*` protège les branches GitLab avec
  fusion réservée aux Maintainers, push direct et force-push interdits.
- [ ] Une promotion `main` vers `release/<SemVer>` est fast-forward et le tag correspondant vise
  exactement ce même SHA canonique.
- [ ] La baseline qualité mesurée est verrouillée sans régression.
- [ ] Les snapshots et releases contiennent SBOM, provenance et SHA-256.
- [ ] Un artefact du Local Lab indique explicitement `vps.deployable=false`.
- [ ] Un déploiement du projet principal promeut un artefact existant sans rebuild sur le VPS.

## Réexamen

- Passage à GitLab Premium/Ultimate permettant un miroir ou une CI de dépôt externe native.
- Passage des dépôts privés à un plan GitHub offrant protections et environnements restreints.
- Création d'une organisation GitHub et d'une fabrique de workflows réutilisables.
- Ajout d'un nouveau type de dépôt ou d'un nouveau canal de production.
