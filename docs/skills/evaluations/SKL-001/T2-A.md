# T2 — Essai A sans skill spécialisé

Début : 2026-09-04 23:40:33 UTC
CONTEXT_READY : 2026-09-04 23:41:10 UTC

## Fiche CONTEXT_READY

- Base : révision historique `25d1c29` ; instructions actuelles `AGENTS.md` lues.
- Objectif : évaluer la conclusion de la validation Windows pour accepter un changement de persistance.
- Périmètre : blobs historiques des scripts, du POM et des tests nécessaires.
- Invariants : PostgreSQL/Testcontainers, migrations partagées immuables, architecture modulaire, absence de live et de secrets, revue humaine avant fusion.
- Contraintes : lecture seule ; aucun skill, correctif, résultat d’évaluation, réseau, Maven, test applicatif, mutation Git, clé API ou `.env` ; seule écriture : cette note.
- Premiers constats : codes de sortie natifs non contrôlés ; intégration conditionnée par la présence de Docker ; Failsafe dans un profil distinct.
- Prochaine action : examiner statiquement le lanceur Windows et les tests PostgreSQL, puis préciser les contrôles proposés.
- Clarification : aucune nécessaire pour ce diagnostic ; le changement concret déterminera les tests métier supplémentaires.

## Réponse

**La conclusion de cette validation Windows ne suffit pas à accepter un changement de persistance.** L’analyse statique révèle un risque de faux succès et une couverture PostgreSQL facultative. Elle ne démontre pas qu’une exécution historique particulière a échoué ou réussi.

1. **Échecs Maven non propagés explicitement.** Dans `25d1c29:scripts/verify-windows.ps1:6–12`, les trois appels à `mvnw.cmd` ne contrôlent jamais `$LASTEXITCODE`. Sous Windows PowerShell lancé par `25d1c29:scripts/verify-windows.cmd:4`, `$ErrorActionPreference = 'Stop'` ne transforme pas à lui seul un code natif non nul en exception bloquante. Le script peut poursuivre après l’échec du wrapper, du build ou de l’intégration et finir normalement. Le lanceur CMD transmet le résultat du processus PowerShell ; il ne répare pas cette perte d’information.

2. **Docker détecté, mais disponibilité non démontrée.** `verify-windows.ps1:10–15` vérifie seulement que la commande existe. Sans commande, les tests PostgreSQL sont omis avec un avertissement ; avec une commande mais sans moteur fonctionnel, leur lancement peut échouer, puis subir le défaut précédent. Le profil `integration` du `25d1c29:pom.xml:135–152` active Failsafe pour `**/*IT.java` : le `verify` standard ne constitue donc pas la preuve de persistance attendue.

3. **Wrapper fragile avant Maven.** `25d1c29:mvnw.cmd:82–95` résout le cache puis indexe directement `(Get-Item $MAVEN_M2_PATH).Target[0]`. Si `Target` est nul, le test de nullité arrive trop tard : l’indexation peut déjà échouer. Les lectures n’utilisent pas `-Force`, point à vérifier pour un répertoire caché. Une défaillance ici précède les tests applicatifs ; elle ne prouve aucun défaut métier. Ces conditions restent à reproduire.

Le plan adapté comporte trois étapes :

- **Fiabiliser la chaîne**, dans un Work Order : capturer immédiatement chaque code natif, arrêter sur échec, produire un résultat final explicite et distinguer « intégration non exécutée ». Pour accepter la persistance, exiger l’intégration et un moteur Docker utilisable. Sécuriser la lecture du cache avant toute indexation.
- **Vérifier les scripts par scénarios contrôlés** : échec de `-version`, de `verify`, du contrôle de secrets et de l’intégration ; Docker absent puis moteur indisponible ; parcours réussi. Vérifier le code final via le lanceur CMD et l’arrêt des étapes suivantes. Pour le wrapper, couvrir cache ordinaire, caché, lien/jonction, chemin avec espaces et `MAVEN_USER_HOME`.
- **Valider le changement réel** avec Java 25 et Maven 3.9.16 compatible avec l’Enforcer historique : exécuter `.\mvnw.cmd verify`, le contrôle de secrets, puis `.\mvnw.cmd -Pintegration verify` sur PostgreSQL/Testcontainers. Conserver révision exacte, versions, codes et rapports Surefire/Failsafe récents, avec nombres exécutés et ignorés. Exiger la présence effective des tests pertinents.

Les sources historiques `src/test/java/com/bettingproject/operations/application/PostgreSqlBootstrapIT.java` et `src/test/java/com/bettingproject/catalog/application/CalendarNormalizationIT.java` couvrent notamment migrations, idempotence, rollback et normalisation. Selon le changement, ajouter des cas ciblés de contraintes, concurrence ou migration depuis un état antérieur représentatif ; conserver les migrations partagées et ajouter une migration corrective.

Preuves manquantes : exécutions, rapports, disponibilité Docker et contenu du changement candidat. Aucun test ni Maven n’a été lancé. Seul ce rapport a été écrit ; aucune modification du dépôt. L’acceptation reste soumise aux preuves ci-dessus et à la revue humaine.

Fin : 2026-09-04 23:42:55 UTC

