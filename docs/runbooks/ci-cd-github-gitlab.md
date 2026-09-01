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
3. Protéger `main` et `v*` dans GitLab ; interdire le push humain direct.
4. Vérifier qu'un runner Linux autorise Docker-in-Docker pour Testcontainers.
5. Faire un bootstrap miroir depuis une station d'administration de confiance.
6. Comparer les `refs/heads/*` et `refs/tags/*` entre GitHub et GitLab.
7. Exécuter GitLab CI et vérifier le SHA exact, les rapports JUnit et l'artefact.
8. Mesurer la baseline qualité sur le SHA de référence et activer le ratchet.
9. Préparer le workflow de miroir dans une Pull Request séparée et désactivée.
10. Activer seulement après test d'une référence verte, rouge, déplacée et supprimée.

## Règles du miroir Free-compatible

- une clé d'écriture GitLab distincte par projet ;
- aucune clé partagée entre dépôts ;
- aucun checkout ni exécution du code source dans le job privilégié de miroir ;
- publication du seul SHA encore pointé par la référence GitHub après une CI verte ;
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

## Snapshots et releases

- un snapshot est produit depuis `main` avec l'IID du pipeline et le SHA court ;
- un artefact de Pull Request référence le commit de fusion synthétique effectivement testé et ne
  peut jamais être promu en production ;
- une Pull Request de préparation peut déjà porter la version Maven finale `X.Y.Z[-rc.N]` : sans
  tag, son artefact et celui du premier build de `main` restent des snapshots non promouvables,
  suffixés par l'IID et le SHA et dotés d'un `source.tag` vide ;
- le tag est créé sur le SHA fusionné seulement après la CI de `main` verte et la revue humaine ;
- un tag `vX.Y.Z[-rc.N]` est accepté seulement s'il désigne le commit extrait, si ce commit est
  atteignable depuis `origin/main` et si la version Maven est identique et non SNAPSHOT ;
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

## Onboarding d'un futur dépôt

Une Pull Request ajoute d'abord le dépôt au registre avec sa classification, ses commandes de test
et son SemVer. La revue humaine matérialise son entrée officielle. Le projet GitLab, la CI sur le
même SHA, le miroir et les protections sont ensuite obligatoires ; un onboarding partiel reste en
échec et n'autorise aucune release.
