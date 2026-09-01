# État du projet au 1er septembre 2026

## Synthèse

Betting Project possède un socle applicatif, un catalogue canonique et une chaîne de validation fusionnés, ainsi qu'un benchmark football finalisé sur une branche séparée avec son corpus complet conservé hors Git. Il ne dispose pas encore d'une chaîne de collecte planifiée et d'enrichissement exploitable de bout en bout.

| Couche | État | Conclusion |
|---|---|---|
| Cadrage produit et architecture | Consolidé | Les décisions durables du cadrage v0.5.1 sont retranscrites dans `project-brief.md` et les ADR. |
| Socle applicatif | Fusionné et clôturé | BOOT-001 a livré Java 25, Spring Boot 4.1, PostgreSQL, Flyway, les trois profils autorisés, des jobs/outbox minimaux et le replay hors réseau. |
| Catalogue canonique | Fusionné et clôturé | CAT-001 a livré compétitions, saisons, équipes, rencontres, mappings, anomalies, provenance et normalisation idempotente. |
| Benchmark fournisseurs | Exécuté | INV-01, CAL-01 et ENR-001 totalisent 180 appels et établissent une baseline par capacité. |
| ENR-001 | Finalisé, prêt pour revue, non intégré | Le diff est reconstruit sur `codex/enr-001`, les 127 preuves sont vérifiées et les validations sont vertes. Commit, publication et fusion restent soumis à des autorisations séparées. |
| Chaîne opérationnelle | Non réalisée | Aucun ordonnanceur réel, routeur fournisseur, worker de jobs complet, workflow de résolution des anomalies ou connecteur de production n'est fusionné. |
| Produit de paris | Non commencé | Cotes, probabilités, valuebets, recommandations, documents, diffusion et suivi de performance sont différés. |

## État fusionné dans `main`

### BOOT-001

Le socle fournit :

- Java 25 avec règle Maven Enforcer `[25,26)` ;
- Maven 3.9.16, Spring Boot 4.1.0 et PostgreSQL 17 ;
- profils `control-api`, `batch-worker` et `replay` ;
- migration Flyway `V001` ;
- jobs persistants, outbox minimale, audit de snapshots et replay hors réseau ;
- contrôles d'architecture et procédures locales initiales.

### CAT-001

Le catalogue fournit :

- modèle canonique des compétitions, saisons, équipes et rencontres ;
- références fournisseurs, mappings confirmés/ambigus/rejetés et anomalies ;
- provenance jusqu'au snapshot brut ;
- normalisation et rejeu idempotents ;
- migration additive Flyway `V002` ;
- 17 tests standards et 11 tests PostgreSQL/Testcontainers validés sous Windows et Ubuntu/WSL2.

CAT-001 est fusionné et clôturé. Les limites restantes — concurrence, ordre des observations, autorité primaire/contrôle et opérations humaines sur les anomalies — appartiennent à CAT-002.

## Benchmark fournisseurs

Les campagnes INV-01, CAL-01 et ENR-001 ont consolidé 180 appels :

- Highlightly : 161 appels sur 161 avec réponse HTTP 2xx ;
- football-data.org : 16 réponses HTTP 200 sur 17 appels, utile comme contrôle calendrier/identité ;
- API-FOOTBALL : accès technique démontré, mais saison 2026 inutilisable avec le plan testé ; statut `BLOCKED_BY_PLAN` sur le chemin critique gratuit.

Ces résultats sont des mesures du 9 au 17 août 2026, pas des garanties permanentes. Un changement de plan, de saison ou de capacité requiert une nouvelle preuve datée et rejouable.

Le collecteur ENR-001 est un outil de benchmark contrôlé. Il ne doit pas être présenté ni réutilisé implicitement comme le futur adaptateur de production.

## Sauvegarde de récupération ENR-001

Avant l'ouverture des modifications DEVX-001, une sauvegarde datée `2026-09-01T11-27-31` a été créée hors dépôt sous `Documents/Betting Project/recovery/ENR-001/`.

La sauvegarde référence le commit de base `25d1c29` et conserve :

