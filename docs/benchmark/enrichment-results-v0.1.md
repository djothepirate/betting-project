# Résultats du benchmark d'enrichissement v0.1

- **Statut :** Consolidé, accepté, fusionné dans `main` et clôturé avec ENR-001
- **Work Order :** échantillon et baseline ENR-001 v0.1 acceptés ; Pull Request `#3` fusionnée au commit `6913cea`
- **Période sportive :** du 12 au 16 août 2026
- **Dernières preuves post-match :** 17 août 2026
- **Fuseau métier :** Europe/Paris
- **Fournisseurs exécutés :** Highlightly et football-data.org
- **Dernière mise à jour :** 2026-09-01
- **Bilan consolidé :** `weekly-provider-scorecard-and-mvp-scope-v0.1.md`
- **Index expurgé des 127 preuves :** `evidence/enr-001-evidence-index-v0.1.json`
- **Manifeste des fixtures synthétiques :** `evidence/enr-001-representative-fixtures-v0.1.json`

## Synthèse d'exécution

| Mesure | Valeur |
|---|---:|
| Rencontres principales engagées | 11 / 12 |
| Appels ENR-001 exécutés | 127 |
| Réponses HTTP 2xx | 127, dont 1 statut récupéré par contenu |
| Preuves brutes conservées | 127 |
| Replays hors réseau réussis | 127 |
| Anomalies ID-01 ouvertes | 2 catégories (`MISSING_PLAYER_ID`, ENR-P06, ENR-P09 et ENR-P12 ; `CROSS_ENDPOINT_PLAYER_ID_MISMATCH`, ENR-P09 à ENR-P11) |
| Alias historiques confirmés | 1 (`CONFIRMED_HISTORICAL_REBRAND_ALIAS`, ENR-C01) |
| Anomalies TIM-01 ouvertes | 0 |
| Résultat STA-01 | `COMPLETE` |
| Résultat EVT-01 | `COMPLETE` |
| Résultat PLY-01 | `COMPLETE_WITH_ANOMALIES` |
| Catégories d'anomalies PLY-01 confirmées | 4 |
| Catégories d'anomalies PLY-01 bloquantes | 0 |
| Réserves sémantiques PLY-01 ouvertes | 0 |
| Réserves activées | 0 |
| Compositions LIN-01 obtenues avant le coup d'envoi sur P03 à P05 | 0 / 3 |
| Compositions obtenues avant le coup d'envoi sur P06 à P08 | 2 / 3, P06 et P08 |
| Compositions obtenues avant le coup d'envoi sur P09 à P11 | 2 / 3, P09 et P10 |
| Compositions obtenues avant le coup d'envoi sur P12 | 1 / 1, à T-15 |
| Résultat prématch ENR-P03 | `ABSENT_AT_KICKOFF` |
| Résultat prématch ENR-P04 | `ABSENT_AT_KICKOFF` |
| Résultat prématch ENR-P05 | `COMPLETE_LATE` |
| Résultat prématch ENR-P06 | `COMPLETE_WITH_ID_ANOMALY` à environ T-28 |
| Résultat post-match ENR-P06 | STA-01 et EVT-01 `COMPLETE` ; PLY-01 `COMPLETE_WITH_ANOMALIES` |
| Résultat prématch ENR-P07 | `ABSENT_AT_LAST_PREMATCH_CHECK` à environ T-28 |
| Résultat tardif ENR-P07 | `COMPLETE_LATE` à la mi-temps déclarée |
| Résultat post-match ENR-P07 | STA-01 `COMPLETE_REDUCED_SCHEMA`, EVT-01 `COMPLETE`, PLY-01 `COMPLETE_WITH_ANOMALIES` |
| Résultat prématch ENR-P08 | `COMPLETE_PREMATCH_LATE_CHECK` à environ T-11 |
| Résultat live ENR-P08 | STA-01 et EVT-01 `COMPLETE_LIVE` ; PLY-01 `COMPLETE_LIVE_WITH_ANOMALIES` |
| Résultat post-match ENR-P08 | STA-01 et EVT-01 `COMPLETE` ; PLY-01 `COMPLETE_WITH_ANOMALIES` |
| Résultat prématch ENR-P09 | `COMPLETE_WITH_NON_BLOCKING_SUBSTITUTE_ID_ANOMALY` à T-30, exécution réelle à environ T-29 |
| Résultat post-match ENR-P09 | STA-01 et EVT-01 `COMPLETE` ; PLY-01 `COMPLETE_WITH_ANOMALIES` |
| Résultat prématch ENR-P10 | `COMPLETE` à T-15, exécution réelle à environ T-12 |
| Résultat post-match ENR-P10 | STA-01 `COMPLETE_REDUCED_SCHEMA`, EVT-01 `COMPLETE`, PLY-01 `COMPLETE_WITH_ANOMALIES` |
| Résultat prématch ENR-P11 | `ABSENT_AT_LAST_PREMATCH_CHECK` à T-15, exécution réelle à environ T-12 ; T-5 ignoré après T0 |
| Résultat post-match ENR-P11 | STA-01 et EVT-01 `COMPLETE` ; PLY-01 `COMPLETE_WITH_ANOMALIES` |
| Résultat prématch ENR-P12 | `COMPLETE_WITH_NON_BLOCKING_SUBSTITUTE_ID_ANOMALY` à T-15, exécution réelle à environ T-14 |
| Résultat post-match ENR-P12 | STA-01 `COMPLETE_REDUCED_SCHEMA` ; PLY-01 `COMPLETE_WITH_ANOMALIES` ; EVT-01 non exécuté faute d'autorisation explicite distincte |
| Contrôle transversal ENR-C01 à J-1 | `CONFIRMED_HISTORICAL_REBRAND_ALIAS` : brut `Hirnyk`, canonique `FC Kryvbas Kryvyi Rih` |
| Contrôle transversal ENR-C01 le jour du match | `NOT_EXECUTED` : fenêtre manquée, clôturée sans nouvel appel fournisseur |
| Incidents locaux récupérés | 3 : finalisation football-data.org P08 ; parseur PowerShell 7 avant P09 ; résolution Maven bloquée avant P12, sans appel fournisseur perdu |
| Automation LIN-01 du 13 août | `PAUSED` |
| Automation prématch du 14 août | `PAUSED` après la huitième échéance |
| Automation prématch du 15 août | `CLOSED_AND_DELETED` après la dernière échéance ; P09/P10 `COMPLETE`, P11 `ABSENT_AT_LAST_PREMATCH_CHECK` |
| Automation prématch du 16 août | `PAUSED` après P12 `COMPLETE` à T-15 ; T-5 fermé sans appel |

## Journal des appels

| Appel | Échantillon | Fenêtre | Famille | Scénarios | HTTP | Latence | Quota après appel | Replay |
|---|---|---|---|---|---:|---:|---:|---|
| ENR-P01-DETAIL-001 | ENR-P01 | FIRST_AUTHORIZED | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 1 056 ms | 99 / 100 | PASS |
| ENR-P01-LINEUP-001 | ENR-P01 | T-30, rattrapage réel à T-17 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 3 092 ms | 99 / 100 | PASS |
| ENR-P01-LINEUP-002 | ENR-P01 | HALF_TIME, rattrapage réel à T+53 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 956 ms | 98 / 100 | PASS |
| ENR-P01-STATISTICS-001 | ENR-P01 | LATE_POST_MATCH, environ T+243 | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 904 ms | 97 / 100 | PASS |
| ENR-P01-EVENTS-001 | ENR-P01 | LATE_POST_MATCH, environ T+243 | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 408 ms | 96 / 100 | PASS |
| ENR-P01-BOX-SCORE-001 | ENR-P01 | LATE_POST_MATCH, environ T+244 | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 2 565 ms | 95 / 100 | PASS |
| ENR-P03-DETAIL-001 | ENR-P03 | PRE_MATCH_BASELINE | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 768 ms | 94 / 100 | PASS |
| ENR-P04-DETAIL-001 | ENR-P04 | PRE_MATCH_BASELINE | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 650 ms | 93 / 100 | PASS |
| ENR-P05-DETAIL-001 | ENR-P05 | PRE_MATCH_BASELINE | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 1 037 ms | 92 / 100 | PASS |
| ENR-P03-LINEUP-001 | ENR-P03 | T-60, exécution réelle à environ T-58 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 409 ms | 99 / 100 | PASS |
| ENR-P04-LINEUP-001 | ENR-P04 | T-60, exécution réelle à environ T-57 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 601 ms | 98 / 100 | PASS |
| ENR-P03-LINEUP-002 | ENR-P03 | T-45, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 488 ms | 97 / 100 | PASS |
| ENR-P04-LINEUP-002 | ENR-P04 | T-45, exécution réelle à environ T-42 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 427 ms | 96 / 100 | PASS |
| ENR-P05-LINEUP-001 | ENR-P05 | T-60, exécution réelle à environ T-57 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 591 ms | 95 / 100 | PASS |
| ENR-P03-LINEUP-003 | ENR-P03 | T-30, exécution réelle à environ T-29 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 1 770 ms | 94 / 100 | PASS |
| ENR-P04-LINEUP-003 | ENR-P04 | T-30, exécution réelle à environ T-28 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 1 647 ms | 93 / 100 | PASS |
| ENR-P05-LINEUP-002 | ENR-P05 | T-45, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 4 489 ms | 92 / 100 | PASS |
| ENR-P03-LINEUP-004 | ENR-P03 | FOLLOW_UP_15M, exécution réelle à environ T-13 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 497 ms | 91 / 100 | PASS |
| ENR-P04-LINEUP-004 | ENR-P04 | FOLLOW_UP_15M, exécution réelle à environ T-13 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 495 ms | 90 / 100 | PASS |
| ENR-P05-LINEUP-003 | ENR-P05 | T-30, exécution réelle à environ T-28 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 503 ms | 89 / 100 | PASS |
| ENR-P03-LINEUP-005 | ENR-P03 | FOLLOW_UP_15M, diagnostic hors périmètre à environ T+1 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 500 ms | 88 / 100 | PASS |
| ENR-P04-LINEUP-005 | ENR-P04 | FOLLOW_UP_15M, diagnostic hors périmètre à environ T+1 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 383 ms | 87 / 100 | PASS |
| ENR-P05-LINEUP-004 | ENR-P05 | FOLLOW_UP_15M, exécution réelle à environ T-14 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 428 ms | 86 / 100 | PASS |
| ENR-P03-LINEUP-006 | ENR-P03 | FOLLOW_UP_15M, diagnostic hors périmètre à environ T+17 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 472 ms | 85 / 100 | PASS |
| ENR-P04-LINEUP-006 | ENR-P04 | FOLLOW_UP_15M, diagnostic hors périmètre à environ T+18 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 466 ms | 84 / 100 | PASS |
| ENR-P05-LINEUP-005 | ENR-P05 | COMPLETE_LATE, disponibilité tardive à environ T+3 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 456 ms | 83 / 100 | PASS |
| ENR-P03-LINEUP-007 | ENR-P03 | FOLLOW_UP_15M, diagnostic hors périmètre à environ T+33 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 2 921 ms | 82 / 100 | PASS |
| ENR-P04-LINEUP-007 | ENR-P04 | FOLLOW_UP_15M, diagnostic hors périmètre à environ T+33 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 3 243 ms | 81 / 100 | PASS |
| ENR-P03-DETAIL-002 | ENR-P03 | LIVE_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 500 ms | 80 / 100 | PASS |
| ENR-P03-LINEUP-008 | ENR-P03 | LIVE_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 602 ms | 79 / 100 | PASS |
| ENR-P03-STATISTICS-001 | ENR-P03 | LIVE_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 711 ms | 78 / 100 | PASS |
| ENR-P03-EVENTS-001 | ENR-P03 | LIVE_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 1 009 ms | 77 / 100 | PASS |
| ENR-P03-BOX-SCORE-001 | ENR-P03 | LIVE_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 916 ms | 76 / 100 | PASS |
| ENR-P04-DETAIL-002 | ENR-P04 | LIVE_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 410 ms | 75 / 100 | PASS |
| ENR-P04-LINEUP-008 | ENR-P04 | LIVE_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 474 ms | 74 / 100 | PASS |
| ENR-P04-STATISTICS-001 | ENR-P04 | LIVE_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 689 ms | 73 / 100 | PASS |
| ENR-P04-EVENTS-001 | ENR-P04 | LIVE_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 592 ms | 72 / 100 | PASS |
| ENR-P04-BOX-SCORE-001 | ENR-P04 | LIVE_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 416 ms | 71 / 100 | PASS |
| ENR-P05-DETAIL-002 | ENR-P05 | LIVE_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 2 743 ms | 70 / 100 | PASS |
| ENR-P05-LINEUP-006 | ENR-P05 | LIVE_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 1 422 ms | 69 / 100 | PASS |
| ENR-P05-STATISTICS-001 | ENR-P05 | LIVE_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 550 ms | 68 / 100 | PASS |
| ENR-P05-EVENTS-001 | ENR-P05 | LIVE_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 494 ms | 67 / 100 | PASS |
| ENR-P05-BOX-SCORE-001 | ENR-P05 | LIVE_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 677 ms | 66 / 100 | PASS |
| ENR-P03-DETAIL-003 | ENR-P03 | POST_MATCH_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 845 ms | 65 / 100 | PASS |
| ENR-P03-LINEUP-009 | ENR-P03 | POST_MATCH_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 657 ms | 64 / 100 | PASS |
| ENR-P03-STATISTICS-002 | ENR-P03 | POST_MATCH_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 486 ms | 63 / 100 | PASS |
| ENR-P03-EVENTS-002 | ENR-P03 | POST_MATCH_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 535 ms | 62 / 100 | PASS |
| ENR-P03-BOX-SCORE-002 | ENR-P03 | POST_MATCH_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 10 792 ms | 61 / 100 | PASS |
| ENR-P04-DETAIL-003 | ENR-P04 | POST_MATCH_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 828 ms | 60 / 100 | PASS |
| ENR-P04-LINEUP-009 | ENR-P04 | POST_MATCH_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 516 ms | 59 / 100 | PASS |
| ENR-P04-STATISTICS-002 | ENR-P04 | POST_MATCH_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 499 ms | 58 / 100 | PASS |
| ENR-P04-EVENTS-002 | ENR-P04 | POST_MATCH_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 460 ms | 57 / 100 | PASS |
| ENR-P04-BOX-SCORE-002 | ENR-P04 | POST_MATCH_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 613 ms | 56 / 100 | PASS |
| ENR-P03-DETAIL-004 | ENR-P03 | FOLLOW_UP_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 943 ms | 55 / 100 | PASS |
| ENR-P03-LINEUP-010 | ENR-P03 | FOLLOW_UP_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 501 ms | 54 / 100 | PASS |
| ENR-P03-STATISTICS-003 | ENR-P03 | FOLLOW_UP_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 409 ms | 53 / 100 | PASS |
| ENR-P03-EVENTS-003 | ENR-P03 | FOLLOW_UP_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 444 ms | 52 / 100 | PASS |
| ENR-P03-BOX-SCORE-003 | ENR-P03 | FOLLOW_UP_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 437 ms | 51 / 100 | PASS |
| ENR-P04-DETAIL-004 | ENR-P04 | FOLLOW_UP_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 751 ms | 50 / 100 | PASS |
| ENR-P04-LINEUP-010 | ENR-P04 | FOLLOW_UP_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 431 ms | 49 / 100 | PASS |
| ENR-P04-STATISTICS-003 | ENR-P04 | FOLLOW_UP_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 501 ms | 48 / 100 | PASS |
| ENR-P04-EVENTS-003 | ENR-P04 | FOLLOW_UP_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 414 ms | 47 / 100 | PASS |
| ENR-P04-BOX-SCORE-003 | ENR-P04 | FOLLOW_UP_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 483 ms | 46 / 100 | PASS |
| ENR-P05-DETAIL-003 | ENR-P05 | FOLLOW_UP_SNAPSHOT | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 849 ms | 45 / 100 | PASS |
| ENR-P05-LINEUP-007 | ENR-P05 | FOLLOW_UP_SNAPSHOT | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 455 ms | 44 / 100 | PASS |
| ENR-P05-STATISTICS-002 | ENR-P05 | FOLLOW_UP_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 416 ms | 43 / 100 | PASS |
| ENR-P05-EVENTS-002 | ENR-P05 | FOLLOW_UP_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 431 ms | 42 / 100 | PASS |
| ENR-P05-BOX-SCORE-002 | ENR-P05 | FOLLOW_UP_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 189 ms | 41 / 100 | PASS |
| ENR-P06-DETAIL-001 | ENR-P06 | T-60, exécution réelle à environ T-58 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 622 ms | 99 / 100 | PASS |
| ENR-P06-LINEUP-001 | ENR-P06 | T-60, exécution réelle à environ T-58 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 538 ms | 98 / 100 | PASS |
| ENR-P06-LINEUP-002 | ENR-P06 | T-45, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 541 ms | 97 / 100 | PASS |
| ENR-P06-LINEUP-003 | ENR-P06 | T-30, exécution réelle à environ T-28 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 752 ms | 96 / 100 | PASS |
| ENR-P07-DETAIL-001 | ENR-P07 | T-60, exécution réelle à environ T-57 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 1 762 ms | 95 / 100 | PASS |
| ENR-P07-LINEUP-001 | ENR-P07 | T-60, exécution réelle à environ T-57 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 412 ms | 94 / 100 | PASS |
| ENR-P07-LINEUP-002 | ENR-P07 | T-45, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 547 ms | 93 / 100 | PASS |
| ENR-P07-LINEUP-003 | ENR-P07 | T-30, exécution réelle à environ T-28 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 3 038 ms | 92 / 100 | PASS |
| ENR-P08-DETAIL-001 | ENR-P08 | T-60, exécution réelle à environ T-57 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 811 ms | 91 / 100 | PASS |
| ENR-P08-LINEUP-001 | ENR-P08 | T-60, exécution réelle à environ T-57 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 1 439 ms | 90 / 100 | PASS |
| ENR-P08-FD-DETAIL-001 | ENR-P08 | T-60, exécution réelle à environ T-55 | DETAIL football-data.org | ID-01, TIM-01, OPS-01, REP-01 | 200 récupéré | ≤ 2 298 ms | Non enregistré | PASS |
| ENR-P08-LINEUP-002 | ENR-P08 | T-45, exécution réelle à environ T-44 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 5 282 ms | 89 / 100 | PASS |
| ENR-P08-LINEUP-003 | ENR-P08 | T-30 planifié, exécution tardive à environ T-11 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 8 119 ms | 88 / 100 | PASS |
| ENR-P07-LINEUP-004 | ENR-P07 | HALF_TIME, collecte manuelle | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 461 ms | 87 / 100 | PASS |
| ENR-P06-STATISTICS-001 | ENR-P06 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 791 ms | 86 / 100 | PASS |
| ENR-P06-EVENTS-001 | ENR-P06 | POST_MATCH | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 511 ms | 85 / 100 | PASS |
| ENR-P06-BOX-SCORE-001 | ENR-P06 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 420 ms | 84 / 100 | PASS |
| ENR-P08-STATISTICS-001 | ENR-P08 | LIVE_SNAPSHOT | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 745 ms | 83 / 100 | PASS |
| ENR-P08-EVENTS-001 | ENR-P08 | LIVE_SNAPSHOT | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 454 ms | 82 / 100 | PASS |
| ENR-P08-BOX-SCORE-001 | ENR-P08 | LIVE_SNAPSHOT | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 291 ms | 81 / 100 | PASS |
| ENR-P07-STATISTICS-001 | ENR-P07 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 459 ms | 80 / 100 | PASS |
| ENR-P07-EVENTS-001 | ENR-P07 | POST_MATCH | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 431 ms | 79 / 100 | PASS |
| ENR-P07-BOX-SCORE-001 | ENR-P07 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 759 ms | 78 / 100 | PASS |
| ENR-P08-STATISTICS-002 | ENR-P08 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 746 ms | 77 / 100 | PASS |
| ENR-P08-EVENTS-002 | ENR-P08 | POST_MATCH | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 448 ms | 76 / 100 | PASS |
| ENR-P08-BOX-SCORE-002 | ENR-P08 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 572 ms | 75 / 100 | PASS |
| ENR-C01-DETAIL-001 | ENR-C01 | J-1 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 718 ms | 74 / 100 | PASS |
| ENR-P09-DETAIL-001 | ENR-P09 | T-60 planifié, exécution réelle à environ T-55 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 3 831 ms | 99 / 100 | PASS |
| ENR-P09-LINEUP-001 | ENR-P09 | T-60 planifié, exécution réelle à environ T-55 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 3 515 ms | 98 / 100 | PASS |
| ENR-P09-LINEUP-002 | ENR-P09 | T-45 planifié, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 731 ms | 97 / 100 | PASS |
| ENR-P09-LINEUP-003 | ENR-P09 | T-30 planifié, exécution réelle à environ T-29 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 1 526 ms | 96 / 100 | PASS |
| ENR-P10-DETAIL-001 | ENR-P10 | T-60, exécution réelle à environ T-57 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 544 ms | 95 / 100 | PASS |
| ENR-P10-LINEUP-001 | ENR-P10 | T-60, exécution réelle à environ T-57 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 499 ms | 94 / 100 | PASS |
| ENR-P11-DETAIL-001 | ENR-P11 | T-60, exécution réelle à environ T-56 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 413 ms | 93 / 100 | PASS |
| ENR-P11-LINEUP-001 | ENR-P11 | T-60, exécution réelle à environ T-56 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 421 ms | 92 / 100 | PASS |
| ENR-P10-FD-DETAIL-001 | ENR-P10 | T-60, exécution réelle à environ T-56 | DETAIL football-data.org | ID-01, TIM-01, OPS-01, REP-01 | 200 | 308 ms | Non enregistré | PASS |
| ENR-P11-FD-DETAIL-001 | ENR-P11 | T-60, exécution réelle à environ T-56 | DETAIL football-data.org | ID-01, TIM-01, OPS-01, REP-01 | 200 | 237 ms | Non enregistré | PASS |
| ENR-P10-LINEUP-002 | ENR-P10 | T-45, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 577 ms | 91 / 100 | PASS |
| ENR-P11-LINEUP-002 | ENR-P11 | T-45, exécution réelle à environ T-43 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 569 ms | 90 / 100 | PASS |
| ENR-P10-LINEUP-003 | ENR-P10 | T-30, exécution réelle à environ T-27 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 636 ms | 89 / 100 | PASS |
| ENR-P11-LINEUP-003 | ENR-P11 | T-30, exécution réelle à environ T-27 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 740 ms | 88 / 100 | PASS |
| ENR-P10-LINEUP-004 | ENR-P10 | T-15, exécution réelle à environ T-12 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 3 039 ms | 87 / 100 | PASS |
| ENR-P11-LINEUP-004 | ENR-P11 | T-15, exécution réelle à environ T-12 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 2 222 ms | 86 / 100 | PASS |
| ENR-P09-STATISTICS-001 | ENR-P09 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 670 ms | 85 / 100 | PASS |
| ENR-P09-EVENTS-001 | ENR-P09 | POST_MATCH | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 417 ms | 84 / 100 | PASS |
| ENR-P09-BOX-SCORE-001 | ENR-P09 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 3 665 ms | 83 / 100 | PASS |
| ENR-P10-STATISTICS-001 | ENR-P10 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 2 446 ms | 82 / 100 | PASS |
| ENR-P10-EVENTS-001 | ENR-P10 | POST_MATCH | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 3 047 ms | 81 / 100 | PASS |
| ENR-P10-BOX-SCORE-001 | ENR-P10 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 5 261 ms | 80 / 100 | PASS |
| ENR-P11-STATISTICS-001 | ENR-P11 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 2 464 ms | 79 / 100 | PASS |
| ENR-P11-EVENTS-001 | ENR-P11 | POST_MATCH | EVENTS | EVT-01, OPS-01, REP-01 | 200 | 2 986 ms | 78 / 100 | PASS |
| ENR-P11-BOX-SCORE-001 | ENR-P11 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 318 ms | 77 / 100 | PASS |
| ENR-P12-DETAIL-001 | ENR-P12 | T-60 planifié, exécution réelle à environ T-53 | DETAIL | ID-01, TIM-01, OPS-01, REP-01 | 200 | 572 ms | 99 / 100 | PASS |
| ENR-P12-LINEUP-001 | ENR-P12 | T-60 planifié, exécution réelle à environ T-52 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 908 ms | 98 / 100 | PASS |
| ENR-P12-LINEUP-002 | ENR-P12 | T-45 planifié, exécution réelle à environ T-44 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 2 456 ms | 97 / 100 | PASS |
| ENR-P12-LINEUP-003 | ENR-P12 | T-30 planifié, exécution réelle à environ T-29 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 497 ms | 96 / 100 | PASS |
| ENR-P12-LINEUP-004 | ENR-P12 | T-15 planifié, exécution réelle à environ T-14 | LINEUP | LIN-01, OPS-01, REP-01 | 200 | 386 ms | 95 / 100 | PASS |
| ENR-P12-STATISTICS-001 | ENR-P12 | POST_MATCH | STATISTICS | STA-01, OPS-01, REP-01 | 200 | 2 469 ms | 99 / 100 | PASS |
| ENR-P12-BOX-SCORE-001 | ENR-P12 | POST_MATCH | BOX_SCORE | PLY-01, OPS-01, REP-01 | 200 | 1 990 ms | 98 / 100 | PASS |

