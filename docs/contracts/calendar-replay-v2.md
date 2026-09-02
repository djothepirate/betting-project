# Contrat de replay calendrier v2

## Objet

Le schéma `cal01-fixture-v2` transporte une observation de calendrier suffisamment riche pour alimenter le catalogue canonique sans dépendre du format d'une API réelle.

Il est réservé aux fixtures expurgées, aux tests et au replay hors ligne. Il ne contient ni clé API, ni en-tête HTTP, ni URL privée, ni donnée de session.

## Enveloppe

```json
{
  "schemaVersion": "cal01-fixture-v2",
  "provider": "highlightly",
  "observedAt": "2026-08-11T10:00:00Z",
  "fixtures": []
}
```

| Champ | Règle |
|---|---|
| `schemaVersion` | Doit valoir `cal01-fixture-v2` pour être normalisable par CAT-001. |
| `provider` | Code stable de la source de la fixture ; il doit correspondre au fournisseur du snapshot brut. |
| `observedAt` | Instant UTC obligatoire et parseable auquel le calendrier a été observé. |
| `fixtures` | Liste, éventuellement vide, des rencontres observées. |

Le schéma `cal01-fixture-v1` de BOOT-001 reste rejouable. Il n'est pas normalisable, car il ne contient ni compétition, ni saison, ni identifiants fournisseurs des équipes. Son passage dans le service CAT-001 conserve le brut et crée une anomalie `LEGACY_SCHEMA_NOT_NORMALIZABLE`.

## Compatibilité décidée par CAT-002

CAT-002 conserve le wire format de `cal01-fixture-v2` sans ajout silencieux de champ :

- v1 reste rejouable mais non normalisable ;
- v2 reste normalisable, avec neutralité inconnue et participants ordonnés ;
- le contrat explicite `cal01-fixture-v3` exige, pour chaque rencontre, la présence de `neutralVenue`, nullable pour représenter `UNKNOWN`, et de `participantsUnordered`, booléen obligatoire ;
- seul `participantsUnordered=true` autorise une identité insensible à l'ordre ; `neutralVenue=true` seul ne permet jamais une inversion et n'entraîne aucune valeur automatique de l'indicateur d'ordre.

Cette section documente la compatibilité acceptée par `CAT-002-D08`. Le contrat v3 est normalisable depuis le lot 2 et son détail normatif est décrit dans `calendar-replay-v3.md`. La migration additive `V003` conserve les fixtures et observations v2 avec `neutralVenue=null` et `participantsUnordered=false` ; ni leur wire format ni leur ordre historique ne sont réinterprétés.

## Rencontre observée

```json
{
  "providerFixtureId": "hly-upl-001",
  "competition": {
    "providerCompetitionId": "hly-upl",
    "name": "Ukrainian Premier League",
    "countryCode": "UKR",
    "type": "DOMESTIC_LEAGUE",
    "season": "2026/2027",
    "phase": "REGULAR_SEASON"
  },
  "kickoff": "2026-08-15T12:00:00Z",
  "status": "SCHEDULED",
  "homeTeam": {
    "providerTeamId": "hly-kryvbas",
    "name": "FC Kryvbas Kryvyi Rih",
    "countryCode": "UKR"
  },
  "awayTeam": {
    "providerTeamId": "hly-livyi-bereh",
    "name": "FC Livyi Bereh Kyiv",
    "countryCode": "UKR"
  }
}
```

Tous les identifiants `provider*Id` sont propres au fournisseur. Ils ne deviennent jamais des identifiants canoniques.

Les noms sources sont conservés pour l'audit. Une égalité ou une ressemblance de nom ne confirme aucun mapping.

## Statuts canoniques acceptés

| Statut | Sens |
|---|---|
| `SCHEDULED` | Rencontre programmée à l'instant indiqué. |
| `POSTPONED` | Rencontre reportée ; l'instant fourni est la dernière valeur observée. |
| `CANCELLED` | Rencontre annulée. |
| `FINISHED` | Rencontre terminée. |

