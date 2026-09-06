# Contrat de replay calendrier v3

## Objet et état d'activation

Le schéma `cal01-fixture-v3` étend explicitement le contrat v2 avec la neutralité du terrain et la sémantique d'ordre des participants. Il est conçu pour des snapshots expurgés et rejouables hors réseau fournisseur.

Depuis le lot 2 de CAT-002, le parseur reconnaît v3, le normaliseur transporte ses deux attributs sans inférence et PostgreSQL les conserve sur l'observation comme sur la rencontre canonique. La migration `V003` ajoute également un journal append-only de chaque évaluation. Le lot 3 définit les politiques pures d'identité, d'autorité, de chronologie et de transition ; depuis le lot 4, le normaliseur les applique effectivement avec une identité ordonnée par défaut et une inversion autorisée uniquement lorsque `participantsUnordered=true`. Le lot 5 ajoute la décision humaine versionnée des mappings et le cycle de vie opérable des anomalies. Le lot 6 ajoute les demandes durables de rejeu, le lot 7 leur adaptateur HTTP interne et le lot 8 leur preuve de redémarrage, sans modifier le wire format v3.

## Enveloppe

```json
{
  "schemaVersion": "cal01-fixture-v3",
  "provider": "synthetic-provider",
  "observedAt": "2026-09-01T15:00:00Z",
  "fixtures": []
}
```

| Champ | Règle |
|---|---|
| `schemaVersion` | Doit valoir exactement `cal01-fixture-v3`. |
| `provider` | Code stable et non vide de la source ; la normalisation vérifiera sa cohérence avec le snapshot brut. |
| `observedAt` | Instant obligatoire et parseable, fixé à la frontière de collecte. Il n'est ni le kickoff, ni l'heure du rejeu, ni `updated_at`. |
| `fixtures` | Liste obligatoire, éventuellement vide. |

Le parseur ne remplace jamais un `observedAt` absent par l'heure de réception. Un adaptateur de collecte qui ne dispose pas d'un instant fournisseur fiable doit construire explicitement cet instant, avec sa provenance, avant de produire le contrat.

## Rencontre observée

```json
{
  "providerFixtureId": "synthetic-v3-unordered-001",
  "competition": {
    "providerCompetitionId": "synthetic-cup",
    "name": "Synthetic Cup",
    "countryCode": "FRA",
    "type": "DOMESTIC_CUP",
    "season": "2026/2027",
    "phase": "FINAL"
  },
  "kickoff": "2026-09-12T19:00:00Z",
  "status": "SCHEDULED",
  "neutralVenue": null,
  "participantsUnordered": true,
  "homeTeam": {
    "providerTeamId": "synthetic-gamma",
    "name": "Synthetic FC Gamma",
    "countryCode": "FRA"
  },
  "awayTeam": {
    "providerTeamId": "synthetic-delta",
    "name": "Synthetic FC Delta",
    "countryCode": "FRA"
  }
}
```

Tous les champs exigés par v2 restent obligatoires avec la même sémantique. Les identifiants et noms restent des observations fournisseur ; ils ne créent aucun mapping implicite.

## Neutralité et ordre

| Champ | Présence | Valeurs acceptées | Sens |
|---|---|---|---|
| `neutralVenue` | obligatoire | `true`, `false` ou `null` | `true` affirme un terrain neutre, `false` affirme un terrain non neutre, `null` signifie `UNKNOWN`. |
| `participantsUnordered` | obligatoire | `true` ou `false` | `true` est la seule valeur qui autorise une identité insensible à l'ordre ; `false` maintient des participants ordonnés. |

Une clé absente n'est jamais remplacée par une valeur par défaut. Une chaîne, un nombre ou un objet n'est jamais converti en booléen. `participantsUnordered` n'accepte pas `null`.

`neutralVenue=true` n'autorise aucune inversion à lui seul et n'entraîne aucune valeur automatique de `participantsUnordered`. Réciproquement, `participantsUnordered=true` ne permet pas de déduire la neutralité.

