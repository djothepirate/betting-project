# MVP-001 - Inventaire des preuves de capacité v1

Inventaire documentaire du 6 septembre 2026. Toutes les affectations réelles restent inactives.
Les conclusions d'août ne valent pas qualification d'une nouvelle saison ou d'un nouveau plan.

## Sources versionnées

### Journée COV-002 auditée — non intégrée aux affectations

La [clarification COV-002](mvp-001-cov002-clarification-20260906.md) et son
[index expurgé](evidence/mvp-001-cov002-20260906-2307-index.json) relèvent le point de mesure
de 23:07 Europe/Paris : 100 appels Highlightly, 37 football-data.org et un diagnostic historique
API-FOOTBALL ; 138 SHA-256 conformes et JSON lisibles vérifiés hors réseau. Les familles dédiées
restent partiellement différées ; ce n'est ni un replay de production, ni une qualification métier
exhaustive. Les comparaisons Lab restent des observations rapportées, sans audit de ses octets.
Rapport et échantillon sources résident dans l'autre worktree ; leurs empreintes sont consignées
dans la note, sans copie des payloads. Aucune clé exécutable ou fixture n'est activée par cet index.

### Sources déjà présentes dans ce dépôt

- [S1 - échantillon ENR](enrichment-sample-v0.1.json), objets ENR-P08, ENR-P10 et ENR-P11 :
  références de compétition, rencontre et phase métier de l'échantillon réel.
- [S2 - bilan accepté](weekly-provider-scorecard-and-mvp-scope-v0.1.md), sections 3.1 et 4.3 :
  observations CAL-01 du 10 au 16 août 2026, codes football-data.org et limitations de plan.
- [S3 - index ENR](evidence/enr-001-evidence-index-v0.1.json) : 127 preuves réelles indexées par
  identifiant logique, famille et hash ; leurs payloads complets restent hors Git.
- [S4 - fixtures représentatives](evidence/enr-001-representative-fixtures-v0.1.json) : corpus
  explicitement synthétique, identifiants et dates remplacés ; jamais une source d'identifiants réels.

## Noyau calendrier

| Compétition | Fournisseur | Référence disponible | Saison source exacte | Phase source exacte | Famille et nature de la preuve |
|---|---|---|---|---|---|
| PPL | highlightly | 80778 (S1 ENR-P08/P11) | NON_ETABLIE pour une affectation | NON_ETABLIE ; S1 décrit la journée 2 en langage métier | DETAIL/LINEUP/STATISTICS/EVENTS/BOX_SCORE, preuves ENR S3 ; calendrier consolidé S2 |
| PD | highlightly | 119924 (S1 ENR-P10) | NON_ETABLIE pour une affectation | NON_ETABLIE ; S1 décrit la journée 1 en langage métier | DETAIL/LINEUP/STATISTICS/EVENTS/BOX_SCORE, preuves ENR S3 ; calendrier consolidé S2 |
| DED | highlightly | NON_ETABLIE dans les sources ciblées | NON_ETABLIE | NON_ETABLIE | Couverture agrégée S2 ; pas de clé complète activable |
| ELC | highlightly | NON_ETABLIE dans les sources ciblées | NON_ETABLIE | NON_ETABLIE | Couverture agrégée S2 ; pas de clé complète activable |
| PPL | football-data.org | PPL (S1 ENR-P08/P11, S2 §4.3) | NON_ETABLIE pour une affectation | NON_ETABLIE | CALENDAR réel consolidé : 9 rencontres ; DETAIL S3 |
| PD | football-data.org | PD (S1 ENR-P10, S2 §4.3) | NON_ETABLIE pour une affectation | NON_ETABLIE | CALENDAR réel consolidé : 5 rencontres ; DETAIL S3 |
| DED | football-data.org | DED (S2 §4.3) | NON_ETABLIE | NON_ETABLIE | CALENDAR réel consolidé : 9 rencontres |
| ELC | football-data.org | ELC (S2 §4.3) | NON_ETABLIE | NON_ETABLIE | CALENDAR réel consolidé : 11 rencontres |
| PPL, PD, DED, ELC | API-FOOTBALL | NON_ETABLIE par clé | 2026 refusée par le plan testé (S2) | NON_ETABLIE | BLOCKED_BY_PLAN ; aucun chemin critique activé |

La période des rencontres ne suffit pas à déterminer le littéral de saison renvoyé par chaque
endpoint. Les descriptions de journée ne prouvent pas davantage une valeur source REGULAR_SEASON.
Les références ci-dessus ne créent donc aucune entrée dans le registre de production.

## Fixtures et preuves manquantes

S4 fournit sept fixtures synthétiques de détail et d'enrichissement. Les fixtures CAT-001/CAT-002
prouvent les contrats canoniques et le replay ; elles ne qualifient pas les endpoints calendrier
natifs. Le lot 1 utilisera des références synthetic-* distinctes pour PPL/PD/DED/ELC, une saison
et une phase synthétiques explicites. Le lot 3 devra prouver les schémas calendrier natifs,
pagination et statuts, puis relier chaque activation réelle à une preuve datée complète.

## Empreintes des migrations à l'ouverture

Base 5e4b05b5a1a7cfa66850643db31d9612192e7281 ; V001 à V008 immuables.

```text
d3ab6619dc4b81f7ac6807c06b5f18c3013de442c525701d150fe5d23ee1f090  V001__bootstrap_schema.sql
69d9e4ff0415a4fcf395c2ce85fe79c84bc57672daa1c2b72f4b4d46ae516271  V002__canonical_calendar.sql
059b95738deb3c22b90399c6096dab9cd3e2594f8279f9d6ea22ab28dfff9dfd  V003__calendar_observation_chronology.sql
2051f1caf8420d3a36c28becfba47dd6b0827d25b1910d4cf0162916bdec8cd3  V004__mapping_decisions_and_anomaly_lifecycle.sql
f38ac525099e3b269b85b19df3ece34d2e0e38d460834b953bbbb8d41507f90f  V005__durable_snapshot_replay.sql
6557d9510b20f6c163f0b96bc914de6d181b151eebd08cb254928162276bc6e3  V006__j7_import_inbox.sql
9a87c602c500a47f5c3984b690185f9906394a651ea4e3d4c9999f8537120cff  V007__j7_import_purge_integrity.sql
6fbee3fec6a84aa390df913df4fbd3b73fc1a522272197e82bbf6bdf01f7716b  V008__j7_import_upgrade_evidence_time_integrity.sql
```
