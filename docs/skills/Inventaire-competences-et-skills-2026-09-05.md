# Compétences capitalisées et skills proposés — Betting Project / SofaScore Local Lab

Date de l’inventaire : 5 septembre 2026.

Ce document recense le savoir-faire observable dans les deux projets et propose des procédures réutilisables pour Codex. Il s’agit d’une proposition de skills à développer ; aucun skill n’est créé ou installé par cet inventaire.

## Périmètre et méthode

Sources consultées : instructions des dépôts, Work Orders, ADR, contrats, runbooks, rapports de qualification et de benchmark, code et tests ciblés, historique Git local, ainsi que des fenêtres de conversations de cadrage et des jalons J0 à J9. L’historique Git visible commence le 8 août pour le laboratoire et le 11 août pour Betting Project ; le cadrage documentaire est antérieur.

Les acquis sont répartis entre :
- **R** : réalisation attestée par du code, des tests ou un compte rendu de qualification ;
- **E** : expérimentation ou validation bornée, dont les résultats ne valent que pour le corpus et l’environnement qualifiés ;
- **P** : capacité prévue, sans preuve suffisante d’achèvement dans le produit.

Les résultats historiques de tests n’ont pas été réexécutés pour cet inventaire. Les offres et couvertures des fournisseurs n’ont pas été vérifiées à nouveau. Les comptes rendus de conversations complètent les sources du dépôt sans remplacer la preuve d’un état Git ou d’une qualification datée.

Betting Project est consulté sur main, avec les fusions CAT-002 et CI-003 visibles. INT-001 est également étudié sur sa branche distincte. Le worktree principal du laboratoire est resté sur une branche J9 au 1er septembre ; les évolutions suivantes sont étudiées dans l’historique et les worktrees correspondants. Une réalisation sur branche ne signifie pas qu’elle est fusionnée.

## Inventaire des compétences

Les identifiants S1 à S15 renvoient aux sources cliquables à la fin du document.

