# ADR-002 - PostgreSQL comme source de vérité, jobs persistants et outbox

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.1
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1

## Résumé de la décision

PostgreSQL est la source de vérité du Betting Project. Les changements de schéma passent par Flyway ; les traitements reprenables, les clés d'idempotence et l'outbox transactionnelle sont persistés dans la même base.

## Contexte

La chaîne doit survivre aux redémarrages, produire les mêmes effets lors d'une relance et conserver les preuves expliquant chaque décision ou exclusion.

Les publications Drive, Gmail et Telegram ne doivent pas devenir des sources d'état. Un arrêt entre une transaction métier et un appel externe ne doit ni perdre ni dupliquer une publication.

Le volume et la charge du jalon 0 ne justifient pas l'ajout immédiat d'un ordonnanceur distribué, de Redis ou d'un broker externe.

## Décision

PostgreSQL conserve les référentiels canoniques, mappings fournisseurs, événements, états, snapshots, jobs, tentatives, anomalies, décisions, audits et messages d'outbox.

Toute modification de schéma est apportée par une migration Flyway versionnée. Aucun changement manuel de structure n'est autorisé dans un environnement partagé.

Les jobs sont persistants et possèdent une clé d'idempotence stable, un statut, un nombre de tentatives, une prochaine exécution, des heures de début et fin, une erreur structurée et des références d'entrée/sortie.

Une transaction métier qui nécessite une publication crée simultanément l'état métier et un message d'outbox. Un worker séparé expédie le message, conserve le résultat et applique une politique de reprise bornée.

Les contraintes d'unicité et les clés métier complètent les contrôles applicatifs afin qu'une relance ne crée aucun doublon.

Les verrous de ligne ou verrous consultatifs PostgreSQL sont utilisés avant d'introduire un système de verrouillage séparé.

## Invariants

- Google Drive, Gmail, Telegram, un PDF ou un cache ne sont jamais la source de vérité d'un état métier.
- Une migration déjà appliquée dans un environnement partagé n'est pas réécrite ; une migration corrective est ajoutée.
- Chaque effet externe possède une clé d'idempotence calculable et conservée.
- Un message d'outbox n'est marqué livré qu'après confirmation de l'appel externe.
- Les tests d'intégration utilisent une instance PostgreSQL réelle et éphémère, pas une base approximative incompatible.

## Alternatives étudiées

### Base en mémoire ou fichiers JSON comme référentiel

**Résultat :** Rejetée

Insuffisant pour les transactions, reprises, contraintes d'unicité, audit et concurrence.

### Broker externe dès le démarrage

**Résultat :** Différée

L'outbox PostgreSQL couvre le besoin initial avec moins de composants à exploiter.

### Redis pour les jobs et verrous

**Résultat :** Différée

Aucun besoin mesuré ne justifie une seconde source d'état au jalon 0.

### Appels externes dans la transaction métier

**Résultat :** Rejetée

Risque de transaction longue, d'état incertain et de duplication lors des reprises.

## Conséquences

### Conséquences positives

- État durable, auditable et transactionnel.
- Reprise après incident sans perte ni doublon externe.
- Moins de composants d'infrastructure au jalon 0.
- Tests réalistes avec migrations identiques à la production.

### Coûts, limites et risques acceptés

- La base concentre plusieurs responsabilités opérationnelles et doit être surveillée et sauvegardée avec soin.
- Les workers d'outbox et de jobs doivent gérer les blocages, délais, reprises et messages définitivement échoués.
- La conservation des snapshots bruts peut augmenter le volume ; une politique de rétention sera nécessaire.

## Critères d'acceptation

- [ ] Les migrations s'appliquent sur une base vide et sur la version précédente sans intervention manuelle.
- [ ] Deux exécutions avec la même clé métier ne créent qu'un événement canonique et qu'un job logique.
- [ ] Un arrêt après le commit métier mais avant la publication laisse un message d'outbox reprenable.
- [ ] La reprise d'un message déjà livré ne crée pas un second document, courriel ou message Telegram.
- [ ] Les tests Testcontainers valident transactions, unicité, verrous et reprise de l'outbox.

## Déclencheurs de réexamen

- Le volume des jobs ou de l'outbox provoque une contention mesurée malgré l'optimisation.
- Un besoin de diffusion à plusieurs consommateurs indépendants apparaît.
- La volumétrie des snapshots impose un stockage objet ou une stratégie d'archivage séparée.
- Les objectifs de disponibilité nécessitent réplication, haute disponibilité ou séparation de bases.

## Références

- PROJECT_BRIEF v0.1, sections 2.3, 5.3, 6.1, 6.2 et 9.1.
- Document de cadrage v0.5.1, sections 2.1, 4.3, 11.2, 11.3 et 13.1.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale acceptée sans modification par le porteur. |
