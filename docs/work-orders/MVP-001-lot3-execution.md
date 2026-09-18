# MVP-001 — Lot 3 : exécution

## Fiche de reprise — 2026-09-18

- Objectif : adaptateurs calendrier distincts du benchmark, audit, conservation et replay.
- Base du lot : `d091055a60e13ac335c1bd4d74d400b59922249b` ; worktree initial propre.
- Branche : `feature/V0.1.0-RC01-CODEX-MVP-001` ; cible finale `feature/V0.1.0-RC01`.
- Invariants : budget avant envoi, permission après commit, aucun retry d'un envoi incertain,
  octets conservés avant parsing, aucune dépendance collection → catalog, V001–V009 immuables.
- Autorisation : réalisation locale, puis commit et push du lot qualifié avec vérification CI.
- Exclus : activation réelle, clé fournisseur, endpoint HTTP entrant, worker, poller, PR et fusion.
- Prochaine étape : contrats, migration V010, transports bornés et parsing natif hors réseau.
- Incertitudes connues : aucune fixture de calendrier natif issue d'une campagne n'est qualifiée
  ici. Les fixtures nouvelles sont synthétiques, fondées sur les schémas officiels ; elles ne
  prouvent pas la couverture actuelle d'un abonnement. Les notes COV existantes restent datées.

## Décision descriptive acceptée

Les pays des équipes et le pays/type de compétition peuvent rester inconnus. Cette décision
du porteur ne permet ni l'invention de valeurs, ni la création d'un mapping par texte. Les
identifiants exacts, noms, saison et phase restent contrôlés. Un pays de compétition n'est
jamais recopié sur une équipe. Le wire format calendrier demeure v3.

## Qualification

Le lot est `COMPLETED`, le lot 4 reste `PENDING`. Neuf critères sur vingt sont désormais démontrés.
La source réelle classpath reste vide et les clients désactivés ; aucun quota réel n'a été consommé.

### Décision de contexte canonique

Le porteur accepte le raccordement exact de la saison et de la phase canoniques via la route
du registre. Les parseurs conservent `league.season`/`round` Highlightly et `season.id`/`stage`
football-data.org. Observations, mappings, anomalies et autorité gardent ces références natives ;
le catalogue ne les rapproche pas par ressemblance. Les anciens replays CAT sans affectation
gardent leurs littéraux ; en production sans affectation, `UNASSIGNED` bloque toujours l'application.

### Livraison fonctionnelle

- Ports de collecte, preuve, dérivation et application ; adaptateur d'application côté catalogue,
  sans cycle `collection → catalog`.
- Clients JDK bornés à 5 s connexion, 30 s corps/requête, 5 Mio et 100 pages, sans redirect/retry
  applicatif. L'activation réelle exige aussi les garde-fous JVM anti-retry au démarrage.
- Permission d'envoi retournée après commit par le budget existant ; audit/outbox préalable,
  réponse/ledger/incident atomiques, parsing ultérieur. Pas de nouveau débit à la réception.
- Corps natif conservé séparément du v3 dérivé, hashes et versions distincts, provenance
  explicite de `observedAt` à la réception de chaque appel, même si le brut est dédupliqué.
- Compteur reçu conservateur : couverture vide, validité non prolongée, incohérence bloquante
  si hors capacité ; idempotence du résultat et de ses observations/incidents.
- Replay manuel d'une page stockée par UUID, hash recalculé, aucun chemin ni appel réseau.
- V010 additive : collecte, page et journal des dérivations ; aucun historique ou fournisseur préchargé.

### Exécutions réelles

1. Parseurs/transports ciblés initialement : 95 tests standards verts.
2. Ciblage élargi : profils, architecture, politique et service verts ; migrations et catalogue
   verts, mais erreurs de fixtures de test calendrier corrigées (code pays canonique à trois
   caractères et ordre de journaux au même instant). Ce passage n'a pas été qualifié vert.
3. Relance ciblée corrigée : 30 tests standards et 24 tests PostgreSQL verts.
4. Candidat final : `mvnw.cmd -Pintegration verify`, **550 tests standards et 196 tests PostgreSQL**,
   zéro échec, erreur ou omission, `BUILD SUCCESS`, code 0 ; fin à **23:46:22 Europe/Paris**.
   Les 196 incluent les 26 scénarios finaux de `CalendarCollectionIT`, les 3 de
   `CalendarCollectionStoreIT`, les 3 de `V010MigrationIT` et les non-régressions historiques.