Les futurs connecteurs auront la responsabilité de traduire les statuts propres à leur API vers ces valeurs. CAT-001 ne déduit pas un statut inconnu.

## Types de compétition acceptés

- `DOMESTIC_LEAGUE`
- `DOMESTIC_CUP`
- `INTERNATIONAL_CLUB`
- `INTERNATIONAL_NATIONAL`

Le type source est conservé dans la fixture, mais la compétition canonique utilisée par la normalisation provient toujours d'un mapping confirmé.

## Règles de mapping

- La compétition est recherchée avec le fournisseur, son identifiant, la saison et la phase.
- Les équipes sont recherchées avec le fournisseur et leur identifiant ; la saison et la phase sont vides.
- Depuis CAT-002, une rencontre déjà liée par son identifiant fournisseur ne peut être modifiée que par une observation `PRIMARY` admissible, ordonnée par son seul `observedAt` et autorisée par la matrice de transitions. Une observation `CONTROL`, `UNASSIGNED`, périmée, contradictoire au même instant ou portant une transition interdite conserve sa preuve et son journal sans écraser le canon. Le détail normatif de l'autorité, de la chronologie et de l'ordre des participants est défini dans [`calendar-replay-v3.md`](calendar-replay-v3.md) ; le wire format v2 reste inchangé.
- Une rencontre sans mapping propre peut rejoindre une rencontre existante uniquement en cas d'égalité exacte de la compétition, de la saison, des deux équipes et de l'instant du coup d'envoi.
- Un mapping `AMBIGUOUS` ou `REJECTED` bloque la rencontre concernée.
- Aucune distance de texte, recherche floue ou substitution automatique de nom n'est utilisée.

Ainsi, aucune substitution fondée sur le seul libellé source `Hirnyk` n'est autorisée. La référence exacte `highlightly:TEAM:5522923` peut toutefois être résolue vers l'équipe canonique `FC Kryvbas Kryvyi Rih` par le mapping explicite `CONFIRMED_HISTORICAL_REBRAND_ALIAS` accepté le 15 août 2026, limité au contexte ukrainien. Le snapshot conserve toujours le libellé brut `Hirnyk`.

## Provenance et idempotence

Le payload complet est conservé dans `raw_snapshot`. Chaque rencontre observée produit au plus une ligne `fixture_observation` pour le couple snapshot/identifiant fournisseur.

Une rencontre normalisée reste reliée :

- au snapshot brut ;
- au fournisseur ;
- aux identifiants sources de la compétition et des équipes ;
- à l'horaire, au statut et à la phase observés ;
- au résultat de normalisation.

Le rejeu du même payload retrouve le même snapshot par la clé `(provider, endpoint, payload_sha256)`, ne duplique pas l'observation et ne crée aucune nouvelle rencontre canonique. Un SHA-256 seul peut correspondre à plusieurs snapshots lorsque le fournisseur ou l'endpoint diffère.

## Erreurs structurées

| Code | Situation |
|---|---|
| `MISSING_MAPPING` | Aucun mapping explicite n'existe pour une compétition ou une équipe. |
| `AMBIGUOUS_MAPPING` | Le mapping nécessite une décision humaine. |
| `REJECTED_MAPPING` | Le rapprochement proposé a été rejeté. |
| `MAPPING_CONFLICT` | Un mapping confirmé contredit les participants ou la compétition observés. |
| `INVALID_FIXTURE` | La rencontre ne respecte pas le contrat v2 ou un statut n'est pas pris en charge. |
| `LEGACY_SCHEMA_NOT_NORMALIZABLE` | La fixture v1 reste rejouable mais ne possède pas le contexte canonique requis. |
| `UNSUPPORTED_SCHEMA` | La version du schéma est inconnue. |
| `INVALID_SNAPSHOT` | Le payload est illisible ou son fournisseur contredit l'enveloppe du snapshot. |

Un schéma inconnu ou invalide ne supprime pas le snapshot brut. L'anomalie est persistée et le résultat de normalisation indique que le payload n'est pas compatible.