Les propriétés `homeTeam` et `awayTeam` conservent toujours les deux positions et l'ordre fournis par la source. Lorsque `participantsUnordered=true`, ces noms de propriétés ne prouvent pas un rôle sportif domicile ou extérieur et le parseur ne trie ni n'inverse les équipes.

## Compatibilité

| Version | Rejouable | Parseur | Neutralité transportée | Ordre transporté | Normalisation catalogue actuelle |
|---|---:|---:|---|---|---:|
| `cal01-fixture-v1` | oui | oui | inconnue | ordre source | non, `LEGACY_SCHEMA_NOT_NORMALIZABLE` |
| `cal01-fixture-v2` | oui | oui | `null` en mémoire | `false` en mémoire | oui, identité strictement ordonnée et politiques d'autorité appliquées |
| `cal01-fixture-v3` | oui | oui | valeur explicite | valeur explicite | oui, ordre exact ou inversion uniquement si `participantsUnordered=true` |

Le wire format v1 ou v2 n'est jamais enrichi silencieusement. Une fixture v2 reste interprétée avec neutralité inconnue et participants ordonnés. Une fixture v1 reste rejouable mais ne reçoit aucun contexte canonique inventé.

## Rejet d'un payload v3 invalide

Le parseur rejette explicitement :

- `observedAt` absent, vide ou non parseable ;
- `fixtures` absent ou non tabulaire ;
- `neutralVenue` absent ou d'un type autre que booléen ou `null` ;
- `participantsUnordered` absent, nul ou d'un type autre que booléen ;
- compétition ou équipe absente ;
- schéma inconnu.

La validation de présence et de type des deux attributs v3 est locale au parseur et ne modifie pas la configuration JSON globale. Le snapshot brut reste la preuve de référence ; aucun champ absent n'est inventé et aucune fixture valide voisine n'est normalisée partiellement lorsqu'un élément rend le payload illisible.

## Application canonique depuis le lot 4

Depuis MVP-001 lot 1, `RegistryCalendarAuthorityPolicy` consulte exclusivement le
[registre de capacités classpath](provider-capability-registry-v1.md). Couverture, rôle et
activation y sont distincts : `CALENDAR_ONLY` ne donne un rôle calendrier que s'il est explicite
et actif. La version de politique est le SHA-256 des octets du registre. L'ancienne ressource
de politique n'est plus chargée comme configuration ou fallback. La baseline réelle reste vide ;
le wire format calendrier, les transitions et les règles de normalisation restent inchangés.

Le normaliseur résout l'autorité à partir de la clé exacte `(provider, providerCompetitionId, season, phase, CALENDAR)` et journalise la version de la politique utilisée. Les valeurs runtime sont comparées littéralement : `*`, `?` et `%` ne sont jamais interprétés comme des motifs et restent des caractères fournisseur ordinaires. L'interdiction des jokers s'applique uniquement aux affectations de la configuration classpath ; toute entrée configurée contenant l'un de ces caractères fait échouer le chargement. La configuration de production reste vide et fermée par défaut : une source sans affectation exacte produit `UNASSIGNED`. Le snapshot brut, l'observation, le journal d'application et l'anomalie sont conservés, mais cette source ne crée ni compétition, ni équipe, ni saison, ni rencontre canonique, ni mapping. Aucune promotion implicite vers `PRIMARY` n'est permise.

Une observation `CONTROL` ne modifie jamais les faits canoniques ni le tampon d'autorité :

- en l'absence de rencontre issue d'un primaire, elle est bloquée avec `CONTROL_WITHOUT_PRIMARY` et aucun mapping de rencontre n'est créé ;
- si tous les faits concordent, elle est journalisée `UNCHANGED` ;
- si un fait diverge, elle est journalisée `CONTROL_DIVERGENCE` et une anomalie est ouverte.