- 33 entrées de statut Git logique ;
- les patches indexé et non indexé ;
- 139 fichiers utiles, dont 3 fichiers non suivis ;
- 457 fichiers de preuves de benchmark ;
- un manifeste SHA-256 de 1 203 entrées avec taille et chemin logique.

Une restauration à blanc a validé l'identité des patches indexé et non indexé ainsi que les octets des fichiers conservés. Le rapport et le manifeste de validation restent avec la sauvegarde externe ; aucun payload complet ni chemin propre à l'ancien checkout n'est requis par le dépôt.

## Finalisation locale ENR-001

Le lot a été reconstruit sur `codex/enr-001` depuis la sauvegarde restaurable, sans rebase destructif du checkout historique et sans appel fournisseur. Il apporte :

- un index expurgé de 127 appels et un vérificateur lecture seule, hors réseau ;
- sept fixtures synthétiques couvrant les familles réellement appelées ;
- le collecteur historique de benchmark, explicitement séparé des futurs adaptateurs de production ;
- un garde de quota sans hypothèse de remise à zéro à minuit, un verrou exclusif et des écritures atomiques ;
- les deux preuves Hirnyk distinctes : ambiguïté textuelle historique et alias exact explicitement confirmé ;
- la baseline MVP et toutes les limites ou non-exécutions enregistrées sans inventer de résultat.

La validation du 1er septembre 2026 réussit avec 57 tests standards, 12 tests PostgreSQL/Testcontainers et 127 preuves sur 127 conformes. La validation Windows complète et le contrôle de secrets sont verts. Le lot reste non commité, non publié et non fusionné jusqu'aux décisions humaines correspondantes.

## Risque P0 traité et fusionné par DEVX-001

À l'ouverture de DEVX-001, deux défauts permettaient un faux succès de la validation Windows :

- le wrapper échouait lorsque le répertoire Maven `.m2` ordinaire exposait une propriété `Target` nulle ;
- le script de validation ne propageait pas systématiquement les codes d'échec des commandes natives et pouvait omettre l'intégration lorsque la CLI Docker était absente.

Le correctif, les tests de faux vert, la validation Windows complète et les contrôles Linux hors intégration locale sont réussis. L'intégration WSL2 locale a échoué comme attendu faute de socket Docker, sans être omise ni transformée en succès. Les CI Windows et Linux de la branche et de la Pull Request sont vertes sur le commit fonctionnel `06ba0e2`, le défaut P2 relevé en revue est corrigé et son fil est résolu. Le porteur a explicitement autorisé la fusion de la Pull Request `#2` le 1er septembre 2026, sous réserve de la CI verte du commit documentaire final. La présence de cette version dans `main` signifie que DEVX-001 est accepté, fusionné et clôturé, ce qui ouvre la reprise séparée d'ENR-001.

## Décisions actives

- Prochain jalon : pipeline football fiable, pas MVP prématch englobant immédiatement cotes et publication.
- Calendrier : `PPL`, `PD`, `DED` et `ELC`.
- Enrichissement initial : Liga Portugal et LaLiga, jusqu'à sept rencontres priorisées par jour.
- Highlightly : source principale pour les capacités mesurées ; football-data.org : source de contrôle sur le noyau ; API-FOOTBALL : bloqué par le plan testé.
- Budget Highlightly : 80 appels, réserve incompressible de 20.
- Compositions : facultatives et non bloquantes ; aucune validation prématch à T0 ou après T0.
- Aucun pari automatique, aucun appel live, aucune dépendance à SofaScore.
- PostgreSQL reste la source de vérité ; les preuves complètes du benchmark restent hors Git avec manifeste expurgé versionné lors d'ENR-001.

## Prochaines portes

1. revoir ENR-001, puis autoriser séparément son commit, sa publication, sa Pull Request et sa fusion ;
2. réaliser CAT-002 avant toute ingestion planifiée en volume ;
3. construire MVP-001 puis ENR-002 et valider un pilote local de sept jours ;
4. ouvrir OPS-001 seulement après acceptation du pipeline local fiable.

Chaque passage vers `main`, protection GitHub ou déploiement nécessite l'autorisation humaine prévue par ADR-005.