| N° | Famille de compétences | Savoir-faire concret observé | Origine, niveau et preuves |
|---:|---|---|---|
| 1 | Cadrage produit et progression par jalons | Définir un MVP, prioriser les compétitions, borner un lot, expliciter les exclusions, dépendances et critères testables. | Betting + Lab, R ; S1, S2. |
| 2 | Mémoire technique et traçabilité | Relier besoin, WO, ADR, contrat, changement, tests et décision ; maintenir roadmap, statut, changelog et rapports d’incident. | Deux projets, R ; synchronisation perfectible ; S1–S3. |
| 3 | Git, branches, worktrees et revue | Séparer les espaces Human/Eclipse et Codex, inspecter les différences, identifier le commit testé, traiter les remarques, vérifier CI et fusion. | Deux projets, R ; historique des tâches de structuration et S2, S3. |
| 4 | Java, Spring et Maven | Java 25, Spring Boot, Maven Wrapper/Enforcer, configuration par profils, démarrage/arrêt et packaging. | Deux projets, R ; S2, S3, S6. |
| 5 | Architecture modulaire | Monolithe modulaire, domaines indépendants de Spring/JDBC/HTTP, ports applicatifs, adaptateurs, dépendances sans cycles, ArchUnit. | Betting, R ; Lab comme expérience complémentaire ; S2. |
| 6 | Windows, PowerShell, WSL et Docker | PowerShell 5.1/7, codes de sortie natifs, chemins avec espaces, fins de ligne, versions d’outils, socket/named pipe Docker, Eclipse. | Deux projets, R ; S3, S6, historique J9. |
| 7 | Gestion du cycle de vie local | Arguments natifs exacts, identité des processus, disponibilité réelle de l’instance, fermeture gracieuse, enregistrement des ressources créées et nettoyage ciblé. | Lab J9, R/E ; S12–S14. |
| 8 | PostgreSQL et migrations | JDBC, Flyway, contraintes, index, migrations additives, upgrade depuis une base préremplie, préservation des données et triggers historiques. | Deux projets, R ; S2, S7. |
| 9 | Transactions et concurrence | Atomicité d’import, verrous, compare-and-set, création unique, savepoints, rollback et reprise après crash. | Deux projets, R ; S2, S8. |
| 10 | Idempotence et effets durables | Clés d’idempotence, reçus, doublons/collisions, audit append-only, jobs et outbox, absence de second effet durable. | Betting et Lab, R ; worker de production complet encore P ; S2, S12, S15. |
| 11 | Contrats JSON et évolution des schémas | Schémas fermés, parsing strict, versions, compatibilité et incompatibilité explicites, évolution bornée aux cas démontrés. | Deux projets, R ; parsers incidents Lab jusqu’à V15 ; S4, S9. |
| 12 | Modélisation canonique du football | Compétitions, saisons, équipes, rencontres et références fournisseurs ; séparation identité canonique et identifiant source. | Deux projets, R ; S1, S2, S9. |
| 13 | Identités, alias et rôle des participants | Rapprochement prudent, alias historique confirmé, ambiguïté visible, HOME/AWAY par identité d’équipe, terrain neutre et ordre des participants. | Betting R et benchmark E ; S1, S2, S5. |
| 14 | Temps et autorité des données | UTC/Europe-Paris, changement d’heure, date métier, observations anciennes, statuts terminaux, reprogrammation, source PRIMARY/CONTROL. | Deux projets, R ; S1, S2, S8. |
| 15 | Règles métier football détaillées | Périodes, prolongations, VAR, buts, avertissements et exclusions, banc, substitutions, penalties et séances de tirs au but. | Lab R/E ; normalisation sémantique réutilisable, mapping source à adapter ; S9. |
| 16 | Qualité et disponibilité | Distinguer absent, null, zéro, vide valide, indisponible, partiel, tardif et incompatible ; schéma réduit/profond ; incohérences inter-endpoints. | Deux projets R/E ; quarantaine de production Betting encore P ; S1, S5, S9. |
| 17 | Acquisition et qualification d’une source | Pagination, limites de plan, capacité par fournisseur/compétition/saison/phase/famille, arrêt sur incident, collecte et conservation du brut. | Benchmarks E, composants R ; transport privé Lab non transférable ; S5, S11. |
| 18 | Quotas et planification temporelle | Compteur local/fournisseur, réserve, reset incertain, fenêtres prématch, arrêt après complétude, heure réelle et retards de réveil. | Betting benchmark R/E ; ordonnanceur complet de production P ; S1, S5. |
| 19 | Provenance, intégrité et déduplication | Brut avant parsing, octets et JSONB, SHA-256, version de parseur, date de réception, occurrence distincte du contenu dédupliqué. | Deux projets, R ; S4, S8, S10. |
| 20 | Replay et fixtures | Fixtures expurgées/synthétiques, exécution sans réseau, rejeu ancien/répété/concurrent, reprise après arrêt, classification historique conservée. | Deux projets, R ; S2, S4, S8. |
| 21 | Cache, historique et comparaison | TTL et version de parseur, inspection sans mutation, chronologie multi-flux, différences sémantiques, corrections tardives. | Lab R/E ; choix de stockage Betting à respecter ; S8. |
| 22 | Exports canoniques reproductibles | Contrat versionné, hashes distincts des données/sources/fichier, export déterministe, validation humaine, publication atomique, reprise sans écrasement. | Lab J7 R/E ; S10. |
| 23 | API interne et interface opérateur | REST/MVC, JSON et paramètres stricts, pagination keyset, erreurs structurées, vues expurgées, formulaires natifs, contrôle de session et d’origine. | Deux projets, R ; S2, S8, S10, S12. |
| 24 | Tests et diagnostic | JUnit, ArchUnit, MockMvc, PostgreSQL/Testcontainers, Pester, POSIX, concurrence, redémarrage, injection de panne et E2E. | Deux projets, R/E ; S2, S3, S12–S15. |
| 25 | Benchmark reproductible | Population/fenêtre figées, ledger des tentatives, dénominateurs, latence/P95, complétude, coût découverte/marginal/effectif, dimensions non mesurées. | Betting ENR et Lab J8, R/E ; S5, S11. |
| 26 | Sécurité des secrets | Expurgation, diagnostic sans valeur, scans dépôt/diff et logs/rapports ignorés, fixtures synthétiques, configuration externalisée. | Deux projets, R ; S3, S6. |
| 27 | CI et chaîne de livraison | GitHub/GitLab, contrats de branches/MR, merge synthétique, SHA qualifié, SemVer, artefacts/SBOM reproductibles, checksums et provenance. | Deux projets, R ; certaines portes opérationnelles restent ouvertes ; S6. |
| 28 | Sécurité des dépendances | Graphe Maven/BOM, diagnostic de flux NVD, différenciation panne du scanner/CVE réelle, seuil bloquant, confiance des caches. | Betting CI-002/003, R ; S6. |
| 29 | Sauvegarde, restauration et rétention | Sauvegarde chiffrée, restauration temporaire, comparaison Flyway/comptages/hash/provenance, publication après vérification, nettoyage prouvé. | Lab R/E ; INT-001 validé sur corpus synthétique en branche ; S8, S15. |
| 30 | Contrats d’échange et PKI locale | HTTP exact, octets identiques, mTLS, ACK corrélé, preuve de possession, CNG/SunMSCAPI, rollback de certificats et ressources possédées. | J9/INT-001 R/E ; S12–S15. |
| 31 | Documentation et restitution | Rapports techniques sourcés, matrices de preuves, comparatifs fournisseurs, cadrage DOCX/PDF et vérification visuelle des livrables. | Comptes rendus historiques de cadrage R ; mise en forme des anciens fichiers non revalidée dans cet audit. |

