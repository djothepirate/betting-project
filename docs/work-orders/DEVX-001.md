# DEVX-001 - Fiabiliser le build, les validations et la mémoire du dépôt

- **Statut :** Pull Request ouverte - revue en cours - fusion soumise à autorisation
- **Version :** 0.6
- **Date d'ouverture et d'acceptation du périmètre :** 2026-09-01
- **Date de préparation pour revue :** 2026-09-01
- **Date de validation humaine et d'autorisation du commit :** 2026-09-01
- **Date de publication de la branche et d'ouverture de la Pull Request :** 2026-09-01
- **Date du correctif de revue sur les artefacts ignorés :** 2026-09-01
- **Responsable de décision :** Porteur du Betting Project
- **Exécutant :** Codex, sur la branche `codex/devx-001`
- **Jalon :** Consolidation préalable au MVP football
- **Prérequis :** BOOT-001 et CAT-001 fusionnés dans `main` ; sauvegarde restaurable ENR-001 validée
- **ADR applicables :** ADR-004, ADR-005 et ADR-007

## Objectif

Rendre impossible tout faux succès de la validation Windows, automatiser les contrôles sans appel fournisseur sur Windows et Linux, puis replacer dans le dépôt la mémoire de projet nécessaire à la reprise d'ENR-001 et au démarrage du MVP football.

## Constat d'ouverture

Le 1er septembre 2026, `mvnw.cmd` échoue avant Maven lorsqu'il indexe la propriété `Target` nulle d'un répertoire `.m2` ordinaire. Malgré cet échec, `scripts/verify-windows.ps1` peut terminer avec le code `0`, car les codes de sortie des commandes natives ne sont pas contrôlés. Le script ignore aussi l'intégration lorsque la CLI Docker est absente, alors que Testcontainers peut accéder directement à Docker Desktop par named pipe.

Le code reste fonctionnel avec Maven installé : 17 tests standards et 11 tests PostgreSQL/Testcontainers réussissent. Le lot corrige donc l'usine de validation, pas une régression métier démontrée.

## Préalable de récupération ENR-001

Avant toute modification Git de DEVX-001, une sauvegarde datée `2026-09-01T11-27-31` a été créée hors dépôt dans l'espace de récupération du projet. Elle référence le commit de base `25d1c29` et conserve 33 entrées de statut Git logique, les patches indexé et non indexé, 139 fichiers utiles dont 3 non suivis, 457 preuves et un manifeste SHA-256 de 1 203 entrées.

Une restauration à blanc a validé l'identité des deux patches et des octets bruts conservés. Le rapport et le manifeste restent avec la sauvegarde externe ; aucun fichier du checkout source n'a été nettoyé, déplacé ou supprimé.

## Périmètre autorisé

- Corriger le Maven Wrapper Windows sans changer Maven 3.9.16.
- Épingler un SHA-256 dérivé d'une archive Maven validée avec le SHA-512 officiel publié par Apache.
- Centraliser l'exécution PowerShell des commandes natives avec arrêt immédiat sur code non nul.
- Rendre l'intégration PostgreSQL obligatoire dans la validation Windows dite complète.
- Ajouter des tests de non-régression des scripts Windows.
- Ajouter une CI GitHub sans étape, configuration ni clé fournisseur sur Windows et Linux.
- Renforcer le contrôle local des secrets et ajouter un contrôle des changements Git.
- Actualiser README, runbooks et statut de CAT-001.
- Ajouter un changelog, un état du projet, une roadmap et une synthèse Markdown des décisions de cadrage.
- Consigner les résultats de validation dans le présent Work Order.

## Exclusions

- Aucun appel à Highlightly, football-data.org, API-FOOTBALL, SofaScore ou un bookmaker.
- Aucune lecture ou copie du fichier local contenant les clés du benchmark.
- Aucun changement du catalogue canonique, des mappings métier ou des migrations Flyway.
- Aucune reprise ou fusion d'ENR-001 dans ce lot.
- Aucun déploiement, changement de protection GitHub, commit, push ou Pull Request implicite.
- Aucun ajout de profil live, microservice, broker, Redis ou Kubernetes.

## Critères d'acceptation

### Wrapper et scripts

- [x] `mvnw.cmd -version` réussit avec un répertoire `.m2` qui n'est pas un lien symbolique.
- [x] Le wrapper conserve Maven 3.9.16 et vérifie un SHA-256 dérivé de l'archive validée avec le SHA-512 officiel publié par Apache.
- [x] Toute commande native en échec interrompt immédiatement la validation Windows avec un code non nul.
- [x] La validation Windows complète exécute toujours les tests PostgreSQL/Testcontainers.
- [x] L'indisponibilité de Testcontainers fait échouer la validation complète.
- [x] Un test automatisé reproduit et interdit le faux vert historique.

