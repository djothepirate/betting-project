# MVP-001 - Fondations de collecte v1

Décisions acceptées par le porteur le 6 septembre 2026 pour les lots 0 et 1.
Ce contrat prépare les lots de collecte ; son existence n'active aucun fournisseur.

## Références et activation

Le [registre des preuves](../benchmark/mvp-001-capability-evidence-v1.md) distingue références
réelles, fixtures synthétiques et lacunes. Aucune saison, phase ou identité manquante n'est déduite
du nom d'une compétition. Le registre classpath initial reste vide et fermé. Les tests démontrent
le fonctionnement sur des références synthétiques. Un code de routage PPL/PD/DED/ELC n'est jamais
un UUID canonique ; les mappings explicites du catalogue restent nécessaires.

Le registre distingue couverture et autorité. PRIMARY exige le rôle PRIMARY, CONTROL le rôle
CONTROL, CALENDAR_ONLY un rôle explicite et le type CALENDAR. PILOT, NON_APPLICABLE et
BLOCKED_BY_PLAN n'accordent aucune autorité. Une entrée inactive reste UNASSIGNED.

## Provenance et replay

La chaîne de collecte future est : intention durable et réservation → tentative auditée → octets
reçus conservés avant parsing → représentation calendrier dérivée → normaliseur existant.
Chaque étape conserve ses liens de provenance. Le hash des octets fournisseur et le hash de la
représentation dérivée sont distincts. La version du parseur est enregistrée ; une nouvelle
interprétation ne réécrit pas une preuve historique. Un replay part des octets stockés sans HTTP.

L'instant observedAt est fixé à la collecte depuis l'instant fournisseur fiable, ou explicitement
depuis la réception avec provenance. Le normaliseur ne fait aucun fallback. L'ordre source et les
valeurs absentes restent représentés, sans tri implicite, zéro inventé ou mapping textuel.

## Budget et interruptions

- Highlightly : capacité de référence 100, plafond opérationnel 80, réserve 20. Ne pas retrancher
  encore 20 du plafond opérationnel 80.
- Réserver avant l'envoi ; inclure les réservations concurrentes dans la disponibilité prudente.
- Comparer budget projet, compteur local et compteur fournisseur ; une absence n'est pas un zéro
  ni une capacité illimitée. L'activation réelle exige une fenêtre et un état explicitement initialisés.
- Ni minuit Europe/Paris ni la seule hausse d'un compteur ne renouvellent une fenêtre.
- Un appel possiblement envoyé dont le résultat n'est pas enregistré conserve un état incertain
  et son coût budgétaire. Aucune relance automatique avant réconciliation explicite.
- HTTP 401/403/429 suspend le fournisseur concerné pour la fenêtre et ouvre un incident.
- L'arrêt d'un fournisseur n'annule pas les preuves déjà conservées pour un autre.

## Bornes des futurs adaptateurs

Connexion : 5 secondes ; requête : 30 secondes ; réponse : 5 Mio au maximum ; collecte : 100 pages
au maximum. Une pagination cyclique, tronquée ou interrompue est explicitement incomplète. Les
pages reçues restent conservées, sans prétendre avoir obtenu un calendrier exhaustif.

La traduction précise des statuts et marqueurs de pagination appartient aux parseurs et fixtures
du lot 3. Un statut inconnu n'est jamais transformé en succès nominal. Les clients ENR restent des
outils de benchmark et ne deviennent pas implicitement les adaptateurs de production.

## Livraison par lot

Chaque lot terminé est validé, commité et poussé sur feature/V0.1.0-RC01-CODEX-MVP-001, puis les
checks du SHA publié sont examinés. Un échec pertinent est corrigé avant progression. Ces pushes
ne créent pas de snapshot durable du train. La PR finale cible feature/V0.1.0-RC01 ; une revue
humaine précède son merge commit. Les lots 0 et 1 ne créent ni migration, ni endpoint, ni worker.