## ENR-P01 - Paris Saint-Germain - Aston Villa

### Baseline CAL-01

- collecte historique : 2026-08-09 à 19:57:07 UTC ;
- identifiant Highlightly : `1347698848` ;
- horaire : 2026-08-12 à 19:00 UTC, soit 21:00 Europe/Paris ;
- statut : `Not started` ;
- SHA-256 du fichier calendrier : `1fe7f1e644b9a688ec892c4269fb880ce435aa2c81b8624b6180cfad8e786c23` ;
- appels supplémentaires consommés pour le rattachement : 0.

### Collecte de détail FIRST_AUTHORIZED

- début de l'appel : 2026-08-11 à 12:17:52.645 UTC ;
- HTTP : 200 ;
- latence : 1 056 ms ;
- taille brute : 5 454 octets ;
- SHA-256 : `f259680705587e3ca5547a2534707a1a1f9a3a5150cd90a09bde9178f78ccf1d` ;
- quota exposé : 99 requêtes restantes sur 100 ;
- replay : `PASS`, 0 appel réseau ;
- fuite exacte de la clé dans les trois preuves : aucune ;
- en-tête d'authentification persisté : aucun.

### ID-01

| Champ | Attendu | Observé | Résultat |
|---|---|---|---|
| Identifiant match | 1347698848 | 1347698848 | PASS |
| Équipe 1 | Paris Saint-Germain, alias Paris Saint Germain/PSG | Paris Saint Germain, rôle HOME | PASS |
| Équipe 2 | Aston Villa | Aston Villa, rôle AWAY | PASS |
| Compétition | UEFA Super Cup, identifiant 452665 | UEFA Super Cup, identifiant 452665 | PASS |
| Saison | 2026 | 2026 | PASS |
| Phase | Finale | Final | PASS |

Conclusion ID-01 : aucune anomalie. L'alias fournisseur `Paris Saint Germain` est explicitement accepté par le manifeste. L'ordre fournisseur est conservé ; l'identité canonique de cette rencontre neutre ne dépend pas de cet ordre.

### TIM-01

| Champ | Attendu | Observé | Résultat |
|---|---|---|---|
| Coup d'envoi UTC | 2026-08-12T19:00:00Z | 2026-08-12T19:00:00.000Z | PASS |
| Coup d'envoi Europe/Paris | 12 août 2026 à 21:00 | 12 août 2026 à 21:00 | PASS |
| Statut | Rencontre non commencée | Not started | PASS |

Conclusion TIM-01 : aucun changement d'horaire ni de statut par rapport à CAL-01.

### Données complémentaires présentes dans le détail

Le fournisseur retourne également, sans notation de complétude à cette fenêtre :

- le stade `Red Bull Arena`, Salzburg, Austria, capacité déclarée `30188` ;
- un arbitre déclaré `Artan, Omar Abdulkadir`, nationalité `Somalia` ;
- des structures `forecast`, `predictions`, `events`, `statistics` et `news`.

Ces champs sont conservés dans le brut. Ils ne sont pas notés dans cette collecte ID-01/TIM-01 : les scénarios LIN-01, STA-01, EVT-01 et PLY-01 restent associés à leurs fenêtres dédiées.

### Preuves locales

Répertoire hors Git :

[ENR-P01-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json)

- `detail.raw.json` ;
- `detail.metadata.json` ;
- `detail.replay.json`.

### LIN-01 - rattrapage du 12 août 2026

Les réveils prévus à 20:00, 20:15 et 20:30 Europe/Paris n'ont pas été exécutés. La tâche locale avait enregistré un horaire avec fuseau `Europe/Paris`, mais l'application l'affichait à 22:00 après conversion. Ce décalage a été confirmé à 20:37, après le passage des trois fenêtres. La tâche erronée a été supprimée avant toute exécution à 22:00.

Une instabilité inhabituelle de la connexion locale a par ailleurs été signalée à partir d'environ 19:45. Elle est conservée comme facteur de contexte susceptible d'avoir perturbé le suivi ou les réveils ultérieurs, sans être retenue comme cause démontrée par les preuves techniques disponibles. Elle ne remplace pas la cause déjà établie du décalage des trois fenêtres initiales : la double conversion de fuseau de la tâche planifiée.

Un seul appel de rattrapage a été exécuté, sans reconstituer artificiellement les trois fenêtres manquées :

- fenêtre portée par la preuve : `T-30` ;
- heure réelle de début : 2026-08-12 à 18:43:06.731 UTC, soit 20:43:06 Europe/Paris ;
- position réelle par rapport au coup d'envoi de 21:00 : environ T-17 ;
- endpoint : `/lineups/1347698848` ;
- HTTP : 200 ;
- latence : 3 092 ms ;
- taille brute : 341 octets ;
- quota exposé après l'appel : 99 requêtes restantes sur 100 ;
- SHA-256 : `d49f68603095e6eaef544b4089aca1c4f4dc12f298ea725410921c08ffe82c77` ;
- replay : `PASS`, avec 0 appel réseau ;
- contrôle de secrets du dépôt : `PASS` ;
- en-tête d'authentification persisté : aucun.

Résultat LIN-01 :

| Équipe | Identité | Formation | Titulaires | Remplaçants |
|---|---|---|---:|---:|
| Paris Saint Germain | Conforme à l'alias accepté | `Unknown` | 0 | 0 |
| Aston Villa | Conforme | `Unknown` | 0 | 0 |

La classification de cette preuve est `ABSENT` : les deux équipes sont identifiées, mais les listes `initialLineup` et `substitutes` sont vides pour chacune. La fenêtre T+15 est ensuite passée sans exécution et sans preuve associée.

Preuve locale hors Git :

[ENR-P01-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json)

### LIN-01 - collecte de rattrapage à la mi-temps

À la suite du constat utilisateur que la rencontre était à la mi-temps, un nouvel appel unique a été exécuté et enregistré sous une fenêtre réelle distincte `HALF_TIME`. Il ne reconstitue ni T-60, ni T-45, ni T-30, ni T+15.

- heure réelle de début : 2026-08-12 à 19:53:20.189 UTC, soit 21:53:20 Europe/Paris ;
- position par rapport au coup d'envoi de 21:00 : environ T+53, pendant la mi-temps ;
- endpoint : `/lineups/1347698848` ;
- HTTP : 200 ;
- latence : 956 ms ;
- taille brute : 3 702 octets ;
- quota exposé après l'appel : 98 requêtes restantes sur 100 ;
- SHA-256 : `0c0b46180d41026850a7642dec8813ec6252a00dce2c23c2468ec8512fcaca53` ;
- replay : `PASS`, avec 0 appel réseau ;
- en-tête d'authentification persisté : aucun.

Résultat LIN-01 :

| Équipe | Identité | Formation | Titulaires | Remplaçants |
|---|---|---|---:|---:|
| Paris Saint Germain | Conforme à l'alias accepté | `4-3-3` | 11 | 12 |
| Aston Villa | Conforme | `4-2-3-1` | 11 | 12 |

La classification est `COMPLETE` : les deux équipes attendues sont identifiées sans ambiguïté et chacune possède exactement onze titulaires. Les listes de remplaçants et les formations sont également présentes. Aucune autre collecte de composition n'est requise pour ENR-P01.

Preuve locale hors Git :

[ENR-P01-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json)

## État des fenêtres LIN-01

| Heure Europe/Paris | Fenêtre | Condition | Action maximale |
|---|---|---|---|
| 12 août 2026 à 20:00 | T-60 | Fenêtre manquée à cause du décalage de planification | 0 appel |
| 12 août 2026 à 20:15 | T-45 | Fenêtre manquée à cause du décalage de planification | 0 appel |
| 12 août 2026 à 20:30 | T-30 | Fenêtre manquée ; rattrapage unique à 20:43, classé `ABSENT` | 1 appel `/lineups/1347698848` |
| 12 août 2026 à 21:15 | T+15 | Fenêtre passée sans exécution ; aucune preuve produite | 0 appel |
| 12 août 2026 à 21:53 | HALF_TIME | Rattrapage réel à la mi-temps, classé `COMPLETE` | 1 appel `/lineups/1347698848` |

Les futures collectes commencent par l'inspection des preuves existantes. Une composition complète provoque un `SKIP` des fenêtres restantes sans consommation de quota. Les horaires futurs sont enregistrés sous forme d'instants UTC explicites afin d'éviter la double conversion observée le 12 août.

## Collectes post-match du 13 août 2026

Les trois familles post-match ont été collectées immédiatement après l'autorisation utilisateur. La rencontre ayant débuté le 12 août à 21:00 Europe/Paris, les appels effectués à partir de 01:03 le 13 août se situent environ T+243 par rapport au coup d'envoi, au-delà de la fenêtre nominale T+90 à T+180. La preuve porte donc la fenêtre honnête `LATE_POST_MATCH` ; aucune heure antérieure n'est reconstituée.

Le quota fournisseur avait été réinitialisé pour la journée du 13 août : les trois appels ont fait passer la valeur exposée de 98 / 100 lors de la dernière collecte du 12 août à respectivement 97 / 100, 96 / 100 et 95 / 100. Le garde-fou local a compté 0, 1 puis 2 appels déjà enregistrés avant chaque requête et la réserve opérationnelle de 20 appels est restée intacte.

### STA-01 - statistiques d'équipe

- début : 2026-08-12 à 23:03:33.172 UTC, soit 2026-08-13 à 01:03:33 Europe/Paris ;
- endpoint : `/statistics/1347698848` ;
- HTTP : 200 ;
- latence : 904 ms ;
- taille brute : 3 756 octets ;
- quota exposé après l'appel : 97 / 100 ;
- SHA-256 : `cc078ed0fd7ae4987478ea67c8bebc24213542cd0d63bfa0adc0da3b9ae62fc4` ;
- replay : `PASS`, avec 0 appel réseau.

Les deux équipes possèdent exactement les mêmes 40 statistiques et aucune valeur n'est nulle. Les zéros explicites restent distingués d'un champ absent : Paris Saint Germain possède notamment 0 corner, 0 tir bloqué, 0 carton jaune et 0 carton rouge.

| Mesure | Paris Saint Germain | Aston Villa |
|---|---:|---:|
| Possession | 61 % | 39 % |
| Expected Goals | 1,03 | 2,13 |
| Tirs cadrés | 6 | 4 |
| Tirs non cadrés | 6 | 8 |
| Tirs bloqués | 0 | 3 |
| Corners | 0 | 2 |
| Fautes | 11 | 13 |
| Cartons jaunes | 0 | 3 |
| Cartons rouges | 0 | 0 |
| Passes totales | 588 | 368 |
| Passes réussies | 525 | 289 |

Classification STA-01 : `COMPLETE`. Cette classification porte sur la présence, la symétrie du schéma et l'absence de confusion entre zéro et valeur absente ; elle ne constitue pas une validation indépendante de chaque valeur sportive.

Preuve locale hors Git :

[ENR-P01-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json)

### EVT-01 - événements

- début : 2026-08-12 à 23:03:34.945 UTC, soit 2026-08-13 à 01:03:34 Europe/Paris ;
- endpoint : `/events/1347698848` ;
- HTTP : 200 ;
- latence : 408 ms ;
- taille brute : 3 981 octets ;
- quota exposé après l'appel : 96 / 100 ;
- SHA-256 : `62df599df8409aa7bfac393ac0210fdf75a7c9a376b623ef575ecafc064ec191` ;
- replay : `PASS`, avec 0 appel réseau.

La réponse contient 16 événements ordonnés chronologiquement de la 20e à la 88e minute :

| Type | Nombre |
|---|---:|
| Buts | 3 |
| Remplacements | 10 |
| Cartons jaunes | 3 |

Les buts retournés sont ceux de Khvicha Kvaratskhelia à la 20e minute, Brian Madjo à la 45e et Désiré Doué à la 61e. La chronologie produit un score événementiel Highlightly de 2–1 pour Paris Saint Germain. Le nombre de buts, de passes décisives et de cartons jaunes concorde avec les agrégats des statistiques d'équipe et des statistiques joueurs.

Classification EVT-01 : `COMPLETE`. La chronologie est structurellement complète et cohérente entre les trois réponses Highlightly collectées ; son exactitude absolue n'a pas encore été contrôlée contre une source sportive indépendante.

Preuve locale hors Git :

[ENR-P01-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json)

### PLY-01 - statistiques joueurs

- début : 2026-08-12 à 23:03:36.200 UTC, soit 2026-08-13 à 01:03:36 Europe/Paris ;
- endpoint : `/box-score/1347698848` ;
- HTTP : 200 ;
- latence : 2 565 ms ;
- taille brute : 45 205 octets ;
- quota exposé après l'appel : 95 / 100 ;
- SHA-256 : `7e5f88ce0860471d20ea4d2a1671fedb56527c58ed2d80a09d2d3c236f0981c5` ;
- replay : `PASS`, avec 0 appel réseau.

| Mesure | Paris Saint Germain | Aston Villa |
|---|---:|---:|
| Joueurs retournés | 23 | 23 |
| Joueurs avec au moins une minute | 16 | 16 |
| Joueurs ayant joué avec une note | 16 / 16 | 16 / 16 |
| Joueurs inutilisés | 7 | 7 |
| Champs statistiques par joueur | 37 | 37 |
| Buts agrégés | 2 | 1 |
| Passes décisives agrégées | 2 | 1 |
| Cartons jaunes agrégés | 0 | 3 |
| Nom complet nul | 0 | 1 |

Les champs comprennent notamment minutes, note, tirs, passes, dribbles, duels, actions défensives, cartons, xG, xA et métriques spécifiques aux gardiens. Les valeurs nulles de métriques non applicables restent présentes dans le schéma et ne sont pas transformées en zéro.

Trois catégories d'anomalies internes sont confirmées. Elles sont acceptées comme limites fournisseur non bloquantes pour la poursuite d'ENR-001 : aucune correction du brut fournisseur n'est tentée.

#### Anomalie confirmée - `cardsSecondYellow`

Pau Torres, João Gomes et John McGinn possèdent chacun `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`. La chronologie Highlightly place leurs avertissements aux 51e, 64e et 72e minutes. Une vérification manuelle utilisateur fondée sur le visionnage intégral de la rencontre puis sur SofaScore confirme que :

- chacun des trois joueurs a reçu exactement un carton jaune ;
- les trois cartons ont été distribués en seconde mi-temps ;
- aucun carton, quelle que soit sa couleur, n'a été distribué en première mi-temps ;
- aucun des trois joueurs n'a été expulsé.

Cette concordance avait d'abord rendu plausible que `cardsSecondYellow` représente le nombre de cartons jaunes reçus en seconde mi-temps. Le snapshot intramatch de Shelbourne - Ajax du 13 août invalide cette hypothèse : Rodrigo Freitas, Aaron Bouwman et Marcos Leonardo ont été avertis aux 27e, 23e et 25e minutes, mais possèdent eux aussi `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`.

