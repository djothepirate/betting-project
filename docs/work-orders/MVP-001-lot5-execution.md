# MVP-001 — Lot 5 : contrôle interne et sélection quotidienne

## Reprise — 2026-09-19

- Activation explicite par la demande « réaliser le lot 5 ».
- Base : `51e85de8df3ea93386ea9c72c74ff3bfe8c4ad1a`, worktree initial propre.
- Branche : `feature/V0.1.0-RC01-CODEX-MVP-001` ; cible de clôture inchangée.
- Le run `35404160290`, tentative 2, est vert sur cette base : Windows 572 tests standards,
  Linux 572 tests standards et 224 tests PostgreSQL/Testcontainers, sans omission.
  Le porteur a passé le dépôt de privé à public ; le blocage initial est historique.

## Périmètre local

Livrer les consultations internes bornées des capacités, budgets, intentions, incidents, jobs
et preuves de collecte, ainsi que la sélection quotidienne PPL/PD après calendrier. Les ports
de lecture restent indépendants de HTTP/JDBC, les projections excluent payloads, identifiants
de compte, clés d'idempotence, tokens de claim et messages d'exception. Les routes sont confinées
à `control-api` sur la boucle locale existante, sans activation de client ou de worker.

La sélection conserve le plafond de sept, les disponibilités prudentes du budget qualifié et
l'ordre déterministe. Le périmètre retenu pour cette implémentation est prévisionnel : aucune
réservation ni appel d'enrichissement, réservés à ENR-002. Le coût par rencontre est une entrée
explicite bornée, jamais une estimation implicite. La prévision ne garantit pas un budget futur.

Aucune nouvelle migration n'est nécessaire pour les consultations. V001–V011 restent
immuables. Les profils, le POM, la CI, les collecteurs de benchmark et le receiver J7 sont préservés.

## Livraison et preuves

| Sous-système | Livraison et preuve |
|---|---|
| Lectures | Ports séparés et projections sans payload, compte, auteur ou clé ; adaptateurs JDBC sous `control-api` seulement |
| Pagination | Limites 1–100, `limit+1`, curseurs liés aux filtres/scope/parent ; dates égales départagées par UUID, tests multi-pages PostgreSQL |
| Contrat HTTP | 13 routes GET et un POST de prévision, parsing local strict, paramètres liés, erreurs génériques ; `CollectionWebContractTest`, `CollectionControlIT` |
| Sélection | `DailySelectionPolicyTest` : maximum sept, coût explicite, budget résiduel, priorité/kickoff/UUID, exclusion hors PPL/PD, statut et date |
| Garde applicative | `DailySelectionServiceTest` : quatre calendriers terminés, registre courant actif et primaire, bornes, candidats prouvés, refus explicites |
| Chaîne complète | `CollectionControlIT` : registre → fenêtre → jobs/outbox → faux fournisseur → brut/audit → canon → consultations/prévision, sans effet supplémentaire de lecture |
| Incidents | 401/403/429 : budget suspendu et incident consultable, coût/audit conservés, autre fournisseur disponible |
| Architecture et profils | Ports sans JDBC/HTTP, sens `catalog` → `collection` → `operations`, aucun nouveau composant sous `batch-worker` ou `replay` |

Contrat : [collection-control-api-v1](../contracts/collection-control-api-v1.md).
Procédures : [runbook local](../runbooks/collection-control-local.md).

## Qualification exécutée

Environnement : Windows, Java 25.0.4, wrapper Maven du dépôt, Docker Desktop accessible par
named pipe, PostgreSQL 17.10/Testcontainers. Aucun fournisseur réel ni clé API n'est utilisé.

1. Tests ciblés sélection, corps JSON/cursors, service, architecture et trois profils : réussis.
2. Intégration ciblée `CollectionControlIT` : réussie. Deux attentes initiales du test ont été
   corrigées (code documentaire `RESOURCE_NOT_FOUND`, query MockMvc transmise par `.param` sans
   double encodage de la saison). Aucun assouplissement de comportement applicatif.
3. `mvnw.cmd -Pintegration verify`, terminé le **19 septembre 2026 à 03:07:06 Europe/Paris** :
   **615 tests standards, 239 tests PostgreSQL**, zéro échec, erreur ou omission, code 0.
   La suite inclut 43 nouveaux tests standards et 15 nouveaux tests PostgreSQL du lot 5 ;
   les totaux 572/224 du lot 4 ne sont pas présentés comme une nouvelle exécution.
4. `scripts/check-no-secrets.cmd` : `PASS (All)`, code 0.
5. `git diff --check`, espaces finaux des fichiers non suivis et index vide avant staging : propres.