### CI et sécurité

- [x] La CI Windows vérifie le wrapper, le build standard et les secrets sans clé fournisseur.
- [x] La CI Linux vérifie le wrapper, le build standard, l'intégration PostgreSQL et les secrets sans clé fournisseur.
- [x] Le contrôle des secrets couvre les changements Git, les sorties locales ignorées `*.log` et `reports/**`, et des formats sensibles supplémentaires sans afficher de valeur secrète ni élargir la lecture aux autres fichiers ignorés destinés à l'injection de clés.
- [x] Aucun test ni aucune étape de CI ne configure ou n'appelle un fournisseur ; aucune clé fournisseur n'est requise.

### Documentation

- [x] CAT-001 est documenté comme fusionné et clôturé.
- [x] README et runbooks reflètent les migrations V001/V002 et les validations CAT-001.
- [x] `CHANGELOG.md`, `docs/project-brief.md`, `docs/project-status.md` et `docs/roadmap.md` sont préparés pour versionnement.
- [x] La mémoire du benchmark et les décisions MVP sont référencées sans dépendre d'un ancien checkout ou d'une conversation.

### Validation finale

- [x] `mvnw.cmd verify` réussit avec 17 tests standards.
- [x] `mvnw.cmd -Pintegration verify` réussit avec 17 tests standards et 11 tests PostgreSQL.
- [x] Les contrôles de secrets Windows et POSIX réussissent.
- [x] `git diff --check` réussit.
- [x] Le diff reste limité à DEVX-001 et est validé par le porteur après revue humaine.
- [x] Les deux jobs GitHub Actions sont observés verts sur la branche et la Pull Request publiée.

## Commandes de validation prévues

```powershell
.\mvnw.cmd -version
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration verify
.\scripts\check-no-secrets.cmd
.\scripts\verify-windows.cmd
```

```bash
./mvnw -version
./mvnw verify
./mvnw -Pintegration verify
./scripts/check-no-secrets.sh
```

## Séquencement

1. Corriger et tester le wrapper et la propagation des codes d'erreur.
2. Renforcer les contrôles de secrets.
3. Ajouter la CI Windows/Linux.
4. Actualiser la mémoire documentaire.
5. Exécuter toutes les validations hors réseau fournisseur.
6. Soumettre le diff à la revue humaine avant toute opération Git distante.

## Définition de terminé

DEVX-001 est prêt pour revue lorsque les commandes de référence ne peuvent plus produire de faux vert, que la CI sans appel fournisseur couvre Windows et Linux, que les validations standard et PostgreSQL réussissent et que la mémoire du dépôt permet de reprendre ENR-001 sans dépendre de la conversation historique.

La fermeture, le commit, le push et la Pull Request nécessitent des décisions humaines distinctes conformément à ADR-005.

## Résultats d'exécution

### Sauvegarde préalable ENR-001

La restauration à blanc de la sauvegarde `2026-09-01T11-27-31` est `PASS` : base `25d1c29`, patches indexé et non indexé identiques, octets du working tree restaurés, 139 fichiers utiles dont 3 non suivis, 457 preuves et 1 203 entrées de manifeste validées. Le checkout source n'a subi aucun nettoyage, déplacement ou suppression.

### Maven Wrapper et fins de ligne

- `mvnw.cmd -version` réussit avec le répertoire utilisateur `.m2` ordinaire et une propriété `Target` vide.
- Maven 3.9.16 et Java 25.0.4 sont détectés sous Windows.
- L'archive Maven 3.9.16 a été comparée au SHA-512 publié par Apache, `ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3`, avant calcul du SHA-256 épinglé, `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`.
- `.gitattributes` impose désormais LF à `mvnw` et aux fichiers `.properties`, ce qui rend le wrapper directement exécutable depuis Linux/WSL2 malgré un checkout créé sous Windows.
- Sous WSL2, `./mvnw -version` réussit avec Maven 3.9.16 et OpenJDK 25.0.3 ; `./mvnw verify` réussit avec 17 tests.

### Validation Windows finale

`scripts\verify-windows.cmd` a terminé avec le code `0` le 1er septembre 2026. Il a exécuté, sans précontrôle de la CLI Docker :

- le wrapper Maven 3.9.16 ;
- le build standard : 17 tests, 0 échec, 0 erreur ;
- le contrôle du dépôt et des changements Git : `PASS` ;
- le profil `integration` : 17 tests standards puis 11 tests PostgreSQL/Testcontainers, 0 échec, 0 erreur ;
- Testcontainers 2.0.5 via le named pipe Docker Desktop, PostgreSQL 17.10 et les migrations `V001` puis `V002`.