## Limites de maturité à conserver

- Le benchmark Betting documente 180 appels dans le corpus INV/CAL/ENR et 127 preuves/replays ENR. Ce sont des mesures d’août 2026, pas une confirmation actuelle des offres fournisseurs.
- Le benchmark Lab J8 distingue réponse compatible, dossier exploitable, dossier complet et dimension non mesurée. L’exactitude externe et la valeur prédictive ne sont pas établies par un HTTP 200 ou un parser vert.
- Le parcours J9 R6 est qualifié **synthétique local Windows/Windows** : 201 pour création, 200 pour doublon, 409 pour collision, avec un seul receipt/payload/outbox et trois audits.
- WO-048 qualifie le provisionnement PKI local : 54 tests Pester et 7 injections d’échec sont consignés. Le contrôle Java SunMSCAPI de cette qualification est sans handshake.
- Le dernier état WO-046 consulté attend encore la qualification backup/restore V30 pour le parcours réel. Le succès synthétique ne qualifie donc pas la chaîne réelle.
- INT-001 est validé par le propriétaire et qualifié localement sur sa branche, avec une restauration cryptographique de données synthétiques ; il n’est pas intégré à main au point Git consulté.
- Ordonnanceur/routage de production, enrichissement/quarantaine, pilote de sept jours, staging VPS, observabilité de production, cotes, probabilités, backtests, publication, live et multisport demeurent tout ou partie prévus dans Betting Project.
- Les méthodes du laboratoire sont capitalisables. Les endpoints privés, transports de collecte, permissions propres au Lab et dépendances runtime ne deviennent pas ceux de Betting Project.

## Bibliothèque proposée : 12 skills ciblés

Les priorités et gains ci-dessous sont une appréciation qualitative fondée sur les tâches récurrentes observées. Aucun gain de temps chiffré n’a encore été mesuré.

