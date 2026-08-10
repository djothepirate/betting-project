# Frontières modulaires initiales

Le dépôt produit une seule application Spring Boot, mais organise le code sous huit modules métier de premier niveau :

| Module | Responsabilité initiale |
|---|---|
| `catalog` | Compétitions, saisons, équipes et rencontres canoniques |
| `collection` | Appels fournisseurs, snapshots bruts et replay |
| `identity` | Mappings, rapprochements et ambiguïtés d'identité |
| `qualification` | Éligibilité, qualité et raisons d'exclusion |
| `enrichment` | Compositions, statistiques et données complémentaires |
| `analysis` | Signaux, évaluations et futurs modèles |
| `publication` | Documents et canaux externes via l'outbox |
| `operations` | Profils, jobs persistants, audits et exploitation |

Chaque module peut contenir :

- `domain` : objets et règles sans dépendance à Spring, JDBC, HTTP ou aux adaptateurs ;
- `application` : cas d'usage et ports ;
- `adapter` : intégrations techniques entrantes ou sortantes.

Les adaptateurs dépendent des ports applicatifs. Les tests ArchUnit interdisent les cycles entre modules de premier niveau et les dépendances du domaine vers Spring ou les adaptateurs.

Le package `bootstrap` contient uniquement l'assemblage de l'application. Le package `shared` contient les primitives transverses qui ne portent pas une règle métier propre à un module.
