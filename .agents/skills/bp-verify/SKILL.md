---
name: bp-verify
description: "Valider une modification du Betting Project ou diagnostiquer un build Windows/Linux et ses faux verts en sélectionnant les commandes et preuves pertinentes. Ne vaut pas qualification sans exécution."
---

# Valider une modification
Résoudre les chemins depuis la racine Git du worktree demandé. Lire AGENTS.md et le WO concerné. Relever le contenu à valider et l'environnement ; distinguer lecture de code, résultat historique et exécution de ce run.

## Choisir les contrôles
- Documentation seule : cohérence des faits/liens et git diff --check ; contrôle de secrets selon les règles du dépôt. Ne pas lancer Maven uniquement pour une prose corrigée.
- Code Java : wrapper et tests pertinents, puis vérification standard demandée par le WO.
- Persistance, migration ou concurrence : PostgreSQL/Testcontainers réel avec le profil integration. Un test H2 ou un build standard ne le remplace pas.
- Scripts Windows/CI : tests des scripts concernés et propagation des échecs ; contrôler aussi l'autre environnement lorsqu'il est affecté.

Références conditionnelles : scripts/verify-windows.ps1, scripts/tests/verify-windows.Tests.ps1 et docs/runbooks/wsl-validation.md. Utiliser les lanceurs du dépôt plutôt qu'en recréer.

## Diagnostic
Sous PowerShell, un programme natif peut échouer sans exception : relever son code immédiatement et vérifier l'arrêt des étapes dépendantes. Distinguer présence de la CLI Docker et accès réel de Testcontainers au moteur. Une intégration omise ou indisponible ne devient jamais une validation complète. Pour une anomalie d'ordre des tests, rechercher les données persistantes partagées et reproduire l'ordre défavorable.

Rattacher chaque résultat au contenu/SHA et à l'environnement testés. Vérifier résultats, tests ignorés et rapports utiles ; le seul code zéro ne prouve pas que les contrôles requis ont eu lieu.

## Éviter les répétitions
Avant une relance, identifier changement depuis la preuve précédente, échec, hypothèse de diagnostic ou exigence explicite qui la justifie. Réutiliser une preuve applicable ; les checks requis d'un nouveau commit ne sont pas automatiquement inutiles.

## Sortie
Pour chaque contrôle : exécuté/résultat, non exécuté/raison, ou proposé. Rapporter une limite d'environnement sans la masquer ; ne jamais annoncer un test réussi après simple inspection de sa source.