Le premier essai exécuté dans le sandbox sans accès réseau a échoué dès le build Maven et la validation a bien propagé un code non nul. La relance autorisée avec accès au cache/réseau Maven et à Docker Desktop a réussi intégralement ; aucun fournisseur sportif n'a été appelé.

### Tests des scripts et sécurité

- Le test `verify-windows.Tests.ps1` réussit sous Windows PowerShell 5.1 et PowerShell 7.
- Quatre échecs simulés sont couverts séparément : `mvnw -version`, build standard, scan de secrets et intégration/Testcontainers. Chaque scénario vérifie l'arrêt immédiat des étapes suivantes.
- Les tests du scanner réussissent sous PowerShell et sous POSIX. Ils couvrent notamment les clés privées PEM, PKCS#8 chiffrées et PGP, les clés AWS/Google, les jetons GitHub/GitLab/Slack/Stripe/SendGrid, JWT et Bearer, les affectations génériques et les endpoints privés SofaScore.
- Des régressions dédiées prouvent qu'un secret présent seulement dans l'index, déjà commité dans `HEAD` puis masqué par le working tree, ou ajouté puis retiré dans deux commits successifs, reste détecté sans que sa valeur soit affichée. En l'absence de base explicite ou pour le SHA nul d'un premier push, `origin/main` est utilisé lorsqu'il existe ; une base explicite introuvable fait échouer le scan.
- Les portées `Repository`/`repository` et `All`/`all` détectent également les secrets synthétiques placés dans un journal ignoré imbriqué et un fichier sous `reports/**`. Les tests vérifient d'abord avec `git check-ignore` que ces artefacts sont réellement ignorés, puis prouvent qu'un fichier d'injection local ignoré hors de ces deux familles reste hors de la sélection.
- Les valeurs synthétiques détectées ne sont jamais réaffichées ; seuls le chemin et l'identifiant de règle sont exposés.
- Les placeholders explicites des fixtures de test restent autorisés et ne masquent pas une valeur réelle de même famille.
- Le scan complet Windows et POSIX, incluant le diff contre `origin/main`, est `PASS`.
- L'analyse syntaxique PowerShell, `sh -n`, l'analyse YAML du workflow et `git diff --check` réussissent.

### CI, correctifs de revue et limites ouvertes

Le 1er septembre 2026, le porteur du Betting Project a validé le diff DEVX-001, autorisé le commit `2fc6b52`, puis son push sur `codex/devx-001`. Après observation de la première CI, il a autorisé le correctif minimal, le commit `e4255d8`, sa publication et l'ouverture de la Pull Request. Aucune de ces décisions n'autorise la fusion ni la modification des protections GitHub.

Le workflow `.github/workflows/ci.yml` contient deux jobs sans secret fournisseur :

- Windows : wrapper, tests des scripts, scan du dépôt et du diff, puis build standard ;
- Linux : wrapper, tests POSIX, scan du dépôt et du diff, build standard, puis intégration PostgreSQL/Testcontainers obligatoire.

La première exécution de branche, `33500860455`, a validé Windows mais révélé sous Linux une dépendance à l'ordre des classes d'intégration : `PostgreSqlBootstrapIT.duplicateRawSnapshotIsIgnored()` laissait son snapshot validé dans la base Testcontainers partagée avant `CalendarNormalizationIT`. Le correctif `e4255d8` ajoute `@Transactional` à cette seule méthode, sans modifier le code de production. L'ordre défavorable Bootstrap puis Calendar a ensuite réussi localement avec 17 tests standards et 11 tests PostgreSQL.

Après publication du correctif, l'exécution de branche `33502308409` puis l'exécution de Pull Request `33502537082` ont chacune terminé avec les deux jobs Windows et Linux verts, intégration PostgreSQL/Testcontainers comprise. La Pull Request `#2`, de `codex/devx-001` vers `main`, est ouverte, non brouillon et sans conflit. La fusion reste soumise à une autorisation humaine explicite distincte.

La revue de la Pull Request a ensuite signalé que `git ls-files --others --exclude-standard` omettait les journaux et rapports ignorés par Git. Le correctif ajoute une seconde énumération Git strictement limitée aux pathspecs `**/*.log` et `**/reports/**` dans les portées dépôt et complète, sous PowerShell comme sous POSIX. Cette liste positive restaure la couverture des sorties locales sans parcourir l'ensemble des fichiers ignorés ni les fichiers servant à injecter des clés ; les diagnostics restent limités au chemin et à l'identifiant de règle.

L'intégration WSL2 locale a correctement échoué faute de socket Docker `/var/run/docker.sock` dans la distribution courante. Cette limite d'environnement n'a pas été masquée : le build Linux standard est vert, l'intégration réelle est verte sous Windows via Docker Desktop, et le job Linux est configuré pour échouer si Testcontainers ne dispose pas de Docker.
