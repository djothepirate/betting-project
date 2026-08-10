# ADR-003 - Abstraction des fournisseurs, snapshots bruts et replay

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.1
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1

## Résumé de la décision

Les fournisseurs sont isolés derrière des ports par capacité. Le modèle canonique ne dépend d'aucun schéma externe ; chaque appel est tracé et les réponses nécessaires sont conservées pour le replay hors ligne.

## Contexte

Le benchmark montre qu'un fournisseur peut être bon pour le calendrier et insuffisant pour les statistiques, ou couvrir une compétition sans couvrir toutes ses phases.

Les noms, identifiants, statuts et horaires varient entre sources. Des corrections tardives, changements de schéma et quotas limités sont attendus.

La sélection finale sera faite par compétition et type de donnée. Elle ne doit pas forcer une modification du cœur métier.

## Décision

Le cœur exprime des capacités logiques telles que calendrier, identité, classement, composition, événements et statistiques. Chaque fournisseur implémente uniquement les capacités réellement disponibles.

Les DTO, codes de statut, identifiants et particularités d'un fournisseur restent dans son adaptateur. L'adaptateur produit des objets d'entrée canoniques ou des erreurs structurées.

Un registre de capacités versionné décrit la disponibilité par fournisseur, compétition, saison, phase et type de donnée. Le routage peut donc choisir une source différente selon le besoin.

Chaque appel conserve au minimum le fournisseur, l'endpoint logique, les heures de demande et réception, l'horodatage source lorsqu'il existe, la latence, le statut HTTP, le quota, le hash du payload et la version du connecteur ou parseur.

Les réponses brutes nécessaires à l'audit et aux tests sont stockées séparément des données normalisées, avec compression et politique de rétention configurables.

Les fixtures de replay sont expurgées des secrets et permettent de rejouer les parseurs, les rapprochements et les régressions sans appel réseau.

Le rapprochement d'identité utilise des mappings explicites et un score de confiance. Un cas ambigu reste AMBIGUOUS et requiert une validation humaine ; il n'est pas fusionné silencieusement.

## Invariants

- Aucun identifiant fournisseur n'est utilisé comme identifiant canonique.
- Aucun adaptateur ne modifie directement une entité canonique sans passer par le service d'application prévu.
- Une réponse brute identique, identifiée par son hash et son contexte, ne crée pas deux snapshots logiques.
- Un replay standard ne contacte jamais Internet et ne consomme aucun quota.
- Une donnée inconnue ou non interprétée reste disponible dans le brut ; elle n'est pas inventée dans le modèle normalisé.
- La décision fournisseur reste distincte pour chaque compétition et type de donnée.

## Alternatives étudiées

### Un fournisseur unique intégré directement au métier

**Résultat :** Rejetée

Contradiction avec le benchmark et risque de dépendance critique.

### Une interface universelle réduite au plus petit dénominateur commun

**Résultat :** Rejetée

Elle masquerait les capacités utiles et empêcherait de mesurer la profondeur propre à chaque source.

### Conserver uniquement les données normalisées

**Résultat :** Rejetée

Impossible de corriger un parseur, auditer une décision ou rejouer un incident sans rappeler l'API.

### Conserver toutes les réponses sans rétention

**Résultat :** Différée

La conservation doit rester proportionnée au quota, à la preuve et au coût de stockage.

## Conséquences

### Conséquences positives

- Remplacement ou combinaison de fournisseurs sans contamination du domaine.
- Benchmark reproductible et décision par capacité.
- Correction des parseurs à partir des réponses enregistrées.
- Traçabilité des quotas, latences, erreurs et changements de schéma.
- Gestion explicite des identités ambiguës, notamment le cas FC Kryvbas Kryvyi Rih.

### Coûts, limites et risques acceptés

- Davantage de contrats, mappings et fixtures à maintenir.
- Le modèle canonique doit évoluer avec prudence pour ne pas refléter un seul fournisseur.
- La conservation des payloads impose expurgation, rétention et contrôle du volume.

## Critères d'acceptation

- [ ] Un adaptateur fictif ou une fixture peut alimenter CAL-01 sans modification du domaine.
- [ ] Une même fixture rejouée deux fois produit les mêmes objets et hashes canoniques.
- [ ] Un changement de schéma inattendu conserve le brut et produit une erreur explicite de compatibilité.
- [ ] Le fournisseur d'une capacité peut être changé par configuration sans migration du modèle canonique.
- [ ] Les logs et fixtures ne contiennent ni clé API, ni en-tête d'autorisation, ni secret de session.
- [ ] Un rapprochement sous le seuil de confiance reste en attente de validation humaine.

## Déclencheurs de réexamen

- Un fournisseur officiel unique garantit contractuellement toutes les capacités nécessaires avec une stabilité démontrée.
- Le nombre de contrats devient trop fragmenté et justifie une nouvelle organisation des ports.
- La volumétrie ou les obligations de licence imposent un stockage brut séparé.
- Un mode événementiel ou streaming impose de nouveaux contrats de fraîcheur et d'ordre.

## Références

- PROJECT_BRIEF v0.1, sections 4.2, 4.3, 5.3, 7.1 et 8.2.
- Document de cadrage v0.5.1, sections 2.1, 4.3, 11.4 et annexe C.
- FOOTBALL_API_BENCHMARK_PROTOCOL v0.3.6, résultats INV-01 et CAL-01.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale acceptée sans modification par le porteur. |