5. Scan Windows `check-no-secrets.cmd` mode `All` : PASS ; diff et espaces finaux des fichiers
   non suivis contrôlés. L'index était vide avant la préparation du commit autorisé.

Les essais initiaux ont également révélé des erreurs de compilation des nouveaux tests
(signatures de factories et type de date de preuve), et une clé fournisseur factice non admise
par le port limité aux deux fournisseurs. Ces tests ont été corrigés, non omis. Les revues
indépendantes ont entraîné des non-régressions sur compteur reçu, statut 401/403/429 avec corps
inutilisable, enregistrement répété, corruption de compression/hash et écho de clé Unicode-échappé.
Le correctif de confidentialité détecte puis écarte les octets, sans fabriquer un brut expurgé.

### Preuves directement pertinentes

| Sujet | Preuve |
|---|---|
| Natifs stricts, états, absence et phases exactes | `NativeCalendarPageParserTest` |
| HTTP borné, erreurs et secret, garde anti-retry | `CalendarPageHttpClientTest`, `CalendarHttpRuntimeSafetyTest` |
| Vrai JDK, loopback uniquement, redirect refusé, taille bornée | `CalendarHttpLoopbackTest` |
| 100 pages, cycles, doublons, budget, idempotence | `CalendarCollectionServiceTest` |
| Contexte logique distinct du source, contrôle sans mutation | `RegistryCalendarCanonicalContextPolicyTest`, `CalendarCollectionIT` |
| Métadonnées inconnues sans création implicite d'identité | `CalendarNormalizationIT` : 7 nouvelles invocations |
| Migration V009 peuplée et installation neuve | `V010MigrationIT` |
| Audit/outbox atomiques et contraintes de provenance | `CalendarCollectionStoreIT` |
| Ledger, replay, concurrence, corruption, rollbacks, arrêt après permission | `CalendarCollectionIT` |
| Profils, absence de JDBC applicatif et de cycles | Trois tests de profils et `ArchitectureRulesTest` |

### Intégrité

V001–V009 sont inchangées, comparaison SHA-256 avec la base du lot :

```text
V001 d3ab6619dc4b81f7ac6807c06b5f18c3013de442c525701d150fe5d23ee1f090
V002 69d9e4ff0415a4fcf395c2ce85fe79c84bc57672daa1c2b72f4b4d46ae516271
V003 059b95738deb3c22b90399c6096dab9cd3e2594f8279f9d6ea22ab28dfff9dfd
V004 2051f1caf8420d3a36c28becfba47dd6b0827d25b1910d4cf0162916bdec8cd3
V005 f38ac525099e3b269b85b19df3ece34d2e0e38d460834b953bbbb8d41507f90f
V006 6557d9510b20f6c163f0b96bc914de6d181b151eebd08cb254928162276bc6e3
V007 9a87c602c500a47f5c3984b690185f9906394a651ea4e3d4c9999f8537120cff
V008 6fbee3fec6a84aa390df913df4fbd3b73fc1a522272197e82bbf6bdf01f7716b
V009 d86d981c7e251af90e1a58ade6cbf94fc665d7ca760873e873b2d58be3afe5e1
V010 5d61a461f32fbd799c821b948365dc119737be67465c27a8dd1e1782b816808a
```

Aucune V011, dépendance H2, modification du POM, nouveau profil, endpoint public ou appel privé
SofaScore. Les seuls serveurs appelés dans les nouveaux tests sont des faux en mémoire ou loopback.
Les scripts, le benchmark ENR, ses clés, les preuves de campagne et le changelog fusionné restent inchangés.

### Limites et publication

Les fixtures sont synthétiques, pas une qualification de couverture d'un abonnement. Une page
mélangeant des phases source reste incompatible ; aucune route voisine n'est déduite. Un restant
sans preuve de couverture peut limiter le budget plus sévèrement que son seul compteur local.
Une collecte interrompue demeure non exhaustive et n'est pas réémise automatiquement, même après
redémarrage. Le worker, les claims, la planification et les projections API restent aux lots 4/5.

Le [contrat](../contracts/provider-calendar-collection-v1.md) et le
[runbook](../runbooks/provider-calendar-local.md) documentent ces limites. Ce candidat fait l'objet
d'un commit distinct et d'un push autorisés ; leurs checks Windows et Linux/PostgreSQL doivent
être lus sur le SHA publié. Ce document ne prétend pas connaître son propre SHA futur ni une CI
encore non exécutée. Pas de PR avant les lots restants, pas de fusion sans revue humaine.
