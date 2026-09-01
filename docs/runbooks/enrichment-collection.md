# Collecte d'enrichissement ENR-001

## Objet

Ce runbook décrit l'outil manuel historique de benchmark Highlightly préparé pour ENR-001. Il n'est ni un ordonnanceur, ni le futur adaptateur de production, et ne participe pas au routeur MVP. Aucun nouvel appel fournisseur n'est nécessaire ou autorisé pour la finalisation d'ENR-001. Il ne couvre ni API-FOOTBALL, ni football-data.org, ni un endpoint privé SofaScore ou FotMob, ni une rencontre absente du manifeste accepté.

Le collecteur :

- lit la clé exclusivement depuis `HIGHLIGHTLY_API_KEY` ;
- vérifie l'identifiant de l'échantillon et l'identifiant fournisseur dans le manifeste accepté ;
- refuse une famille d'endpoint incompatible avec les scénarios demandés ;
- interdit l'enrichissement complet des réserves et contrôles non activés ;
- ne suit pas les redirections HTTP ;
- n'effectue aucun retry automatique ;
- arrête l'exécution sur HTTP 401, 403 ou 429 ;
- limite le lot à 80 appels Highlightly par jour et préserve une réserve fournisseur de 20 appels ;
- conserve toute suspension 401/403/429 jusqu'au diagnostic, y compris au-delà de minuit Europe/Paris ;
- conserve le dernier compteur fournisseur observé sans supposer de remise à zéro et décrémente prudemment chaque appel ultérieur dépourvu d'en-tête de quota ;
- échoue fermé si un compteur fournisseur est mal formé, négatif ou incohérent, et recoupe chaque horodatage ISO avec le `runId` UTC de la preuve ;
- acquiert un verrou exclusif dans la racine de preuves afin d'interdire deux collectes simultanées ;
- refuse toute racine de preuves située dans le dépôt Git ; les suffixes `*.raw.json`, `*.metadata.json` et `*.replay.json` sont aussi ignorés globalement par Git ;
- crée avant l'appel un marqueur d'issue incertaine : un timeout, une interruption ou un échec avant la persistance des métadonnées interdit toute reprise jusqu'au diagnostic humain ;
- conserve le corps brut sans transformation, son SHA-256 et des métadonnées expurgées ;
- exécute immédiatement une vérification de replay hors ligne.

## Injection locale de la clé sous Windows

La valeur ne doit pas être placée dans une commande, un argument, le dépôt ou un fichier de preuve. Pour la saisir sans affichage dans une console PowerShell, puis l'enregistrer dans l'environnement utilisateur :

```powershell
.\scripts\configure-highlightly-key.ps1
```

Le script de collecte utilise d'abord la variable du processus. À défaut, il charge la variable de l'environnement utilisateur dans son propre processus. La valeur n'est jamais affichée.

Pour supprimer la variable utilisateur après le benchmark :

```powershell
.\scripts\configure-highlightly-key.ps1 -Remove
```

## Première collecte autorisée

Depuis la racine du dépôt :

```powershell
.\scripts\collect-enrichment.ps1 `
    -SampleId ENR-P01 `
    -MatchId 1347698848 `
    -Endpoint detail `
    -Window FIRST_AUTHORIZED `
    -Scenarios 'ID-01,TIM-01,OPS-01,REP-01'
```

Le répertoire de sortie par défaut est `../output/benchmark/enr01/`. Il reste hors du dépôt Git.

## Fenêtres suivantes pour ENR-P01

Composition à T-60 :

```powershell
.\scripts\collect-enrichment.ps1 `
    -SampleId ENR-P01 `
    -MatchId 1347698848 `
    -Endpoint lineup `
    -Window T-60 `
    -Scenarios 'LIN-01,OPS-01,REP-01'
```

Une seconde collecte T-45 peut être exécutée quinze minutes plus tard si la réponse T-60 est absente, incomplète ou non interprétable. Le contrôle T-30 n'est exécuté que si aucune des deux premières réponses ne contient une composition complète pour les deux équipes. Après le match, les familles `detail`, `statistics`, `events` et `box-score` sont collectées dans la fenêtre T+90 à T+180 conformément au plan ENR-001.

## Preuves produites

Chaque appel réussi ou réponse HTTP reçue produit trois fichiers :

- `*.raw.json` : corps exact retourné par le fournisseur ;
- `*.metadata.json` : contexte expurgé, statut HTTP, latence, quotas exposés, SHA-256 et version du connecteur ;
- `*.replay.json` : contrôle hors réseau du hash et de la validité JSON.

Une absence de clé échoue avant tout appel. Une erreur réseau sans réponse ne crée pas de faux fichier brut. Une réponse HTTP non concluante est conservée comme preuve, puis le collecteur retourne un code d'échec.

Le marqueur `.enr-001-call-outcome-uncertain` ne doit être supprimé qu'après comparaison avec le compteur fournisseur et décision humaine explicite. Cette limitation est propre à l'outil historique : MVP-001 devra persister et auditer chaque tentative avant émission, gérer l'incident et reprendre le job de manière idempotente.

## Vérification du corpus clôturé, sans réseau

Le corpus complet reste hors Git. Son index expurgé est versionné sous `docs/benchmark/evidence/enr-001-evidence-index-v0.1.json`. Après construction de l'artefact, la vérification des 127 triplets s'exécute sans clé et sans appel réseau :

```powershell
.\scripts\verify-enrichment-evidence.ps1 `
    -EvidenceRoot '<racine-restaurée>\evidence\benchmark\enr01'
```

Le vérificateur n'accepte que les chemins relatifs listés dans l'index, recalcule les empreintes et tailles, valide le JSON, contrôle les métadonnées et les rapports de replay, puis retourne un code non nul au premier bilan incohérent. Les trois rapports football-data.org historiques utilisent un schéma legacy explicitement déclaré dans l'index ; l'absence historique de `networkCalls` n'est pas assimilée à un appel réseau.
