# Jobs calendrier : qualification et reprise locales

MVP-001 lot 4 ; [contrat normatif](../contracts/collection-jobs-v1.md). Ce runbook ne vaut pas
autorisation d'appeler un fournisseur réel. Le flag de boucle, les clients et le registre restent
inactifs dans la configuration livrée. Il n'existe pas encore de commande HTTP de planification.

## Qualification sans compte API

```powershell
.\mvnw.cmd -Pintegration verify
.\scripts\check-no-secrets.cmd
git diff --check
```

`CalendarJobsIT` utilise PostgreSQL 17 et des clients synthétiques en mémoire, des transactions
distinctes et des redémarrages réels de contextes/pools. `CollectionWorkerLoopTest` démontre le
déclenchement opt-in avec un worker factice ; aucun réseau fournisseur n'est utilisé.

## Planifier et exécuter dans un contexte local qualifié

1. Initialiser explicitement les fenêtres via les cas d'usage budgétaires existants et l'identité
   opérateur locale, sans ouvrir de fichier de clés de benchmark.
2. Fournir au cas d'usage interne `CalendarJobPlanningService.planDay` une date, une échéance et
   les bindings exacts du registre. Conserver l'UUID de plan pour une répétition idempotente.
3. Examiner chaque résultat : `NO_PRIMARY` et `MISSING_EXACT_WINDOW_BINDING` ne sont pas des jobs
   créés. Le plan ne fabrique pas de correspondance ou de consommation passée.
4. Exécuter `JobWorker.tick()` dans le contexte `batch-worker` de test, ou activer explicitement
   la boucle uniquement dans un environnement qualifié. Le flag est
   `betting.collection.worker.enabled=true` ; les portes fournisseur du lot 3 restent séparées.
5. Examiner l'état durable avant de conclure. `SUCCEEDED` signifie que la collecte annoncée a
   été traitée, pas que tous les mappings ont été acceptés ni que l'abonnement couvre tout.

Aucune modification d'URL, clé ou configuration active de fournisseur n'est nécessaire pour
rejouer les tests du dépôt. Ne pas activer ce flag en production pour faire simplement une revue.

## Diagnostic après arrêt

Utiliser d'abord les lectures bornées de [/internal/collection](collection-control-local.md)
livrées au lot 5 ; les ports internes ou une consultation SQL locale en lecture seule permettent
ensuite d'approfondir la provenance :

- `persistent_job` : `managed_collection`, statut, tentative/max, version, bail et code générique ;
- `collection_job_event` : enchaînement des claims, expirations, reprises et terminaison ;
- `calendar_job_input` : cible exacte, empreinte du registre et parseur figés ;
- outbox `COLLECTION_JOB` : acquittement durable du travail, distinct de `PROVIDER_CALENDAR` ;
- `calendar_collection_page`, audit et intention budgétaire : preuve reçue ou envoi incertain ;
- `calendar_collection_derivation` et `collection_job_effect` : effet d'application déjà acquis.

Un bail courant ne doit pas être repris. Après expiration réelle, un tick récupère le job avec
backoff, dans sa borne de tentatives. Ne pas modifier les baux ou états SQL pour forcer un appel :
les modifications directes présentes dans les tests ne sont pas une procédure d'exploitation.

`COMMITTED_FOR_SEND` sans résultat et `SEND_UNCERTAIN` exigent une inspection puis, au besoin,
une réconciliation budgétaire explicite. Le coût reste acquis. Ne jamais remplacer l'intention,
son UUID ou sa clé pour contourner ce refus. Un job terminal n'est pas réouvert par le worker.
Une nouvelle demande n'est admissible qu'après une décision explicite sur sa finalité et le budget.

Pour une preuve native déjà conservée, `enqueueReplay` peut programmer un replay par UUID de page,
sans HTTP. Une répétition de sa clé ne crée pas de seconde application logique. Les demandes
CAT-002 restent consultables et reprenables manuellement dans leur API existante ; ce worker ne
les traite pas. Ne pas confondre les deux familles de demandes.

## Limites de livraison

L'absence de CI distante n'est pas un succès Linux. Le blocage Actions du run `35399114529`
du lot 3 est historique : les lots 4 (`51e85de`, run `35404160290`, tentative 2) et 5
(`6843ff8`, run `35411773181`) ont leurs CI Windows et Linux/PostgreSQL vertes. La tête finale
de PR exige ses propres checks. Aucun réglage de facturation ni contournement de CI ne fait
partie de ce runbook. Le [dossier de revue MVP-001](../reviews/MVP-001-final-review.md)
prépare la PR vers `feature/V0.1.0-RC01`, sans autoriser sa fusion ou une activation réelle.
