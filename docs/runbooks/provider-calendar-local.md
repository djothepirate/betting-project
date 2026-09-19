# Calendrier fournisseur : qualification et diagnostic local

Ce runbook concerne MVP-001 lot 3. Il n'autorise aucune activation réelle. Le registre classpath
livré est vide ; les deux clients sont désactivés. Aucun worker, endpoint de collecte, poller ou
planificateur n'est livré ici. Le benchmark ENR reste séparé et ses clés ne doivent pas être lues.

Le lot 4 complète désormais ces cas d'usage par un moteur désactivé par défaut ; sa planification,
son diagnostic et ses limites sont décrits dans [collection-jobs-local](collection-jobs-local.md).
Le chemin manuel décrit ci-dessous et les portes d'activation fournisseur restent inchangés.

## Qualifier sans fournisseur

```powershell
.\mvnw.cmd -Pintegration verify
.\scripts\check-no-secrets.cmd
git diff --check
```

PostgreSQL/Testcontainers est obligatoire. `CalendarCollectionIT` utilise des transports en mémoire
et de vrais adaptateurs de persistance. `CalendarHttpLoopbackTest` utilise seulement `127.0.0.1`
avec un secret factice. Aucun de ces scénarios ne consomme un abonnement fournisseur.

## Examiner une collecte

Le cas d'usage interne `CalendarCollectionService.collect` accepte une commande typée : UUID de
collecte, fenêtre budgétaire, capacité exacte, date UTC et année de filtre. Le résultat est durable.
Répéter son UUID avec le même contenu ne réémet jamais un appel. Un contenu différent est un conflit.

Examiner, par les ports internes ou les projections PostgreSQL locales, dans cet ordre :

1. `calendar_collection` : état et motif ; `RUNNING` après un arrêt n'est pas un succès ;
2. `calendar_collection_page` : intention, audit, statut HTTP, empreinte et instant de réception ;
3. `provider_call_intent` : réservation ou engagement définitivement comptabilisé ;
4. `provider_call_audit` et incidents budgétaires : aucun secret ni URL authentifiée ;
5. `calendar_collection_derivation` : version de parseur, résultat et snapshot dérivé ;
6. observations, journaux d'application et anomalies existants du catalogue.

Ne pas modifier manuellement les états pour forcer un envoi. Une outbox `PENDING` n'autorise
aucun retry ; un engagement `COMMITTED_FOR_SEND` ou `UNCERTAIN` conserve son coût. Les opérations
administratives budgétaires requièrent `BETTING_OPERATOR_ID` et une justification expurgée.

## Rejouer sans réseau

Sous `control-api`, `CalendarNativeReplayService.replayPage(UUID)` repart des octets natifs
d'une page stockée, vérifie leur empreinte et dérive à nouveau v3. Il ne prend ni chemin de fichier,
ni URL, ne réserve rien et n'appelle aucun client. Il ajoute un historique sans déclarer complète
une collecte interrompue. Une corruption donne `INTEGRITY_ERROR`, jamais une normalisation forcée.

Le rejeu CAT-002 générique d'un snapshot v3 reste valable pour les représentations **dérivées**.
Un corps natif fournisseur n'est pas directement un contrat v3. Sous le profil `replay`, seuls
les parseurs natifs et l'encodeur sans persistance sont disponibles ; aucun JDBC ou client HTTP.

## Portes d'une future activation séparée

Une activation demandera des preuves réelles datées, des mappings explicites, une affectation
exacte du registre et une fenêtre budgétaire initialisée. Une variable de clé seule ne suffit pas.
Les propriétés `betting.providers.highlightly.enabled` et `betting.providers.football-data.enabled`
restent absentes/false dans le dépôt. Les secrets seraient fournis hors Git via `HIGHLIGHTLY_API_KEY`
ou `FOOTBALL_DATA_API_KEY`, jamais par un argument de commande ou une fixture.

La JVM doit aussi avoir été démarrée avec `-Djdk.httpclient.disableRetryConnect=true` et
`-Djdk.httpclient.redirects.retrylimit=1`, sans journal HTTP JDK. Ne pas modifier ces options
après le chargement du client. Le [contrat](../contracts/provider-calendar-collection-v1.md)
décrit les limites, réponses incompatibles, métadonnées inconnues et budgets conservateurs.

Une page mélangeant des phases natives reste incompatible dans cette version. `COMPLETED`
décrit les pages annoncées dans le contexte exact, pas la couverture commerciale de l'abonnement.
