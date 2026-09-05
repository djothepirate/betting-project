---
name: ss-postgres-change
description: "Concevoir ou relire une migration PostgreSQL du SofaScore Local Lab : provenance, transactions, concurrence, ledger, upgrade prérempli, sauvegarde et reprise selon le périmètre."
---

# Modifier la persistance du SofaScore Local Lab

Identifier le worktree Lab et lire AGENTS.md, le WO, les migrations et les composants concernés. Résoudre les chemins depuis la racine Git. Utiliser docs/architecture/ARCHITECTURE.md et les ports réellement présents ; ne pas imposer la structure de packages du Betting Project principal.

## Invariants et conception

- Décrire l'état durable avant/après, contraintes, index, idempotence et frontières transactionnelles. Examiner nominal, doublon, collision, concurrence, rollback et reprise selon le changement.
- Les migrations de src/main/resources/db/migration sont append-only après partage. Préserver les snapshots, hashes, versions de parseur et historiques ; ne pas inventer une provenance pendant un backfill.
- Séparer données brutes, projections et décisions humaines. Pour J7, lire les contrats et ledgers existants : grant, consommation, tentative, réception et résultat n'ont pas le même sens. Un grant enregistré ou un ACK reçu ne prouve pas à lui seul toute la chaîne de livraison.
- Conserver le mécanisme durable du Lab concerné. Ne pas transposer automatiquement l'outbox du receiver Betting Project au sender du laboratoire, ni ajouter un retry, poller ou consommateur sans besoin autorisé. Deux dépôts ou processus ne forment pas une transaction SQL unique.

## Preuves

Prévoir installation neuve et upgrade depuis la version précédente préremplie, avec contrôle des données, de la provenance et des contraintes utiles. Utiliser PostgreSQL/Testcontainers via `-Pintegration-tests verify`, après vérification des exécutions Maven effectives. Une lecture des tests est une analyse de couverture, pas une nouvelle exécution.

Pour sauvegarde, restauration ou rétention, consulter docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md et les scripts qualifiés du worktree. Identifier primaire et cible isolée, sauvegarde, preuves comparées et nettoyage réellement autorisé. Respecter les secrets privés et les invites opérateur sans les lire ou les journaliser. Une simple revue ne nécessite aucune mutation primaire, purge ou restauration.

## Sortie

Livrer changement ou revue avec matrice invariant → scénario → preuve, migrations touchées, commandes adaptées et limites. Relier le résultat au bon schéma, commit et environnement ; distinguer restauration synthétique, primaire protégée et autorisation opérationnelle.
