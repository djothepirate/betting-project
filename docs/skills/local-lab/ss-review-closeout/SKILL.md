---
name: ss-review-closeout
description: "Préparer une revue ou clôturer un Work Order et une PR du SofaScore Local Lab en reliant décisions, diff, qualification, manifestes et état Git courant."
---

# Revoir et clôturer un lot du SofaScore Local Lab

Identifier le worktree Lab, lire AGENTS.md, le WO dans docs/work_orders/active ou completed, le diff et les preuves utiles de docs/validation. Résoudre les références depuis la racine Git. Les instructions et autorisations actuelles du propriétaire restent applicables.

## État et revue

Établir base, candidat, dernier changement fonctionnel, tests, revues et décisions humaines. Distinguer implémenté, qualifié, validé, clôturé, publié et fusionné. Vérifier l'état distant lorsqu'une action Git en dépend ; un rapport local ne prouve pas la situation actuelle de la PR.

Ne pas recopier un ancien état « en attente » si une décision ultérieure applicable l'a remplacé. Conserver l'attestation et les manifestes figés, ajouter la nouvelle décision dans le WO ou un suivi daté. Une validation propriétaire historique ne couvre pas implicitement un nouveau correctif fonctionnel.

Relier chaque finding à un défaut concret et sa preuve. Vérifier le contenu corrigé et ses contrôles pertinents. Ne pas relancer toutes les suites pour une prose seule sans changement, diagnostic ou exigence applicable ; ne pas déclarer vert un profil omis, un JAR construit sans tests ou un run d'un autre SHA.

## Clôture et livraison

Vérifier la définition de terminé et les décisions déjà acquises. Lorsque les critères et la clôture sont autorisés, suivre la convention du dépôt pour déplacer le WO d'active vers completed et réparer les liens concernés. La présence dans completed ne prouve pas une fusion.

Synchroniser README.md, CHANGELOG.md et les références réellement touchées en une passe. Pour une campagne, distinguer qualification synthétique, permission fournisseur, manifeste, owner-go, consommation et résultat ; reprendre les décisions actuelles sans en révoquer ou renouveler une par simple mise à jour documentaire. Ne pas transformer clôture en nouveau POST, retry ou nettoyage privé.

Effectuer les commits, publications ou fusions déjà autorisés après les vérifications nécessaires, sans demander une seconde autorisation. Une revue seule n'autorise pas une fusion. Consigner les faits durables et référencer les checks du candidat courant sans annoncer une CI future réussie.

## Sortie

Donner état final, fichiers, preuves exécutées, limites et seules décisions encore ouvertes. Préserver la séparation du Lab expérimental et du Betting Project principal.
