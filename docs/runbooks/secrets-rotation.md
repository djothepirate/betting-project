# Rotation, révocation et incident de secret

## Prévention

- Utiliser des secrets distincts pour local, staging et production lorsque le fournisseur le permet.
- Injecter les valeurs par environnement ou fichier extérieur au dépôt.
- Ne jamais copier le fichier local des clés API dans ce dépôt.
- Masquer les en-têtes d'autorisation, cookies et paramètres sensibles avant journalisation.

## Rotation planifiée

1. Générer une nouvelle valeur dans le compte du fournisseur.
2. Mettre à jour l'environnement cible sans supprimer l'ancienne valeur.
3. Valider le connecteur avec une requête bornée.
4. Révoquer l'ancienne valeur.
5. Consigner la date, le compte concerné et le résultat, jamais la valeur.

## Suspicion de fuite

1. Désactiver ou révoquer immédiatement la valeur concernée.
2. Suspendre le connecteur si la révocation n'est pas immédiate.
3. Rechercher les traces dans Git, les logs, rapports, artefacts, sauvegardes et captures.
4. Remplacer la valeur et vérifier que les anciens accès échouent.
5. Documenter la cause, l'étendue, les corrections et le contrôle de non-récurrence.

Une valeur détectée dans l'historique Git est considérée compromise même si le fichier courant a été supprimé.
