---
name: ss-data-contract-replay
description: "Créer ou relire un contrat, parseur, import/export et replay hors réseau du SofaScore Local Lab, avec snapshots, complétude, provenance et validation humaine explicites."
---

# Faire évoluer les données et le replay du SofaScore Local Lab

Identifier le worktree Lab, lire AGENTS.md et le WO, puis le contrat, les parseurs et les fixtures concernés. Résoudre les références depuis la racine Git. Les contrats sont notamment dans docs/architecture, les règles dans docs/requirements et les preuves dans docs/validation ; ne pas supposer l'arborescence du Betting Project principal.

## Contrat et cas

Construire une matrice entrée → résultat → preuve. Distinguer absent, null, zéro, vide valide, indisponible, partiel et incompatible selon le composant. Une variante observée justifie une adaptation bornée ; elle ne permet pas d'assouplir globalement le schéma ou de convertir une donnée manquante en zéro.

Conserver l'identité fournisseur séparée de l'identité canonique, l'ordre des participants et les temps source. Les DTO externes restent à la frontière d'adaptation, tandis que références fournisseur et provenance restent représentables dans les types métier existants. Ne pas importer les classes ou règles PRIMARY/CONTROL du Betting Project dans le Lab.

## Provenance et J7

Préserver les octets du snapshot, leur hash, le parseur et l'heure de réception. Garder bruts et normalisés séparés, et distinguer observation historique de réinterprétation. Employer des fixtures expurgées ou synthétiques et les chemins de replay existants sans réseau fournisseur. Une bonne lecture du JSON ne prouve pas son exactitude métier externe.

Pour J7, lire docs/architecture/J7-CANONICAL-EVENT-EXPORT.md, docs/runbooks/J7-CANONICAL-EVENT-EXPORT.md et l'ADR-SS-003 applicable s'il est présent. Distinguer statut humain, complétude, origine synthétique ou dérivée fournisseur, octets exportés, hashes et contrat ACK. Un parsing réussi ne confère ni validation humaine ni autorisation de livraison. Préserver les avertissements de complétude et l'historique des décisions ; un manifeste gelé n'est pas réécrit pour masquer un échec.

## Sortie

Livrer contrat, parseur, corpus ou revue ciblée avec compatibilités, anomalies, preuves exécutées et limites. Réutiliser les normaliseurs du Lab. Ne pas ouvrir un endpoint, un transport ou une campagne, ni créer une dépendance du Betting Project au laboratoire pour compléter un travail hors réseau.