| Skill proposé | Déclenchement | Procédure et résultat attendu | Priorité |
|---|---|---|---|
| **bp-work-order** | Reprendre une tâche, préparer ou découper un lot. | Retrouver branche/worktree, WO et preuves courantes ; résoudre les contradictions de statut ; préciser objectif, critères, fichiers/modules probables, dépendances et validation. Sortie : fiche de reprise courte et WO concret. | Lot 1 |
| **bp-verify** | Valider une modification ou diagnostiquer un build/CI rouge. | Sélectionner les scripts existants selon le changement, vérifier environnement et codes natifs, distinguer standard/intégration/non exécuté, examiner les rapports utiles. Sortie : résultat attaché au SHA, causes des échecs et limites exactes. | Lot 1 |
| **bp-postgres-change** | Modifier migration, commande durable, import ou concurrence. | Concevoir transaction, clé d’idempotence, contraintes/verrous/CAS ; vérifier migrations partagées ; tester installation et upgrade prérempli, doublon/collision, rollback et redémarrage selon le risque. Sortie : migration/implémentation et preuve PostgreSQL appropriée. | Lot 1 |
| **bp-data-contract-replay** | Ajouter un fournisseur, parser, import/export ou version de contrat. | Définir contrat et états de données, préserver octets/provenance, bâtir fixtures et cas d’incompatibilité, rejouer sans réseau, vérifier déterminisme et absence de reclassification historique silencieuse. Sortie : contrat, parser, corpus et matrice de résultats. | Lot 1 |
| **bp-review-closeout** | Préparer une revue/PR, traiter une remarque ou clôturer un lot. | Relier critères et preuves au bon commit ; reprendre les autorisations déjà acquises ; aligner une fois WO/statut/PR ; distinguer livré, testé, validé et fusionné ; vérifier la CI du candidat pertinent. Sortie : diff et dossier de revue cohérents, puis clôture dans le périmètre autorisé. | Lot 1 |
| **bp-football-quality** | Normaliser rencontre, identité, statut, composition ou incident. | Appliquer les règles canoniques Betting, PRIMARY/CONTROL, temps source, alias explicites, ordre des participants, absent/vide/zéro et incohérences métier. Les mappings Lab restent des exemples à adapter. Sortie : règles, anomalies visibles et exemples de validation. | Lot 2 |
| **bp-java-module** | Ajouter une fonctionnalité ou un port/adaptateur au monolithe. | Localiser les responsabilités dans les modules existants, respecter ports applicatifs et profils, intégrer domaine/application/adaptateurs, sélectionner ArchUnit et tests utiles. Sortie : évolution cohérente avec l’architecture existante. | Lot 2 |
| **bp-ci-security** | Modifier pipeline, package, dépendance ou traiter un scan rouge. | Charger seulement le mode utile : pipeline/provenance ou dépendances/NVD. Vérifier SHA, SBOM, reproductibilité, frontières de confiance du cache, seuils bloquants et distinction panne du scan/vulnérabilité. Sortie : correctif ciblé et preuves pertinentes. | Lot 2 |
| **bp-provider-benchmark** | Évaluer une capacité fournisseur ou comparer des variantes. | Définir matrice de capacité, échantillon et fenêtre, budget autorisé, ledger et dénominateurs ; réutiliser les preuves ; mesurer coût/latence/complétude ; marquer ce qui n’est pas mesuré. Sortie : scorecard datée et recalculable. | Lot 2 |
| **bp-windows-runtime** | Problème de lancement, scripts, environnement ou distribution Windows. | Résoudre outils et arguments, tester chemins avec espaces, vérifier instance/processus/readiness, gérer PowerShell 5.1/7 et Docker, nettoyer les seules ressources du run. Sortie : diagnostic reproduit, correctif et preuve hôte. | Lot 2 |
| **bp-local-integration** | Qualifier un échange ou parcours local de bout en bout. | Préparer un parcours synthétique borné ; utiliser l’interface native si concernée ; contrôler protocole/octet/ACK et effets PostgreSQL/outbox ; injecter panne/doublon/collision ; charger la référence PKI seulement si nécessaire. Sortie : rapport E2E avec portée explicite. | Lot 3, au prochain lot d’intégration |
| **bp-backup-restore** | Modifier ou qualifier sauvegarde, restauration, rétention. | Identifier corpus et ressources autorisés ; restaurer en cible isolée ; vérifier schéma/données/provenance ; traiter les pannes et prouver le nettoyage. Sortie : preuve de restauration et limites, sans déduire une autorisation de purge. | Lot 3, avant un besoin d’exploitation |

Un skill ordonnanceur peut ensuite être extrait des travaux MVP-001 lorsque les règles de production sont stabilisées. Les connaissances actuelles sur quotas et fenêtres alimentent immédiatement son futur cahier de validation, sans prétendre que l’ordonnanceur est déjà livré.

## Mise en œuvre recommandée

Créer d’abord les cinq skills du lot 1. Leur utilité couvre les prochains développements du pipeline et les tâches quotidiennes. Ajouter les autres au rythme des besoins ; ne pas rendre obligatoire le chargement des douze skills pour chaque demande.

