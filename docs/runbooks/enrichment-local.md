# Runbook local — consultation qualité ENR-002

## Préconditions

- Utiliser le profil `control-api` et la base PostgreSQL locale du projet.
- Conserver le serveur sur `127.0.0.1` ; ne pas utiliser de reverse proxy ou de tunnel réseau.
- Le registre de production et les clients fournisseurs restent désactivés. Aucun token API n'est
  requis pour consulter les données déjà stockées.
- Démarrer PostgreSQL selon le [runbook de développement local](local-development.md), puis
  lancer l'application avec sa configuration locale habituelle et le profil `control-api`.

## Lire un jour

Dans PowerShell, la route est en lecture seule :

```powershell
Invoke-RestMethod -Method Get `
  -Uri 'http://127.0.0.1:8080/internal/collection/enrichment/daily?date=2030-08-10'
```

Remplacer la date par la date UTC voulue. La réponse présente au plus sept rencontres du plan,
leurs étapes, les cinq familles, les agrégats de qualité et la dernière tentative par étape et
famille. Une date sans plan renvoie une réponse valide avec `planPresent: false` et une liste
`fixtures` vide.

`NOT_COLLECTED` signifie qu'aucune observation n'est enregistrée pour cette famille dans ce plan.
Ne pas le convertir en `ABSENT`. Les états `NULL_VALUE`, `EMPTY`, `PARTIAL` et `INCOMPATIBLE`
restent distincts. `ageSeconds` est un âge mesuré à l'instant de lecture, sans seuil de fraîcheur
ni verdict automatique.

## Sécurité et limites d'usage

Cet endpoint ne demande pas d'authentification : seule l'écoute loopback le protège actuellement.
Ne pas exposer son port, sa réponse ou des captures contenant les identifiants de rencontres sur
un réseau partagé. Ne pas soumettre d'URL ou de chemin de fichier ; le contrat refuse les
paramètres de localisation arbitraires. Les octets fournisseurs complets ne sont pas renvoyés.

Cette lecture n'arme ni ne relance de job, ne réserve pas de budget et ne contacte pas Highlightly
ou football-data.org. La collecte réelle et le pilote de sept jours sont soumis à une décision
distincte. En cas d'erreur `ProblemDetail`, ne pas déduire de détails opérationnels absents du
code stable ; consulter les journaux locaux contrôlés sans copier de secret ou payload brut.