Une observation `PRIMARY` utilise exclusivement `observedAt` :

- un instant antérieur produit `STALE`, conserve l'observation et le journal, ne modifie pas le canon et n'ouvre pas d'anomalie de normalisation ;
- le même instant avec les mêmes faits produit `UNCHANGED` et préserve le tampon courant ;
- le même instant avec des faits contradictoires produit `EQUAL_AUTHORITY_TIME_CONFLICT` et une anomalie ;
- un instant postérieur avec les mêmes faits produit `UNCHANGED` et avance le tampon d'autorité ;
- un instant postérieur avec des faits autorisés produit `UPDATED` ; une transition interdite produit `INVALID_TRANSITION` et une anomalie.

La matrice de statut appliquée est fermée et explicite :

| État canonique courant | États candidats admis |
|---|---|
| `SCHEDULED` | `SCHEDULED`, `POSTPONED`, `CANCELLED`, `FINISHED` |
| `POSTPONED` | `POSTPONED`, `SCHEDULED`, `CANCELLED`, `FINISHED` |
| `FINISHED` | `FINISHED` uniquement |
| `CANCELLED` | `CANCELLED` uniquement |

`FINISHED` et `CANCELLED` sont donc terminaux. Un changement d'un autre fait canonique reste possible lorsque le statut courant et candidat sont identiques et que l'observation est plus récente ; le même instant avec des faits différents reste un conflit, quelle que soit la transition de statut.

Lors de la création par un primaire, le tampon d'autorité référence l'observation effectivement stockée. Chaque application ultérieure journalise également l'observation d'autorité précédente, le rôle et la version de politique. L'observation source demeure immuable, y compris lorsqu'un même snapshot est réévalué.

L'identité compare d'abord compétition, saison, participants et kickoff. L'ordre exact reste la règle pour v2 et pour v3 avec `participantsUnordered=false`. Avec `participantsUnordered=true`, une rencontre existante peut être retrouvée avec les participants inversés ; l'ordre du payload reste conservé dans l'observation et ne réécrit jamais l'ordre canonique existant. `neutralVenue` n'accorde toujours aucune permission d'inversion. Plusieurs candidats symétriques rendent l'identité ambiguë et bloquent l'application.

La transaction de normalisation acquiert un verrou advisory PostgreSQL global avant l'évaluation des rencontres. Les insertions de référentiels et de rencontres sont atomiques, les lignes candidates sont verrouillées, les mappings existants ne sont pas écrasés et les mises à jour canoniques vérifient par compare-and-set l'autorité attendue. Une course qui invalide cette précondition fait échouer la transaction au lieu de valider une mutation partielle.

Ces garanties réutilisent les colonnes et le journal de `V003` ; le lot 4 n'ajoutait aucune migration propre.

## Décisions humaines et anomalies depuis le lot 5

Sous le seul profil `control-api`, une décision humaine peut confirmer ou rejeter un mapping exact. La commande contient une version attendue et une clé d'idempotence ; l'auteur est résolu par `OperatorIdentityProvider`, jamais accepté depuis le contenu de la commande. La création explicite utilise `expectedVersion=0` et les modifications ultérieures utilisent un compare-and-set sur la version courante.

Chaque décision réussie conserve dans une même transaction :

- le mapping résultant et sa nouvelle version ;
- un reçu idempotent portant l'empreinte de la commande ;
- une entrée append-only avec auteur, justification expurgée, état précédent et état résultant ;
- la corrélation aux anomalies de mapping ouvertes qui correspondent exactement au même fournisseur, type, référence, saison et phase.

Une répétition exacte retourne le résultat initial sans dupliquer le mapping, le reçu ou l'historique. Une version obsolète ou une clé d'idempotence réutilisée avec un autre contenu ne produit aucune mutation. Une décision ne résout pas elle-même les anomalies corrélées : seul un rejeu complet peut constater leur disparition.