Chaque skill devrait contenir :
1. Une description précise des demandes qui doivent le déclencher.
2. Un SKILL.md court avec les décisions propres au projet et le résultat attendu.
3. Des liens vers les documents maintenus du dépôt, lus seulement si nécessaires.
4. Des scripts uniquement pour une opération répétitive ou nécessitant un résultat déterministe ; appeler en priorité les scripts existants.
5. Des exemples d’évaluation tirés des incidents réels, y compris un cas où le skill ne doit pas s’activer.

Les règles permanentes restent dans AGENTS.md, les décisions dans les ADR, l’état courant dans les Work Orders/statut et les contrats dans docs/contracts. Les skills expliquent comment appliquer ces sources. Ils ne doivent pas figer un ancien SHA, un ancien total de tests, une ancienne version d’outil ni dupliquer tout le dépôt.

La documentation officielle décrit le chargement progressif des skills : nom/description d’abord, puis instructions et ressources utiles. Une bibliothèque courte et bien délimitée favorise une sélection pertinente. Une implantation au niveau du dépôt, par exemple dans .agents/skills, est adaptée aux procédures Betting ; les détails machine et les secrets restent hors des fichiers partagés. [Documentation officielle OpenAI — création et découverte des skills](https://learn.chatgpt.com/docs/build-skills).

Les skills déjà disponibles pour les documents, PDF, tableurs et présentations peuvent rester les outils de restitution. Un nouveau skill générique Word, PDF ou Java apporterait moins que la formalisation des procédures spécifiques ci-dessus.

## Évaluer les skills avec les incidents historiques

| Cas de référence | Résultat que le skill doit produire |
|---|---|
| DEVX-001 : commande native Maven en échec, script Windows retournant zéro. | Signaler l’échec réel, arrêter les étapes dépendantes et ne pas annoncer une validation complète. |
| DEVX-001 : Docker CLI absent, mais Docker Desktop accessible par named pipe ; WSL sans socket. | Diagnostiquer chaque environnement réellement, sans déduire l’état de Testcontainers de la seule présence de la CLI. |
| Testcontainers : un test laisse des données et fait échouer une classe suivante. | Reproduire l’ordre défavorable et corriger l’isolation sans transformer inutilement le code métier. |
| Migration Lab V6 sur V5 préremplie avec trigger append-only. | Tester l’upgrade et vérifier la conservation historique et les contraintes, au-delà du seul démarrage sur base vide. |
| CAT-002 : caractères *, ? et % reçus du fournisseur. | Distinguer validation de configuration et valeur brute légitime ; conserver observation/anomalie si elle ne peut être appliquée. |
| Donnée ancienne ou source CONTROL contradictoire. | Conserver la preuve sans faire régresser ni écraser le canon. |
| Parser corrigé sur un snapshot dédupliqué ancien. | Préserver l’état historique et distinguer le nouveau résultat de replay. |
| Benchmark : HTTP 200, composition après T0 ou absence d’appel. | Distinguer transport, qualité métier, retard et non-exécution ; employer le bon dénominateur. |
| CI-003 : cache déclaré isolé par une clé choisie dans le YAML. | Examiner la frontière serveur réelle et conserver les garde-fous du dépôt. |
| J9 : JAR dans un chemin contenant des espaces. | Valider les arguments et le démarrage sous Windows réel, avec preuve d’arrêt sans résidu. |
| J9 : doublon/collision HTTP. | Vérifier 201/200/409 selon le contrat et contrôler qu’aucun effet durable supplémentaire n’est créé. |
| CAT-002/J9 : document d’état devenu obsolète après revue/CI. | Reconstituer l’état depuis les preuves datées, corriger la synthèse une fois et reprendre les autorisations déjà données. |

Mesurer ensuite, sur quelques tâches comparables, le temps de reprise de contexte, les retours de revue dus à un invariant connu, les faux verts, les corrections documentaires successives et les exécutions inutiles. Aucun pourcentage de gain ne doit être annoncé avant cette mesure.

## Sources locales principales

- **S1** — [Cadrage durable Betting Project](C:/Dev/BettingProject/codex/betting-project/docs/project-brief.md) et [roadmap](C:/Dev/BettingProject/codex/betting-project/docs/roadmap.md).
- **S2** — [Frontières des modules](C:/Dev/BettingProject/codex/betting-project/docs/architecture/module-boundaries.md), [CAT-002](C:/Dev/BettingProject/codex/betting-project/docs/work-orders/CAT-002.md) et [revue finale](C:/Dev/BettingProject/codex/betting-project/docs/reviews/CAT-002-final-review.md).
- **S3** — [DEVX-001](C:/Dev/BettingProject/codex/betting-project/docs/work-orders/DEVX-001.md) et [validation WSL](C:/Dev/BettingProject/codex/betting-project/docs/runbooks/wsl-validation.md).
- **S4** — [Replay de snapshots persistés](C:/Dev/BettingProject/codex/betting-project/docs/contracts/stored-snapshot-replay-v1.md).
- **S5** — [Scorecard fournisseurs et MVP](C:/Dev/BettingProject/codex/betting-project/docs/benchmark/weekly-provider-scorecard-and-mvp-scope-v0.1.md) et [ENR-001](C:/Dev/BettingProject/codex/betting-project/docs/work-orders/ENR-001.md).
- **S6** — [CI-001](C:/Dev/BettingProject/codex/betting-project/docs/work-orders/CI-001.md), [CI-002](C:/Dev/BettingProject/codex/betting-project/docs/work-orders/CI-002.md), [CI-003](C:/Dev/BettingProject/codex/betting-project/docs/work-orders/CI-003.md) et [runbook CI](C:/Dev/BettingProject/codex/betting-project/docs/runbooks/ci-cd-github-gitlab.md).
- **S7** — [Incident de migration V6 sur base préremplie](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/docs/validation/J4-V6-PREFILLED-UPGRADE-INCIDENT-20260815.md).
- **S8** — [Import atomique multi-match](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md) et [historique, sauvegarde et rétention](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md).
- **S9** — [Règles des incidents football](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md).
- **S10** — [Contrat d’export canonique J7](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/docs/architecture/J7-CANONICAL-EVENT-EXPORT.md).
- **S11** — [Rapport benchmark J8](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/docs/benchmark/J8-BENCHMARK-REPORT-20260830.md).
- **S12** — [Qualification E2E synthétique J9 R6](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/j9-wo042-pr25-ci-readiness/docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R6-20260903.md).
- **S13** — [WO-048, provisionnement PKI local](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/w48/docs/work_orders/completed/WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning.md).
- **S14** — [WO-046, qualification réelle en préparation](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/w46/docs/work_orders/active/WO-SS-20260904-046-j9-j7-real-local-e2e-campaign.md).
- **S15** — [Work Order INT-001](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/betting-project-int-001-j7-receiver/docs/work-orders/INT-001.md), [qualification locale](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/betting-project-int-001-j7-receiver/docs/reviews/INT-001-local-readiness.md) et [contrat receiver J7](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/betting-project-int-001-j7-receiver/docs/contracts/j7-import-receiver-v1.md), commit consulté de06153, non intégré à main.

Les chemins .tmp désignent les worktrees existants au moment de l’inventaire et peuvent disparaître lors de leur nettoyage. Les références WO, rapports et commits permettent de retrouver ces preuves dans l’historique.

Conversations complémentaires consultées : « Continue projet paris sportifs », « Évaluer Betting Project », « Configurer GitLab CI NVD », « Betting Project - Structurer dépôts et workspaces », « Projet Java SofaScore », « Projet Java SofaScore - J1/J2/J3 », « Projet Java SofaScore - J7 », « Projet Java Sofascore - J8 » et « Préparer le jalon J9 ».

## Livraison de cet inventaire

Seul ce document d’analyse est créé dans le dossier Documents. Aucun code, test, Work Order, configuration ou skill des deux dépôts n’est modifié. Aucun test applicatif n’est relancé et aucun appel fournisseur n’est effectué. Aucun fichier local de clés, secret, passphrase ou certificat privé n’est consulté.

Les choix encore ouverts sont la validation de l’ordre proposé, le périmètre du premier lot et le lieu d’installation lors d’une future demande de création. Ils n’empêchent pas d’utiliser dès maintenant cet inventaire pour prioriser les skills.
