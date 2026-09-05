---
name: bp-data-contract-replay
description: "Créer, faire évoluer ou relire un contrat, parseur, import/export et replay du Betting Project avec fixtures hors réseau, provenance et états explicites des données."
---

# Faire évoluer un contrat et son replay
Résoudre les chemins depuis la racine Git. Lire AGENTS.md, le contrat concerné dans docs/contracts et ses parseurs/tests ; consulter docs/project-brief.md pour les invariants métier nécessaires.

## Décrire les cas
Établir une matrice entrée → résultat attendu → preuve. Distinguer absent, null, zéro, vide valide, indisponible, partiel et incompatible selon le contrat, sans convertir silencieusement l'un en l'autre. Une variante observée justifie une évolution bornée, pas un assouplissement général du schéma.

Garder les DTO externes dans les adaptateurs. Une référence fournisseur ne devient pas un identifiant canonique ; les références et la provenance restent représentables dans le domaine avec les types existants, notamment ProviderMappingKey et FixtureObservation. Respecter autorité PRIMARY/CONTROL, temps source et ordre des participants ; neutralVenue seul n'autorise pas une permutation. Les contraintes de configuration ne s'appliquent pas automatiquement aux littéraux bruts reçus d'une source.

## Préserver la preuve
Conserver les octets/provenance avant interprétation, leur hash, dates et versions connues. Séparer classification historique et nouvelle interprétation ; ne pas fabriquer rétroactivement une version de parseur inconnue.
Utiliser fixtures expurgées ou synthétiques et replay sans réseau fournisseur. Une réussite de parsing ne prouve pas une exactitude métier externe.

Pour le replay stocké, suivre docs/contracts/stored-snapshot-replay-v1.md : sélection UUID ou SHA-256 unique, contrôle des octets avant parsing, erreur explicite si ambigu ou altéré, aucune sélection par chemin arbitraire. Distinguer idempotence de commande, effets canoniques et historique des tentatives. Le profil replay de fichiers reste sans base ; le replay PostgreSQL appartient à control-api.

## Sortie
Livrer contrat/parseur/corpus ou revue ciblée, compatibilités et anomalies, preuves exécutées versus simplement présentes et limites. Réutiliser le normaliseur existant ; ne pas ouvrir un second chemin de normalisation ni importer de dépendance au laboratoire.
