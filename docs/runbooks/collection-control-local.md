# Contrôle local des collectes — MVP-001 lot 5

Le [contrat HTTP](../contracts/collection-control-api-v1.md) est normatif. L'API est strictement
locale, sans authentification, sous `control-api` et `127.0.0.1`. Ne pas ouvrir de port au réseau,
ne pas la placer derrière un proxy. Les endpoints n'existent pas sous `batch-worker` ou `replay`.
Le receiver J7, s'il est activé séparément, conserve ses propres contraintes TLS/mTLS ; ce runbook
ne l'active ni ne contourne son connecteur.

## Démarrer et observer

Utiliser la configuration PostgreSQL déjà décrite par le
[développement local](local-development.md), sans ajouter de mot de passe aux arguments.
Le registre classpath de production reste vide, les clients et la boucle worker restent désactivés.
Un démarrage ne crée donc ni fenêtre, ni job, ni appel fournisseur.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=control-api"
```

Les commandes suivantes supposent le port HTTP local par défaut, sans J7 activé. Elles ne
configurent aucun fournisseur. Les références 2030 et UUID des exemples sont synthétiques.

```powershell
$collectionBase = 'http://127.0.0.1:8080/internal/collection'
Invoke-RestMethod "$collectionBase/budget-windows?limit=20"
Invoke-RestMethod "$collectionBase/jobs?type=CALENDAR_DISCOVERY&limit=20"
Invoke-RestMethod "$collectionBase/incidents?provider=highlightly&limit=20"
Invoke-RestMethod "$collectionBase/calendars?date=2030-08-10&limit=20"
Invoke-RestMethod "$collectionBase/capabilities?competitionCode=PPL&season=2030%2F2031&phase=LEAGUE&dataType=CALENDAR"
```

Réutiliser `nextCursor` tel quel avec les mêmes filtres et le même parent. Changer `limit` est
permis. Ne pas le réutiliser sur une autre route. L'absence de capacités ou de fenêtres est
normale tant que l'activation réelle n'est pas explicitement préparée.

Les détails d'une fenêtre exposent compteurs et état ; `/availability` donne le résultat prudent.
Les événements, intentions, jobs et pages restent paginés. Aucun octet brut ni identifiant de
compte n'est retourné. Les anomalies/mappings canoniques se consultent par
[/internal/catalog](catalog-control-api-local.md), sans second workflow humain.

## Prévoir les rencontres à enrichir

Prendre quatre UUID de calendriers terminés correspondant aux quatre compétitions, au même
jour UTC, à la même fenêtre Highlightly et au registre courant. Envoyer le corps décrit par le
contrat à `POST /daily-selection/preview`, avec un coût prévisionnel explicite et des priorités
canoniques éventuelles. Ne pas envoyer un nom d'équipe ou un ID fournisseur comme UUID canonique.

Le résultat ne réserve aucun appel. Vérifier le code budgétaire, `reservationCreated=false`,
le nombre de rencontres (maximum sept), le coût calculé et les exclusions. Une réponse vide ne
signifie pas l'absence de matchs : consulter les causes budgétaires, le calendrier et les anomalies.
Une réévaluation après changement de budget/faits peut donner une autre liste. Le lot 5 n'offre
pas de bouton « enrichir » ; ENR-002 doit consommer une sélection de manière durable et revalidée.

## Incidents et reprises

- 401/403/429 : la fenêtre concernée est suspendue. Lire `/incidents`, puis la fenêtre, ses
  intentions et événements. Les autres fournisseurs restent indépendants. Aucun endpoint de
  ce lot ne lève la suspension, ne crée de nouvelle fenêtre ou ne rembourse le coût.
- Envoi incertain : ne pas rejouer la requête HTTP. Le coût reste acquis et la reprise suit le
  [contrat budgétaire](../contracts/provider-budget-v1.md) avec preuve et réconciliation explicites.
- Job en échec/reprise : consulter le job et ses événements, puis les pages conservées. Suivre le
  [runbook jobs](collection-jobs-local.md), sans mutation SQL ad hoc ou déblocage implicite.
- Calendrier incomplet ou registre différent : ne pas fabriquer une sélection exhaustive ;
  corriger la cause par les cas d'usage autorisés, puis vérifier à nouveau les preuves.

## Validation hors réseau fournisseur

```powershell
.\mvnw.cmd -Pintegration verify
.\scripts\check-no-secrets.cmd
git diff --check
```

`CollectionControlIT` démarre PostgreSQL 17 et utilise de vrais services, jobs, transactions et
adaptateurs JDBC, MockMvc et des fournisseurs synthétiques. Les preuves et limites du candidat
figurent dans le [rapport du lot 5](../work-orders/MVP-001-lot5-execution.md). Aucun compte réel,
appel externe ou nouveau secret n'est nécessaire pour cette qualification.