Une première invocation ciblée Failsafe mal citée sous PowerShell a été refusée par Maven avant
exécution ; l'argument `-Dit.test=CollectionControlIT` a été protégé par guillemets. Les erreurs
initiales de compilation ont également été corrigées avant les preuves ci-dessus. Elles ne
sont pas comptées comme suites réussies. Une première suite complète était verte à 02:59:13 ;
la revue finale a ensuite durci les UUID des chemins HTTP, car la conversion Spring admet des
formes abrégées. Une non-régression couvre leur refus et la suite complète a été relancée sur
ce dernier changement de code. Aucun chemin CI/script n'a changé : pas de relance
Windows complète redondante ; la suite intégration couvre également les tests standards.
La qualification Linux complète sera celle du SHA publié, pas une validation WSL partielle.

## Intégrité des migrations

Les onze migrations correspondent aux empreintes de la base `51e85de` :

| Migration | SHA-256 |
|---|---|
| V001 | `d3ab6619dc4b81f7ac6807c06b5f18c3013de442c525701d150fe5d23ee1f090` |
| V002 | `69d9e4ff0415a4fcf395c2ce85fe79c84bc57672daa1c2b72f4b4d46ae516271` |
| V003 | `059b95738deb3c22b90399c6096dab9cd3e2594f8279f9d6ea22ab28dfff9dfd` |
| V004 | `2051f1caf8420d3a36c28becfba47dd6b0827d25b1910d4cf0162916bdec8cd3` |
| V005 | `f38ac525099e3b269b85b19df3ece34d2e0e38d460834b953bbbb8d41507f90f` |
| V006 | `6557d9510b20f6c163f0b96bc914de6d181b151eebd08cb254928162276bc6e3` |
| V007 | `9a87c602c500a47f5c3984b690185f9906394a651ea4e3d4c9999f8537120cff` |
| V008 | `6fbee3fec6a84aa390df913df4fbd3b73fc1a522272197e82bbf6bdf01f7716b` |
| V009 | `d86d981c7e251af90e1a58ade6cbf94fc665d7ca760873e873b2d58be3afe5e1` |
| V010 | `5d61a461f32fbd799c821b948365dc119737be67465c27a8dd1e1782b816808a` |
| V011 | `2be2aa01b2437285aeb6739e5f1235ac98067f879de1f460bc839d4990a7b53d` |

Aucune V012, dépendance H2, modification de configuration fournisseur, payload fournisseur réel,
secret ou endpoint privé n'est ajouté. Les fixtures natives générées par le test sont synthétiques.

## Revue technique et limites

Le candidat comporte **35 fichiers** (9 documents et 26 fichiers Java de code/tests).
Le diff est limité aux lectures, à la sélection, aux tests et à leur documentation. Le contrôle
des champs exposés, scopes de curseurs, paramètres SQL, profils, autorité des candidats et
absence de mutation de la prévision est couvert. Cette revue technique ne remplace pas la revue
humaine du lot 6 et de la future PR.

- L'API est locale sans authentification ; aucune exposition avant OPS-001.
- Le registre réel reste vide ; clients et boucle worker désactivés. Aucune activation ni appel
  fournisseur n'est réalisé, y compris pendant la validation.
- La prévision exige quatre calendriers primaires complets du même jour UTC et du même registre.
  Une liste partielle n'est pas déclarée exhaustive. Les candidats sans provenance d'autorité
  reliée aux collectes ne sont pas inventés ; les anomalies restent consultables côté catalogue.
- Le coût d'enrichissement n'est pas déterminé par MVP-001. L'entrée explicite permet de comparer
  des hypothèses, mais ne constitue ni une réservation ni un droit d'envoi. ENR-002 devra imposer
  le plafond durable et revalider les faits, capacités et budgets avant exécution.
- Les résultats sont bornés, sans vue transactionnelle de pagination figée ni index ajouté sans
  mesure. Le calcul de sélection conserve brièvement les verrous budgétaires, jamais pendant HTTP.

## Sortie et livraison

Lot 5 : **COMPLETED localement**, état global `ACTIVE - IMPLEMENTATION`.
Trois critères supplémentaires sont prouvés : sélection prévisionnelle, incidents consultables,
API bornée et profils. Total **15/20**, cinq ouverts. Lot 6 : prochain, **PENDING**.

Le commit distinct puis le push du périmètre vérifié sont autorisés par la consigne du porteur
de livrer chaque lot terminé. Les checks du SHA publié seront lus après le push ; ce document
ne prétend pas qu'un commit futur a déjà passé sa CI. Aucune PR de clôture, fusion, livraison du
train, activation de fournisseur ou modification des paramètres GitHub n'est effectuée.
