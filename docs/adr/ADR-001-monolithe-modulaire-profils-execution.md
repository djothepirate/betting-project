# ADR-001 - Monolithe modulaire et profils d'exécution

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.1
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1

## Résumé de la décision

Le Betting Project principal est construit comme un monolithe modulaire Java/Spring : un seul dépôt, un cycle de version commun et une même image applicative, avec des limites de modules vérifiables et plusieurs profils d'exécution.

## Contexte

Le projet est individuel, doit rester exploitable sur un VPS de capacité limitée et doit livrer rapidement une chaîne football prématch fiable.

La solution doit néanmoins isoler le domaine, les connecteurs externes, les traitements, la publication et l'exploitation afin qu'un fournisseur ou un canal puisse être remplacé sans réécriture globale.

Une architecture distribuée introduirait dès le jalon 0 des contrats réseau, des déploiements, de l'observabilité et des incidents supplémentaires sans besoin de charge démontré.

## Décision

Le code applicatif est organisé en modules métier explicites dans un même build Maven. Le découpage initial couvre au minimum le catalogue, la collecte, l'identité, la qualification, l'enrichissement, l'analyse, la publication et l'exploitation.

Les dépendances vont des adaptateurs vers les contrats applicatifs et le domaine. Le domaine n'importe aucun client HTTP, DTO fournisseur, SDK externe, mécanisme de publication ni détail de persistance.

Une même image applicative peut être lancée avec plusieurs profils : control-api pour l'administration et les validations humaines, batch-worker pour les traitements persistants, et ultérieurement live-worker lorsqu'un jalon live sera autorisé.

Le jalon 0 ne déploie ni microservices, ni Kubernetes, ni bus de messages externe. PostgreSQL porte initialement les états, verrous, jobs et messages d'outbox. Redis reste différé jusqu'à la démonstration d'un besoin.

L'interface d'administration est rendue côté serveur afin d'éviter un front-end séparé au démarrage.

## Invariants

- Un module ne lit pas directement les tables ou repositories internes d'un autre module.
- Les appels externes sont déclenchés derrière des ports applicatifs et jamais depuis les objets du domaine.
- Les profils d'exécution partagent le même modèle de données et les mêmes migrations.
- Le profil live reste désactivé tant que le jalon live n'est pas explicitement ouvert.
- Une panne de rédaction IA ou de publication ne doit pas arrêter la collecte ni perdre l'état des jobs.

## Alternatives étudiées

### Microservices dès le jalon 0

**Résultat :** Rejetée

Trop de coût opérationnel et de contrats distribués pour un projet porté par une personne et une charge non démontrée.

### Application monolithique sans limites internes

**Résultat :** Rejetée

Le couplage rendrait les fournisseurs, publications et traitements difficiles à tester et à remplacer.

### Architecture réactive généralisée

**Résultat :** Différée

Aucun besoin de concurrence ou de streaming ne justifie cette complexité pour le MVP prématch.

## Conséquences

### Conséquences positives

- Déploiement, diagnostic et reprise simplifiés.
- Transactions locales possibles entre données métier, jobs et outbox.
- Tests d'intégration plus simples et évolution progressive des modules.
- Possibilité d'isoler les charges par profils sans multiplier les produits à maintenir.

### Coûts, limites et risques acceptés

- Une discipline d'architecture et des tests de dépendances sont nécessaires pour éviter l'érosion des limites.
- Tous les modules partagent le même cycle de livraison et la même base de données.
- Une défaillance non maîtrisée peut affecter plusieurs fonctions si l'isolation par profils et transactions est insuffisante.

## Critères d'acceptation

- [ ] Le build Maven identifie les modules ou frontières retenus et interdit les dépendances circulaires.
- [ ] Un test d'architecture vérifie que le domaine n'importe aucun package d'adaptateur ou d'infrastructure.
- [ ] control-api et batch-worker démarrent séparément depuis la même version applicative.
- [ ] La désactivation d'un adaptateur externe n'empêche pas le démarrage du noyau et du mode replay.
- [ ] Aucun composant Kubernetes, broker externe ou Redis n'est requis pour exécuter le jalon 0.

## Déclencheurs de réexamen

- Une charge mesurée exige une mise à l'échelle indépendante qu'aucun profil ne permet.
- Un besoin d'isolation de panne ou de cadence de déploiement indépendante est démontré.
- Une équipe distincte devient propriétaire d'un domaine avec un contrat stable.
- La contention PostgreSQL ou les besoins de streaming justifient un composant spécialisé.

## Références

- PROJECT_BRIEF v0.1, sections 1.3, 6.1 et 9.1.
- Document de cadrage v0.5.1, sections 2.1, 4.1, 4.3, 11 et 13.1.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale acceptée sans modification par le porteur. |
