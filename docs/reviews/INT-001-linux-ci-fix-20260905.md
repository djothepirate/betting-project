# INT-001 — Échec CI Linux et correctif de validation des URI TLS

- Work Order : [INT-001](../work-orders/INT-001.md).
- PR : [#11](https://github.com/djothepirate/betting-project/pull/11).
- Base du correctif : `cd9d0826893d17f295103689f7fa0c0ce446177c`.
- Run défaillant : [33962324484](https://github.com/djothepirate/betting-project/actions/runs/33962324484).
- Job Linux : `101296236700` ; Windows : `101296236634`.

## Diagnostic et reproduction

Le job Linux échoue pendant les tests standards : 303 tests, un échec, aucune erreur ni omission
dans cette suite. `J7ReceiverActivationGuardTest.refusesNonLocalStoresAndAlternativeTlsMaterial`
attend une exception ligne 66 et ne la reçoit pas. Les étapes PostgreSQL/Testcontainers et de
packaging sont ensuite omises. Le job Windows du même run réussit.

`file:////synthetic-host/share/server.p12` n'a pas d'autorité URI mais possède un chemin commençant
par `//`. Le code convertissait l'URI en `Path` avant de vérifier le préfixe UNC. Sous Unix, cette
conversion normalise les séparateurs et perd le préfixe : le contrôle accepte alors le chemin.
La vérification dépendait donc du système d'exploitation. Ce défaut se situe dans le code de
validation, pas dans Maven, Docker, le workflow ou une attente de test à supprimer.

La commande `./mvnw -B -ntp -Dtest=J7ReceiverActivationGuardTest test` sur Ubuntu/WSL2, Java
25.0.3, reproduit l'échec avant correction : 14 tests, un échec identique, code de sortie 1.
Les premiers essais hors ligne ont rencontré un cache incomplet (parent Spring Boot 4.1.1,
puis plugin JAR 3.5.1 pour `verify`) ; ils ne constituent pas des exécutions de tests.
La résolution Maven a été autorisée pour compléter ces dépendances. Aucun fournisseur n'est appelé.

## Changement borné

La garde examine désormais le chemin URI décodé avant `Path.of(uri)`. Les séparateurs `/` et
`\` sont traités uniformément pour refuser les préfixes réseau et device, y compris les variantes
percent-encodées. Le contrôle d'autorité et les contrôles après conversion restent en place.
Cette validation est partagée par les gardes précoce et effective.

Les anciennes assertions en boucle deviennent des cas paramétrés nommés : 11 formes interdites
pour chacune des deux propriétés TLS, vérifiées dans les deux phases. Un test positif vérifie
des chemins absolus natifs et une URI locale avec espace encodé. Les alternatives TLS restent
couvertes séparément. Aucun seuil, exclusion de test ou contrôle réseau n'est assoupli.

## Validation du correctif

La suite standard Linux `./mvnw -B -ntp verify` réussit sous Ubuntu/WSL2 et Java 25.0.3 :
326 tests, zéro échec, erreur ou skip, puis `BUILD SUCCESS`, le 5 septembre à 13:17 Europe/Paris.
La durée Maven est de 2 min 33 s. Le nombre augmente par individualisation des 22 cas de stores
et ajout du test positif ; il ne représente pas une réexécution des 98 tests d'intégration.
La CI Windows/Linux du commit publié reste la preuve de qualification distante,
y compris PostgreSQL/Testcontainers ; ses résultats ne sont pas anticipés dans ce document.

Le correctif ne touche ni la persistance, ni les migrations, ni le protocole d'import, ni les
scripts de sauvegarde/restauration. Le rapport historique du 4 septembre reste intact. Sa revue
propriétaire porte sur le candidat historique ; le présent correctif fonctionnel est visible dans
la PR et ne vaut pas nouvelle approbation propriétaire ni autorisation de fusion.
