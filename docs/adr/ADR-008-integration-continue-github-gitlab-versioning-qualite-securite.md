# ADR-008 — Intégration continue, versionnement, qualité et sécurité

- **Statut :** Accepté
- **Date :** 2026-09-01
- **Décideur :** Porteur du Betting Project
- **Portée :** Tous les dépôts officiellement rattachés au Betting Project
- **Choix validés :** 1A, 2A et 3A
- **Work Order d'application :** CI-001

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

GitHub est l'unique source canonique du code, des branches, des Pull Requests et des tags. Chaque
dépôt officiel possède un projet GitLab privé homonyme sous `djothepirate-betting-project`.
GitLab exécute la CI et conserve les artefacts ; aucun développement ni Merge Request n'y est
autorisé.

Le registre `ci/betting-project-repositories.yml` matérialise le périmètre officiel. Un nouveau
dépôt entre dans le Betting Project uniquement après une Pull Request de ce registre approuvée par
le porteur. Son admission n'est terminée qu'après création de son projet GitLab, synchronisation du
SHA exact, pipeline vert, classification de livraison et configuration des protections.

La synchronisation est unidirectionnelle, GitHub vers GitLab, et ne publie qu'une référence dont la
CI GitHub est verte. Elle est préparée dans un lot distinct, désactivé par défaut, puis activée
seulement après création des projets, clés par dépôt, protections et test de concordance des refs.

### 2. Branches

`main` est la seule branche permanente. Une branche de travail suit :

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
`hotfix/<incident-id>-<slug>` et doivent être reliés à un Work Order avant fusion. Il n'existe pas
de branche permanente `develop` ou `release`. Toute branche est fusionnée par Pull Request après
revue humaine, puis supprimée. Les branches ouvertes avant cet ADR sont tolérées jusqu'à leur
clôture, sans créer de précédent.

### 3. Versionnement et immutabilité

Chaque dépôt suit SemVer indépendamment des autres :

- développement Maven : `X.Y.Z-SNAPSHOT` ;
- release candidate facultative : tag `vX.Y.Z-rc.N` ;
- release : tag immuable `vX.Y.Z` et version Maven exacte `X.Y.Z` ;
- snapshot distribué : `X.Y.Z-snapshot.p<id-pipeline>.g<sha-court>`.

Un tag de release doit viser un commit de `main`, correspondre exactement à la version Maven et
ne jamais être déplacé. Un artefact est construit une fois, identifié par SHA-256, accompagné d'un
SBOM CycloneDX et d'une provenance. Une provenance de release exclut tout identifiant de run ou de
forge ; elle porte la version d'artefact déterminée par le tag. La production promeut ces mêmes
octets ; aucun rebuild n'est autorisé sur le VPS. GitLab est l'autorité de publication des tags :
GitHub compile et teste ces refs, sans produire un second bundle de release.

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

- La CI GitHub reste la première porte de la Pull Request ; GitLab travaille sur les refs vertes
  synchronisées.
- Les versions des dépôts peuvent évoluer sans coordination artificielle.
- Le premier lot qualité produit des mesures ; la fusion reste interdite tant que le ratchet n'est
  pas verrouillé.
- GitLab Free impose un mécanisme de synchronisation et des étapes d'administration manuelles.
- Le packaging VPS complet est un Work Order séparé ; un JAR traçable n'est pas présenté comme un
  livrable de production.

## Critères de conformité

- [ ] Chaque dépôt officiel figure dans le registre et possède un miroir GitLab privé homonyme.
- [ ] Les branches et tags GitLab correspondent au SHA GitHub attendu.
- [ ] La baseline qualité mesurée est verrouillée sans régression.
- [ ] Les snapshots et releases contiennent SBOM, provenance et SHA-256.
- [ ] Un artefact du Local Lab indique explicitement `vps.deployable=false`.
- [ ] Un déploiement du projet principal promeut un artefact existant sans rebuild sur le VPS.

## Réexamen

- Passage à GitLab Premium/Ultimate permettant un miroir ou une CI de dépôt externe native.
- Passage des dépôts privés à un plan GitHub offrant protections et environnements restreints.
- Création d'une organisation GitHub et d'une fabrique de workflows réutilisables.
- Ajout d'un nouveau type de dépôt ou d'un nouveau canal de production.