Sous `control-api` et `batch-worker`, chaque occurrence alimente une projection versionnée et le journal `OPENED`, `OBSERVED`, `RESOLVED` ou `REOPENED`. Une nouvelle occurrence d'une anomalie `IGNORED` est journalisée sans la rouvrir. Après le parcours complet d'un snapshot v2 ou v3, les anomalies ouvertes de ce snapshot qui ne sont plus reproduites deviennent `RESOLVED`. Les chemins v1, schéma inconnu ou payload invalide sortent avant cette évaluation complète. `STALE` reste uniquement un résultat du journal d'application et n'ouvre pas d'anomalie propre ; le snapshot v2 ou v3 reste néanmoins une évaluation complète pour les autres anomalies antérieures qui ne sont plus reproduites.

Lorsqu'elle provient d'une rencontre, l'occurrence référence le `FixtureApplicationLog` correspondant. Projection, événement, observation, application et éventuelle mutation canonique partagent la même transaction. Une anomalie historique sans saison ni phase n'est enrichie que si son rattachement est unique sous verrou ; aucune candidate n'est choisie arbitrairement lorsqu'il existe plusieurs contextes possibles.

La migration additive `V004__mapping_decisions_and_anomaly_lifecycle.sql` porte ces versions, reçus, historiques, corrélations et événements sans modifier `V001`, `V002` ou `V003`.

## Rejeu d'un snapshot stocké depuis le lot 6

Le lot 6 ne modifie ni l'enveloppe ni les règles de parsing de `cal01-fixture-v3`. Il permet au `control-api` de sélectionner un `raw_snapshot` PostgreSQL par UUID ou par empreinte SHA-256 unique, de vérifier ses octets, puis de réutiliser exactement le normaliseur décrit ci-dessus.

La demande est conservée avant son exécution et la tentative commence seulement après ce premier commit. Chaque tentative réussie est corrélée aux nouvelles applications de fixture et aux nouveaux événements d'anomalie qu'elle a produits. Un arrêt entre la création et l'exécution laisse une demande durable reprenable ; la reprise d'un snapshot déjà évalué ne duplique ni l'observation ni l'effet canonique, mais conserve une nouvelle trace d'évaluation.

Une décision humaine crée, dans sa propre transaction, une demande par snapshot distinct corrélé. Le rejeu ultérieur peut alors résoudre l'ancienne anomalie de mapping ou constater qu'elle persiste. Un rejet reste bloquant et ne peut jamais être remplacé silencieusement par un mapping textuel automatique.

Le contrat complet des sélecteurs, états, transactions, empreintes et corrélations est défini dans [`stored-snapshot-replay-v1.md`](stored-snapshot-replay-v1.md).

## Adaptateur interne et limites après les lots 7 et 8

Le rejeu stocké reste un cas d'usage interne du seul profil `control-api`. Le profil `batch-worker` ne revendique aucune demande et le profil `replay` reste strictement hors réseau, sans DataSource et sans Flyway, pour le rejeu de fichiers ou d'octets.

Depuis le lot 7, les consultations paginées, décisions humaines, créations et reprises manuelles sont exposées sous `/internal/catalog` par le contrat [`catalog-control-api-v1`](catalog-control-api-v1.md). Les projections ne contiennent aucun payload brut, reçu ou secret ; les commandes refusent tout chemin arbitraire et l'application écoute uniquement sur `127.0.0.1`. Le runbook [`catalog-control-api-local`](../runbooks/catalog-control-api-local.md) interdit toute exposition avant OPS-001.

Le lot 8 n'ajoute aucun comportement de production. Il prouve par `ControlApiRestartIT` qu'une demande `PENDING` commitée par une première instance est relue et terminée par une deuxième instance sur la même base, puis reconnue terminale par une troisième sans nouvelle tentative ni nouvel effet logique. Aucun endpoint public, worker de fond, poller, appel fournisseur ou migration V006 n'est introduit.
