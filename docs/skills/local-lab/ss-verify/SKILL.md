---
name: ss-verify
description: "Valider un changement ou diagnostiquer un build Windows/Linux du SofaScore Local Lab : commandes Maven effectives, scripts PowerShell, PostgreSQL et faux verts."
---

# Vérifier un changement du SofaScore Local Lab

Identifier le worktree Lab, lire AGENTS.md, le WO et le diff ; résoudre les chemins depuis cette racine. Relier chaque preuve au contenu, à la commande, à l'environnement et au périmètre autorisé. Ne pas appliquer les profils du Betting Project principal.

## Choisir l'exécution

- Lire pom.xml et scripts/Verify-Local.ps1 avant de sélectionner les commandes. Le profil de persistance du Lab est `integration-tests`. Vérifier aussi les exécutions héritées de Failsafe : l'absence de profil explicite n'est pas une preuve d'absence d'intégration.
- Le lanceur Windows `scripts/Verify-Local.ps1` utilise `mvnw.cmd -DskipITs clean verify` pour le standard et ajoute `mvnw.cmd -Pintegration-tests verify` avec `-WithIntegrationTests`. Confirmer le contenu du lanceur dans le worktree courant avant de l'utiliser. Sous Linux, utiliser les commandes wrapper correspondantes et leurs contrôles pertinents.
- Une demande strictement hors ligne sans Docker nécessite de borner aussi les tests d'intégration : `--offline` limite la résolution Maven, pas les accès réseau des tests ni les démarrages Testcontainers. Examiner les profils fournisseur et les scripts ciblés ; ne pas activer Playwright, un receiver ou une campagne pour valider une modification ordinaire.
- Persistance, migrations ou concurrence modifiées : PostgreSQL/Testcontainers réel, upgrade prérempli et scénarios touchés. Aucune validation H2 ou intégration omise ne remplace cette preuve.
- Documentation et skills : vérifier cohérence, références, UTF-8, diff et secrets. Ne relancer une suite applicative que pour un changement pertinent, un échec, un diagnostic ou une exigence applicable du dépôt ou de la demande.

## Diagnostic et résultats

Sous PowerShell, relever immédiatement le code natif et interrompre les étapes dépendantes en cas d'échec. Pour les lanceurs Java, préserver les arguments et chemins avec espaces ; comparer les noms de configuration à ceux effectivement liés par Spring. Distinguer CLI Docker présente et moteur accessible.

Lire les rapports Surefire/Failsafe et Pester pertinents : tests exécutés, échecs, erreurs et skips. Un code zéro, un JAR produit ou un test simplement présent ne prouve pas la qualification. Ne pas recopier les anciens totaux du Lab comme seuil permanent.

Avant une relance, nommer le changement, l'hypothèse ou l'exigence qui la justifie. Le cycle `-Pintegration-tests verify` peut réexécuter le standard ; éviter les doublons spontanés tout en respectant les contrôles explicitement requis.

## Sortie

Donner cause et correctif, contrôles exécutés et résultats, omissions motivées et preuve distante restant à obtenir. Une revue ou un diagnostic n'autorise pas une mutation primaire ou une livraison réelle.
