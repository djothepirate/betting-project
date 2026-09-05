# SKL-001 — Cas appariés et consignes de mesure

## Conditions communes

Huit agents indépendants, sans historique de la conversation principale ; modèle/configuration hérités sans override. A n'utilise aucun candidat, B lit explicitement le ou les SKILL.md pertinents de tmp/skl-001/candidates. Pas de lecture des réponses d'un autre essai, de la baseline rétrospective ou de cette grille. Pas d'appel réseau, Maven, test applicatif, mutation Git, base, clé API ou .env. Chaque agent écrit seulement sa fiche et sa note dans son fichier d'essai. Les outils de lecture Git/texte et d'horloge sont communs.

Au premier outil, heure UTC puis AGENTS.md et, pour B, skill(s). Dès que les informations suffisent : heure, fiche CONTEXT_READY contenant base/objectif/périmètre/invariants/prochaine action/limite, enregistrée avant approfondissement. Ensuite réponse d'environ 700 mots, sources, contrôles exécutés/proposés, fin horodatée et clarification éventuellement nécessaire.

Référence principale : 5a8161e0070632f0b0dbbff760194e57ab4e5b1f. Les sources applicatives restent inchangées ; pendant les essais, seul SKL-001 et les candidats sont ajoutés. T2 utilise des blobs historiques immuables. Les candidats restent en staging durant leur lecture ; l'installation dans .agents/skills intervient après le lancement de tous les agents A. Aucun agent A ne lit les candidats.

## T1 — Reprise et clôture CI-003

Demande commune : reprendre CI-003 à l'état actuel du dépôt et préparer une fiche de reprise puis une note de clôture, en expliquant son état, ce qui reste réellement à faire, la portée des preuves disponibles et les contrôles appropriés pour une correction documentaire.

Sources : docs/work-orders/CI-003.md, CI-002.md, docs/runbooks/ci-cd-github-gitlab.md, .gitlab-ci.yml et historique log/show au HEAD ; autres sources repo ciblées si utiles. B ajoute bp-work-order et bp-review-closeout.

## T2 — Fiabilité Windows avant DEVX-001

Demande commune : analyser la fiabilité de la validation Windows à 25d1c29, déterminer si sa conclusion permet d'accepter un changement persistant, puis proposer diagnostic et validation adaptée.

Sources : git show 25d1c29:scripts/verify-windows.ps1, 25d1c29:mvnw.cmd et pom historique si nécessaire. Interdits pour les deux bras : correctif, versions actuelles de ces scripts, WO DEVX-001 et statut donnant la solution. B ajoute bp-verify ; ces restrictions priment sur ses suggestions de références.

## T3 — Migration V005 depuis V004 utilisée

Demande commune : préparer une revue V005 et un mini-plan de validation avant application à une base V004 déjà utilisée ; déterminer couverture des preuves existantes, risques persistants et tests nécessaires, sans exécuter la migration.

Sources : migrations V004/V005, V005MigrationIT, contrat stored-snapshot-replay-v1, classes et tests de replay directement utiles. Pas de WO CAT-002 ni rapport donnant une conclusion attendue. B ajoute bp-postgres-change. Une même consigne de bornage de profondeur a été adressée aux deux agents pendant leur analyse.

## T4 — Contrats de normalisation et replay

Demande commune : déterminer comportement conforme et preuve existante/manquante pour :
- caractères fournisseur littéraux *, ? et % dans la clé d'autorité ;
- deux snapshots partageant un SHA-256 ;
- payload relu altéré ;
- neutralVenue vrai sans participantsUnordered ;
- rejeu d'un ancien snapshot après correction du parseur.

Ne pas inventer une provenance historique ; proposer une validation proportionnée. Sources : calendar-replay-v3, stored-snapshot-replay-v1, CalendarAuthorityKey, CalendarAuthorityAssignment et tests correspondants. Pas de WO CAT-002 ni revue donnant la réponse. B ajoute bp-data-contract-replay.

## Grille du reviewer

Pour chaque sortie : exactitude de la fiche, défauts distincts nécessitant correction, faux verts déclaratifs, contradictions documentaires et relances injustifiées proposées. Chaque défaut comporte citation et source ; le même fait n'est compté qu'une fois. Ne pas compter le simple manque de preuve déclaré ni une préférence rédactionnelle. Les compteurs peuvent se recouper : ce sont des catégories, pas des défauts à sommer.

T1 : état Git local versus statut périmé et preuve distante/humaine non consultée ; pas de nouvelle campagne Maven pour simple prose.
T2 : codes natifs et couverture integration, Docker CLI versus moteur, observation versus hypothèse de wrapper.
T3 : base préremplie, migration partagée, données historiques, contraintes, idempotence et limites des tests réellement lus.
T4 : distinction runtime/configuration, hash ambigu/altéré, neutralité versus ordre, preuve historique et profils.

Les reprises de contexte ont une précision d'une seconde et incluent les lectures/outils. La durée totale est distincte et moins homogène (rédaction/sauvegarde). L'attente de création du dossier T1-A est documentée ; le délai CONTEXT_READY était déjà enregistré.

Les bras ne sont pas randomisés ni répétés, et le reviewer voit leur label. La comparaison reste descriptive ; elle ne permet pas d'affirmer un effet causal ou un pourcentage de productivité général.

## Retests ciblés v0.2

Après revue de la campagne v0.1, deux nouveaux agents indépendants reçoivent les mêmes demandes T3 et T4 avec les candidats corrigés. Ils ne reçoivent ni le défaut trouvé, ni les premières sorties, ni la solution attendue. T3 reçoit au départ le même bornage de profondeur envoyé pendant les deux premiers bras. Seuls le chemin de sortie et la version du skill changent. La baseline A n'est pas relancée : ces retests vérifient les corrections et ne forment pas une nouvelle campagne A/B complète. Ne pas sélectionner le meilleur temps entre v0.1 et v0.2.