La [documentation officielle du box-score Highlightly](https://highlightly.net/football-api/documentation/) définit `cardsSecondYellow` comme le nombre de seconds cartons jaunes ayant entraîné un carton rouge ; `cardsRed` dénombre séparément les cartons rouges directs. Pour un seul avertissement sans expulsion, la combinaison attendue est donc `cardsYellow=1`, `cardsSecondYellow=0`, `cardsRed=0`. Pour une expulsion après deux avertissements, un seul événement est un « second jaune » : `cardsSecondYellow` devrait donc valoir 1, et non 2. La documentation ne permet pas encore d'établir empiriquement si le second avertissement est inclus dans `cardsYellow`, même si sa définition littérale suggère un total de deux cartons jaunes.

Les six joueurs concernés sur ENR-P01 et ENR-P05 n'ayant reçu qu'un avertissement et n'ayant pas été expulsés, leur valeur `cardsSecondYellow=1` contredit la définition officielle. Le champ est classé `INVALID_SECOND_YELLOW_VALUE`. Il est conservé brut pour audit, mais ne peut alimenter une expulsion sans concordance avec le flux d'événements.

#### Anomalies confirmées

1. Trois joueurs déclarés avec 0 minute possèdent des métriques attendues non nulles : Ilya Zabarnyi et Ibrahim Mbaye ont chacun `expectedGoals=0.09`, tandis que Kosta Nedeljković possède `expectedGoals=0.09`, `expectedAssists=0.19` et `expectedGoalsOnTarget=0.55`. La vérification manuelle utilisateur confirme qu'aucun de ces trois joueurs n'était titulaire et qu'aucun n'est entré en jeu. L'anomalie est donc confirmée. Ces valeurs doivent être conservées comme données brutes mais exclues des agrégats métier lorsque `minutesPlayed=0`, tant que leur origine n'est pas expliquée.
2. Le champ `fullName` de Triston Rowe est nul, bien que son identifiant, son nom court, son poste et son numéro soient présents. La vérification utilisateur confirme que `Triston` est son prénom et `Rowe` son nom de famille. L'identité canonique reste donc exploitable sans ambiguïté par repli vers le champ `name="Triston Rowe"`. La valeur brute `fullName=null` n'est pas modifiée.

Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`. La couverture des joueurs ayant participé et la profondeur du schéma satisfont le scénario, mais les trois catégories d'anomalies confirmées interdisent une ingestion aveugle des champs concernés.

Disposition : `ACCEPTED_NON_BLOCKING`. Les métriques attendues des joueurs à 0 minute sont exclues des agrégats ; l'identité de Triston Rowe utilise le champ `name` lorsque `fullName` est nul. Ces mesures limitent l'impact sans prétendre résoudre les défauts de la source.

Preuve locale hors Git :

[ENR-P01-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json)

## Conclusion provisoire ENR-P01

Les cinq familles d'endpoint autorisées ont désormais été exercées au moins une fois pour ENR-P01. ID-01, TIM-01, LIN-01, STA-01, EVT-01, OPS-01 et REP-01 disposent de résultats exploitables. PLY-01 est exploitable sous réserve des trois catégories d'anomalies confirmées. Aucune nouvelle collecte n'est nécessaire immédiatement pour Paris Saint-Germain - Aston Villa ; la suite d'ENR-001 porte sur les autres rencontres principales de l'échantillon et sur la vérification de la répétabilité des constats.

## Prochain lot de collecte - 13 août 2026

| Rôle | Échantillon | Rencontre | Coup d'envoi Europe/Paris | Identifiant Highlightly |
|---|---|---|---|---:|
| Principale | ENR-P03 | Anderlecht - PAOK | 20:30 | 1368039450 |
| Principale | ENR-P04 | Rangers - Jagiellonia | 20:30 | 1360605965 |
| Principale | ENR-P05 | Shelbourne - Ajax | 20:45 | 1368087106 |
| Réserve P03/P04 | ENR-R01 | Hearts - Benfica | 20:45 | 1368042854 |

Hearts - Benfica reste dormant tant que P03 et P04 sont exploitables. Son enrichissement complet requiert une activation explicite conformément au manifeste accepté.

### Contrôle prématch ID-01/TIM-01

Les trois rencontres principales ont fait l'objet d'un appel de détail à partir de 01:51 Europe/Paris :

| Échantillon | Participants observés | Compétition / tour | Coup d'envoi observé | Statut | Résultat |
|---|---|---|---|---|---|
| ENR-P03 | Anderlecht - PAOK | UEFA Europa League / 3rd Qualifying Round | 18:30Z / 20:30 Paris | Not started | PASS |
| ENR-P04 | Rangers FC - Jagiellonia | UEFA Europa League / 3rd Qualifying Round | 18:30Z / 20:30 Paris | Not started | PASS |
| ENR-P05 | Shelbourne - Ajax | UEFA Europa Conference League / 3rd Qualifying Round | 18:45Z / 20:45 Paris | Not started | PASS |

L'alias accepté `Rangers FC` est correctement rattaché à Rangers. Les trois identifiants, compétitions, participants et horaires sont conformes au manifeste. P03 et P04 étant exploitables, ENR-R01 reste dormant et aucun appel fournisseur n'a été consommé pour Hearts - Benfica.

Preuves locales hors Git :

- [ENR-P03-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P05-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json).

| Heure Europe/Paris | Instant UTC explicite | Action conditionnelle |
|---|---|---|
| 19:30 | 17:30Z | LIN-01 T-60 pour P03 et P04 |
| 19:45 | 17:45Z | LIN-01 T-45 pour P03/P04 si nécessaire ; LIN-01 T-60 pour P05 |
| 20:00 | 18:00Z | LIN-01 T-30 pour P03/P04 si nécessaire ; LIN-01 T-45 pour P05 si nécessaire |
| 20:15 | 18:15Z | premier suivi à 15 minutes pour P03/P04 si nécessaire ; LIN-01 T-30 pour P05 si nécessaire |
| 20:30 | 18:30Z | arrêt de P03/P04 à leur coup d'envoi ; dernier contrôle possible de P05 avant son coup d'envoi, uniquement si non `COMPLETE` |

Les six responsabilités de planification acceptées par l'utilisateur ont d'abord été implémentées par une automation heartbeat consolidée, car l'application Codex n'autorise qu'une seule automation heartbeat active par tâche. Cette automation porte l'identifiant `enr-001-p03-p04-t-60` et le nom `ENR-001 LIN-01 P03-P05 toutes les 15 min`. La planification initiale comportait 18 occurrences jusqu'à 23:45, mais cette borne était incorrecte pour un besoin strictement prématch. Après clarification du périmètre le 13 août à 21:08 Europe/Paris, l'automation a été mise au statut `PAUSED` et toutes les occurrences restantes ont été annulées.

Lors de sa première persistance, la récurrence avait de nouveau enregistré `19:30` comme heure technique, que l'application a interprétée comme 19:30 UTC et affichée à 21:30 Europe/Paris. La capture utilisateur a permis d'identifier l'erreur avant toute échéance. La tâche existante a été corrigée le 13 août à 02:31 Europe/Paris, sans création d'un doublon : son premier instant technique a été fixé à 17:30 UTC, correspondant à 19:30 Europe/Paris le 13 août 2026.

La règle corrigée impose désormais deux conditions d'arrêt par rencontre : une composition `COMPLETE` supprime les appels ultérieurs, et l'atteinte de l'heure de coup d'envoi arrête les appels même si la composition demeure absente ou incomplète. Une composition apparue après T0 est conservée comme preuve `COMPLETE_LATE`, mais ne valide pas LIN-01. Les appels postérieurs à T0 déjà effectués le 13 août restent conservés pour audit et sont explicitement hors périmètre prématch. Hearts - Benfica reste exclu sans activation explicite.

### Exécution LIN-01 T-60 - 13 août 2026

Le heartbeat prévu à 19:30 Europe/Paris a été reçu à 19:31:27, soit avec un retard de 1 minute et 27 secondes. Après inspection des preuves et tentative initiale bloquée par le bac à sable réseau, les deux appels autorisés ont été exécutés sans retry fournisseur :

| Échantillon | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | 19:32:31 | environ T-58 | 200 | 409 ms | 99 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | 19:32:55 | environ T-57 | 200 | 601 ms | 98 / 100 | 0 / 0 | `ABSENT` |

Pour Anderlecht - PAOK comme pour Rangers FC - Jagiellonia, les deux équipes attendues sont correctement identifiées, mais `formation` vaut `Unknown` et les listes `initialLineup` et `substitutes` sont vides. Les deux replays hors réseau sont `PASS`, les empreintes SHA-256 correspondent et aucun en-tête d'authentification n'est persisté. Les classifications `ABSENT` rendent P03 et P04 éligibles au contrôle T-45 de 19:45. P05 n'était pas éligible à 19:30 et n'a consommé aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json).

### Exécution LIN-01 T-45 P03/P04 et T-60 P05 - 13 août 2026

Le heartbeat prévu à 19:45 Europe/Paris a été reçu à 19:46:57, soit avec un retard de 1 minute et 57 secondes. L'inspection préalable a confirmé que les dernières preuves P03 et P04 étaient `ABSENT` et qu'aucune preuve lineup n'existait encore pour P05. Les trois appels conditionnels ont donc été exécutés :

| Échantillon | Fenêtre | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | T-45 | 19:47:29 | environ T-43 | 200 | 488 ms | 97 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | T-45 | 19:47:47 | environ T-42 | 200 | 427 ms | 96 / 100 | 0 / 0 | `ABSENT` |
| ENR-P05 | T-60 | 19:48:03 | environ T-57 | 200 | 591 ms | 95 / 100 | 0 / 0 | `ABSENT` |

Les réponses P03 et P04 sont byte pour byte identiques à leurs preuves T-60 respectives. Pour Shelbourne - Ajax, les deux équipes sont correctement identifiées, mais les formations valent `Unknown` et les listes `initialLineup` et `substitutes` sont vides. Les trois replays sont `PASS` et aucune donnée d'authentification n'est persistée. P03 et P04 restent éligibles au contrôle T-30 de 20:00 ; P05 reste éligible au contrôle T-45 de 20:00. ENR-R01 n'a fait l'objet d'aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P05-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json).

### Exécution LIN-01 T-30 P03/P04 et T-45 P05 - 13 août 2026

Le heartbeat prévu à 20:00 Europe/Paris a été reçu à 20:00:57, soit avec un retard de 57 secondes. L'inspection préalable a confirmé que P03, P04 et P05 étaient toujours `ABSENT`. Les trois appels conditionnels ont été exécutés :

| Échantillon | Fenêtre | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | T-30 | 20:01:26 | environ T-29 | 200 | 1 770 ms | 94 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | T-30 | 20:01:46 | environ T-28 | 200 | 1 647 ms | 93 / 100 | 0 / 0 | `ABSENT` |
| ENR-P05 | T-45 | 20:02:02 | environ T-43 | 200 | 4 489 ms | 92 / 100 | 0 / 0 | `ABSENT` |

Chaque réponse est byte pour byte identique aux preuves antérieures de la même rencontre : équipes correctement identifiées, formations `Unknown`, compositions et remplaçants vides. Les trois replays sont `PASS` et aucune donnée d'authentification n'est persistée. P03 et P04 restent ouverts pour le premier suivi `FOLLOW_UP_15M` de 20:15 ; P05 reste éligible au contrôle T-30 de 20:15. ENR-R01 n'a fait l'objet d'aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P05-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Exécution LIN-01 de 20:15 - premier suivi P03/P04 et T-30 P05

Le heartbeat prévu à 20:15 Europe/Paris a été reçu à 20:16:27, soit avec un retard de 1 minute et 27 secondes. L'inspection préalable a confirmé que P03, P04 et P05 étaient toujours `ABSENT`. Les trois appels conditionnels ont été exécutés :

| Échantillon | Fenêtre | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | FOLLOW_UP_15M | 20:16:51 | environ T-13 | 200 | 497 ms | 91 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | FOLLOW_UP_15M | 20:17:03 | environ T-13 | 200 | 495 ms | 90 / 100 | 0 / 0 | `ABSENT` |
| ENR-P05 | T-30 | 20:17:14 | environ T-28 | 200 | 503 ms | 89 / 100 | 0 / 0 | `ABSENT` |

Chaque réponse est byte pour byte identique aux preuves antérieures de la même rencontre : équipes correctement identifiées, formations `Unknown`, compositions et remplaçants vides. Les trois replays sont `PASS` et aucune donnée d'authentification n'est persistée. P03, P04 et P05 restent toutes éligibles au contrôle récurrent `FOLLOW_UP_15M` de 20:30. ENR-R01 n'a fait l'objet d'aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P05-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

### Exécution LIN-01 récurrente de 20:30 - P03 à P05

Le heartbeat prévu à 20:30 Europe/Paris a été reçu à 20:30:27, soit avec un retard de 27 secondes. L'inspection préalable a confirmé que P03, P04 et P05 étaient toujours `ABSENT`. Les trois appels `FOLLOW_UP_15M` ont été exécutés :

| Échantillon | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | 20:30:57 | environ T+1 | 200 | 500 ms | 88 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | 20:31:08 | environ T+1 | 200 | 383 ms | 87 / 100 | 0 / 0 | `ABSENT` |
| ENR-P05 | 20:31:18 | environ T-14 | 200 | 428 ms | 86 / 100 | 0 / 0 | `ABSENT` |

Chaque réponse est byte pour byte identique aux preuves antérieures de la même rencontre : équipes correctement identifiées, formations `Unknown`, compositions et remplaçants vides. P03 et P04 ne possèdent donc toujours aucune composition après leur coup d'envoi théorique ; P05 reste sans composition à environ T-14. Les trois replays sont `PASS` et aucune donnée d'authentification n'est persistée. P03, P04 et P05 restent éligibles au contrôle récurrent de 20:45. ENR-R01 n'a fait l'objet d'aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-005](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-005](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P05-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json).

### Exécution LIN-01 récurrente de 20:45 - P03 à P05

Le heartbeat prévu à 20:45 Europe/Paris a été reçu à 20:46:58, soit avec un retard de 1 minute et 58 secondes. L'inspection préalable a confirmé que P03, P04 et P05 étaient encore `ABSENT`. Les trois appels `FOLLOW_UP_15M` ont été exécutés :

| Échantillon | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | 20:47:23 | environ T+17 | 200 | 472 ms | 85 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | 20:47:34 | environ T+18 | 200 | 466 ms | 84 / 100 | 0 / 0 | `ABSENT` |
| ENR-P05 | 20:47:45 | environ T+3 | 200 | 456 ms | 83 / 100 | 11 / 11 | `COMPLETE_LATE` |

P03 et P04 renvoient encore les mêmes payloads vides : équipes identifiées, formations `Unknown`, compositions et remplaçants absents. Ces appels ont été effectués après T0 et sont requalifiés en diagnostics hors périmètre prématch.

Pour P05, l'empreinte et la taille de la réponse changent. `initialLineup` est structuré par lignes tactiques imbriquées : ses quatre éléments de premier niveau ne sont pas quatre joueurs, mais les quatre lignes de la formation. Leur aplatissement donne exactement 11 joueurs uniques pour Shelbourne et 11 pour Ajax. Shelbourne est fourni en `3-5-2` avec 8 remplaçants ; Ajax en `4-3-3` avec 12 remplaçants. La donnée est structurellement complète, mais elle n'a été obtenue qu'environ trois minutes après T0 : P05 est donc classé `COMPLETE_LATE` et ne valide pas le besoin prématch LIN-01.

Les trois replays sont `PASS` et aucune donnée d'authentification n'est persistée. ENR-R01 n'a fait l'objet d'aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-006](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-006](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P05-LINEUP-005](evidence/enr-001-evidence-index-v0.1.json).

### Exécution LIN-01 récurrente de 21:00 - P03/P04, P05 ignoré

Le heartbeat prévu à 21:00 Europe/Paris a été reçu à 21:01:58, soit avec un retard de 1 minute et 58 secondes. Le contrôle préalable a aplati les lignes tactiques de chaque dernière preuve et compté les identifiants uniques : P03 et P04 restaient `ABSENT`, tandis que P05 était confirmé `COMPLETE` avec 11 titulaires uniques par équipe. P05 a donc été ignoré sans appel et sans consommation de quota.

| Échantillon | Action | Début réel Europe/Paris | Position réelle | HTTP | Latence | Quota restant | Titulaires domicile / extérieur | Classification |
|---|---|---|---|---:|---:|---:|---:|---|
| ENR-P03 | FOLLOW_UP_15M | 21:02:36 | environ T+33 | 200 | 2 921 ms | 82 / 100 | 0 / 0 | `ABSENT` |
| ENR-P04 | FOLLOW_UP_15M | 21:02:54 | environ T+33 | 200 | 3 243 ms | 81 / 100 | 0 / 0 | `ABSENT` |
| ENR-P05 | `SKIP_COMPLETE_LATE` | — | environ T+17 | — | — | aucun appel | 11 / 11 dans la dernière preuve | `COMPLETE_LATE` |

P03 et P04 renvoient encore des payloads byte pour byte identiques à toutes leurs preuves antérieures : équipes identifiées, formations `Unknown`, compositions et remplaçants vides. Ces deux appels postérieurs à T0 sont conservés pour audit, mais ne participent pas à la mesure de réussite prématch. P05 a été ignoré sans appel. Les deux replays produits sont `PASS`, aucune donnée d'authentification n'est persistée et ENR-R01 n'a fait l'objet d'aucun appel.

Preuves locales hors Git :

- [ENR-P03-LINEUP-007](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P04-LINEUP-007](evidence/enr-001-evidence-index-v0.1.json).

### Correction de périmètre et clôture LIN-01 - 13 août 2026

La clarification utilisateur reçue après le contrôle de 21:00 établit que LIN-01 vise exclusivement la disponibilité des compositions avant le coup d'envoi. Le fait que les rencontres P03 et P04 aient déjà commencé — avec, selon le constat utilisateur, deux buts déjà marqués dans Anderlecht - PAOK — confirme que les contrôles postérieurs ne répondent plus au besoin métier.

La dernière preuve strictement antérieure à T0 donne le résultat final suivant :

| Échantillon | Dernière preuve avant T0 | État à cette preuve | Première preuve complète | Résultat prématch LIN-01 |
|---|---|---|---|---|
| ENR-P03 | 20:16:51, environ T-13 | 0 / 0 titulaire | aucune | `ABSENT_AT_KICKOFF` |
| ENR-P04 | 20:17:03, environ T-13 | 0 / 0 titulaire | aucune | `ABSENT_AT_KICKOFF` |
| ENR-P05 | 20:31:18, environ T-14 | 0 / 0 titulaire | 20:47:45, environ T+3 | `COMPLETE_LATE` |

Le taux de réussite prématch LIN-01 sur ce lot est donc de **0 / 3**. Les appels P03/P04 exécutés à partir de 20:30 et l'appel P05 de 20:47 sont conservés comme preuves de disponibilité postérieure, mais ils ne valident pas le scénario. L'automation `enr-001-p03-p04-t-60` a été mise au statut `PAUSED` à 21:08 Europe/Paris ; aucun déclenchement à partir de 21:15 ne doit avoir lieu.

## Snapshot intramatch P03 à P05 - 13 août 2026

### Périmètre et horodatage

Une collecte ponctuelle a été autorisée par l'utilisateur afin d'identifier les données effectivement disponibles pendant les trois rencontres. Au moment de la demande, les positions communiquées étaient la 69e minute pour Anderlecht - PAOK, la 71e minute pour Rangers FC - Jagiellonia et la 53e minute pour Shelbourne - Ajax.

Les quinze appels ont réellement été exécutés entre 22:00:20 et 22:01:23 Europe/Paris. Les réponses de détail indiquent respectivement les 70e, 71e et 55e minutes. Cet écart d'une à deux minutes est conservé explicitement : les positions communiquées décrivent l'instant de la demande, tandis que les horodatages et chronomètres fournisseur décrivent l'instant de chaque preuve.

Ce snapshot est distinct de LIN-01. Il ne transforme ni `ABSENT_AT_KICKOFF` pour P03/P04 ni `COMPLETE_LATE` pour P05 en succès prématch. L'automation `enr-001-p03-p04-t-60` reste `PAUSED`.

### Couverture obtenue à l'instant T

| Échantillon | État du détail | Composition | Statistiques d'équipe | Événements | Statistiques joueurs |
|---|---|---|---|---|---|
| ENR-P03 | 70e, Anderlecht 3-1 PAOK | `ABSENT`, 0 / 0 titulaire | 39 champs par équipe | 9 événements | réponse vide `[]` |
| ENR-P04 | 71e, Rangers FC 1-1 Jagiellonia | `ABSENT`, 0 / 0 titulaire | 13 champs par équipe | 8 événements | réponse vide `[]` |
| ENR-P05 | 55e, Shelbourne 1-2 Ajax | `COMPLETE_LATE`, 11 / 11 titulaires | 40 champs par équipe | 7 événements | 19 joueurs Shelbourne et 23 joueurs Ajax |

Les cinq familles d'endpoint autorisées répondent en HTTP 200 pour les trois rencontres. Le contenu est toutefois hétérogène : le détail, les statistiques agrégées et les événements sont exploitables sur les trois matches ; les compositions et le box-score joueurs ne sont disponibles que sur P05.

### Détail des données live

| Échantillon | Score live | Buts ou penalties enregistrés | Cartons jaunes | Remplacements | Tirs domicile / extérieur |
|---|---:|---:|---:|---:|---:|
| ENR-P03 | 3-1 | 4 | 1 | 4 | 15 / 9 |
| ENR-P04 | 1-1 | 2, dont 1 penalty | 3 | 3 | non fournis dans le détail |
| ENR-P05 | 1-2 | 3 | 3 | 1 | 8 / 14 |

P03 expose notamment une possession de 47 % / 53 %, des xG de 1,91 / 1,29 et 9 / 2 tirs cadrés. P04 expose une statistique plus réduite de 13 champs par équipe : possession de 50 % / 50 %, 3 / 3 tirs cadrés et 5 / 1 corners, sans xG, fautes ni total de passes. P05 expose une possession de 16 % / 84 %, des xG de 0,40 / 0,42, 2 / 5 tirs cadrés et 2 / 10 corners.

Une incohérence de fraîcheur interne apparaît pour P04 : le flux d'événements contient trois cartons jaunes de Jagiellonia aux 46e, 63e et 70e minutes, tandis que l'endpoint de statistiques n'en agrège encore que deux. À cet instant précis, l'événementiel est donc plus récent que l'agrégat d'équipe.

Le payload de détail contient déjà l'état, le score, les événements, des statistiques, les tirs et des prédictions live. Pour un futur contrôle live contraint par le quota, un appel de détail peut constituer le premier niveau de collecte ; les endpoints spécialisés ne sont nécessaires que pour obtenir un schéma stable, contrôler la fraîcheur ou récupérer les statistiques joueurs.

### PLY-01 P05 et anomalies observées

Le box-score P05 contient 19 joueurs pour Shelbourne et 23 pour Ajax, avec 37 champs statistiques par joueur. Onze joueurs Shelbourne et douze joueurs Ajax possèdent un temps de jeu strictement positif au moment de la preuve ; onze joueurs de chaque équipe possèdent déjà une note.

Les limites fournisseur déjà observées sur ENR-P01 se reproduisent :

- cinq joueurs ont `fullName=null` : Kerr McInroy, Rodrigo Freitas, Killian Ryan, Joey Ikala Wuna et Abdellah Ouazane ;
- cinq joueurs à zéro minute possèdent au moins une métrique attendue non nulle : Killian Ryan, Sean Moore, Daniel Ring, Joeri Heerkens et Davy Klaassen ;
- les minutes du box-score sont légèrement moins fraîches que le chronomètre du détail.

Le snapshot invalide en outre l'hypothèse selon laquelle `cardsSecondYellow` désignerait un carton reçu en seconde période. Rodrigo Freitas, Aaron Bouwman et Marcos Leonardo ont été avertis aux 27e, 23e et 25e minutes, donc en première période, mais chacun possède `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`. La documentation officielle établit que le champ désigne un second avertissement ayant entraîné une expulsion. Comme aucun des trois joueurs n'a été expulsé, le champ est classé `INVALID_SECOND_YELLOW_VALUE` et ne doit produire une expulsion métier qu'après concordance avec l'événementiel.

### Exploitabilité et quotas

- appels du snapshot : 15 / 15 en HTTP 200 ;
- preuves brutes : 15 / 15 conservées ;
- replays hors réseau : 15 / 15 `PASS` ;
- quota fournisseur restant après le dernier appel : 66 / 100 ;
- compteur opérationnel local après le dernier appel : 40 / 80 ;
- réserve de sécurité de 20 appels : préservée ;
- fuite de secret ou persistance de l'en-tête d'authentification : aucune.

Preuves locales hors Git :

- ENR-P03 : [ENR-P03-DETAIL-002](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P03-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json) ;
- ENR-P04 : [ENR-P04-DETAIL-002](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P04-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json) ;
- ENR-P05 : [ENR-P05-DETAIL-002](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P05-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json).

## Snapshot post-match P03 et P04 - 13 août 2026

### Exécution et intégrité

Après confirmation utilisateur de la fin des deux rencontres, dix appels ont été exécutés entre 22:30:54 et 22:31:21 Europe/Paris : `detail`, `lineup`, `statistics`, `events` et `box-score` pour P03 puis P04. La fenêtre réelle `POST_MATCH_SNAPSHOT` est conservée ; aucune position T+ arbitraire n'est reconstituée.

- réponses HTTP 200 : 10 / 10 ;
- preuves brutes conservées : 10 / 10 ;
- replays hors réseau `PASS` : 10 / 10 ;
- empreintes SHA-256 concordantes : 10 / 10 ;
- quota fournisseur restant après le dernier appel : 56 / 100 ;
- compteur opérationnel local après le dernier appel : 50 / 80 ;
- réserve de sécurité de 20 appels : préservée ;
- fuite de secret ou en-tête d'authentification persisté : aucun.

### ENR-P03 - Anderlecht 3-2 PAOK

Le détail indique `Finished`, horloge 90 et score final 3-2. Le flux événementiel contient 23 événements :

| Type | Nombre |
|---|---:|
| Buts | 4 |
| Penalty marqué | 1 |
| Cartons jaunes | 5 |
| Cartons rouges | 2 |
| Remplacements | 10 |
| But annulé par la VAR pour hors-jeu | 1 |

La chronologie de score est constituée de L. Petrot à la 19e pour Anderlecht, A. Živković à la 34e pour PAOK, L. Ambros à la 47e et D. Sikan à la 55e pour Anderlecht, puis A. Jeremejeff sur penalty à la 88e pour PAOK. Les deux expulsions PAOK concernent A. Živković à la 89e et A. Jeremejeff à 90+2. Aucun avertissement antérieur n'est attribué à ces deux joueurs dans le flux ; les événements sont donc compatibles avec deux rouges directs.

| Statistique finale | Anderlecht | PAOK |
|---|---:|---:|
| Possession | 45 % | 55 % |
| Expected Goals | 1,91 | 2,15 |
| Tirs cadrés | 9 | 4 |
| Tirs non cadrés | 6 | 6 |
| Tirs bloqués | 0 | 3 |
| Corners | 2 | 8 |
| Fautes | 12 | 8 |
| Cartons jaunes | 3 | 2 |
| Cartons rouges | 0 | 2 |
| Passes totales | 362 | 433 |

L'endpoint de statistiques conserve 39 champs par équipe. Les agrégats de cartons concordent avec les cinq jaunes et les deux rouges du flux d'événements. En revanche, les tableaux de tirs intégrés au détail, qui contenaient 15 / 9 éléments à la 70e minute, sont vides dans le détail post-match ; l'endpoint spécialisé de statistiques reste donc nécessaire après le statut `Finished`.

La composition est toujours strictement identique à toutes les preuves P03 précédentes : équipes identifiées, formations `Unknown`, 0 / 0 titulaire et aucun remplaçant. Le box-score joueurs retourne encore `[]`, après une latence inhabituellement élevée de 10 792 ms. P03 est donc `ABSENT_POST_MATCH` pour la composition et `EMPTY_POST_MATCH` pour PLY-01.

### ENR-P04 - Rangers FC 1-1 Jagiellonia

Le détail indique `Finished`, horloge 90 et score final 1-1. Le flux contient 16 événements :

| Type | Nombre |
|---|---:|
| Penalty marqué | 1 |
| But | 1 |
| Cartons jaunes | 5 |
| Remplacements | 9 |

L. Shankland ouvre le score sur penalty à la 19e minute pour Rangers FC et N. Prelec égalise à la 65e pour Jagiellonia, sur une passe d'A. Klynge. Aucun carton rouge n'est présent.

| Statistique finale | Rangers FC | Jagiellonia |
|---|---:|---:|
| Possession | 50 % | 50 % |
| Tirs cadrés | 3 | 4 |
| Tirs non cadrés | 7 | 3 |
| Tirs bloqués | 3 | 0 |
| Corners | 6 | 2 |
| Cartons jaunes dans l'agrégat | 1 | 3 |
| Cartons rouges | 0 | 0 |

Le schéma reste limité à 13 champs par équipe, sans xG, fautes ni passes. Les trois avertissements de Jagiellonia concordent désormais entre l'agrégat et l'événementiel. Une incohérence finale subsiste pour Rangers FC : le flux contient deux cartons jaunes distincts à la 73e minute, pour V. Dragojević et D. McInnes, tandis que l'agrégat n'en compte qu'un. Le décalage observé en direct n'était donc pas seulement un retard de mise à jour ; la divergence persiste après `Finished`.

La composition P04 reste elle aussi byte pour byte identique au payload vide antérieur : formations `Unknown`, 0 / 0 titulaire et aucun remplaçant. Le box-score joueurs retourne `[]`. P04 est donc `ABSENT_POST_MATCH` pour la composition et `EMPTY_POST_MATCH` pour PLY-01.

### Conclusions du snapshot post-match

1. Les scores finaux, événements et statistiques d'équipe sont disponibles pour P03 et P04 immédiatement après la fin des rencontres.
2. La profondeur statistique reste dépendante du match : 39 champs par équipe sur P03 contre seulement 13 sur P04.
3. Les compositions et statistiques joueurs ne sont pas garanties, même après `Finished` : elles restent totalement absentes sur les deux rencontres.
4. L'événementiel doit rester prioritaire pour le contrôle disciplinaire : il est cohérent avec les agrégats P03, mais révèle un carton jaune Rangers FC absent de l'agrégat P04.
5. Le détail peut perdre certaines données après le match, comme les tableaux de tirs P03 ; les endpoints spécialisés doivent donc être conservés pour le snapshot final.
6. Ces constats ne changent pas LIN-01 : P03 et P04 demeurent `ABSENT_AT_KICKOFF`, et aucune composition postérieure ne pourrait valider le besoin prématch.

Preuves locales hors Git :

- ENR-P03 : [ENR-P03-DETAIL-003](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P03-BOX-SCORE-002](evidence/enr-001-evidence-index-v0.1.json) ;
- ENR-P04 : [ENR-P04-DETAIL-003](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P04-BOX-SCORE-002](evidence/enr-001-evidence-index-v0.1.json).

## Snapshot de suivi P03 à P05 - 13 août 2026

### Exécution et objectif

Une nouvelle collecte complète a été autorisée afin de mesurer la publication différée de P03/P04 et d'obtenir l'état final de P05. Les quinze appels ont été exécutés entre 23:23:28 et 23:23:55 Europe/Paris sous la fenêtre `FOLLOW_UP_SNAPSHOT`.

- réponses HTTP 200 : 15 / 15 ;
- preuves brutes conservées : 15 / 15 ;
- replays hors réseau `PASS` : 15 / 15 ;
- empreintes SHA-256 concordantes : 15 / 15 ;
- quota fournisseur restant après le dernier appel : 41 / 100 ;
- compteur opérationnel local après le dernier appel : 65 / 80 ;
- réserve de sécurité de 20 appels : préservée ;
- automation `enr-001-p03-p04-t-60` : toujours `PAUSED`.

### Matrice de disponibilité

| Échantillon | Détail | Composition | Statistiques d'équipe | Événements | Box-score joueurs |
|---|---|---|---|---|---|
| ENR-P03 | `Finished`, 3-2 | toujours 0 / 0, `PERSISTENTLY_ABSENT` | 39 champs/équipe, révisés | 23, inchangés | toujours `[]`, `PERSISTENTLY_EMPTY` |
| ENR-P04 | `Finished`, 1-1 | toujours 0 / 0, `PERSISTENTLY_ABSENT` | 13 champs/équipe, inchangés | 16, inchangés | toujours `[]`, `PERSISTENTLY_EMPTY` |
| ENR-P05 | `Finished`, 2-2 | 11 / 11, `COMPLETE_LATE` | 40 champs/équipe | 21 | 19 / 23 joueurs, `COMPLETE_WITH_ANOMALIES` |

### Publication différée P03 et P04

Environ 52 minutes après le premier snapshot post-match, aucune donnée de composition ou de joueur n'est apparue pour P03 ou P04. Les empreintes des compositions et box-scores sont identiques à toutes les preuves antérieures : formations `Unknown`, titulaires et remplaçants vides, puis réponse `[]` pour PLY-01.

Les chronologies sont elles aussi byte pour byte identiques au premier snapshot final : 23 événements pour P03 et 16 pour P04. Les statistiques P04 sont entièrement inchangées, y compris la divergence finale entre deux cartons jaunes Rangers FC dans l'événementiel et un seul dans l'agrégat.

Les statistiques P03 ont en revanche fait l'objet d'une révision différée après `Finished`, sans changement du score ni de la chronologie. Seize valeurs ont changé. Les principales corrections sont :

| Statistique | Première valeur finale | Valeur de suivi |
|---|---:|---:|
| Possession Anderlecht | 45 % | 44 % |
| Possession PAOK | 55 % | 56 % |
| Passes réussies Anderlecht | 302 | 301 |
| Passes échouées Anderlecht | 60 | 61 |
| Passes totales PAOK | 433 | 438 |
| Passes réussies PAOK | 366 | 368 |
| Passes échouées PAOK | 67 | 70 |

D'autres ajustements portent sur les dribbles, touches, dégagements, passes longues et tacles. Les xG, tirs cadrés, corners, fautes et agrégats disciplinaires restent inchangés. Une statistique collectée immédiatement après `Finished` doit donc être considérée comme révisable ; un second snapshot différé est utile lorsque la précision finale est requise.

### État final P05 - Shelbourne 2-2 Ajax

Le détail indique `Finished`, horloge 90 et score final 2-2. Le flux contient 21 événements :

| Type | Nombre |
|---|---:|
| Buts | 4 |
| Cartons jaunes | 6 |
| Remplacements | 10 |
| But annulé par la VAR pour hors-jeu | 1 |

La chronologie de score est constituée de J. Brandt à la 20e pour Ajax, R. Freitas à la 24e pour Shelbourne, A. Gaaei à la 39e pour Ajax et S. Moore à la 85e pour Shelbourne. Un but de Shelbourne est annulé pour hors-jeu à la 65e minute.

| Statistique finale | Shelbourne | Ajax |
|---|---:|---:|
| Possession | 28 % | 72 % |
| Expected Goals | 0,69 | 1,13 |
| Expected Assists | 0,47 | 1,52 |
| Tirs cadrés | 5 | 7 |
| Tirs non cadrés | 5 | 6 |
| Tirs bloqués | 2 | 6 |
| Corners | 4 | 11 |
| Fautes | 10 | 12 |
| Cartons jaunes | 4 | 2 |
| Cartons rouges | 0 | 0 |
| Passes totales | 264 | 695 |
| Passes réussies | 211 | 638 |

La composition est byte pour byte identique à la première version complète obtenue à T+3 : Shelbourne en `3-5-2`, Ajax en `4-3-3`, avec exactement 11 titulaires uniques par équipe. Elle demeure `COMPLETE_LATE` et ne valide pas LIN-01.

Le box-score final contient toujours 19 joueurs Shelbourne et 23 joueurs Ajax, mais désormais 16 joueurs avec un temps de jeu positif et une note pour chaque équipe. Les agrégats de buts et de passes décisives concordent avec l'événementiel : 2 buts et 2 passes pour Shelbourne, 2 buts et 1 passe pour Ajax.

### Anomalies joueurs P05 finales

Les cinq valeurs `fullName=null` persistent pour Kerr McInroy, Rodrigo Freitas, Killian Ryan, Joey Ikala Wuna et Abdellah Ouazane.

Les métriques attendues attribuées à des joueurs à zéro minute évoluent entre les snapshots, ce qui confirme leur caractère instable :

- Killian Ryan : `xG=0.02`, `xA=0.09`, `xGOT=0.29` ;
- Joeri Heerkens : `xG=0.02`, `xA=0.06`, `xGOT=0.15` ;
- Lucas Rosa : `xG=0.36`, `xA=0.03`, `xGOT=0.17` ;
- Davy Klaassen : `xA=0.02`.

Sean Moore et Daniel Ring ne relèvent plus de cette anomalie parce qu'ils sont finalement entrés en jeu ; Lucas Rosa apparaît en revanche comme nouveau cas à zéro minute. L'exclusion des métriques attendues lorsque `minutesPlayed=0` reste obligatoire.

La valeur erronée `cardsSecondYellow=1` se reproduit pour les cinq joueurs dont le jaune est présent dans le box-score : Jonathan Lunney, Rodrigo Freitas, James Roche, Aaron Bouwman et Marcos Leonardo. Aucun n'a été expulsé ; `INVALID_SECOND_YELLOW_VALUE` reste confirmé.

Une quatrième catégorie d'anomalie PLY-01 est confirmée : Sean Gannon reçoit un carton jaune à 90+2 dans le flux d'événements et l'agrégat d'équipe compte bien quatre jaunes Shelbourne, mais sa ligne joueur indique `cardsYellow=0`, `cardsSecondYellow=0`, `cardsRed=0`. Le box-score totalise donc seulement trois jaunes Shelbourne. Cette divergence est classée `MISSING_PLAYER_YELLOW_CARD`. L'événementiel et l'agrégat d'équipe priment sur le box-score individuel pour ce carton.

### Conclusions de suivi

1. P03/P04 ne présentent aucune publication différée des compositions ou statistiques joueurs jusqu'à ce contrôle ; leur absence doit être considérée comme persistante pour ce lot.
2. Les statistiques d'équipe peuvent être révisées après `Finished`, comme le démontrent les seize corrections P03 ; une seconde collecte différée améliore donc la valeur d'archive.
3. Cette révision n'est pas systématique : les statistiques et événements P04 sont strictement inchangés.
4. Le résultat final P05 est exploitable sur les dimensions équipe et événement, et le box-score est structurellement complet pour les participants, mais reste `COMPLETE_WITH_ANOMALIES`.
5. Les tirs intégrés au détail sont vides pour les trois rencontres terminées ; l'endpoint spécialisé de statistiques est indispensable au snapshot final.
6. La chronologie événementielle reste la source disciplinaire prioritaire en cas de contradiction avec les agrégats ou le box-score joueur.

### Conclusion consolidée sur l'origine et le périmètre des irrégularités

Les contrôles techniques écartent, pour les anomalies observées, une altération introduite par le collecteur : les réponses HTTP sont conservées octet pour octet, leurs empreintes concordent, les replays hors réseau sont valides, les identifiants de match et d'équipe sont corrects et aucun échec de parsing n'a transformé une donnée présente en donnée absente. Les absences, valeurs incohérentes et révisions temporelles existent directement dans les payloads Highlightly. Elles sont donc classées comme limites ou irrégularités de données fournisseur, et non comme anomalies produites par l'application locale.

Le découpage par compétition du lot P03 à P05 est le suivant :

| Échantillon | Compétition canonique | Compétition Highlightly | League ID | Composition avant T0 | Première composition complète | Box-score final |
|---|---|---|---:|---|---|---|
| ENR-P03 | Ligue Europa | UEFA Europa League | `3337` | absente | aucune | `[]` |
| ENR-P04 | Ligue Europa | UEFA Europa League | `3337` | absente | aucune | `[]` |
| ENR-P05 | Ligue Conférence | UEFA Europa Conference League | `722432` | absente | environ T+3 | disponible |

Dans cet échantillon, les compositions restées définitivement manquantes concernent donc exclusivement les deux rencontres de Ligue Europa. La rencontre de Ligue Conférence Shelbourne - Ajax présente un retard incompatible avec le besoin prématch, mais pas une absence définitive : sa composition devient complète à T+3 et son box-score joueurs est disponible pendant puis après le match.

Le même alignement apparaît pour les statistiques joueurs : P03/P04 renvoient toujours `[]`, tandis que P05 renvoie un box-score structurellement complet, quoique affecté par quatre catégories d'anomalies de contenu. Cela suggère qu'une absence de composition chez Highlightly s'accompagne d'une absence des données joueurs dépendantes pour la rencontre concernée.

La conclusion est enregistrée sous la forme `PROVIDER_DATA_IRREGULARITY` avec un signal `COMPETITION_CORRELATED_COVERAGE_GAP`. Sa portée reste limitée au lot observé : deux matches de Ligue Europa contre un match de Ligue Conférence, tous au troisième tour qualificatif retour. Elle ne permet pas encore d'affirmer que toutes les rencontres de Ligue Europa sont dépourvues de compositions, ni que toutes les rencontres de Ligue Conférence sont correctement couvertes.

Le troisième tour qualificatif retour est désormais terminé pour les deux compétitions. Aucune nouvelle rencontre de cette même phase ne peut donc compléter le lot ou permettre une réplication à périmètre constant. Les prochaines échéances auront lieu la semaine suivante dans le cadre des barrages aller de Ligue Europa et de Ligue Conférence.

Le prochain contrôle devra constituer une nouvelle strate `BARRAGES_ALLER`, et non une répétition du troisième tour. Il réutilisera les mêmes critères et fenêtres strictement antérieures à T0 afin de rendre les résultats comparables. L'interprétation suivra trois cas :

1. compositions de Ligue Europa encore absentes et compositions de Ligue Conférence disponibles : renforcement du signal lié à la compétition ;
2. compositions disponibles dans les deux compétitions : signal possiblement propre au troisième tour qualificatif retour ou à son intégration fournisseur ;
3. résultats hétérogènes au sein d'une même compétition : couverture vraisemblablement déterminée au niveau de la rencontre ou de l'intégration source, plutôt qu'au seul niveau de la compétition.

Aucune automation n'est créée avant la définition du nouvel échantillon, la confirmation des rencontres et de leurs horaires, ainsi que l'attribution de nouveaux identifiants d'échantillon.

Preuves locales hors Git :

- ENR-P03 : [ENR-P03-DETAIL-004](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P03-BOX-SCORE-003](evidence/enr-001-evidence-index-v0.1.json) ;
- ENR-P04 : [ENR-P04-DETAIL-004](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P04-BOX-SCORE-003](evidence/enr-001-evidence-index-v0.1.json) ;
- ENR-P05 : [ENR-P05-DETAIL-003](evidence/enr-001-evidence-index-v0.1.json) à [ENR-P05-BOX-SCORE-002](evidence/enr-001-evidence-index-v0.1.json).

## Collecte pré-match P06 - 14 août 2026

### Fenêtre T-60 - Al-Hilal - Al-Faisaly

Le heartbeat attendu vers 19:00 Europe/Paris a été reçu à 19:00:29, soit 29 secondes après l'heure métier. La fenêtre T-60 reste valide. L'inspection préalable n'a trouvé aucune preuve ENR-P06 du jour et aucun appel enregistré dans le lot du 14 août.

Le détail Highlightly a ensuite été collecté à 19:01:52 Europe/Paris :

- endpoint : `/matches/1364134211` ;
- HTTP : 200 ;
- latence : 622 ms ;
- quota restant : 99 / 100 ;
- SHA-256 : `b9c442547111a6040a906831dbdcb25ac4580f374e15d859391dc2f2535d4802` ;
- replay hors réseau : `PASS` ;
- rencontre confirmée : Al-Hilal Saudi - Al-Faisaly ;
- mapping explicite appliqué : Al-Hilal Saudi vers Al-Hilal ;
- rôles : Al-Hilal Saudi à domicile, Al-Faisaly à l'extérieur ;
- compétition fournisseur : `Pro League`, saison 2026, ligue `262041` ;
- horaire : `2026-08-14T18:00:00Z`, soit 20:00 Europe/Paris ;
- état : `Not started`.

La composition Highlightly a été collectée à 19:02:27 Europe/Paris :

- endpoint : `/lineups/1364134211` ;
- HTTP : 200 ;
- latence : 538 ms ;
- quota restant : 98 / 100 ;
- SHA-256 : `ae00125c722203eeb4beacc881edfc59a44311d07ab55a8853ea5855e1e82414` ;
- replay hors réseau : `PASS` ;
- Al-Hilal Saudi : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Al-Faisaly : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 : `ABSENT`.

Les tableaux `initialLineup` sont réellement vides des deux côtés ; l'absence ne résulte donc ni d'un défaut d'aplatissement des lignes tactiques ni d'une déduplication incorrecte. Deux appels Highlightly ont été consommés et la réserve quotidienne de 20 appels reste préservée. ENR-P06 demeure éligible au contrôle conditionnel T-45.

Preuves locales hors Git :

- détail : [ENR-P06-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) ;
- composition : [ENR-P06-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json).

### Fenêtre T-45 - Al-Hilal - Al-Faisaly

Le heartbeat attendu vers 19:15 Europe/Paris a été reçu à 19:15:59, soit 59 secondes après l'heure métier. L'inspection préalable a confirmé que la seule preuve de composition disponible était celle de T-60, classée `ABSENT`.

L'appel conditionnel Highlightly a été exécuté à 19:16:38 Europe/Paris :

- endpoint : `/lineups/1364134211` ;
- HTTP : 200 ;
- latence : 541 ms ;
- quota restant : 97 / 100 ;
- SHA-256 : `ae00125c722203eeb4beacc881edfc59a44311d07ab55a8853ea5855e1e82414` ;
- replay hors réseau : `PASS` ;
- Al-Hilal Saudi : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Al-Faisaly : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 : `ABSENT`.

Le payload est byte pour byte identique à celui de T-60. Aucun début de publication de la composition n'est donc observable à T-45. Le compteur opérationnel local atteint 3 appels sur 80, le quota fournisseur indique 97 / 100 et la réserve de 20 appels reste préservée. ENR-P06 demeure éligible au dernier contrôle T-30.

Preuve locale hors Git : [ENR-P06-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Fenêtre T-30 - Al-Hilal - Al-Faisaly

Le heartbeat attendu vers 19:30 Europe/Paris a été reçu à 19:31:28, soit 1 minute et 28 secondes après l'heure métier. L'inspection préalable a confirmé que la preuve T-45 restait classée `ABSENT` et présentait toujours l'empreinte du payload vide.

Le dernier appel de composition autorisé avant le coup d'envoi a été exécuté à 19:31:54 Europe/Paris, soit environ T-28 :

- endpoint : `/lineups/1364134211` ;
- HTTP : 200 ;
- latence : 752 ms ;
- quota restant : 96 / 100 ;
- SHA-256 : `04dad826559881c7b0a5d95a5c2491c57393638b223cd9fadcd285ea9b91bba4` ;
- replay hors réseau : `PASS` ;
- Al-Hilal Saudi : formation `4-4-2`, 11 titulaires après aplatissement récursif et déduplication, 9 remplaçants ;
- Al-Faisaly : formation `4-3-3`, 11 titulaires structurés sur quatre lignes tactiques, 9 remplaçants.

Le changement d'empreinte par rapport aux fenêtres T-60 et T-45 confirme que la composition a été publiée entre T-45 et T-30. Les deux équipes disposent sportivement de leurs onze titulaires avant le coup d'envoi. Une anomalie d'identité fournisseur demeure toutefois côté Al-Faisaly : le titulaire Abdulrahman Al-Shereaf possède `id=null`. Deux remplaçants, Saud Haddad et Abdullah Bin Subayt, possèdent également `id=null`.

La classification opérationnelle LIN-01 est donc `COMPLETE_WITH_ID_ANOMALY` : la composition répond au besoin prématch, mais la règle stricte d'unicité fondée exclusivement sur un identifiant non nul n'est pas satisfaite sans réserve. L'anomalie `MISSING_PLAYER_ID` est enregistrée pour ID-01. ENR-P06 est fermé pour les appels de composition et aucun appel supplémentaire ne doit être exécuté après cette fenêtre. Le compteur opérationnel local atteint 4 / 80 ; la réserve quotidienne de 20 appels reste préservée.

Preuve locale hors Git : [ENR-P06-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

## Collecte pré-match P07 - 14 août 2026

### Fenêtre T-60 - Saint-Étienne - Clermont

Le heartbeat attendu vers 19:45 Europe/Paris a été reçu à 19:46:59, soit 1 minute et 59 secondes après l'heure métier. La fenêtre reste valide, à environ T-58. L'inspection préalable n'a trouvé aucune preuve ENR-P07 du jour.

Le détail Highlightly a été collecté à 19:47:38 Europe/Paris :

- endpoint : `/matches/1321127224` ;
- HTTP : 200 ;
- latence : 1 762 ms ;
- quota restant : 95 / 100 ;
- SHA-256 : `c992bc4f2f4bcc5a6896833df7c525d3a03ca3d9a30e1722b179d22ae1309c96` ;
- replay hors réseau : `PASS` ;
- rencontre confirmée : Saint-Étienne à domicile contre Clermont Foot à l'extérieur ;
- l'accent de `Saint-Étienne` est préservé et le mapping explicite Clermont Foot vers Clermont est appliqué ;
- compétition fournisseur : `Ligue 2`, ligue `53546`, tour `Regular Season - 2` ;
- horaire : `2026-08-14T18:45:00Z`, soit 20:45 Europe/Paris ;
- état : `Not started`.

La composition Highlightly a été collectée à 19:47:53 Europe/Paris, soit environ T-57 :

- endpoint : `/lineups/1321127224` ;
- HTTP : 200 ;
- latence : 412 ms ;
- quota restant : 94 / 100 ;
- SHA-256 : `611e6d9903fa73c43896432b910f95d3e7bab3d3568fb0cd014eab71af1c131e` ;
- replay hors réseau : `PASS` ;
- Saint-Étienne : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Clermont Foot : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 T-60 : `ABSENT`.

Les tableaux `initialLineup` sont vides pour les deux équipes. L'absence ne résulte donc ni de l'imbrication tactique ni de la déduplication. Le compteur opérationnel local atteint 6 / 80, le quota fournisseur indique 94 / 100 et la réserve quotidienne de 20 appels reste préservée. ENR-P07 demeure éligible au contrôle conditionnel T-45 de 20:00 Europe/Paris.

Preuves locales hors Git :

- détail : [ENR-P07-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) ;
- composition : [ENR-P07-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json).

### Fenêtre T-45 - Saint-Étienne - Clermont

Le heartbeat attendu vers 20:00 Europe/Paris a été reçu à 20:01:29, soit 1 minute et 29 secondes après l'heure métier. L'inspection préalable a confirmé que la preuve T-60 demeurait `ABSENT`. P06 étant fermé et P08 n'étant pas encore éligible, aucun autre appel n'a été effectué pendant cette occurrence.

L'appel conditionnel Highlightly a été exécuté à 20:01:55 Europe/Paris, soit environ T-43 :

- endpoint : `/lineups/1321127224` ;
- HTTP : 200 ;
- latence : 547 ms ;
- quota restant : 93 / 100 ;
- SHA-256 : `611e6d9903fa73c43896432b910f95d3e7bab3d3568fb0cd014eab71af1c131e` ;
- replay hors réseau : `PASS` ;
- Saint-Étienne : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Clermont Foot : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 T-45 : `ABSENT`.

L'empreinte est identique à celle de T-60 : aucun début de publication n'est observable. Le compteur opérationnel local atteint 7 / 80, le quota fournisseur indique 93 / 100 et la réserve quotidienne de 20 appels reste préservée. ENR-P07 demeure éligible à son dernier contrôle T-30 de 20:15 Europe/Paris.

Preuve locale hors Git : [ENR-P07-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Fenêtre T-30 - Saint-Étienne - Clermont

Le heartbeat attendu vers 20:15 Europe/Paris a été reçu à 20:15:59, soit 59 secondes après l'heure métier. L'inspection préalable a confirmé que P07 restait `ABSENT` à T-45.

Le dernier appel de composition autorisé avant le coup d'envoi a été exécuté à 20:17:07 Europe/Paris, soit environ T-28 :

- endpoint : `/lineups/1321127224` ;
- HTTP : 200 ;
- latence : 3 038 ms ;
- quota restant : 92 / 100 ;
- SHA-256 : `611e6d9903fa73c43896432b910f95d3e7bab3d3568fb0cd014eab71af1c131e` ;
- replay hors réseau : `PASS` ;
- Saint-Étienne : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Clermont Foot : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 T-30 : `ABSENT`.

Le payload est byte pour byte identique aux preuves T-60 et T-45. P07 est donc clôturé en `ABSENT_AT_LAST_PREMATCH_CHECK` : la composition n'a pas été obtenue à la dernière fenêtre autorisée. Aucun appel de composition ne doit être exécuté après T-30 ni après le coup d'envoi.

Preuve locale hors Git : [ENR-P07-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

## Collecte pré-match P08 - 14 août 2026

### Fenêtre T-60 - Sporting CP - Vitória SC

La même occurrence, reçue à 20:15:59 Europe/Paris, a ouvert P08. L'inspection préalable n'a trouvé aucune preuve P08 du jour. Les collectes ont été exécutées environ 57 à 55 minutes avant le coup d'envoi de 21:15.

Le détail Highlightly a été collecté à 20:17:25 Europe/Paris :

- endpoint : `/matches/1340719797` ;
- HTTP : 200 ;
- latence : 811 ms ;
- quota restant : 91 / 100 ;
- SHA-256 : `f3c20eb1acbd738daecd006bcb3642e21b9a11ed980af9f13522a4f8616dc226` ;
- replay hors réseau : `PASS` ;
- équipes brutes : Sporting CP à domicile contre Guimaraes à l'extérieur ;
- mappings explicites appliqués : Sporting CP vers Sporting CP et Guimaraes vers Vitória SC ;
- compétition : `Primeira Liga`, ligue `80778`, tour `Regular Season - 2` ;
- horaire : `2026-08-14T19:15:00Z`, soit 21:15 Europe/Paris ;
- état : `Not started`.

La composition Highlightly a été collectée à 20:17:37 Europe/Paris :

- endpoint : `/lineups/1340719797` ;
- HTTP : 200 ;
- latence : 1 439 ms ;
- quota restant : 90 / 100 ;
- SHA-256 : `966dc045d9a75c9eddbf5a587c1f888d34a5bc9c455be22ddbe84cae10072b34` ;
- replay hors réseau : `PASS` ;
- Sporting CP : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Guimaraes : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 T-60 : `ABSENT`.

Le contrôle unique football-data.org `/v4/matches/567268` a été exécuté à partir de 20:19:54 Europe/Paris. Le payload brut a été reçu et écrit à 20:19:56, puis la première version du collecteur local a échoué pendant la finalisation SHA-256 en raison d'une incompatibilité avec Windows PowerShell 5.1. Conformément à la règle d'appel unique, la requête fournisseur n'a pas été répétée. La preuve brute a été finalisée hors réseau :

- HTTP : 200, récupéré par inférence à partir du payload de détail valide ;
- latence : au plus 2 298 ms, reconstruite entre l'instant de début du run et l'écriture du fichier brut ;
- en-têtes de quota : non disponibles après l'incident local ;
- taille brute : 1 057 octets ;
- SHA-256 recalculé hors réseau : `047c87540464be5b1555432a9a8209403e22ef939b5674859b9753db39a82361` ;
- replay hors réseau : `PASS`, identifiant parsé `567268` ;
- compétition : `Primeira Liga`, code `PPL` ;
- équipes brutes : Sporting Clube de Portugal à domicile contre Vitória SC à l'extérieur ;
- horaire : `2026-08-14T19:15:00Z`, soit 21:15 Europe/Paris ;
- statut : `TIMED` ;
- score : non renseigné avant le match.

La comparaison directe valide ID-01 et TIM-01 : les deux fournisseurs désignent les mêmes équipes, dans les mêmes rôles, et le même horaire. Highlightly utilise `Sporting CP` et `Guimaraes`, tandis que football-data.org utilise `Sporting Clube de Portugal` et `Vitória SC`. Les compositions, événements et statistiques détaillées de football-data.org restent `NON_APPLICABLE`.

Le collecteur football-data.org a été corrigé pour utiliser les primitives cryptographiques compatibles avec Windows PowerShell 5.1 ; aucune clé ni valeur `X-Auth-Token` n'est persistée. Le compteur Highlightly local atteint 10 / 80, son quota indique 90 / 100 et sa réserve de 20 appels reste préservée. P08 demeure éligible au contrôle conditionnel T-45 de 20:30 Europe/Paris.

Preuves locales hors Git :

- détail Highlightly : [ENR-P08-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) ;
- composition Highlightly : [ENR-P08-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json) ;
- détail football-data.org récupéré : [ENR-P08-FD-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json).

### Fenêtre T-45 - Sporting CP - Vitória SC

Le heartbeat attendu vers 20:30 Europe/Paris a été reçu à 20:30:34, soit 34 secondes après l'heure métier. L'inspection préalable a confirmé que la preuve P08 T-60 restait `ABSENT` et qu'une unique preuve football-data.org existait déjà. P06 et P07 étant fermés, aucun appel ne leur a été adressé ; football-data.org n'a pas été rappelé.

L'appel conditionnel de composition Highlightly a été exécuté à 20:31:13 Europe/Paris, soit environ T-44 :

- endpoint : `/lineups/1340719797` ;
- HTTP : 200 ;
- latence : 5 282 ms ;
- quota restant : 89 / 100 ;
- SHA-256 : `966dc045d9a75c9eddbf5a587c1f888d34a5bc9c455be22ddbe84cae10072b34` ;
- replay hors réseau : `PASS` ;
- Sporting CP : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- Guimaraes : formation `Unknown`, 0 titulaire unique et 0 remplaçant ;
- classification LIN-01 T-45 : `ABSENT`.

L'empreinte est identique à celle de T-60 : aucun début de publication n'est observable. Le compteur Highlightly local atteint 11 / 80, le quota fournisseur indique 89 / 100 et la réserve quotidienne de 20 appels reste préservée. P08 demeure éligible à son dernier contrôle T-30 de 20:45 Europe/Paris.

Preuve locale hors Git : [ENR-P08-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Dernière fenêtre planifiée T-30 - exécution réelle à environ T-11

Le dernier heartbeat attendu vers 20:45 Europe/Paris a été reçu à 20:46:35, soit 1 minute et 35 secondes après l'heure métier. L'inspection préalable a confirmé que P08 restait `ABSENT` à T-45, que P06 et P07 étaient fermés et que football-data.org avait déjà été appelé une fois.

L'appel Highlightly n'a toutefois commencé qu'à 21:03:59 Europe/Paris, soit 18 minutes et 59 secondes après l'heure T-30 planifiée et environ 11 minutes avant le coup d'envoi. Ce retard d'exécution est explicitement enregistré : la preuve ne démontre pas la disponibilité de la composition à T-30, mais elle reste strictement prématch.

Résultat de l'appel :

- endpoint : `/lineups/1340719797` ;
- HTTP : 200 ;
- latence : 8 119 ms ;
- quota restant : 88 / 100 ;
- SHA-256 : `8607fda5083edd211400cf7efcac2ca24af45a3e70449cf6cdc85dd163c25508` ;
- replay hors réseau : `PASS` ;
- Sporting CP : formation `4-2-3-1`, 11 titulaires uniques avec identifiants non nuls, 12 remplaçants ;
- Guimaraes : formation `4-2-3-1`, 11 titulaires uniques avec identifiants non nuls, 11 remplaçants ;
- aucune anomalie d'identifiant n'est détectée dans les titulaires ou les remplaçants ;
- classification LIN-01 : `COMPLETE_PREMATCH_LATE_CHECK`.

Le changement d'empreinte par rapport à T-60 et T-45 confirme la publication de la composition entre 20:31:18 et 21:03:59 Europe/Paris. Les deux équipes satisfont strictement le critère de complétude : onze titulaires après aplatissement récursif et onze identifiants uniques non nuls. Le besoin de disponibilité avant le coup d'envoi est satisfait, mais l'instant précis de publication ne peut pas être resserré davantage avec les preuves disponibles.

P08 est fermé pour les compositions. Le compteur Highlightly local atteint 12 / 80, le quota fournisseur indique 88 / 100 et la réserve quotidienne de 20 appels reste préservée. Les huit occurrences de l'automation consolidée sont terminées ; celle-ci est mise au statut `PAUSED` et aucun nouvel appel prématch ne doit être exécuté.

Preuve locale hors Git : [ENR-P08-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

## Collecte tardive P07 à la mi-temps - 14 août 2026

À la demande de l'utilisateur, un snapshot manuel distinct `HALF_TIME` a été exécuté pendant la mi-temps déclarée de Saint-Étienne - Clermont. L'automation prématch est restée `PAUSED` et n'a pas été réactivée.

L'appel Highlightly a été exécuté à 21:45:34 Europe/Paris :

- endpoint : `/lineups/1321127224` ;
- HTTP : 200 ;
- latence : 461 ms ;
- quota restant : 87 / 100 ;
- SHA-256 : `564f63c458f1c4b13e0148bf056f3ed24664bf84d36b6606d8e392396ca08473` ;
- replay hors réseau : `PASS` ;
- Saint-Étienne : formation `4-2-3-1`, 11 titulaires uniques avec identifiants non nuls, 8 remplaçants uniques avec identifiants non nuls ;
- Clermont Foot : formation `4-4-2`, 11 titulaires uniques avec identifiants non nuls, 9 remplaçants uniques avec identifiants non nuls ;
- classification de la preuve : `COMPLETE_LATE`.

Le payload diffère des trois preuves prématch, toutes identiques et vides. La composition a donc été publiée après le dernier contrôle effectué à 20:17:10 et au plus tard à 21:45:34. Comme aucune observation n'a été réalisée entre environ T-28 et la mi-temps, cette preuve ne permet pas de déterminer si la publication est intervenue avant ou après le coup d'envoi.

Le résultat prématch de P07 reste `ABSENT_AT_LAST_PREMATCH_CHECK` et LIN-01 n'est pas validé pour cette rencontre. En revanche, la formulation selon laquelle aucune composition P07 n'a pu être collectée n'est plus exacte : une composition complète, sans anomalie d'identifiant, est désormais disponible tardivement. Le compteur Highlightly local du 14 août atteint 13 / 80, le quota fournisseur indique 87 / 100 et la réserve quotidienne de 20 appels reste préservée.

Preuve locale hors Git : [ENR-P07-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json).

## Évolution acceptée du protocole LIN-01

À la suite des compositions observées à environ T-28 pour P06, T-11 pour P08 et tardivement pour P07, le séquencement des prochains lots est étendu et accepté comme suit :

1. T-60 : premier contrôle systématique pour une rencontre active ;
2. T-45 : contrôle uniquement si la composition n'est pas complète ;
3. T-30 : contrôle uniquement si elle reste non complète ;
4. T-15 : contrôle tardif prématch uniquement si nécessaire ;
5. T-5 : dernier contrôle facultatif pour une rencontre explicitement prioritaire ;
6. T0 et après : aucun appel ne peut valider LIN-01.

Une réponse `COMPLETE` arrête immédiatement la séquence de la rencontre et supprime tous les appels conditionnels restants. Les fenêtres historiques déjà exécutées ne sont pas requalifiées. Les comparaisons futures distingueront les fenêtres communes T-60, T-45 et T-30 des nouvelles extensions T-15 et T-5.

## Collecte post-match P06 - 14 août 2026

À la demande de l'utilisateur, les trois familles post-match de la rencontre Al-Hilal Saudi - Al-Faisaly ont été collectées à partir de 22:16:15 Europe/Paris. Aucun nouvel appel de détail ou de composition n'a été exécuté.

### Exploitabilité et traçabilité

| Famille | Début Europe/Paris | HTTP | Latence | Taille | SHA-256 | Replay | Classification |
|---|---:|---:|---:|---:|---|---|---|
| STATISTICS | 22:16:15 | 200 | 791 ms | 3 755 octets | `c96b37a43d73ff057e11a7ef3f192f097da2197156393b1b078157a41d8cadc0` | PASS | `COMPLETE` |
| EVENTS | 22:16:32 | 200 | 511 ms | 4 254 octets | `d56a0e4e7696d18326109cf945ac8f4757e270adadfc992648d26b6bafa75d2f` | PASS | `COMPLETE` |
| BOX_SCORE | 22:16:43 | 200 | 1 420 ms | 39 523 octets | `460469fec06c16f96ca51bd9777956e98a5cd467a26d4c98023c1211298770f0` | PASS | `COMPLETE_WITH_ANOMALIES` |

Le compteur Highlightly local du 14 août atteint 16 / 80 et le quota fournisseur indique 84 / 100 après le troisième appel. La réserve quotidienne de 20 appels reste préservée.

Preuves locales hors Git :

- [ENR-P06-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P06-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P06-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json).

### Statistiques d'équipe STA-01

Les deux équipes sont correctement identifiées et chacune possède 40 statistiques renseignées. Les valeurs principales sont cohérentes et directement exploitables :

| Statistique | Al-Hilal Saudi | Al-Faisaly |
|---|---:|---:|
| Expected Goals | 4,50 | 1,82 |
| Expected Assists | 1,16 | 1,41 |
| Possession | 50 % | 50 % |
| Tirs cadrés | 5 | 5 |
| Tirs non cadrés | 9 | 2 |
| Tirs dans la surface | 16 | 6 |
| Corners | 7 | 1 |
| Passes totales | 375 | 377 |
| Passes réussies | 309 | 318 |
| Fautes | 18 | 15 |
| Cartons jaunes | 0 | 1 |
| Cartons rouges | 0 | 0 |

Classification STA-01 : `COMPLETE`.

### Événements EVT-01

Le flux contient 17 événements : 3 buts hors penalty, 3 penalties transformés, 10 remplacements et 1 carton jaune. Les six événements de but reconstituent un score de 4-2 pour Al-Hilal Saudi. Les 23 identifiants joueurs distincts présents dans l'événementiel sont tous retrouvés dans le box-score.

La chronologie des buts est complète : Karim Benzema sur penalty à la 19e minute, Malcom à la 26e, Rúben Neves sur penalty à la 28e, Alexandre Mendy à la 59e, Théo Bongonda à la 64e et Rúben Neves sur penalty à la 82e. L'unique avertissement concerne Abdullah Almogren à la 62e minute.

Classification EVT-01 : `COMPLETE`.

### Statistiques joueurs PLY-01

Le box-score contient 20 joueurs pour chacune des deux équipes : 11 titulaires et 9 remplaçants, avec 16 joueurs crédités de minutes positives et 4 joueurs à zéro minute par équipe. Les 40 identifiants joueurs sont non nuls et uniques. Les totaux de buts et de passes décisives concordent avec l'événementiel : 4 buts et aucune passe décisive pour Al-Hilal Saudi ; 2 buts et 2 passes décisives pour Al-Faisaly.

Trois catégories d'anomalies déjà connues se reproduisent :

1. `MISSING_PLAYER_FULL_NAME` : six joueurs possèdent `fullName=null`. Meshal Al Dawood a joué 11 minutes ; Abdulrahman Al-Shereaf 71 minutes ; André Jordy Ella 24 minutes ; Mohammed Al-Yami, Sabri Abdu Dahal et Abdullah Bin Subayt sont restés à zéro minute. Le champ court `name`, l'identifiant et les autres attributs restent présents.
2. `ZERO_MINUTE_EXPECTED_METRICS` : sept des huit joueurs à zéro minute possèdent au moins une métrique attendue non nulle. Mohammed Al-Yami, Mohammed Al-Owais, Nawaf Al-Habashi, Mohammed Al-Hasawi, Abdullah Bin Subayt et Mohammed Jahfali sont concernés par `expectedAssists` et, pour deux d'entre eux, `expectedGoals`. Yaseen Barnawi possède `expectedGoalsOnTargetConceded=1.5` et `expectedGoalsPrevented=-0.51`, valeurs identiques à celles du gardien titulaire adverse. Ces métriques doivent être exclues des agrégats métier lorsque `minutesPlayed=0`.
3. `INVALID_SECOND_YELLOW_VALUE` : Abdullah Almogren possède `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`. L'événementiel ne contient qu'un carton jaune à la 62e minute, les statistiques d'équipe totalisent un jaune et aucun rouge, et le joueur est remplacé à la 65e minute. `cardsSecondYellow=1` est donc incohérent avec l'absence d'un second avertissement et d'une expulsion.

Aucune nouvelle catégorie d'anomalie PLY-01 n'est créée : le total consolidé reste de quatre catégories, toutes non bloquantes. Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`.

## Snapshot intramatch P08 - 14 août 2026

À la demande de l'utilisateur, un snapshot statistique de Sporting CP - Vitória SC a été exécuté pendant la seconde période. Les trois appels ont commencé entre 22:39:25 et 22:39:53 Europe/Paris. Le détail et la composition n'ont pas été rappelés : la composition P08 avait déjà été obtenue avant le coup d'envoi et ce snapshot vise uniquement STA-01, EVT-01 et PLY-01.

### Exploitabilité et traçabilité

| Famille | Début Europe/Paris | HTTP | Latence | Taille | SHA-256 | Replay | Classification |
|---|---:|---:|---:|---:|---|---|---|
| STATISTICS | 22:39:25 | 200 | 745 ms | 3 738 octets | `0fe06dfd5684ce6638791a4207677756c3f649e66ca041abbb5bc7fecd09a9ab` | PASS | `COMPLETE_LIVE` |
| EVENTS | 22:39:37 | 200 | 454 ms | 2 455 octets | `06068c600cf2897e1111beac4ace9fcaf600f4509074a662093c9be7eb707496` | PASS | `COMPLETE_LIVE` |
| BOX_SCORE | 22:39:53 | 200 | 1 291 ms | 44 358 octets | `1b78d572883d7c926d7a4b941ca5e835b7a7479f98034045f11997f6e2a18cb4` | PASS | `COMPLETE_LIVE_WITH_ANOMALIES` |

Le compteur Highlightly local du 14 août atteint 19 / 80 et le quota fournisseur indique 81 / 100 après le troisième appel. La réserve quotidienne de 20 appels reste préservée.

Preuves locales hors Git :

- [ENR-P08-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P08-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P08-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json).

### État statistique observé

Les statistiques d'équipe contiennent 40 champs par équipe. Au moment de la preuve, elles donnent :

| Statistique | Sporting CP | Guimaraes / Vitória SC |
|---|---:|---:|
| Expected Goals | 1,12 | 0,74 |
| Expected Assists | 0,48 | 0,58 |
| Possession | 57 % | 43 % |
| Tirs cadrés | 3 | 1 |
| Tirs non cadrés | 0 | 5 |
| Tirs dans la surface | 3 | 8 |
| Corners | 3 | 1 |
| Passes totales | 327 | 247 |
| Passes réussies | 285 | 204 |
| Fautes | 9 | 10 |
| Cartons jaunes | 2 | 2 |
| Cartons rouges | 0 | 0 |

Le flux contient 10 événements : 3 buts, 4 cartons jaunes et 3 remplacements. Le score reconstitué est Sporting CP 3-0 Vitória SC, avec Flávio Gonçalves à la 9e minute, Fotis Ioannidis à la 22e et Sergi Altimira à la 41e. Le dernier événement visible est le carton jaune de Geny Catamo à la 55e minute. Le box-score crédite les joueurs présents de 56 minutes au maximum : la preuve représente donc un état fournisseur autour de la 56e minute. Sans appel de détail live, l'écart exact entre le chronomètre du match et la fraîcheur de ces endpoints n'est pas déterminé.

Les 12 identifiants joueurs distincts présents dans les événements sont tous retrouvés dans le box-score. Les totaux joueurs concordent avec le flux : 3 buts et 3 passes décisives pour Sporting CP, aucun but ni passe décisive pour Guimaraes.

### Statistiques joueurs et anomalies

Le box-score contient 23 joueurs Sporting CP, dont les 11 titulaires avec minutes positives, et 22 joueurs Guimaraes, dont 14 joueurs avec minutes positives après les trois remplacements de la 46e minute. Aucun identifiant joueur n'est nul.

Trois catégories d'anomalies déjà connues se reproduisent :

1. `MISSING_PLAYER_FULL_NAME` : Francisco Dias et Santi Verdi, tous deux remplaçants Guimaraes restés à zéro minute, possèdent `fullName=null`.
2. `ZERO_MINUTE_EXPECTED_METRICS` : sept joueurs à zéro minute possèdent au moins une métrique attendue non nulle. Sporting CP : Rodrigo Dias, Zeno Debast et Pedro Lima. Guimaraes : Lucas Furtado, Juan Castillo, Santi Verdi et Matija Mitrović.
3. `INVALID_SECOND_YELLOW_VALUE` : les quatre joueurs avertis dans le flux — Sergi Altimira, Geny Catamo, Óscar Rivas et Thiago Balieiro — possèdent chacun `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`. Les événements et statistiques d'équipe n'indiquent ni second avertissement ni expulsion.

Aucune nouvelle catégorie d'anomalie PLY-01 n'est ouverte. Ces réserves restent non bloquantes et doivent être réévaluées lors du snapshot post-match, car la rencontre était toujours en cours.

## Collecte post-match P07 - 14 août 2026

À la demande de l'utilisateur, les statistiques post-match de Saint-Étienne - Clermont ont été collectées entre 22:53:31 et 22:54:01 Europe/Paris. Aucun appel de détail ou de composition n'a été répété ; la composition complète obtenue à la mi-temps reste la preuve LIN-01 tardive de référence.

### Exploitabilité et traçabilité

| Famille | Début Europe/Paris | HTTP | Latence | Taille | SHA-256 | Replay | Classification |
|---|---:|---:|---:|---:|---|---|---|
| STATISTICS | 22:53:31 | 200 | 459 ms | 3 565 octets | `f0d705ae433d640c2c2fadd27470cbff1b0a9a73239ec2d2b39082b4f9133f0e` | PASS | `COMPLETE_REDUCED_SCHEMA` |
| EVENTS | 22:53:48 | 200 | 431 ms | 4 257 octets | `656399d792869a1efb471197e05fcfbfc78d953e294dd787811cd1bdeebfaed3` | PASS | `COMPLETE` |
| BOX_SCORE | 22:54:01 | 200 | 1 759 ms | 38 356 octets | `c5ea9817e1b9f02f776ba7883a3e86911d63328b188303bac7914209968d9103` | PASS | `COMPLETE_WITH_ANOMALIES` |

Le compteur Highlightly local du 14 août atteint 22 / 80 et le quota fournisseur indique 78 / 100 après le troisième appel. La réserve quotidienne de 20 appels reste préservée.

Preuves locales hors Git :

- [ENR-P07-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P07-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P07-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json).

### Statistiques d'équipe STA-01

Les deux équipes sont correctement identifiées et possèdent chacune 38 statistiques non nulles. Le schéma est parfaitement symétrique, mais il ne contient ni `Expected Goals` ni `Expected Assists`, contrairement aux 40 champs observés sur P06 et P08.

| Statistique | Saint-Étienne | Clermont Foot |
|---|---:|---:|
| Possession | 60 % | 40 % |
| Tirs cadrés | 5 | 2 |
| Tirs non cadrés | 10 | 2 |
| Tirs dans la surface | 16 | 3 |
| Corners | 7 | 6 |
| Passes totales | 522 | 344 |
| Passes réussies | 483 | 292 |
| Fautes | 15 | 15 |
| Cartons jaunes | 1 | 1 |
| Cartons rouges | 0 | 0 |

STA-01 est classé `COMPLETE_REDUCED_SCHEMA` : les valeurs retournées sont complètes et symétriques, mais la profondeur statistique est inférieure à celle des rencontres de Saudi Pro League et de Liga Portugal testées le même jour. Cette limite de couverture de la Ligue 2 devra être vérifiée sur d'autres rencontres avant généralisation.

### Événements EVT-01

Le flux contient 17 événements : 4 buts, 11 remplacements et 2 cartons jaunes. Les quatre buts reconstituent le score Saint-Étienne 3-1 Clermont Foot : Irvin Cardona à la 41e minute, Thierno Ballo à la 54e, Jakob Breum à la 71e et Ousmane Diop à la 89e. Les passes décisives sont attribuées à Thierno Ballo, Áron Csongvai et Junior Sambia ; le but de Thierno Ballo n'a pas de passeur.

Les 24 identifiants joueurs distincts présents dans l'événementiel sont tous retrouvés dans le box-score. Les avertissements de Jakob Breum à la 49e minute et d'Ivan M'Bahia à la 68e concordent avec les agrégats d'équipe.

Classification EVT-01 : `COMPLETE`.

### Statistiques joueurs PLY-01

Le box-score contient 19 joueurs Saint-Étienne, dont 16 avec minutes positives, et 20 joueurs Clermont Foot, dont 17 avec minutes positives. Les six joueurs restés à zéro minute ne possèdent aucune métrique attendue non nulle : `ZERO_MINUTE_EXPECTED_METRICS` ne se reproduit donc pas sur P07.

Les totaux joueurs concordent avec les événements : 3 buts et 2 passes décisives pour Saint-Étienne ; 1 but et 1 passe décisive pour Clermont Foot. Les 39 identifiants joueurs sont non nuls et uniques.

Deux catégories d'anomalies déjà connues se reproduisent :

1. `MISSING_PLAYER_FULL_NAME` : quatre joueurs ayant participé possèdent `fullName=null` — Adam Baallal, 11 minutes ; Nathan Kasia Nkondo, 10 minutes ; Christ Okou, 74 minutes ; Arthur Saintorens, 19 minutes. Leur identifiant et leur nom court restent présents.
2. `INVALID_SECOND_YELLOW_VALUE` : Jakob Breum et Ivan M'Bahia possèdent chacun `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`, alors que le flux ne contient qu'un avertissement pour chacun et que les statistiques d'équipe n'indiquent aucun rouge.

`MISSING_PLAYER_YELLOW_CARD` ne se reproduit pas : les deux cartons du flux sont présents dans les agrégats et les lignes joueurs. Aucune nouvelle catégorie d'anomalie PLY-01 n'est ouverte. Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`.

## Collecte post-match P08 - 14 août 2026

À la demande de l'utilisateur, les statistiques post-match de Sporting CP - Vitória SC ont été collectées entre 23:44:56 et 23:45:20 Europe/Paris. Aucun appel de détail ou de composition n'a été répété. Cette collecte est comparée au snapshot intramatch situé autour de la 56e minute.

### Exploitabilité et traçabilité

| Famille | Début Europe/Paris | HTTP | Latence | Taille | SHA-256 | Replay | Classification |
|---|---:|---:|---:|---:|---|---|---|
| STATISTICS | 23:44:56 | 200 | 746 ms | 3 750 octets | `914d494a98686324ac4895385aa27e3ccf33963f1b541c9061eb3bfefa35d60c` | PASS | `COMPLETE` |
| EVENTS | 23:45:08 | 200 | 448 ms | 5 412 octets | `7b8a843323f914b5ddb8d49a11bc156be7650ef02b84a08ab017b41446394c45` | PASS | `COMPLETE` |
| BOX_SCORE | 23:45:20 | 200 | 1 572 ms | 44 415 octets | `3980ed121b71c184aa574d4755745ce9c0cb9a2d563dd8c0c93198f67088abec` | PASS | `COMPLETE_WITH_ANOMALIES` |

Le compteur Highlightly local du 14 août atteint 25 / 80 et le quota fournisseur indique 75 / 100 après le troisième appel. La réserve quotidienne de 20 appels reste préservée.

Preuves locales hors Git :

- [ENR-P08-STATISTICS-002](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P08-EVENTS-002](evidence/enr-001-evidence-index-v0.1.json) ;
- [ENR-P08-BOX-SCORE-002](evidence/enr-001-evidence-index-v0.1.json).

### Statistiques d'équipe STA-01

Les deux équipes conservent un schéma complet et symétrique de 40 champs. Les valeurs finales principales sont :

| Statistique | Sporting CP | Guimaraes / Vitória SC |
|---|---:|---:|
| Expected Goals | 1,12 | 1,12 |
| Expected Assists | 0,50 | 0,87 |
| Possession | 56 % | 44 % |
| Tirs cadrés | 3 | 3 |
| Tirs non cadrés | 0 | 7 |
| Tirs dans la surface | 3 | 11 |
| Corners | 5 | 1 |
| Passes totales | 510 | 396 |
| Passes réussies | 446 | 330 |
| Fautes | 12 | 21 |
| Cartons jaunes | 4 | 4 |
| Cartons rouges | 0 | 0 |

Par rapport au snapshot situé autour de la 56e minute, les xG Sporting restent à 1,12 tandis que ceux de Vitória SC passent de 0,74 à 1,12. Les xA passent de 0,48 / 0,58 à 0,50 / 0,87. Les agrégats de passes, fautes, tirs et cartons sont actualisés. Classification STA-01 : `COMPLETE`.

### Événements EVT-01

Le flux passe de 10 événements live à 22 événements finaux : 5 buts, 9 remplacements et 8 cartons jaunes. Les cinq buts reconstituent le score Sporting CP 3-2 Vitória SC :

- Flávio Gonçalves à la 9e minute, passe décisive de Fotis Ioannidis ;
- Fotis Ioannidis à la 22e, passe décisive de Geny Catamo ;
- Sergi Altimira à la 41e, passe décisive de Fotis Ioannidis ;
- Samu à la 66e, passe décisive de Miguel Nogueira ;
- Alioune Ndoye à la 69e, passe décisive de Tony Strata.

Les 25 identifiants joueurs distincts du flux sont tous retrouvés dans le box-score. Les huit avertissements concordent avec les quatre jaunes de chaque équipe dans l'agrégat. Classification EVT-01 : `COMPLETE`.

### Statistiques joueurs PLY-01

Le box-score conserve 23 joueurs Sporting CP et 22 joueurs Guimaraes. Après les neuf remplacements, 15 joueurs Sporting et 16 joueurs Guimaraes possèdent un temps de jeu positif. Les totaux concordent avec l'événementiel : 3 buts et 3 passes décisives pour Sporting CP ; 2 buts et 2 passes décisives pour Guimaraes. Aucun identifiant joueur n'est nul.

Les anomalies live évoluent mais ne disparaissent pas :

1. `MISSING_PLAYER_FULL_NAME` : Santi Verdi conserve `fullName=null` après être entré pour 19 minutes ; Francisco Dias conserve également `fullName=null` et reste à zéro minute.
2. `ZERO_MINUTE_EXPECTED_METRICS` : huit joueurs à zéro minute possèdent des métriques attendues non nulles. Sporting CP : João Virgínia, Rodrigo Dias, Zeno Debast et Pedro Lima. Guimaraes : Lucas Furtado, Juan Castillo, Telmo Arcanjo et Matija Mitrović. Le groupe diffère du snapshot live : Santi Verdi est entré en jeu, tandis que João Virgínia et Telmo Arcanjo apparaissent désormais dans l'anomalie.
3. `INVALID_SECOND_YELLOW_VALUE` : les huit joueurs avertis possèdent chacun `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`, sans second avertissement ni expulsion dans l'événementiel. Sont concernés Sergi Altimira, Geny Catamo, Eduardo Quaresma, Gonçalo Inácio, Óscar Rivas, Thiago Balieiro, Miguel Nogueira et Gustavo Silva.

`MISSING_PLAYER_YELLOW_CARD` ne se reproduit pas : les huit cartons sont concordants entre événements, agrégats d'équipe et lignes joueurs. Aucune nouvelle catégorie d'anomalie PLY-01 n'est ouverte. Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`.

### Conclusion de comparaison live / post-match

Les trois endpoints spécialisés ont correctement poursuivi leur enrichissement après le snapshot live : score de 3-0 devenu 3-2, 12 événements ajoutés, neuf remplacements complets, huit avertissements et statistiques d'équipe finales. En revanche, les champs joueurs problématiques ne sont pas corrigés après le match : `cardsSecondYellow` reste erroné pour tous les joueurs avertis et les métriques attendues des joueurs à zéro minute restent instables. Cette collecte renforce la règle consistant à privilégier l'événementiel et les agrégats d'équipe pour la discipline, et à filtrer les métriques attendues individuelles lorsque `minutesPlayed=0`.

## Contrôle transversal ENR-C01 à J-1 - 14 août 2026

Le contrôle J-1 de FC Kryvbas Kryvyi Rih - FC Livyi Bereh Kyiv a été exécuté à 23:50:32 Europe/Paris, conformément au plan limité à ID-01, TIM-01, OPS-01 et REP-01. Un seul appel de détail Highlightly a été effectué ; aucun enrichissement complet n'est autorisé pour ce contrôle transversal.

### Traçabilité de l'appel

| Famille | Fenêtre | HTTP | Latence | Taille | Quota | SHA-256 | Replay |
|---|---|---:|---:|---:|---:|---|---|
| DETAIL | J-1 | 200 | 718 ms | 1 918 octets | 74 / 100 | `585afb8fb5ae7ac1647724377d9e85aa4d2028773f3de8417d0b73ff6d6a35ab` | PASS |

Le compteur Highlightly local du 14 août atteint 26 / 80. La réserve quotidienne de 20 appels reste préservée.

Preuve locale hors Git : [ENR-C01-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json).

### Comparaison avec CAL-01

| Champ | CAL-01 du 9 août | Contrôle J-1 du 14 août | Conclusion |
|---|---|---|---|
| Identifiant rencontre | `1332201287` | `1332201287` | Stable |
| Compétition | `Premier League`, ligue `284167`, saison 2026 | identique | Stable |
| Tour | `Regular Season - 3` | identique | Stable |
| Équipe à domicile | `Hirnyk`, identifiant `5522923` | identique | Libellé fournisseur non actualisé ; identité canonique confirmée |
| Équipe à l'extérieur | `Livyi Bereh`, identifiant `14605646` | identique | Stable |
| Coup d'envoi UTC | 15 août 2026 à 10:00 | identique | Stable |
| Coup d'envoi Europe/Paris | 15 août 2026 à 12:00 | identique | Stable |
| État | `Not started` | `Not started` | Stable |

### Conclusions ID-01 et TIM-01

Highlightly renvoie toujours `Hirnyk` pour l'équipe à domicile. Aucune actualisation vers le nom canonique `FC Kryvbas Kryvyi Rih` n'est observable cinq jours après CAL-01 et à environ douze heures du coup d'envoi. `Hirnyk` reste donc conservé sans altération comme valeur brute et le libellé fournisseur est qualifié `STALE_PROVIDER_NAME`.

La décision acceptée le 15 août 2026 requalifie néanmoins le cas ID-01 en `CONFIRMED_HISTORICAL_REBRAND_ALIAS`. Les sources officielles du club expliquent que le Kryvbas actuel a été constitué en 2020 sur la base du FC Hirnyk et décrivent l'opération comme une restauration accompagnée, dans les faits, du renommage de Hirnyk. La fiche officielle UPL identifie le club actuel comme `Kryvbas`. Ces preuves, ajoutées à la stabilité de l'identifiant Highlightly `5522923`, autorisent le mapping opérationnel `CONFIRMED` vers `FC Kryvbas Kryvyi Rih`.

Le mapping est strictement borné à la référence `highlightly:TEAM:5522923` dans le contexte ukrainien. Il ne crée aucune règle de substitution par nom et ne permet donc pas d'assimiler une autre entité appelée `Hirnyk` à Kryvbas. L'anomalie `AMBIGUOUS_TEAM_IDENTITY` d'ENR-C01 est clôturée par reclassification ; la valeur brute et toutes les observations de provenance restent conservées.

Sources de décision : [histoire officielle du FC Kryvbas](https://fckryvbas.com/istoriya-klubu), [explication de la direction du FC Kryvbas](https://en.fckryvbas.com/post/nakavu-kerivnictvo-fk-krivbas-zustrilos-z-fanami-vidverti-vidpovidi-chastina-1) et [fiche officielle du club en Ukrainian Premier League](https://www.upl.ua/en/clubs/view/1478).

L'horaire, les rôles domicile/extérieur, la compétition, la saison, le tour et le statut restent identiques à CAL-01. TIM-01 est donc classé `STABLE_NO_CHANGE`. Le payload ne contient encore ni score, ni événement, ni statistique d'équipe, ce qui est cohérent avec l'état `Not started`.

Le second contrôle prévu dans le plan reste à exécuter le 15 août, jour du match et avant le coup d'envoi de 12:00 Europe/Paris. Il constituera une nouvelle observation sans écraser cette preuve J-1 et mesurera désormais la fraîcheur du libellé Highlightly, sans remettre en cause l'identité canonique confirmée.

## ENR-P09 - Al-Nassr - Al-Fateh - prématch du 15 août 2026

### Exécution T-60

Le heartbeat a été reçu à 19:01:34 Europe/Paris, soit 1 minute et 34 secondes après l'échéance métier de 19:00. L'inspection préalable effectuée à 19:03:46 n'a trouvé aucune preuve P09 ni aucun appel ENR-001 du 15 août : DETAIL et LINEUP étaient donc tous deux éligibles.

Un premier lancement direct du script avec PowerShell 7 a échoué au parsing avant toute lecture de clé et avant tout appel réseau. La reprise a utilisé `scripts/collect-enrichment.cmd`, wrapper Windows PowerShell 5.1 déjà validé par les collectes précédentes. Cet incident local n'a consommé aucun quota mais a décalé les appels réels à environ T-55.

| Famille | Début réel Paris | HTTP | Latence | Taille | Quota | SHA-256 | Replay |
|---|---:|---:|---:|---:|---:|---|---|
| DETAIL | 19:04:34 | 200 | 3 831 ms | 2 040 octets | 99 / 100 | `362a469ad503f7b593bd679bb2ee62157c4783a4d4cb0ed68da37ebe119dba55` | PASS |
| LINEUP | 19:04:52 | 200 | 3 515 ms | 338 octets | 98 / 100 | `0a1373e82b3f8f1f0bb85abb969b763c03f76ab2b85e254b3e150bd1706a0ec8` | PASS |

Le détail confirme le match `1364136764`, la ligue Highlightly `262041` (`Pro League`), la saison 2026, le tour `Regular Season - 1`, le coup d'envoi du 15 août à 18:00 UTC soit 20:00 Europe/Paris, et l'état `Not started`. Les rôles et identités sont cohérents : `Al-Nassr`, identifiant `2501873`, à domicile ; `Al-Fateh SC`, identifiant `2495065`, à l'extérieur. L'alias accepté `Al-Fateh` ↔ `Al-Fateh SC` est appliqué sans modifier la valeur brute.

La réponse LINEUP identifie correctement les deux équipes, mais renvoie `formation=Unknown`, zéro titulaire et zéro remplaçant des deux côtés. Après aplatissement récursif, le décompte reste 0 / 0 titulaire unique. Classification LIN-01 : `ABSENT`.

Le compteur local du 15 août atteint 2 / 80, le quota fournisseur indique 98 / 100 et la réserve de sécurité de 20 appels reste intacte. P09 reste éligible au contrôle conditionnel T-45 prévu à 19:15 Europe/Paris.

Preuves locales hors Git : [ENR-P09-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P09-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-45

Le heartbeat T-45 a été reçu à 19:16:32 Europe/Paris, soit 1 minute et 32 secondes après l'échéance de 19:15. L'inspection à 19:16:58 a confirmé que la dernière composition P09 était celle de T-60, classée `ABSENT`, et qu'aucune preuve T-45 concurrente n'existait.

L'appel LINEUP a débuté à 19:17:10, soit environ T-43 : HTTP 200 en 731 ms, 338 octets, quota 97 / 100, SHA-256 `0a1373e82b3f8f1f0bb85abb969b763c03f76ab2b85e254b3e150bd1706a0ec8`, replay `PASS`.

Le payload est strictement identique à T-60. Al-Nassr et Al-Fateh SC sont correctement identifiés, mais les formations restent `Unknown`, les listes `initialLineup` et `substitutes` sont vides et le décompte récursif reste 0 / 0 titulaire unique. Classification LIN-01 T-45 : `ABSENT`.

Le compteur local du 15 août atteint 3 / 80, la réserve de sécurité reste intacte et P09 demeure éligible à T-30 à 19:30 Europe/Paris.

Preuve locale hors Git : [ENR-P09-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-30

Le heartbeat T-30 a été reçu à 19:30:33 Europe/Paris, soit 33 secondes après l'échéance. L'inspection à 19:30:59 a confirmé P09 `ABSENT` à T-45, l'absence de preuve T-30 concurrente et l'éligibilité d'un unique appel LINEUP.

L'appel a débuté à 19:31:10, soit environ T-29 : HTTP 200 en 1 526 ms, 3 319 octets, quota 96 / 100, SHA-256 `6c1ab6d6643b617646c9f9a012b7695e3df91ae5273ad6b85473a9268b8a4de3`, replay `PASS`. Le changement d'empreinte par rapport à T-60 et T-45 confirme la publication de la composition après 19:17:10 et au plus tard à 19:31:10.

Après aplatissement récursif et déduplication par identifiant :

- Al-Nassr : formation `4-4-2`, 11 titulaires, 11 identifiants titulaires uniques et non nuls, 9 remplaçants identifiés ;
- Al-Fateh SC : formation `4-2-3-1`, 11 titulaires, 11 identifiants titulaires uniques et non nuls, 9 remplaçants ;
- le remplaçant d'Al-Fateh SC Ibrahim Al Abood possède `id=null` ; aucun titulaire n'est concerné.

La condition LIN-01 portant sur les titulaires est pleinement satisfaite. Classification : `COMPLETE_WITH_NON_BLOCKING_SUBSTITUTE_ID_ANOMALY`. La catégorie ID-01 `MISSING_PLAYER_ID`, déjà ouverte sur ENR-P06, se reproduit sur un remplaçant de P09 sans invalider la composition.

P09 est fermé : l'échéance T-15 devra être ignorée sans appel et aucune collecte LIN-01 n'est autorisée à T0 ou après. Le compteur local du 15 août atteint 4 / 80, le quota fournisseur indique 96 / 100 et la réserve de sécurité reste intacte. La prochaine fenêtre appelable concerne P10 et P11 à T-60, le 15 août à 20:30 Europe/Paris.

Preuve locale hors Git : [ENR-P09-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

## ENR-P10 - Sevilla - Rayo Vallecano - prématch du 15 août 2026

### Exécution T-60

Le heartbeat attendu à 20:30 Europe/Paris a été reçu à 20:32:04, soit une gigue de 2 minutes et 4 secondes. L'inspection préalable à 20:32:34 a confirmé l'absence de toute preuve P10 et P11. DETAIL Highlightly, LINEUP Highlightly et l'unique contrôle DETAIL football-data.org étaient donc éligibles pour chaque rencontre.

| Fournisseur | Famille | Début réel Paris | HTTP | Latence | Taille | Quota | SHA-256 | Replay |
|---|---|---:|---:|---:|---:|---:|---|---|
| Highlightly | DETAIL | 20:33:10 | 200 | 544 ms | 6 772 octets | 95 / 100 | `26c52f989c7db92f660078e9f4b193d4994f03c1ba6c92ad7f7712b4cd613734` | PASS |
| Highlightly | LINEUP | 20:33:26 | 200 | 499 ms | 339 octets | 94 / 100 | `8a5f6398af26e9eeb05c3a31e001a30fa619647941ed3a26f532172ccd143b2d` | PASS |
| football-data.org | DETAIL | 20:34:00 | 200 | 308 ms | 1 057 octets | Non enregistré | `808ce9fe9b1b921e1850d3e859cb62256692b847e5283010586bdf20dbaa5042` | PASS |

Highlightly confirme le match `1336360975`, la ligue `119924` nommée `La Liga`, le tour `Regular Season - 1`, le coup d'envoi `2026-08-15T19:30:00.000Z`, soit 21:30 Europe/Paris, et l'état `Not started`. Les rôles et identités sont `Sevilla FC` `456920` à domicile et `Rayo Vallecano` `620312` à l'extérieur.

football-data.org confirme le match `564633`, la compétition `Primera Division` code `PD`, le même coup d'envoi `2026-08-15T19:30:00Z` et le statut `TIMED`. Il renvoie `Sevilla FC` `559` à domicile et `Rayo Vallecano de Madrid` `87` à l'extérieur. Les mappings acceptés `Sevilla` ↔ `Sevilla FC` et `Rayo Vallecano` ↔ `Rayo Vallecano de Madrid` permettent la comparaison sans altérer les valeurs brutes. ID-01 et TIM-01 sont concordants entre les deux fournisseurs ; les compositions, événements et statistiques détaillées football-data.org restent `NON_APPLICABLE`.

La réponse LINEUP Highlightly identifie les deux équipes mais renvoie `formation=Unknown`, zéro titulaire et zéro remplaçant de chaque côté. Après aplatissement récursif et déduplication, le résultat reste 0 / 0 titulaire unique. Classification LIN-01 T-60 : `ABSENT`. P10 reste éligible au contrôle conditionnel T-45 prévu à 20:45 Europe/Paris.

Preuves locales hors Git : [ENR-P10-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json), [ENR-P10-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P10-FD-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-45

Le heartbeat a été reçu à 20:46:04 Europe/Paris, soit 1 minute et 4 secondes après l'échéance. L'inspection à 20:46:19 a confirmé P10 `ABSENT` à T-60 et l'absence de preuve T-45 concurrente.

L'appel LINEUP a débuté à 20:46:33, soit environ T-43 : HTTP 200 en 577 ms, 339 octets, quota 91 / 100, SHA-256 `8a5f6398af26e9eeb05c3a31e001a30fa619647941ed3a26f532172ccd143b2d`, replay `PASS`. L'empreinte est identique à T-60. Sevilla FC et Rayo Vallecano restent correctement identifiés, mais les formations sont `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant. Classification LIN-01 T-45 : `ABSENT`.

P10 reste éligible au contrôle conditionnel T-30 prévu à 21:00 Europe/Paris. Preuve locale hors Git : [ENR-P10-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-30

Le heartbeat a été reçu à 21:02:04 Europe/Paris, soit 2 minutes et 4 secondes après l'échéance. L'inspection à 21:02:21 a confirmé P10 `ABSENT` à T-45 et l'absence de preuve T-30 concurrente.

L'appel LINEUP a débuté à 21:02:31, soit environ T-27 : HTTP 200 en 636 ms, 339 octets, quota 89 / 100, SHA-256 `8a5f6398af26e9eeb05c3a31e001a30fa619647941ed3a26f532172ccd143b2d`, replay `PASS`. L'empreinte reste identique à T-60 et T-45. Les formations sont toujours `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant. Classification LIN-01 T-30 : `ABSENT`.

P10 reste éligible au contrôle conditionnel T-15 prévu à 21:15 Europe/Paris. Preuve locale hors Git : [ENR-P10-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-15

Le heartbeat a été reçu à 21:17:04 Europe/Paris, soit 2 minutes et 4 secondes après l'échéance. L'inspection à 21:17:18 a confirmé P10 `ABSENT` à T-30 et l'absence de preuve T-15 concurrente.

L'appel LINEUP a débuté à 21:17:33, soit environ T-12 : HTTP 200 en 3 039 ms, 3 656 octets, quota 87 / 100, SHA-256 `20b4a6010a9856e4ba8041bde281635b8de808baf1c5386cd64a2ef3534f9504`, replay `PASS`. Le changement d'empreinte situe la publication de la composition après 21:02:31 et au plus tard à 21:17:33.

Après aplatissement récursif et déduplication par identifiant :

- Sevilla FC : formation `4-2-3-1`, 11 titulaires, 11 identifiants titulaires uniques et non nuls, 12 remplaçants tous identifiés ;
- Rayo Vallecano : formation `4-2-3-1`, 11 titulaires, 11 identifiants titulaires uniques et non nuls, 11 remplaçants tous identifiés ;
- aucun identifiant titulaire ou remplaçant n'est nul et aucun doublon titulaire n'est présent.

La condition LIN-01 est entièrement satisfaite. Classification T-15 : `COMPLETE`. P10 est fermé ; la fenêtre T-5 devra être ignorée sans appel. Preuve locale hors Git : [ENR-P10-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json).

## ENR-P11 - Rio Ave - Porto - prématch du 15 août 2026

### Exécution T-60

| Fournisseur | Famille | Début réel Paris | HTTP | Latence | Taille | Quota | SHA-256 | Replay |
|---|---|---:|---:|---:|---:|---:|---|---|
| Highlightly | DETAIL | 20:33:36 | 200 | 413 ms | 5 781 octets | 93 / 100 | `aced05ee9fba844ae82b5bb2a236c99d4570f8fee7ca65ab8a4df8bdeb2b09c0` | PASS |
| Highlightly | LINEUP | 20:33:46 | 200 | 421 ms | 333 octets | 92 / 100 | `3558d0032949f974f1f5576ebf12b5b7e4785ffca49db59a9c5d1371389418e4` | PASS |
| football-data.org | DETAIL | 20:34:11 | 200 | 237 ms | 1 030 octets | Non enregistré | `38f04e83bc813c9629e64a2545eff938f35bd348d04a4f322a7e8dcc48c81059` | PASS |

Highlightly confirme le match `1340718946`, la ligue `80778` nommée `Primeira Liga`, le tour `Regular Season - 2`, le coup d'envoi `2026-08-15T19:30:00.000Z`, soit 21:30 Europe/Paris, et l'état `Not started`. Les rôles et identités sont `Rio Ave FC` `193110` à domicile et `FC Porto` `181196` à l'extérieur.

football-data.org confirme le match `567274`, la compétition `Primeira Liga` code `PPL`, le même coup d'envoi `2026-08-15T19:30:00Z` et le statut `TIMED`. Il renvoie `Rio Ave FC` `496` à domicile et `FC Porto` `503` à l'extérieur. Les mappings acceptés `Rio Ave` ↔ `Rio Ave FC` et `Porto` ↔ `FC Porto` sont cohérents ; ID-01 et TIM-01 concordent entre les deux fournisseurs. Les familles détaillées football-data.org restent `NON_APPLICABLE`.

La réponse LINEUP Highlightly identifie les deux équipes mais renvoie `formation=Unknown`, zéro titulaire et zéro remplaçant de chaque côté. Après aplatissement récursif et déduplication, le résultat reste 0 / 0 titulaire unique. Classification LIN-01 T-60 : `ABSENT`. P11 reste éligible au contrôle conditionnel T-45 prévu à 20:45 Europe/Paris.

Après ces six appels, le compteur Highlightly local du 15 août atteint 8 / 80, le quota fournisseur indique 92 / 100 et la réserve quotidienne de 20 appels reste intacte. Les deux appels football-data.org ont été exécutés à plus de dix secondes d'intervalle, sans répétition et bien sous la limite de huit appels par minute.

Preuves locales hors Git : [ENR-P11-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json), [ENR-P11-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P11-FD-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-45

L'inspection à 20:46:19 a également confirmé P11 `ABSENT` à T-60 et l'absence de preuve T-45 concurrente. L'appel LINEUP a débuté à 20:46:45, soit environ T-43 : HTTP 200 en 569 ms, 333 octets, quota 90 / 100, SHA-256 `3558d0032949f974f1f5576ebf12b5b7e4785ffca49db59a9c5d1371389418e4`, replay `PASS`.

L'empreinte est identique à T-60. Rio Ave FC et FC Porto restent correctement identifiés, mais les formations sont `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant. Classification LIN-01 T-45 : `ABSENT`. P11 reste éligible au contrôle conditionnel T-30 prévu à 21:00 Europe/Paris.

Après ces deux appels, le compteur Highlightly local du 15 août atteint 10 / 80, le quota fournisseur indique 90 / 100 et la réserve quotidienne de 20 appels reste intacte. Preuve locale hors Git : [ENR-P11-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-30

L'inspection à 21:02:21 a également confirmé P11 `ABSENT` à T-45 et l'absence de preuve T-30 concurrente. L'appel LINEUP a débuté à 21:02:42, soit environ T-27 : HTTP 200 en 740 ms, 333 octets, quota 88 / 100, SHA-256 `3558d0032949f974f1f5576ebf12b5b7e4785ffca49db59a9c5d1371389418e4`, replay `PASS`.

L'empreinte reste identique à T-60 et T-45. Les formations sont toujours `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant. Classification LIN-01 T-30 : `ABSENT`. P11 reste éligible au contrôle conditionnel T-15 prévu à 21:15 Europe/Paris.

Après ces deux appels, le compteur Highlightly local du 15 août atteint 12 / 80, le quota fournisseur indique 88 / 100 et la réserve quotidienne de 20 appels reste intacte. Preuve locale hors Git : [ENR-P11-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-15

L'inspection à 21:17:18 a également confirmé P11 `ABSENT` à T-30 et l'absence de preuve T-15 concurrente. L'appel LINEUP a débuté à 21:17:49, soit environ T-12 : HTTP 200 en 2 222 ms, 333 octets, quota 86 / 100, SHA-256 `3558d0032949f974f1f5576ebf12b5b7e4785ffca49db59a9c5d1371389418e4`, replay `PASS`.

L'empreinte reste identique à T-60, T-45 et T-30. Rio Ave FC et FC Porto sont correctement identifiés, mais les formations restent `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant. Classification LIN-01 T-15 : `ABSENT`.

P11 étant une rencontre multi-fournisseurs explicitement prioritaire, elle reste seule éligible à la dernière fenêtre T-5 prévue à 21:25 Europe/Paris. Après ces deux appels, le compteur Highlightly local du 15 août atteint 14 / 80, le quota fournisseur indique 86 / 100 et la réserve quotidienne de 20 appels reste intacte. Preuve locale hors Git : [ENR-P11-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json).

### Échéance finale T-5

Le heartbeat final a été reçu à 21:26:34 Europe/Paris, soit 1 minute et 34 secondes après l'échéance de 21:25. L'inspection réelle des preuves et de l'heure s'est achevée à 21:31:28, soit après le coup d'envoi fixé à 21:30.

P10 était déjà `COMPLETE` et a été ignoré sans appel. P11 possédait toujours comme dernière preuve sa composition T-15 classée `ABSENT`, sans preuve T-5 concurrente. La règle interdisant tout appel LIN-01 à T0 ou après T0 a donc prévalu : aucun appel tardif n'a été exécuté et aucun quota n'a été consommé.

Classification prématch finale P11 : `ABSENT_AT_LAST_PREMATCH_CHECK`, fondée sur la preuve T-15 obtenue à environ T-12. Cette classification n'affirme pas que la composition est restée absente jusqu'au coup d'envoi ; elle constate uniquement qu'aucune preuve admissible plus tardive n'a pu être collectée. Le compteur Highlightly du 15 août reste à 14 / 80, le quota à 86 / 100 et la réserve de sécurité demeure intacte. L'automation du 15 août a d'abord été mise au statut `PAUSED`, puis supprimée du planificateur après la clôture de sa dernière échéance afin qu'aucun nouveau réveil ne puisse survenir.

## Collecte post-match finale P09 à P11 - 16 août 2026

Après confirmation de la fin des trois rencontres du 15 août, les familles STATISTICS, EVENTS et BOX_SCORE ont été collectées pour P09, P10 et P11 entre 00:58:32 et 01:00:49 Europe/Paris le 16 août. Aucun appel DETAIL ou LINEUP n'a été répété. football-data.org n'a pas été rappelé : conformément au plan accepté, ses compositions, événements et statistiques détaillées sont `NON_APPLICABLE`.

### Exploitabilité et traçabilité

| Échantillon | Famille | Début Europe/Paris | HTTP | Latence | Taille | SHA-256 | Replay | Classification |
|---|---|---:|---:|---:|---:|---|---|---|
| P09 | STATISTICS | 00:58:32 | 200 | 670 ms | 3 753 octets | `14fda64614de8370acd282a40616acdb5c8d9371be0899de2943448879cc48a3` | PASS | `COMPLETE` |
| P09 | EVENTS | 00:58:44 | 200 | 417 ms | 3 768 octets | `5fd1bc3d4846c4d59c672d90c6d2d629d51e0f3c129b091fc6167f0c2cc7388b` | PASS | `COMPLETE` |
| P09 | BOX_SCORE | 00:58:56 | 200 | 3 665 ms | 38 606 octets | `36fc3a61cbc59a140f037d3e0c7887fc30d01cf5703c4f1d8efa92a4e8487885` | PASS | `COMPLETE_WITH_ANOMALIES` |
| P10 | STATISTICS | 00:59:24 | 200 | 2 446 ms | 3 675 octets | `e063a908a86bc32ef99231189c9d89318d0040a2410b5d4626cdba98d196dba2` | PASS | `COMPLETE_REDUCED_SCHEMA` |
| P10 | EVENTS | 00:59:40 | 200 | 3 047 ms | 5 864 octets | `7fd779b6ce72d53d11a87952482e3c0c6ea8eacca13f1cb03237a29942a9d0b5` | PASS | `COMPLETE` |
| P10 | BOX_SCORE | 00:59:58 | 200 | 5 261 ms | 44 279 octets | `e3b1bb85a97c31a5807366317b0c159699f6ba32fe97dfd9e5ea184c50e735c8` | PASS | `COMPLETE_WITH_ANOMALIES` |
| P11 | STATISTICS | 01:00:19 | 200 | 2 464 ms | 3 747 octets | `5a36129f964c4da60019b5c0e00742bc71ce2cf9d732824b49e7b92e7e51d6d1` | PASS | `COMPLETE` |
| P11 | EVENTS | 01:00:34 | 200 | 2 986 ms | 3 463 octets | `ed8eea1ee1c2a51ece75772722181994bda5645238fd292c663d2d0c50b1a1b9` | PASS | `COMPLETE` |
| P11 | BOX_SCORE | 01:00:48 | 200 | 1 318 ms | 45 128 octets | `3b2ce5f4e23710f66501bc24a646657cc79f3828040b7b379817eea6879ced31` | PASS | `COMPLETE_WITH_ANOMALIES` |

Les neuf réponses sont exploitables, leurs neuf payloads bruts sont conservés et leurs neuf replays hors réseau sont `PASS`. Le compteur local Europe/Paris du 16 août atteint 9 / 80. Le quota fournisseur est passé de 86 / 100 avant minuit à 85 / 100 lors du premier appel, puis à 77 / 100 : aucune remise à zéro liée à minuit Europe/Paris n'a été observée. La réserve quotidienne de 20 appels n'a pas été entamée.

Preuves locales hors Git :

- P09 : [ENR-P09-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json), [ENR-P09-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P09-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json) ;
- P10 : [ENR-P10-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json), [ENR-P10-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P10-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json) ;
- P11 : [ENR-P11-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json), [ENR-P11-EVENTS-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P11-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json).

### P09 - Al-Nassr 3-0 Al-Fateh SC

#### Statistiques d'équipe STA-01

Les deux équipes sont correctement identifiées et possèdent chacune 40 statistiques non nulles, avec un schéma parfaitement symétrique.

| Statistique | Al-Nassr | Al-Fateh SC |
|---|---:|---:|
| Expected Goals | 1,63 | 1,06 |
| Expected Assists | 1,37 | 0,81 |
| Possession | 59 % | 41 % |
| Tirs cadrés | 9 | 3 |
| Passes totales | 449 | 308 |
| Cartons jaunes | 1 | 1 |
| Cartons rouges | 0 | 0 |

Classification STA-01 : `COMPLETE`.

#### Événements EVT-01

Le flux contient 15 événements : 3 buts, 10 remplacements et 2 cartons jaunes. Les buts d'Ângelo à la 37e minute, João Félix à la 40e et Samú Costa à la 73e reconstituent le score final de 3-0. Les trois buts et les trois passes décisives concordent avec les totaux joueurs ; les deux avertissements concordent avec les agrégats d'équipe et les lignes joueurs.

Classification EVT-01 : `COMPLETE`.

#### Statistiques joueurs PLY-01 et identifiants

Le box-score contient 20 joueurs Al-Nassr et 19 joueurs Al-Fateh SC. Chaque équipe possède 16 joueurs crédités de minutes positives. Les 39 identifiants du box-score sont non nuls et uniques.

Trois catégories PLY-01 déjà connues se reproduisent :

1. `MISSING_PLAYER_FULL_NAME` : Abdulrahman Al-Otaibi, resté à zéro minute, et Jefferson Ramos, entré pour 27 minutes, possèdent `fullName=null`.
2. `ZERO_MINUTE_EXPECTED_METRICS` : Nader Al Sharari et Ali Al-Hassan possèdent respectivement `expectedAssists=0.02` et `0.16` malgré zéro minute ; Abdulaziz Al Fawaz possède également `expectedAssists=0.16` à zéro minute.
3. `INVALID_SECOND_YELLOW_VALUE` : João Félix et Faisal Al Abdulwahed possèdent chacun `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`, alors que le flux ne contient qu'un avertissement pour chacun et aucune expulsion.

`MISSING_PLAYER_YELLOW_CARD` ne se reproduit pas sur P09. En revanche, la réconciliation événementiel / box-score ouvre la catégorie ID-01 non bloquante `CROSS_ENDPOINT_PLAYER_ID_MISMATCH` :

- `F. Al Abdulwahed` utilise l'identifiant `96356561` dans l'événementiel, contre `56411173` pour Faisal Al Abdulwahed dans le box-score ;
- `F. Al Zubaidi` utilise `93214002`, contre `64795409` pour Fahad Zubaidi ;
- `A. R. Al Khaibre` utilise `92765617`, contre `71217055` pour Abdul Rahman Al Khaibari ;
- le remplaçant entrant `Bin Mahmoud`, identifiant `92790089`, n'existe pas sous cette référence dans le box-score ; celui-ci attribue les neuf minutes correspondantes à Sattam Al Tumbukti, identifiant `65096318`. Ce dernier rapprochement reste une observation de rôle et de durée, pas un alias canonique automatiquement accepté.

Les valeurs brutes restent inchangées et aucun rapprochement inter-endpoints ne doit reposer sur le seul identifiant fournisseur. Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`.

### P10 - Sevilla FC 2-1 Rayo Vallecano

#### Statistiques d'équipe STA-01

Les deux équipes possèdent chacune 39 statistiques non nulles et le même schéma. Par rapport aux schémas à 40 champs de P09 et P11, seul le champ `Attacks` est absent des deux côtés.

| Statistique | Sevilla FC | Rayo Vallecano |
|---|---:|---:|
| Expected Goals | 2,30 | 2,00 |
| Expected Assists | 0,64 | 0,46 |
| Possession | 49 % | 51 % |
| Tirs cadrés | 4 | 3 |
| Passes totales | 318 | 336 |
| Cartons jaunes agrégés | 4 | 4 |
| Cartons rouges | 1 | 0 |

STA-01 est classé `COMPLETE_REDUCED_SCHEMA` : l'absence symétrique du seul champ `Attacks` n'empêche pas l'exploitation des autres statistiques, mais elle doit rester distinguée d'un schéma complet à 40 champs.

#### Événements EVT-01

Le flux contient 24 événements : 1 but hors penalty, 2 penalties transformés, 10 remplacements, 9 cartons jaunes, 1 carton rouge direct et 1 but annulé par la VAR. Álvaro García ouvre le score à la 4e minute ; Jon Guridi égalise sur penalty à la 52e ; Peque donne la victoire à Sevilla sur penalty à 90+7. Le but d'Isi Palazón est annulé à la 32e. Le rouge direct de Kike Salas à la 88e concorde avec `cardsRed=1` dans sa ligne joueur et avec l'agrégat Sevilla.

Classification EVT-01 : `COMPLETE`.

#### Statistiques joueurs PLY-01 et identifiants

Le box-score contient 23 joueurs Sevilla et 22 joueurs Rayo, dont 16 joueurs avec minutes positives pour chaque équipe. Les 45 identifiants sont non nuls et uniques. Les totaux joueurs reconstituent bien deux penalties Sevilla et un but Rayo.

Les quatre catégories PLY-01 connues sont observées :

1. `MISSING_PLAYER_FULL_NAME` : Nico Guillén, 45 minutes, Miguel Ángel Sierra Ortega, 70 minutes, et Iker Muñoz, zéro minute, côté Sevilla ; Marco Román et Ivan Alonso Martin, tous deux à zéro minute, côté Rayo.
2. `ZERO_MINUTE_EXPECTED_METRICS` : José Ángel Carmona, Julio Díaz et Andres Castrin côté Sevilla ; Sergio Lozano et Marco Román côté Rayo. Marco Román possède notamment `expectedGoals=0.18` et `expectedAssists=0.01` malgré zéro minute.
3. `INVALID_SECOND_YELLOW_VALUE` : Isaac Romero, Jon Guridi, Gabriel Suazo, Lucien Agoumé, Álvaro García, Unai López, Fran Pérez et Alemão possèdent chacun `cardsYellow=1`, `cardsSecondYellow=1` et `cardsRed=0`, sans second avertissement ni expulsion correspondante.
4. `MISSING_PLAYER_YELLOW_CARD` : l'événementiel attribue un jaune à Juan Iglesias à 90+12, mais sa ligne joueur conserve `cardsYellow=0`, `cardsSecondYellow=0`, `cardsRed=0`. L'événementiel totalise ainsi cinq jaunes Sevilla, tandis que l'agrégat d'équipe et le box-score n'en totalisent que quatre.

La catégorie ID-01 `CROSS_ENDPOINT_PLAYER_ID_MISMATCH` se reproduit également : `N. Guilen` utilise l'identifiant `104954444` dans l'événementiel, contre `88048156` pour Nico Guillén dans le box-score. Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`.

### P11 - Rio Ave FC 0-2 FC Porto

#### Statistiques d'équipe STA-01

Les deux équipes sont correctement identifiées et possèdent chacune 40 statistiques non nulles avec un schéma symétrique.

| Statistique | Rio Ave FC | FC Porto |
|---|---:|---:|
| Expected Goals | 0,58 | 1,97 |
| Expected Assists | 0,84 | 1,99 |
| Possession | 48 % | 52 % |
| Tirs cadrés | 1 | 6 |
| Passes totales | 407 | 441 |
| Cartons jaunes joueurs | 0 | 0 |
| Cartons rouges | 0 | 0 |

Classification STA-01 : `COMPLETE`.

#### Événements EVT-01

Le flux contient 14 événements : 2 buts, 10 remplacements, 1 but annulé par la VAR pour hors-jeu et 1 événement disciplinaire sans identifiant joueur. Nehuén Pérez marque à la 11e minute sur une passe de Gabri Veiga ; le but de Jakub Kiwior est annulé à la 34e ; Borja Sainz marque à la 82e sur une passe de Hwang In-beom. Le score et les deux passes décisives concordent avec le box-score.

L'événement de la 81e minute est attribué à `F. Farioli` avec `playerId=null`. Il n'est donc pas assimilé à un carton joueur et n'entre pas en contradiction avec les zéros des agrégats joueurs. Classification EVT-01 : `COMPLETE`.

#### Statistiques joueurs PLY-01 et identifiants

Le box-score contient 23 joueurs par équipe, dont 16 joueurs avec minutes positives de chaque côté. Les 46 identifiants sont non nuls et uniques.

Deux catégories PLY-01 connues se reproduisent :

1. `MISSING_PLAYER_FULL_NAME` : Miguel Patricío et Samuel da Silva Alves, dix minutes chacun, ainsi que Caike, Dai Balde, Antoine Wenck, Duarte Cunha et Eduardo Ferreira, restés à zéro minute.
2. `ZERO_MINUTE_EXPECTED_METRICS` : Caike, Jakub Brabec et Dai Balde côté Rio Ave ; João Costa, Dominik Prpić, Francisco Moura et Rodrigo Mora côté Porto. Dai Balde possède notamment `expectedGoals=0.19`, `expectedAssists=0.01` et `expectedGoalsOnTarget=0.5` malgré zéro minute.

`INVALID_SECOND_YELLOW_VALUE` et `MISSING_PLAYER_YELLOW_CARD` ne se reproduisent pas sur P11. La catégorie ID-01 `CROSS_ENDPOINT_PLAYER_ID_MISMATCH` se reproduit néanmoins : `R. Galcik` utilise l'identifiant `86939671` dans l'événementiel, contre `9891833` pour Roland Galčík dans le box-score. Classification PLY-01 : `COMPLETE_WITH_ANOMALIES`.

### Conclusion transversale du lot post-match du 15 août

Les trois scores finaux sont reconstitués sans ambiguïté et les totaux de buts, penalties, passes décisives et rouges directs concordent entre événements et box-scores. P09 et P11 disposent du schéma statistique complet à 40 champs ; P10 possède 39 champs symétriques, seul `Attacks` étant absent.

Les quatre catégories PLY-01 déjà connues restent les seules catégories PLY-01 confirmées et demeurent non bloquantes. `MISSING_PLAYER_YELLOW_CARD` se reproduit sur Juan Iglesias ; les trois autres catégories se reproduisent sur au moins deux rencontres. Une nouvelle catégorie ID-01 non bloquante, `CROSS_ENDPOINT_PLAYER_ID_MISMATCH`, est ouverte sur six références distinctes réparties entre P09, P10 et P11. Elle impose de préserver la provenance par endpoint et d'interdire toute jointure silencieuse fondée uniquement sur l'identifiant joueur Highlightly.

Les résultats finaux sont donc : STA-01 `COMPLETE` pour P09 et P11, `COMPLETE_REDUCED_SCHEMA` pour P10 ; EVT-01 `COMPLETE` pour les trois rencontres ; PLY-01 `COMPLETE_WITH_ANOMALIES` pour les trois rencontres. Aucune anomalie détectée n'est bloquante pour la poursuite d'ENR-001.

## ENR-P12 - Paris Saint-Germain - Lens - prématch du 16 août 2026

### Exécution T-60

Le heartbeat prévu à 19:45 Europe/Paris a été reçu à 19:46:55, soit une gigue de 1 minute et 55 secondes. L'inspection des preuves, achevée à 19:51:07, a confirmé qu'aucun DETAIL ou LINEUP P12 n'avait déjà été collecté. Le compteur local du 16 août était alors de 9 / 80 et le dernier quota fournisseur observé de 77 / 100.

Une première tentative s'est arrêtée avant l'appel réseau : Maven a essayé de résoudre le parent Spring Boot alors que l'accès sortant du bac à sable était interdit. Cet incident local n'a produit aucune preuve et n'a consommé aucun appel fournisseur. L'artefact exécutable déjà construit a ensuite été réutilisé avec l'option `-SkipBuild`.

| Fournisseur | Famille | Début réel Paris | Fenêtre réelle approximative | HTTP | Latence | Taille | Quota | SHA-256 | Replay |
|---|---|---:|---:|---:|---:|---:|---:|---|---|
| Highlightly | DETAIL | 19:52:23 | T-53 | 200 | 572 ms | 1 127 octets | 99 / 100 | `a0a918c8a9e900cd36fb52fa15b560ebd63c2b9faeb53404520cd7a80f4e2d04` | PASS |
| Highlightly | LINEUP | 19:52:46 | T-52 | 200 | 908 ms | 334 octets | 98 / 100 | `398fd8fb46972e7a2043345835f83196b70f4afd731baa7c0d62b2cc1e76afff` | PASS |

Le détail confirme sans divergence :

- le match Highlightly `1316319925` ;
- la ligue `448410`, libellée `Trophée des Champions`, saison 2026 ;
- le tour `Final` ;
- le coup d'envoi `2026-08-16T18:45:00Z`, soit 20:45 Europe/Paris ;
- le statut `Not started` ;
- `Paris Saint Germain`, identifiant `73119`, dans le rôle brut HOME ;
- `Lens`, identifiant `99500`, dans le rôle brut AWAY.

`Paris Saint Germain` est l'alias explicitement accepté de Paris Saint-Germain. Les identifiants, les participants et l'horaire correspondent au manifeste. L'ordre brut PSG HOME / Lens AWAY est conservé et classé `ORDER_STABLE` par rapport à CAL-01. Le terrain neutre rendrait une inversion admissible sous la classification distincte `ORDER_INVERTED_NEUTRAL_VENUE`, mais aucune inversion n'est observée dans cette preuve. ID-01 et TIM-01 sont `PASS`.

La réponse LINEUP identifie également les deux équipes, mais renvoie pour chacune :

- `formation=Unknown` ;
- un tableau `initialLineup` vide ;
- un tableau `substitutes` vide.

Après aplatissement récursif et déduplication par identifiant, le résultat demeure 0 / 0 titulaire unique et 0 / 0 remplaçant. Classification LIN-01 T-60 : `ABSENT`. P12 reste éligible au contrôle conditionnel T-45 prévu à 20:00 Europe/Paris.

Le quota fournisseur est passé à 99 / 100 sur DETAIL puis à 98 / 100 sur LINEUP, ce qui établit une remise à zéro intervenue après le dernier appel de 01:00. Le compteur local Europe/Paris atteint 11 / 80 et la réserve de sécurité de 20 appels reste intacte. Aucun appel football-data.org ou post-match n'a été exécuté.

Preuves locales hors Git : [ENR-P12-DETAIL-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P12-LINEUP-001](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-45

Le heartbeat prévu à 20:00 Europe/Paris a été reçu à 20:00:53, soit une gigue de 53 secondes. L'inspection à 20:01:09 a confirmé que la preuve T-60 était toujours `ABSENT` et qu'aucune preuve T-45 concurrente n'existait. Aucun DETAIL n'a été répété.

L'unique appel conditionnel LINEUP a débuté à 20:01:27, soit environ T-44 : HTTP 200 en 2 456 ms, 334 octets, quota 97 / 100, SHA-256 `398fd8fb46972e7a2043345835f83196b70f4afd731baa7c0d62b2cc1e76afff`, replay `PASS`.

L'empreinte et les 334 octets sont strictement identiques à T-60. Paris Saint Germain `73119` reste HOME et Lens `99500` AWAY ; les formations demeurent `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant après aplatissement récursif et déduplication. L'ordre reste `ORDER_STABLE` et aucune anomalie ID-01 n'est ouverte.

Classification LIN-01 T-45 : `ABSENT`. Le compteur local du 16 août atteint 12 / 80, le quota fournisseur indique 97 / 100 et la réserve de sécurité de 20 appels reste intacte. P12 demeure éligible au contrôle conditionnel T-30 prévu à 20:15 Europe/Paris.

Preuve locale hors Git : [ENR-P12-LINEUP-002](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-30

Le heartbeat prévu à 20:15 Europe/Paris a été reçu à 20:15:23, soit une gigue de 23 secondes. L'inspection à 20:15:45 a confirmé la classification T-45 `ABSENT`, l'absence de preuve T-30 concurrente et un dernier quota de 97 / 100. Aucun DETAIL n'a été répété.

L'appel conditionnel LINEUP a débuté à 20:15:58, soit environ T-29 : HTTP 200 en 497 ms, 334 octets, quota 96 / 100, SHA-256 `398fd8fb46972e7a2043345835f83196b70f4afd731baa7c0d62b2cc1e76afff`, replay `PASS`.

La réponse reste byte pour byte identique à T-60 et T-45. Paris Saint Germain `73119` est toujours HOME et Lens `99500` AWAY ; les formations restent `Unknown`, avec 0 / 0 titulaire unique et 0 / 0 remplaçant après aplatissement récursif et déduplication. L'ordre demeure `ORDER_STABLE` et aucune anomalie ID-01 n'est ouverte.

Classification LIN-01 T-30 : `ABSENT`. Le compteur local du 16 août atteint 13 / 80, le quota fournisseur indique 96 / 100 et la réserve de sécurité de 20 appels reste intacte. P12 demeure éligible au contrôle conditionnel T-15 prévu à 20:30 Europe/Paris.

Preuve locale hors Git : [ENR-P12-LINEUP-003](evidence/enr-001-evidence-index-v0.1.json).

### Exécution T-15 et fermeture du prématch

Le heartbeat prévu à 20:30 Europe/Paris a été reçu à 20:30:53, soit une gigue de 53 secondes. L'inspection à 20:31:06 a confirmé la classification T-30 `ABSENT`, l'absence de preuve T-15 concurrente et un dernier quota de 96 / 100. Aucun DETAIL n'a été répété.

L'appel conditionnel LINEUP a débuté à 20:31:17, soit environ T-14 : HTTP 200 en 386 ms, 3 288 octets, quota 95 / 100, SHA-256 `f6401a6c9f43658ad0f0cbc2749160143bdc6e6175be5a68b076eabec54c5e3a`, replay `PASS`.

Le changement d'empreinte et de taille situe la publication de la composition après la preuve T-30 de 20:15:58 et au plus tard au début de l'appel T-15 de 20:31:17. Après aplatissement récursif et déduplication par identifiant :

- Paris Saint Germain `73119`, rôle brut HOME, formation `4-3-3` : 11 titulaires, 11 identifiants titulaires uniques et non nuls, aucun doublon, 9 remplaçants tous identifiés ;
- Lens `99500`, rôle brut AWAY, formation `3-4-2-1` : 11 titulaires, 11 identifiants titulaires uniques et non nuls, aucun doublon, 9 remplaçants ;
- Mezian Mesloub, remplaçant de Lens, possède `id=null` ; les huit autres remplaçants lensois ont un identifiant unique non nul.

Les identifiants d'équipe et les rôles restent conformes au manifeste ; l'ordre demeure `ORDER_STABLE`. La condition de complétude LIN-01 est satisfaite pour les 22 titulaires. L'identifiant absent d'un remplaçant reproduit la catégorie ID-01 non bloquante `MISSING_PLAYER_ID`, déjà observée sur d'autres échantillons, sans remettre en cause la composition initiale.

Classification finale prématch P12 : `COMPLETE_WITH_NON_BLOCKING_SUBSTITUTE_ID_ANOMALY` à T-15, exécution réelle à environ T-14. Le compteur local du 16 août atteint 14 / 80, le quota fournisseur indique 95 / 100 et la réserve de sécurité de 20 appels reste intacte.

L'automation `enr-001-p12-psg-lens-pr-match-16-ao-t` a été mise au statut `PAUSED` immédiatement après validation. La fenêtre T-5 est fermée sans appel, conformément au séquencement conditionnel ; aucune collecte à T0 ou après T0 n'est autorisée dans LIN-01.

Preuve locale hors Git : [ENR-P12-LINEUP-004](evidence/enr-001-evidence-index-v0.1.json).

## Collecte post-match ENR-P12 - 17 août 2026

### Périmètre, autorisation et traçabilité

La collecte a été exécutée le 17 août après confirmation utilisateur de la fin de Lens - Paris Saint-Germain. Le libellé utilisateur inverse l'ordre du manifeste, mais la rencontre est une finale sur terrain neutre : les participants restent Paris Saint Germain `73119` et Lens `99500`, et les rôles bruts du fournisseur sont préservés sans créer d'anomalie d'identité.

L'ordre des entrées n'est pas stable entre les familles post-match : STATISTICS retourne Paris Saint Germain puis Lens, tandis que BOX_SCORE retourne Lens puis Paris Saint Germain. Ces entrées ne comportent pas de champ HOME/AWAY. Cette variation n'est pas requalifiée en anomalie d'identité sur terrain neutre, mais elle impose une règle d'intégration : associer les données par `team.id` et réutiliser les rôles explicitement fournis par DETAIL ou LINEUP ; la position dans un tableau post-match ne doit jamais porter la sémantique domicile/extérieur.

Deux familles correspondant directement aux statistiques ont été appelées : STATISTICS pour STA-01 et BOX_SCORE pour PLY-01. Une tentative d'exécution de la famille EVENTS a été refusée avant tout appel fournisseur, le terme « statistiques » n'étant pas considéré comme une autorisation explicite distincte pour le journal événementiel. Aucun quota n'a été consommé et aucune preuve orpheline n'a été créée par ce refus. EVT-01 demeure donc `NOT_EXECUTED`, sans préjuger de la disponibilité des événements chez Highlightly.

| Famille | Début réel Paris | HTTP | Latence | Taille | Quota après appel | SHA-256 | Replay |
|---|---:|---:|---:|---:|---:|---|---|
| STATISTICS | 12:39:49 | 200 | 2 469 ms | 1 634 octets | 99 / 100 | `6d424602cd7a16c369f7bbfffb7a218c669bbca048a1a88e23833aea60103081` | PASS |
| BOX_SCORE | 12:40:55 | 200 | 1 990 ms | 39 433 octets | 98 / 100 | `163c16c521907a8a4349b883f21cb46b581176a248378ae5f213a9290e3a9360` | PASS |

Les deux appels sont les seuls appels Highlightly enregistrés le 17 août au moment du contrôle. Les deux réponses sont HTTP 200, les deux payloads bruts sont conservés, leur JSON est valide et leurs replays hors réseau sont `PASS`. Le quota fournisseur a été remis à zéro avant la collecte, puis est passé de 99 / 100 à 98 / 100 ; la réserve de sécurité de 20 appels reste intacte. Le total ENR-001 atteint 127 appels, 127 réponses HTTP 2xx, 127 preuves brutes et 127 replays réussis.

Preuves locales hors Git : [ENR-P12-STATISTICS-001](evidence/enr-001-evidence-index-v0.1.json) et [ENR-P12-BOX-SCORE-001](evidence/enr-001-evidence-index-v0.1.json).

### STA-01 - statistiques d'équipe

STATISTICS identifie exactement les deux équipes attendues, avec 16 champs non nuls et symétriques par équipe. Le schéma est nettement plus réduit que le schéma à 40 champs rencontré sur plusieurs matchs de championnat, mais aucune valeur n'est absente d'un seul côté. STA-01 est donc classé `COMPLETE_REDUCED_SCHEMA`.

| Statistique brute Highlightly | Paris Saint Germain | Lens |
|---|---:|---:|
| Shots accuracy | 0,27 | 0,25 |
| Shots on target | 6 | 2 |
| Shots off target | 10 | 5 |
| Blocked shots | 6 | 1 |
| Shots within penalty area | 10 | 3 |
| Shots outside penalty area | 12 | 5 |
| Fouls | 10 | 9 |
| Corners | 6 | 2 |
| Offsides | 2 | 2 |
| Possession | 0,69 | 0,31 |
| Yellow cards | 3 | 1 |
| Red cards | 1 | 1 |
| Goalkeeper saves | 1 | 6 |
| Total passes | 725 | 319 |
| Successful passes | 657 | 249 |
| Failed passes | 68 | 70 |

Les valeurs de possession sont conservées sous leur forme décimale brute : `0.69` et `0.31`, soit une somme exacte de 1. Les trois décompositions arithmétiques disponibles sont cohérentes : 657 + 68 = 725 passes pour Paris Saint Germain, 249 + 70 = 319 pour Lens, et les tirs cadrés plus non cadrés plus bloqués donnent respectivement 22 et 8 tentatives, compatibles avec les précisions brutes 0,27 et 0,25 après arrondi.

### PLY-01 - statistiques joueurs et rapprochement avec le prématch

BOX_SCORE retourne 20 joueurs par équipe : 11 titulaires, 5 remplaçants entrés en jeu et 4 remplaçants non utilisés pour chacune. Les 40 joueurs possèdent un identifiant non nul et unique dans leur équipe, un `fullName` non vide et une ligne statistique exploitable. Aucun joueur à zéro minute ne possède de métrique numérique non nulle et toutes les métriques attendues `expectedGoals`, `expectedAssists`, `expectedGoalsOnTarget`, `expectedGoalsOnTargetConceded` et `expectedGoalsPrevented` sont nulles dans ce payload.

Les 40 noms du box-score correspondent exactement, avec respect de la casse et des accents, aux 40 noms de la composition T-15. Les 39 identifiants déjà présents dans LINEUP sont stables dans BOX_SCORE. Mezian Mesloub, remplaçant lensois dont l'identifiant était nul dans LINEUP, reçoit l'identifiant `107848097` dans BOX_SCORE. Cette récupération inter-endpoints est enregistrée sans réécrire la preuve prématch et sans effacer la catégorie source `MISSING_PLAYER_ID`.

Le score statistique reconstituable est Lens 1-0 Paris Saint Germain : Florian Thauvin porte `goalsScored=1`, Matthieu Udol `assists=1`, et aucun joueur parisien ne porte de but. Cette conclusion provient exclusivement du box-score ; l'absence d'EVT-01 empêche de contrôler la minute et la typologie de l'action.

| Contrôle transversal STATISTICS / BOX_SCORE | Paris Saint Germain | Lens | Résultat |
|---|---:|---:|---|
| Tirs cadrés | 6 / 6 | 2 / 2 | Concordant |
| Tirs non cadrés | 10 / 10 | 5 / 5 | Concordant |
| Passes totales | 725 / 725 | 319 / 319 | Concordant |
| Passes réussies | 657 / 657 | 249 / 249 | Concordant |
| Passes manquées | 68 / 68 | 70 / 70 | Concordant |
| Cartons jaunes | 3 / 3 | 1 / 1 | Concordant |
| Cartons rouges | 1 / 1 | 1 / 1 | Concordant |

Les rouges joueurs concordent avec les agrégats d'équipe : Nuno Mendes porte `cardsRed=1` pour Paris Saint Germain et Kyllian Antonio `cardsRed=1` pour Lens. Les quatre jaunes agrégés sont également reproduits exactement au niveau joueur.

La catégorie connue `INVALID_SECOND_YELLOW_VALUE` se reproduit toutefois pour quatre joueurs :

- Andrija Bulatovic : `cardsYellow=1`, `cardsSecondYellow=1`, `cardsRed=0` ;
- Achraf Hakimi : `cardsYellow=1`, `cardsSecondYellow=1`, `cardsRed=0` ;
- Désiré Doué : `cardsYellow=1`, `cardsSecondYellow=1`, `cardsRed=0` ;
- Fabián Ruiz : `cardsYellow=1`, `cardsSecondYellow=1`, `cardsRed=0`.

Ces quadruplets sont incompatibles avec la définition d'un second jaune entraînant l'exclusion : aucun des quatre joueurs ne porte deux jaunes ni un rouge. La valeur brute est conservée mais `cardsSecondYellow` ne doit pas alimenter un calcul métier sans validation ou correction fournisseur.

PLY-01 est classé `COMPLETE_WITH_ANOMALIES`. Aucune nouvelle catégorie d'anomalie n'est ouverte : `INVALID_SECOND_YELLOW_VALUE` est une catégorie déjà confirmée et non bloquante ; `MISSING_PLAYER_FULL_NAME` et `ZERO_MINUTE_EXPECTED_METRICS` ne se reproduisent pas dans ce payload. `MISSING_PLAYER_YELLOW_CARD` n'est pas évaluable sans EVT-01.

## Finalisation et revalidation hors réseau - 1er septembre 2026

La finalisation a été reconstruite depuis la sauvegarde restaurable revalidée, sans modifier le checkout historique et sans exécuter de nouvel appel fournisseur. Les payloads complets restent hors Git ; l'index expurgé et les fixtures synthétiques versionnées suffisent à contrôler leur intégrité et les formes observées.

| Contrôle | Résultat |
|---|---:|
| Entrées attendues dans l'index | 127 |
| Entrées indexées | 127 |
| Preuves vérifiées en lecture seule | 127 |
| Empreintes ou replays non conformes | 0 |
| Tests standards | 57 / 57 |
| Tests PostgreSQL/Testcontainers | 12 / 12 |
| Validation Windows complète | `PASS` |
| Contrôle de secrets | `PASS` |
| Fichiers de preuve externes scannés sans alerte | 457 / 457 |

Le manifeste conserve 12 rencontres principales, 4 réserves et 1 contrôle transversal. ENR-P02, les quatre réserves, le second contrôle ENR-C01 et EVT-01 P12 restent explicitement non exécutés dans les conditions décrites plus haut ; aucun résultat n'a été inventé pour les remplacer. Le lot a été revu, publié au commit `743aff7`, fusionné dans `main` par la Pull Request `#3` au commit `6913cea`, puis clôturé le 1er septembre 2026.
