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
3. Laisser `main` non protégée, protéger `release/*` et `v*`, puis interdire le push direct et le
   force-push vers les branches de release.
4. Réserver la fusion de `release/*` aux Maintainers et configurer le projet en fast-forward sans
   squash ni suppression de la branche source.
5. Vérifier qu'un runner Linux autorise Docker-in-Docker pour Testcontainers.
6. Faire un bootstrap de `main` depuis une station d'administration de confiance.
7. Comparer `main` et les tags synchronisés entre GitHub et GitLab ; exclure les branches
   `release/*`, propres à GitLab, de cette comparaison.
8. Exécuter GitLab CI et vérifier le SHA exact, les rapports JUnit et l'artefact.
9. Mesurer la baseline qualité sur le SHA de référence et activer le ratchet.
10. Préparer le workflow de miroir dans une Pull Request séparée et désactivée.
11. Activer seulement après test d'une référence verte, rouge, déplacée et supprimée.

## Règles du miroir Free-compatible

- une clé d'écriture GitLab distincte par projet ;
- aucune clé partagée entre dépôts ;
- aucun checkout ni exécution du code source dans le job privilégié de miroir ;
- publication du seul SHA encore pointé par `main` GitHub après une CI verte ;
- synchronisation des seuls tags SemVer immuables, jamais des branches GitLab `release/*` ;
- jamais de force-push sur `main` ;
- tags SemVer immuables ;
- rotation tous les 90 jours et immédiate en cas de suspicion.

Sur un dépôt privé GitHub Free, un secret de dépôt n'est pas isolé d'un auteur autorisé à ajouter
un workflow. L'activation stricte exige donc soit GitHub Pro avec un environnement limité à
`main`, soit un dépôt contrôleur séparé et réservé à l'administration. Aucun secret de miroir ne
doit être ajouté avant cette décision d'exploitation.

Le cache Maven GitLab reste désactivé : une politique ou une clé définie dans le YAML d'une branche
n'est pas une frontière de confiance. Sa réactivation exige une séparation serveur qualifiée des
caches de refs protégées et non protégées ; `main` et les tags ne doivent jamais lire un cache
qu'une ref non protégée peut alimenter.

## Promotion de `main` vers `release/<SemVer>`

1. Préparer la version Maven exacte `X.Y.Z[-rc.N]` sur une branche de travail, puis la fusionner
   dans `main` par Pull Request GitHub après revue humaine et CI verte.
2. Noter le `CANDIDATE_SHA` de `main`, suspendre toute nouvelle synchronisation de `main` pendant
   la promotion, puis copier ce SHA vers GitLab et attendre son pipeline vert.
3. Créer la branche cible `release/X.Y.Z[-rc.N]` depuis le sommet de la précédente branche de
   release protégée. Vérifier que ce sommet est un ancêtre de `CANDIDATE_SHA`.
4. Pour la toute première release, choisir un `BASE_SHA` strictement ancêtre de `CANDIDATE_SHA`,
   protéger temporairement la branche exacte `main` avec push et force-push interdits, créer la
   branche protégée via l'interface ou l'API GitLab sur `BASE_SHA`, puis retirer immédiatement la
   règle exacte `main`. Vérifier ensuite que seule la règle `release/*` protège des branches. Cette
   séquence n'autorise aucun push direct vers une branche de release.
5. Ouvrir une Merge Request GitLab dont la source est `main` et la cible
   `release/X.Y.Z[-rc.N]`. La source et la cible doivent appartenir au même projet, la cible doit
   être protégée et la version Maven doit être exactement `X.Y.Z[-rc.N]`. Ne jamais sélectionner le
   squash ni la suppression de la source.
6. Avant fusion, vérifier que la source de la Merge Request vaut toujours `CANDIDATE_SHA`, que son
   pipeline est vert, que les discussions sont résolues et que la fusion est fast-forward.
7. Après fusion, vérifier que `main`, `release/X.Y.Z[-rc.N]` et `CANDIDATE_SHA` désignent exactement
   le même commit. Conserver la branche de release comme preuve durable ; ne pas y pousser.
8. Après revue humaine, créer sur GitHub le tag `vX.Y.Z[-rc.N]` sur `CANDIDATE_SHA`, attendre sa CI
   verte, puis synchroniser ce tag immuable vers GitLab. Le pipeline GitLab doit refuser le bundle
   si le tag ne correspond pas au sommet de la branche de release associée.
9. Vérifier le bundle, ses empreintes et sa provenance, puis reprendre la synchronisation de
   `main`. Si un SHA change ou si le fast-forward devient impossible, arrêter la promotion.

Le workflow donne la priorité au pipeline `merge_request_event` lorsqu'une Merge Request est
ouverte. Pour le même changement, le pipeline de branche issu de `push` est alors supprimé via
`CI_OPEN_MERGE_REQUESTS`, y compris lorsque la source est `main`. Le filtre reste limité à la source
`push` afin de conserver les pipelines de tag, les lancements manuels et les déclenchements par API
ou pipeline parent. En dehors d'une Merge Request ouverte, le push de synchronisation de `main`
continue de produire et conserver son snapshot.

Une branche de release déjà associée à son tag est scellée. Toute nouvelle Merge Request vers cette
branche doit échouer ; une correction crée une nouvelle version. Les tags complets doivent être
présents dans le checkout du pipeline de Merge Request afin que ce refus reste bloquant.

## Snapshots et releases

- un snapshot est produit depuis `main` avec l'IID du pipeline et le SHA court ;
- le bundle d'une Pull Request référence le commit de fusion synthétique effectivement testé, reste
  éphémère dans le workspace du runner et n'est jamais téléversé ni promu en production ;
- une Pull Request de préparation peut déjà porter la version Maven finale `X.Y.Z[-rc.N]` : sans
  tag, son artefact et celui du premier build de `main` restent des snapshots non promouvables,
  suffixés par l'IID et le SHA et dotés d'un `source.tag` vide ;
- le tag est créé sur le SHA promu seulement après les CI de `main`, de la Merge Request GitLab et
  de la branche `release/<SemVer>` vertes, puis après revue humaine ;
- un tag `vX.Y.Z[-rc.N]` est accepté seulement s'il désigne le commit extrait, si ce commit est
  atteignable depuis `origin/main`, s'il correspond au sommet de `release/X.Y.Z[-rc.N]` et si la
  version Maven est identique et non SNAPSHOT ;
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
