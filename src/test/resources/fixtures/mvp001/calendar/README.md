# Corpus calendrier natif synthétique MVP-001 lot 3

Ces trois réponses sont fabriquées pour les tests hors réseau ; elles ne sont pas des payloads
fournisseur collectés ou des copies expurgées de COV-002. Noms, références et dates sont
synthétiques. Elles ne prouvent aucune couverture réelle ni ne rendent une capacité active.

Les enveloppes et noms des champs sont fondés sur les documentations officielles consultées
le 18 septembre 2026, sans appel aux API :

- [Highlightly, Get all matches](https://highlightly.net/football-api/documentation/) :
  liste `data`, `pagination.totalCount/offset/limit`, `league.season`, `round` et états.
- [football-data.org, Competition / Matches](https://docs.football-data.org/general/v4/competition.html) :
  `competition`, `resultSet.count`, `matches` et filtres de la sous-ressource compétition.
- [football-data.org, Match](https://docs.football-data.org/general/v4/match.html) :
  identifiant propre de saison, `startDate`, `stage`, statuts et borne `dateTo` exclusive.

Highlightly page 0 déclare une autre page ; page 1 termine la liste. Le champ additionnel
`neutralVenue` de page 0 est délibérément hors contrat interprété : il ne doit pas créer une
connaissance de neutralité. Les deux positions source restent ordonnées. Les pays des équipes
et le type canonique de compétition restent inconnus : aucune propagation du pays de la
compétition ni classification national/international n'est tentée.

La saison football-data.org `950001` est un identifiant source distinct de l'année de filtre
`2030`. La phase Highlightly est le littéral exact `Regular Season - 1`, sans réduction en une
phase générique. Une page à contexte mélangé est incompatible dans cette version fermée.

Les tests dérivent des variantes pour statuts, types invalides, pagination incomplète, doublons,
horaires et références contradictoires. Une exception de parsing ne contient pas les octets reçus.
Les payloads complets des campagnes demeurent hors Git ; le corpus historique ENR est inchangé.
