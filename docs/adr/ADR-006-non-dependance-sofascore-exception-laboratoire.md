# ADR-006 - Non-dépendance à SofaScore et exception du laboratoire local

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.1
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1; ADR-SS-001

## Résumé de la décision

Le Betting Project principal ne dépend pas de SofaScore et n'appelle pas ses endpoints privés depuis le VPS. Les observations visuelles restent facultatives ; le laboratoire Windows autorisé par ADR-SS-001 demeure isolé et non approuvé pour la production.

## Contexte

SofaScore est utile pour les contrôles humains et certaines données avancées, mais son accès automatisé n'est pas un contrat stable du projet principal.

Le cadrage interdit la dépendance opérationnelle du VPS à des endpoints privés, à une session de navigateur ou au poste Windows.

ADR-SS-001 autorise une expérimentation locale contrôlée dans un dépôt séparé. Cette exception ne doit pas être interprétée comme une intégration de production.

## Décision

Le code et les conteneurs du Betting Project principal ne contactent aucun endpoint privé SofaScore, n'interceptent aucune réponse réseau et ne reproduisent aucune session ou requête de l'application.

SofaScore peut être utilisé par l'opérateur comme source visuelle facultative. Une valeur issue d'une saisie, d'un copier-coller ou d'une capture reste un brouillon jusqu'à validation humaine et est stockée comme VisualObservation avec provenance, heure, confiance et preuve.

L'absence de SofaScore, du navigateur ou du poste Windows ne bloque ni la découverte, ni l'identité, ni l'enrichissement minimal, ni la publication, ni le bilan.

Le projet betting-sofascore-local-lab autorisé par ADR-SS-001 reste séparé : exécution locale Windows uniquement, trafic borné, payloads bruts locaux et aucun composant critique du VPS dépendant de sa disponibilité.

Une exportation éventuelle du laboratoire vers le projet principal utilise un JSON normalisé et versionné, avec provenance SOFASCORE_LOCAL_LAB_EXPORT. Elle ne doit pas être confondue avec une VisualObservation ni devenir obligatoire.

Toute promotion vers la production, tout polling automatique ou toute transmission de payload brut exige un nouvel ADR et une réévaluation des conditions applicables.

## Invariants

- Aucun secret, cookie, jeton, signature ou paramètre technique SofaScore n'est transmis au VPS.
- Aucun navigateur automatisé permanent n'est requis pour le fonctionnement quotidien.
- Une observation visuelle non validée ne devient pas une donnée canonique exploitable.
- Le mode d'acquisition direct du laboratoire n'est jamais enregistré comme observation visuelle.
- Les tests standards du projet principal ne contactent jamais SofaScore.
- Le laboratoire ne constitue ni une source de secours obligatoire ni une condition de réussite du MVP.

## Alternatives étudiées

### Connecteur SofaScore direct dans le VPS

**Résultat :** Rejetée

Dépendance fragile et contraire au cadrage du projet principal.

### Navigateur automatisé permanent

**Résultat :** Rejetée

Incompatible avec l'autonomie recherchée et trop fragile pour la chaîne standard.

### Observation visuelle facultative

**Résultat :** Retenue

Permet un contrôle humain ponctuel avec provenance distincte et sans dépendance critique.

### Laboratoire local isolé

**Résultat :** Retenue sous conditions

Autorisé uniquement par ADR-SS-001 et sans promotion implicite vers la production.

## Conséquences

### Conséquences positives

- Autonomie du VPS et absence de dépendance à une session utilisateur.
- Séparation claire entre API, observation humaine et expérimentation locale.
- Possibilité de bénéficier de contrôles visuels sans contaminer la chaîne permanente.
- Réversibilité complète si SofaScore devient indisponible ou modifie son fonctionnement.

### Coûts, limites et risques acceptés

- Certaines données avancées peuvent nécessiter une vérification manuelle.
- Le laboratoire séparé ajoute une documentation et des règles d'isolation à maintenir.
- Les exports éventuels exigent une validation et une provenance supplémentaires.

## Critères d'acceptation

- [ ] Aucune URL ou route privée SofaScore n'apparaît dans le code du projet principal.
- [ ] La chaîne quotidienne fonctionne avec le connecteur ou bridge SofaScore absent et désactivé.
- [ ] Les observations visuelles possèdent source, mode, heure, confiance, validation et preuve.
- [ ] Les tests rejettent une observation ambiguë ou non validée.
- [ ] Le laboratoire local reste dans un dépôt et une base séparés, sans secret de production.
- [ ] Un export du laboratoire, s'il est testé, porte une version de schéma et une provenance distincte.

## Déclencheurs de réexamen

- Une API officielle ou une autorisation contractuelle adaptée devient disponible.
- Une fonctionnalité du MVP devient dépendante de données uniquement disponibles dans SofaScore.
- Le laboratoire doit être déployé hors du poste Windows ou automatisé.
- Les conditions d'utilisation ou le cadre juridique évoluent substantiellement.
- Un blocage, incident de sécurité ou changement de schéma remet en cause l'expérimentation.

## Références

- PROJECT_BRIEF v0.1, sections 1.3, 3.3, 4.2, 6.2 et 9.1.
- Document de cadrage v0.5.1, sections 2.3 et 8.8.
- ADR-SS-001 - Expérimentation des endpoints SofaScore depuis Windows, accepté le 8 août 2026.
- Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale acceptée sans modification par le porteur. |
