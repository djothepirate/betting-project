# Skills de développement validés — lot 1

Le propriétaire a validé le 5 septembre 2026 les cinq skills du Betting Project et leurs déclinaisons SofaScore Local Lab, puis autorisé leur livraison intégrale sur les branches `main` des deux applications. Le suivi de livraison Betting Project est [SKL-003](../work-orders/SKL-003.md).

Les [vérifications de livraison](SKL-003-delivery-validation.md) couvrent les fichiers indexés, leurs empreintes, les métadonnées et la documentation. L'état de publication/fusion et les checks courants restent consultables dans la PR de la branche `codex/skl-003-skills-delivery`.

## Betting Project

Les cinq skills se trouvent dans `.agents/skills`, à portée dépôt. Codex les découvre depuis les checkouts et worktrees qui contiennent cette livraison, sans installation personnelle supplémentaire.

| Skill | Usage |
|---|---|
| [bp-work-order](../../.agents/skills/bp-work-order/SKILL.md) | Reprise de contexte, cadrage et critères d'un lot. |
| [bp-verify](../../.agents/skills/bp-verify/SKILL.md) | Choix des contrôles, diagnostic Windows/Linux et faux verts. |
| [bp-postgres-change](../../.agents/skills/bp-postgres-change/SKILL.md) | Migrations, transactions, concurrence et preuves PostgreSQL. |
| [bp-data-contract-replay](../../.agents/skills/bp-data-contract-replay/SKILL.md) | Contrats, parseurs, provenance et replay hors réseau. |
| [bp-review-closeout](../../.agents/skills/bp-review-closeout/SKILL.md) | Revue, décisions propriétaire et clôture documentée. |

Exemple : `Utilise $bp-work-order et $bp-verify pour reprendre ce WO et vérifier les preuves du correctif.` Les autorisations de la tâche restent applicables ; sélectionner un skill ne crée pas une permission de publication ou de campagne.

## Sources et preuves conservées

- [Inventaire des compétences](Inventaire-competences-et-skills-2026-09-05.md), version historique du 5 septembre, conservée avec son empreinte d'origine.
- [SKL-001](../work-orders/SKL-001.md), [protocole](SKL-001-cases.md), [évaluation](SKL-001-evaluation.md), [candidats](evaluations/SKL-001/candidate-manifest.json) et [preuves signées](evaluations/SKL-001/evidence-manifest.json).
- [SKL-002](../work-orders/SKL-002.md), [installation Lab](SKL-002-local-lab-installation.md) et [essais Lab](evaluations/SKL-002/review.md).

Les fichiers de `evaluations` sont des attestations historiques. Leurs chemins locaux décrivent le poste observé, sans constituer des dépendances de fonctionnement ; leurs temps, états et totaux ne remplacent pas les preuves d'une nouvelle tâche. Les attributs Git préservent les octets signés, y compris les fins de ligne. Les rapports de tête distinguent leurs décisions initiales de l'autorisation de livraison ultérieure.

## SofaScore Local Lab

Les [sources ss-* archivées ici](local-lab/) correspondent à la version évaluée lors de SKL-002. Le paquet maintenu et son installateur sont livrés dans le [dépôt SofaScore Local Lab](https://github.com/djothepirate/betting-sofascore-local-lab/tree/main/docs/skills). Utiliser cet installateur depuis le Lab pour placer les cinq skills à portée utilisateur dans `.agents/skills` du compte courant. Cela couvre aussi les anciens worktrees et évite de déclarer une seconde copie à portée dépôt portant les mêmes noms.

Cette documentation et ces sources d'outillage ne créent aucune dépendance applicative entre les deux dépôts. Les dix fichiers des skills BP et les dix fichiers des skills Lab restent identiques aux versions validées.
